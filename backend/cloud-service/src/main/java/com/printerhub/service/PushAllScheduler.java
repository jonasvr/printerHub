package com.printerhub.service;

import com.printerhub.core.repository.PrinterRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Periodically sends a pushall command to every connected printer so the
 * dashboard always has fresh data without waiting for the next broker push.
 *
 * The interval is configurable at runtime via the /api/v1/settings endpoint.
 * Default is 60 s; valid range 30–300 s (enforced in the controller).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushAllScheduler {

    private final PrinterRepository printerRepository;
    private final AdapterRegistry   adapterRegistry;

    @Value("${printerhub.pushall-interval-seconds:60}")
    private long intervalSeconds;

    private final ScheduledExecutorService executor =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pushall-scheduler");
            t.setDaemon(true);
            return t;
        });

    private volatile ScheduledFuture<?> future;

    @PostConstruct
    public void init() {
        reschedule();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public long getIntervalSeconds() {
        return intervalSeconds;
    }

    public synchronized void setIntervalSeconds(long seconds) {
        intervalSeconds = seconds;
        reschedule();
        log.info("Pushall interval updated to {} s", seconds);
    }

    private synchronized void reschedule() {
        if (future != null) future.cancel(false);
        future = executor.scheduleWithFixedDelay(
            this::sendPushAll, intervalSeconds, intervalSeconds, TimeUnit.SECONDS
        );
    }

    private void sendPushAll() {
        printerRepository.findAllByActiveTrue().forEach(printer -> {
            try {
                adapterRegistry.forBrand(printer.getBrand())
                               .requestFullStatus(printer.getId());
            } catch (Exception e) {
                log.warn("Pushall failed for printer {}: {}", printer.getName(), e.getMessage());
            }
        });
    }
}
