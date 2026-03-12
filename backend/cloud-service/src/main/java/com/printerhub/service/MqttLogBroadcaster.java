package com.printerhub.service;

import com.printerhub.core.adapter.MqttMessageEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Listens for MqttMessageEvents published by adapters and forwards each entry
 * to the WebSocket topic "/topic/printers/{id}/mqtt-logs".
 *
 * This component lives in cloud-service (which owns the WebSocket context)
 * rather than in the adapter module, keeping the adapters decoupled from
 * the transport layer.
 */
@Component
@RequiredArgsConstructor
public class MqttLogBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void onMqttMessage(MqttMessageEvent event) {
        var entry = event.getEntry();
        messagingTemplate.convertAndSend(
            "/topic/printers/" + entry.printerId() + "/mqtt-logs",
            entry
        );
    }
}
