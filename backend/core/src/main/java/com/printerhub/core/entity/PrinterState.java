package com.printerhub.core.entity;

/**
 * The operational state of a printer at any given moment.
 * This is what gets broadcast over WebSocket to the dashboard.
 */
public enum PrinterState {
    IDLE,      // Connected, ready to print
    PRINTING,  // Actively printing
    PAUSED,    // Print paused (by user or error)
    ERROR,     // Printer reported an error
    OFFLINE    // Can't reach the printer
}
