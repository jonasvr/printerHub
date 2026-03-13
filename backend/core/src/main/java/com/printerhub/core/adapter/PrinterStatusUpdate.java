package com.printerhub.core.adapter;

import com.printerhub.core.entity.PrinterState;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot of a printer's current state, returned by PrinterAdapter.getStatus().
 *
 * Using a Java record here: immutable, no boilerplate, ideal for a DTO
 * that is created once and passed around without modification.
 *
 * Temperatures use int (Celsius integers) — printer firmware never reports
 * fractional degrees in practice.
 */
public record PrinterStatusUpdate(
        UUID printerId,
        PrinterState state,
        double progressPercent,    // 0.0 – 100.0
        String currentFile,        // null when idle
        int bedTempActual,
        int bedTempTarget,
        int nozzleTempActual,
        int nozzleTempTarget,
        int remainingMinutes,      // -1 when unknown
        Instant timestamp,
        boolean mqttConnected,     // true when the MQTT socket is open
        String connectionError,    // null = connected; non-null = human-readable failure reason
        int chamberTempActual,     // 0 = absent or unknown
        int layerCurrent,          // 0 = not printing
        int layerTotal,            // 0 = not printing
        int speedPercent,          // spd_mag from Bambu; 0 = unknown
        List<HmsAlert> hmsAlerts,  // empty = no alerts; null treated as no alerts
        List<AmsStatus> amsList    // null = no AMS hardware present
) {}
