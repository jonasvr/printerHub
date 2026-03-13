package com.printerhub.service;

import com.printerhub.core.adapter.PrinterAdapter;
import com.printerhub.core.adapter.PrinterStatusUpdate;
import com.printerhub.core.entity.Printer;
import com.printerhub.core.repository.PrinterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PreDestroy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Core business logic for printer management.
 *
 * Responsibilities:
 * 1. Register / deregister printers (connect adapters)
 * 2. Broadcast status updates over WebSocket on a fixed schedule
 * 3. Delegate control commands (pause/resume/cancel) to the right adapter
 *
 * The @Transactional annotations on write methods ensure that if a DB
 * write and an MQTT command both need to happen, they stay consistent —
 * if one fails, neither persists.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrinterService {

    private final PrinterRepository printerRepository;
    private final AdapterRegistry adapterRegistry;
    private final SimpMessagingTemplate messagingTemplate; // sends WebSocket messages

    // Exponential backoff state — keyed by printer ID
    private static final long MIN_RETRY_MS =   30_000L;
    private static final long MAX_RETRY_MS =  600_000L;
    private final Map<UUID, Long> retryDelayMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextRetryAt  = new ConcurrentHashMap<>();

    // ── Startup ─────────────────────────────────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void connectAllPrintersOnStartup() {
        List<Printer> active = printerRepository.findAllByActiveTrue();
        log.info("Reconnecting {} printer(s) on startup", active.size());
        active.stream()
              .map(printer -> CompletableFuture.runAsync(() -> connectSafely(printer)))
              .toList()                                        // start all before waiting
              .forEach(CompletableFuture::join);              // wait so startup log is clean
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 60_000)
    public void retryDisconnectedPrinters() {
        long now = System.currentTimeMillis();
        printerRepository.findAllByActiveTrue().stream()
            .filter(printer -> !adapterRegistry.forBrand(printer.getBrand())
                                               .getStatus(printer.getId()).mqttConnected())
            .filter(printer -> now >= nextRetryAt.getOrDefault(printer.getId(), 0L))
            .map(printer -> CompletableFuture.runAsync(() -> {
                log.info("Retrying MQTT connection for printer {}", printer.getName());
                boolean ok = connectSafely(printer);
                if (ok) {
                    retryDelayMs.remove(printer.getId());
                    nextRetryAt.remove(printer.getId());
                } else {
                    long delay = Math.min(
                        retryDelayMs.getOrDefault(printer.getId(), MIN_RETRY_MS) * 2,
                        MAX_RETRY_MS);
                    retryDelayMs.put(printer.getId(), delay);
                    nextRetryAt.put(printer.getId(), System.currentTimeMillis() + delay);
                    log.info("Next retry for printer {} in {} s", printer.getName(), delay / 1000);
                }
            }))
            .toList()
            .forEach(CompletableFuture::join);
    }

    @PreDestroy
    public void disconnectAllOnShutdown() {
        log.info("Shutting down — disconnecting all printers");
        printerRepository.findAllByActiveTrue().forEach(printer -> {
            try {
                adapterRegistry.forBrand(printer.getBrand()).disconnect(printer.getId());
            } catch (Exception e) {
                log.warn("Error disconnecting printer {} on shutdown: {}", printer.getName(), e.getMessage());
            }
        });
    }

    private boolean connectSafely(Printer printer) {
        try {
            adapterRegistry.forBrand(printer.getBrand()).connect(printer);
            return true;
        } catch (Exception e) {
            log.error("Failed to connect printer {}: {}", printer.getName(), e.getMessage());
            return false;
        }
    }

    // ── Registration ────────────────────────────────────────────────────────

    @Transactional
    public Printer registerPrinter(Printer printer) {
        Printer saved = printerRepository.save(printer);
        // Immediately open a connection via the appropriate adapter
        adapterRegistry.forBrand(saved.getBrand()).connect(saved);
        return saved;
    }

    @Transactional
    public Printer updatePrinter(UUID printerId, Printer patch) {
        Printer existing = findOrThrow(printerId);
        existing.setName(patch.getName());
        existing.setModel(patch.getModel());
        existing.setSerialNumber(patch.getSerialNumber());
        existing.setIpAddress(patch.getIpAddress());
        if (patch.getAccessCode() != null && !patch.getAccessCode().isBlank()) {
            existing.setAccessCode(patch.getAccessCode());
        }
        existing.setBrand(patch.getBrand());
        Printer saved = printerRepository.save(existing);
        // Reconnect so the adapter picks up any changed IP/credentials
        retryDelayMs.remove(printerId);
        nextRetryAt.remove(printerId);
        adapterRegistry.forBrand(saved.getBrand()).disconnect(printerId);
        adapterRegistry.forBrand(saved.getBrand()).connect(saved);
        return saved;
    }

    @Transactional
    public void deregisterPrinter(UUID printerId) {
        Printer printer = findOrThrow(printerId);
        adapterRegistry.forBrand(printer.getBrand()).disconnect(printerId);
        printer.setActive(false);
        printerRepository.save(printer);
    }

    // ── Status broadcast ────────────────────────────────────────────────────

    /**
     * Every 2 seconds, poll each active printer's adapter and push the latest
     * status over WebSocket to all subscribed browser clients.
     *
     * fixedDelay = wait 2s between the *end* of one run and the *start* of the next.
     * This avoids pile-up if an adapter call is slow.
     *
     * The destination "/topic/printers/{id}" lets Angular subscribe only to
     * the printers it cares about (e.g. the ones visible on screen).
     */
    @Scheduled(fixedDelay = 2000)
    public void broadcastStatusUpdates() {
        List<Printer> active = printerRepository.findAllByActiveTrue();
        for (Printer printer : active) {
            try {
                PrinterAdapter adapter = adapterRegistry.forBrand(printer.getBrand());
                PrinterStatusUpdate status = CompletableFuture
                        .supplyAsync(() -> adapter.getStatus(printer.getId()))
                        .get(500, TimeUnit.MILLISECONDS);
                messagingTemplate.convertAndSend("/topic/printers/" + printer.getId(), status);
            } catch (TimeoutException e) {
                log.warn("Status timeout for printer {}", printer.getName());
            } catch (Exception e) {
                log.warn("Failed to get status for printer {}: {}", printer.getName(), e.getMessage());
            }
        }
    }

    // ── Control commands ────────────────────────────────────────────────────

    public void pause(UUID printerId) {
        PrinterAdapter adapter = adapterForPrinter(printerId);
        adapter.pause(printerId);
    }

    public void resume(UUID printerId) {
        adapterForPrinter(printerId).resume(printerId);
    }

    public void cancel(UUID printerId) {
        adapterForPrinter(printerId).cancel(printerId);
    }

    // ── Queries ─────────────────────────────────────────────────────────────

    public List<Printer> getAllActivePrinters() {
        return printerRepository.findAllByActiveTrue();
    }

    public List<com.printerhub.core.adapter.MqttLogEntry> getMqttLogs(UUID printerId) {
        Printer printer = findOrThrow(printerId);
        return adapterRegistry.forBrand(printer.getBrand()).getRecentLogs(printerId);
    }

    public Printer getPrinter(UUID printerId) {
        return findOrThrow(printerId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Printer findOrThrow(UUID printerId) {
        return printerRepository.findById(printerId)
                .orElseThrow(() -> new IllegalArgumentException("Printer not found: " + printerId));
    }

    private PrinterAdapter adapterForPrinter(UUID printerId) {
        Printer printer = findOrThrow(printerId);
        return adapterRegistry.forBrand(printer.getBrand());
    }
}
