package com.printerhub.core.adapter;

import java.util.List;

/**
 * Status snapshot for one AMS unit. unitId matches the "id" field in the MQTT ams array.
 * A printer with no AMS will have amsList = null in PrinterStatusUpdate.
 * A printer with one AMS unit will have a single-element list here.
 */
public record AmsStatus(
        String unitId,
        List<AmsTray> trays
) {}
