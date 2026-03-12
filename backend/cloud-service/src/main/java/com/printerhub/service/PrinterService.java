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

import java.util.List;
import java.util.UUID;

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

    // ── Startup ─────────────────────────────────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void connectAllPrintersOnStartup() {
        List<Printer> active = printerRepository.findAllByActiveTrue();
        log.info("Reconnecting {} printer(s) on startup", active.size());
        for (Printer printer : active) {
            try {
                adapterRegistry.forBrand(printer.getBrand()).connect(printer);
            } catch (Exception e) {
                log.error("Failed to reconnect printer {} on startup: {}", printer.getName(), e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 30_000)
    public void retryDisconnectedPrinters() {
        printerRepository.findAllByActiveTrue().forEach(printer -> {
            PrinterStatusUpdate status = adapterRegistry.forBrand(printer.getBrand())
                                                        .getStatus(printer.getId());
            if (!status.mqttConnected()) {
                log.info("Retrying MQTT connection for printer {}", printer.getName());
                adapterRegistry.forBrand(printer.getBrand()).connect(printer);
            }
        });
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
        existing.setAccessCode(patch.getAccessCode());
        existing.setBrand(patch.getBrand());
        Printer saved = printerRepository.save(existing);
        // Reconnect so the adapter picks up any changed IP/credentials
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
                PrinterStatusUpdate status = adapter.getStatus(printer.getId());
                messagingTemplate.convertAndSend("/topic/printers/" + printer.getId(), status);
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
