package com.printerhub.core.adapter;

/**
 * A single Health Monitoring System (HMS) alert from the Bambu MQTT payload.
 *
 * attr encodes: module (bits 31-24), submodule (bits 23-16), severity (bits 15-8), detail (bits 7-0)
 * code identifies the specific fault within that module.
 * Both fields can exceed Integer.MAX_VALUE (Bambu uses unsigned 32-bit), so long is used.
 */
public record HmsAlert(long attr, long code) {}
