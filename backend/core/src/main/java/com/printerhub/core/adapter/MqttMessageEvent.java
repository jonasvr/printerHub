package com.printerhub.core.adapter;

import org.springframework.context.ApplicationEvent;

/**
 * Spring ApplicationEvent fired by BambuAdapter (or any adapter) whenever
 * an MQTT message arrives. Listened to by MqttLogBroadcaster in cloud-service,
 * which forwards the entry to the WebSocket log topic.
 *
 * Using events here keeps the adapter layer decoupled from WebSocket/STOMP —
 * the adapter just fires an event; who listens and what they do is not its concern.
 */
public class MqttMessageEvent extends ApplicationEvent {

    private final MqttLogEntry entry;

    public MqttMessageEvent(Object source, MqttLogEntry entry) {
        super(source);
        this.entry = entry;
    }

    public MqttLogEntry getEntry() {
        return entry;
    }
}
