package com.printerhub.adapter.bambu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.printerhub.core.adapter.MqttLogEntry;
import com.printerhub.core.adapter.MqttMessageEvent;
import com.printerhub.core.adapter.PrinterAdapter;
import com.printerhub.core.adapter.PrinterStatusUpdate;
import com.printerhub.core.entity.Printer;
import com.printerhub.core.entity.PrinterBrand;
import com.printerhub.core.entity.PrinterState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
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
    private final ApplicationEventPublisher eventPublisher;

    // Keyed by printerId → active MQTT client
    private final Map<UUID, MqttClient> clients = new ConcurrentHashMap<>();

    // Latest status snapshot per printer (updated whenever the broker pushes a message)
    private final Map<UUID, PrinterStatusUpdate> statusCache = new ConcurrentHashMap<>();

    // Serial ↔ printerId — populated in connect(), avoids DB lookups on every MQTT message
    private final Map<String, UUID> serialToId = new ConcurrentHashMap<>();
    private final Map<UUID, String> idToSerial = new ConcurrentHashMap<>();

    // Ring buffer of the last 100 raw MQTT messages per printer, for the log dialog
    private static final int LOG_BUFFER_SIZE = 100;
    private final Map<UUID, Deque<MqttLogEntry>> logBuffers = new ConcurrentHashMap<>();

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
            // Seed cache so the dashboard reflects the connection immediately,
            // before the first MQTT message arrives (which may take a few seconds).
            // Also clears any connectionError from a previous failed attempt.
            statusCache.put(printer.getId(), new PrinterStatusUpdate(
                    printer.getId(), PrinterState.IDLE,
                    0, null, 0, 0, 0, 0, -1, Instant.now(), true, null
            ));
            log.info("Connected to Bambu printer {} ({})", printer.getName(), printer.getSerialNumber());

        } catch (MqttException e) {
            String hint = switch (e.getReasonCode()) {
                case MqttException.REASON_CODE_BROKER_UNAVAILABLE ->
                    "Broker unavailable — is the printer powered on and reachable at " + printer.getIpAddress() + "?";
                case MqttException.REASON_CODE_FAILED_AUTHENTICATION,
                     MqttException.REASON_CODE_NOT_AUTHORIZED ->
                    "Authentication failed — check the access code.";
                default ->
                    "Connection failed (MQTT code " + e.getReasonCode() + ")";
            };
            log.error("Failed to connect to Bambu printer {} (reason code {}): {}", printer.getName(), e.getReasonCode(), hint);
            statusCache.put(printer.getId(), offlineStatus(printer.getId(), hint));
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
        PrinterStatusUpdate cached = statusCache.get(printerId);
        if (cached == null) return offlineStatus(printerId);
        // Re-stamp mqttConnected with the live socket state each poll cycle
        boolean connected = clients.containsKey(printerId) && clients.get(printerId).isConnected();
        if (cached.mqttConnected() == connected) return cached;
        return new PrinterStatusUpdate(
                cached.printerId(), cached.state(), cached.progressPercent(),
                cached.currentFile(), cached.bedTempActual(), cached.bedTempTarget(),
                cached.nozzleTempActual(), cached.nozzleTempTarget(),
                cached.remainingMinutes(), cached.timestamp(), connected, cached.connectionError()
        );
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

    @Override
    public List<MqttLogEntry> getRecentLogs(UUID printerId) {
        Deque<MqttLogEntry> buf = logBuffers.get(printerId);
        if (buf == null) return List.of();
        // Return a snapshot (newest-first) so the UI shows recent messages at the top
        List<MqttLogEntry> snapshot = new ArrayList<>(buf);
        java.util.Collections.reverse(snapshot);
        return snapshot;
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
            String rawJson = objectMapper.writeValueAsString(root);
            log.debug("Raw MQTT [{}]: {}", serial, rawJson);

            // Buffer the raw message and fire an event for the WebSocket log broadcaster
            MqttLogEntry logEntry = new MqttLogEntry(printerId, Instant.now(), rawJson);
            logBuffers.computeIfAbsent(printerId, id -> new ArrayDeque<>())
                      .addLast(logEntry);
            if (logBuffers.get(printerId).size() > LOG_BUFFER_SIZE) {
                logBuffers.get(printerId).pollFirst();
            }
            eventPublisher.publishEvent(new MqttMessageEvent(this, logEntry));

            JsonNode print = root.path("print");
            PrinterStatusUpdate update;
            if (!print.path("gcode_state").isMissingNode()) {
                // Full status snapshot — replace cache entirely
                update = parseBambuPayload(printerId, root);
            } else {
                // Partial message — merge temps/progress into existing snapshot if present
                PrinterStatusUpdate current = statusCache.get(printerId);
                if (current == null) return; // nothing to merge into yet
                boolean hasData = !print.path("nozzle_temper").isMissingNode()
                        || !print.path("bed_temper").isMissingNode()
                        || !print.path("mc_percent").isMissingNode();
                if (!hasData) return;
                update = new PrinterStatusUpdate(
                        printerId,
                        current.state(),
                        print.path("mc_percent").isMissingNode()     ? current.progressPercent()  : print.path("mc_percent").asDouble(),
                        current.currentFile(),
                        print.path("bed_temper").isMissingNode()      ? current.bedTempActual()    : print.path("bed_temper").asInt(),
                        current.bedTempTarget(),
                        print.path("nozzle_temper").isMissingNode()   ? current.nozzleTempActual() : print.path("nozzle_temper").asInt(),
                        current.nozzleTempTarget(),
                        current.remainingMinutes(),
                        Instant.now(),
                        true,
                        null
                );
            }
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
        clients.forEach((printerId, client) -> {
            if (!client.isConnected()) {
                statusCache.put(printerId, offlineStatus(printerId,
                    "Connection lost — " + cause.getMessage()));
            }
        });
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
                print.path("mc_percent").asDouble(0),
                print.path("subtask_name").asText(null),
                print.path("bed_temper").asInt(0),
                print.path("bed_target_temper").asInt(0),
                print.path("nozzle_temper").asInt(0),
                print.path("nozzle_target_temper").asInt(0),
                print.path("mc_remaining_time").asInt(-1),
                Instant.now(),
                true,  // message arrived → MQTT is connected
                null   // no error
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
     * Returns an SSL socket factory that:
     * 1. Trusts any certificate (Bambu ships self-signed certs)
     * 2. Disables hostname verification (Bambu cert has no SAN for the LAN IP)
     * Phase 3: replace with a proper trust store and cert pinning.
     */
    private SSLSocketFactory trustAllSocketFactory() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509ExtendedTrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                    public void checkClientTrusted(X509Certificate[] c, String a, Socket s) {}
                    public void checkServerTrusted(X509Certificate[] c, String a, Socket s) {}
                    public void checkClientTrusted(X509Certificate[] c, String a, SSLEngine e) {}
                    public void checkServerTrusted(X509Certificate[] c, String a, SSLEngine e) {}
                }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new java.security.SecureRandom());
            SSLSocketFactory base = ctx.getSocketFactory();

            // Wrap factory so every socket has hostname verification disabled.
            // Java does hostname verification separately from cert trust —
            // setEndpointIdentificationAlgorithm(null) disables it per-socket.
            return new SSLSocketFactory() {
                private SSLSocket noHostnameVerification(Socket s) {
                    SSLParameters params = ((SSLSocket) s).getSSLParameters();
                    params.setEndpointIdentificationAlgorithm(null);
                    ((SSLSocket) s).setSSLParameters(params);
                    return (SSLSocket) s;
                }
                public String[] getDefaultCipherSuites() { return base.getDefaultCipherSuites(); }
                public String[] getSupportedCipherSuites() { return base.getSupportedCipherSuites(); }
                // Paho calls the no-arg form to get an unconnected socket, then connects it.
                // Must override here; the default SSLSocketFactory throws "Unconnected sockets not implemented".
                public Socket createSocket() throws IOException {
                    return noHostnameVerification(base.createSocket());
                }
                public Socket createSocket(Socket s, String h, int port, boolean ac) throws IOException {
                    return noHostnameVerification(base.createSocket(s, h, port, ac));
                }
                public Socket createSocket(String h, int port) throws IOException {
                    return noHostnameVerification(base.createSocket(h, port));
                }
                public Socket createSocket(String h, int port, InetAddress l, int lp) throws IOException {
                    return noHostnameVerification(base.createSocket(h, port, l, lp));
                }
                public Socket createSocket(InetAddress h, int port) throws IOException {
                    return noHostnameVerification(base.createSocket(h, port));
                }
                public Socket createSocket(InetAddress h, int port, InetAddress l, int lp) throws IOException {
                    return noHostnameVerification(base.createSocket(h, port, l, lp));
                }
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to create trust-all SSL factory", e);
        }
    }

    private PrinterStatusUpdate offlineStatus(UUID printerId, String error) {
        return new PrinterStatusUpdate(
                printerId, PrinterState.OFFLINE,
                0, null, 0, 0, 0, 0, -1, Instant.now(), false, error
        );
    }

    private PrinterStatusUpdate offlineStatus(UUID printerId) {
        return offlineStatus(printerId, null);
    }
}
