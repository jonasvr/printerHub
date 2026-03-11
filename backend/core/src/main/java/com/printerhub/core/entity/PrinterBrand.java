package com.printerhub.core.entity;

/**
 * Supported printer brands.
 * Each brand maps to exactly one PrinterAdapter implementation.
 * Adding a new brand = add an enum value + create a new adapter module.
 */
public enum PrinterBrand {
    BAMBU,       // Bambu Lab (X1C, P1S, A1) — MQTT
    PRUSA,       // Prusa Research (MK4, XL) — HTTP polling
    CREALITY,    // Creality (Ender, K1) — HTTP polling
    GENERIC_HTTP // Catch-all for any printer with an HTTP API
}
