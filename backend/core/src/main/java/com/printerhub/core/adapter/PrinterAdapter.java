package com.printerhub.core.adapter;

import com.printerhub.core.entity.Printer;
import com.printerhub.core.entity.PrinterBrand;

import java.util.List;
import java.util.UUID;

/**
 * The contract every brand adapter must fulfill.
 *
 * This is the heart of PrinterHub's pluggable architecture.
 * Adding support for a new brand means:
 *   1. Create a new Maven module (e.g. prusa-adapter)
 *   2. Implement this interface
 *   3. Register it as a Spring @Component
 *
 * The AdapterRegistry (in cloud-service) will auto-discover all
 * implementations via Spring's dependency injection.
 *
 * Why an interface instead of an abstract class?
 * - Forces each adapter to be fully self-contained
 * - Easy to mock in unit tests
 * - No risk of shared mutable state creeping in via a base class
 */
public interface PrinterAdapter {

    /**
     * Which brand this adapter handles.
     * Used by AdapterRegistry to route printer events to the right adapter.
     */
    PrinterBrand getSupportedBrand();

    /**
     * Open a connection to the printer.
     * For Bambu: subscribe to MQTT topics.
     * For Prusa: start the HTTP polling loop.
     */
    void connect(Printer printer);

    /**
     * Release resources for this printer (called on deregister or shutdown).
     */
    void disconnect(UUID printerId);

    /**
     * Return the latest known status snapshot.
     * For MQTT adapters this returns cached data (the broker pushes updates);
     * for HTTP adapters this may trigger a live poll.
     */
    PrinterStatusUpdate getStatus(UUID printerId);

    /** Send a pause command to the printer. */
    void pause(UUID printerId);

    /** Resume a paused print. */
    void resume(UUID printerId);

    /** Cancel the current print. */
    void cancel(UUID printerId);

    /**
     * Return the most recent raw MQTT log entries for this printer.
     * Default returns empty — only adapters that buffer logs need to override this.
     */
    default List<MqttLogEntry> getRecentLogs(UUID printerId) {
        return List.of();
    }

    /**
     * Ask the printer for a full status dump right now (e.g. Bambu pushall).
     * Default is a no-op — adapters that support on-demand refresh override this.
     */
    default void requestFullStatus(UUID printerId) {}
}
