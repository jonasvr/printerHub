package com.printerhub.core.adapter;

/**
 * A single filament tray slot in a Bambu AMS unit.
 * colorHex is 6-char RGB (alpha stripped from Bambu's 8-char RGBA format).
 * isActive is true when this tray is the one currently feeding filament.
 */
public record AmsTray(
        String id,
        String filamentType,
        String subBrand,
        String colorHex,
        boolean isActive
) {}
