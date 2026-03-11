package com.printerhub.adapter.bambu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.printerhub.core.adapter.PrinterAdapter;
import com.printerhub.core.adapter.PrinterStatusUpdate;
import com.printerhub.core.entity.Printer;
import com.printerhub.core.entity.PrinterBrand;
import com.printerhub.core.entity.PrinterState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bambu Lab printer adapter — communicates via MQTT.
 *
 * How Bambu MQTT works:
 * - Each printer runs a small MQTT broker.
 * - We connect as a client using the printer's serial number + access code.
 * - Topic layout:
 *     device/{serial}/report  ← printer pushes status JSON here
 *     device/{serial}/request → we publish commands here
 *
 * This class manages one MqttClient per printer, storing them in a map
 * so we can disconnect/reconnect individual printers without affecting others.
 *
 * Phase 1 simplification: we parse only the fields needed for the dashboard.
 * The full Bambu JSON schema has ~200 fields; we expand parsing in later phases.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BambuAdapter implements PrinterAdapter, MqttCallback {

    private final BambuMqttProperties props;
    private final ObjectMapper objectMapper;

    // Keyed by printerId → active MQTT client
    private final Map<UUID, MqttClient> clients = new ConcurrentHashMap<>();

    // Latest status snapshot per printer (updated whenever the broker pushes a message)
    private final Map<UUID, PrinterStatusUpdate> statusCache = new ConcurrentHashMap<>();

    // Serial ↔ printerId — populated in connect(), avoids DB lookups on every MQTT message
    private final Map<String, UUID> serialToId = new ConcurrentHashMap<>();
    private final Map<UUID, String> idToSerial = new ConcurrentHashMap<>();

    // ── PrinterAdapter contract ──────────────────────────────────────────────

    @Override
    public PrinterBrand getSupportedBrand() {
        return PrinterBrand.BAMBU;
    }

    @Override
    public void connect(Printer printer) {
        String protocol  = "ssl://";
        String brokerUrl = protocol + printer.getIpAddress() + ":" + props.getMqttPort();
        String clientId  = "printerhub-" + printer.getId();

        try {
            MqttClient client = new MqttClient(brokerUrl, clientId);
            client.setCallback(this);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            // Bambu uses serial number + access code as MQTT credentials
            options.setUserName("bblp");
            options.setPassword(printer.getAccessCode().toCharArray());

            if (props.isSkipTlsVerify()) {
                options.setSocketFactory(trustAllSocketFactory());
            }

            client.connect(options);

            // Subscribe to the printer's report topic
            String reportTopic = "device/" + printer.getSerialNumber() + "/report";
            client.subscribe(reportTopic, 0);

            clients.put(printer.getId(), client);
            serialToId.put(printer.getSerialNumber(), printer.getId());
            idToSerial.put(printer.getId(), printer.getSerialNumber());
            log.info("Connected to Bambu printer {} ({})", printer.getName(), printer.getSerialNumber());

        } catch (MqttException e) {
            log.error("Failed to connect to Bambu printer {}: {}", printer.getName(), e.getMessage());
            // Put an OFFLINE snapshot so the dashboard shows a meaningful state
            cacheOfflineStatus(printer.getId());
        }
    }

    @Override
    public void disconnect(UUID printerId) {
        MqttClient client = clients.remove(printerId);
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
            } catch (MqttException e) {
                log.warn("Error disconnecting printer {}: {}", printerId, e.getMessage());
            }
        }
        String serial = idToSerial.remove(printerId);
        if (serial != null) serialToId.remove(serial);
        statusCache.remove(printerId);
    }

    @Override
    public PrinterStatusUpdate getStatus(UUID printerId) {
        // Return cached value; MQTT adapter is push-based (printer sends updates)
        return statusCache.getOrDefault(printerId, offlineStatus(printerId));
    }

    @Override
    public void pause(UUID printerId) {
        publishCommand(printerId, buildCommand("pause"));
    }

    @Override
    public void resume(UUID printerId) {
        publishCommand(printerId, buildCommand("resume"));
    }

    @Override
    public void cancel(UUID printerId) {
        publishCommand(printerId, buildCommand("stop"));
    }

    // ── MqttCallback ────────────────────────────────────────────────────────

    /**
     * Called by the Paho client whenever a message arrives from the broker.
     * We parse the Bambu JSON payload and update our status cache.
     * The topic format is "device/{serial}/report" — we use the serial
     * to look up which printerId to update.
     */
    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String serial = extractSerial(topic);
            UUID printerId = serialToId.get(serial);
            if (printerId == null) return;

            JsonNode root = objectMapper.readTree(message.getPayload());
            PrinterStatusUpdate update = parseBambuPayload(printerId, root);
            statusCache.put(printerId, update);
            log.debug("MQTT update from {} — state={}, progress={}%, nozzle={}°C",
                    serial, update.state(), update.progressPercent(), update.nozzleTempActual());

        } catch (Exception e) {
            log.debug("Could not parse Bambu MQTT message: {}", e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("Lost MQTT connection: {}", cause.getMessage());
        // TODO Phase 2: implement reconnect with exponential back-off
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Unused — QoS 0 fire-and-forget for commands
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Parses the Bambu "print" sub-object from the report JSON.
     * The real payload is much larger; this extracts only what the dashboard needs.
     */
    private PrinterStatusUpdate parseBambuPayload(UUID printerId, JsonNode root) {
        JsonNode print = root.path("print");

        String gcodeState = print.path("gcode_state").asText("IDLE");
        PrinterState state = mapBambuState(gcodeState);

        return new PrinterStatusUpdate(
                printerId,
                state,
                print.path("mc_percent").asDouble(0),      // progress %
                print.path("subtask_name").asText(null),   // file name
                print.path("bed_temper").asInt(0),
                print.path("bed_target_temper").asInt(0),
                print.path("nozzle_temper").asInt(0),
                print.path("nozzle_target_temper").asInt(0),
                print.path("mc_remaining_time").asInt(-1), // minutes remaining
                Instant.now()
        );
    }

    private PrinterState mapBambuState(String gcodeState) {
        return switch (gcodeState.toUpperCase()) {
            case "RUNNING"  -> PrinterState.PRINTING;
            case "PAUSE"    -> PrinterState.PAUSED;
            case "FAILED"   -> PrinterState.ERROR;
            case "FINISH"   -> PrinterState.IDLE;
            default         -> PrinterState.IDLE;
        };
    }

    private void publishCommand(UUID printerId, String payload) {
        MqttClient client = clients.get(printerId);
        if (client == null || !client.isConnected()) {
            log.warn("Cannot send command — printer {} not connected", printerId);
            return;
        }
        String serial = idToSerial.get(printerId);
        if (serial == null) {
            log.warn("Cannot send command — no serial found for printer {}", printerId);
            return;
        }
        try {
            client.publish("device/" + serial + "/request", new MqttMessage(payload.getBytes()));
        } catch (MqttException e) {
            log.error("Failed to publish command to {}: {}", printerId, e.getMessage());
        }
    }

    private String buildCommand(String action) {
        // Bambu command format (simplified) — full schema documented in docs/bambu-api.md
        return "{\"print\":{\"command\":\"" + action + "\",\"sequence_id\":\"0\"}}";
    }

    private String extractSerial(String topic) {
        // topic = "device/{serial}/report"
        String[] parts = topic.split("/");
        return parts.length >= 2 ? parts[1] : "unknown";
    }

    /**
     * Returns an SSL socket factory that accepts any certificate.
     * Used for Bambu printers which ship with self-signed TLS certs.
     * Phase 3: replace with a proper trust store.
     */
    private javax.net.ssl.SSLSocketFactory trustAllSocketFactory() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new java.security.SecureRandom());
            return ctx.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create trust-all SSL factory", e);
        }
    }

    private void cacheOfflineStatus(UUID printerId) {
        statusCache.put(printerId, offlineStatus(printerId));
    }

    private PrinterStatusUpdate offlineStatus(UUID printerId) {
        return new PrinterStatusUpdate(
                printerId, PrinterState.OFFLINE,
                0, null, 0, 0, 0, 0, -1, Instant.now()
        );
    }
}
