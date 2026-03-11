package com.printerhub.adapter.bambu;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed config bound to application.yml under the "bambu" prefix.
 *
 * Example in application.yml:
 *   bambu:
 *     mqtt-port: 8883
 *
 * Using @ConfigurationProperties keeps config in one place and gives
 * you IDE autocompletion + type safety vs bare @Value annotations.
 */
@Component
@ConfigurationProperties(prefix = "bambu")
@Data
public class BambuMqttProperties {

    /**
     * Port used by Bambu printers for LAN MQTT.
     * Bambu uses 8883 (TLS) for cloud mode, 1883 for local LAN mode.
     */
    private int mqttPort = 8883;

    /**
     * Whether to skip TLS certificate validation.
     * Set to true ONLY for local LAN mode where the printer uses a self-signed cert.
     */
    private boolean skipTlsVerify = false;
}
