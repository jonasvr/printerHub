package com.printerhub.core.adapter;

import java.time.Instant;
import java.util.UUID;

/**
 * A single raw MQTT message as received from a printer.
 * Stored in BambuAdapter's in-memory ring buffer and broadcast to
 * the frontend for the live-log dialog.
 */
public record MqttLogEntry(
        UUID printerId,
        Instant timestamp,
        String payload   // raw JSON string from the broker
) {}
