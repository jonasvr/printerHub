package com.printerhub.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configures STOMP over WebSocket.
 *
 * Why STOMP instead of raw WebSocket?
 * - STOMP is a simple pub/sub protocol layered on WebSocket.
 * - Angular's @stomp/stompjs library speaks STOMP natively.
 * - We get topic subscriptions (/topic/printers/{id}) for free
 *   without writing a custom message routing system.
 *
 * Message flow:
 *   Browser → WS → /app/... → @MessageMapping handler → broker → /topic/... → Browser
 *
 * For Phase 1 we use an in-memory broker (no external message broker needed).
 * Phase 3 can swap to a RabbitMQ or Redis broker for multi-instance support.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /topic/** = server → client broadcasts (printer status updates)
        // /queue/**  = server → specific client (future: user-targeted notifications)
        registry.enableSimpleBroker("/topic", "/queue");

        // /app/** = client → server messages (e.g. pause command from browser)
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // Allow the Angular dev server (port 4200) and production origin
                .setAllowedOriginPatterns("http://localhost:4200", "https://*.printerhub.io")
                // SockJS fallback for browsers that don't support native WebSocket
                .withSockJS();
    }
}
