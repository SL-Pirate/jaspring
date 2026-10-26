package dev.isira.jaspring.boot.live;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import dev.isira.jaspring.boot.JaspringRuntime;

/// The WebSocket transport at `/_jaspring/ws`, active when the websocket starter is present and the transport is `websocket`.
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.web.socket.config.annotation.WebSocketConfigurer")
@ConditionalOnProperty(name = "jaspring.live.transport", havingValue = "websocket", matchIfMissing = true)
@EnableWebSocket
public class WebSocketTransportConfiguration implements WebSocketConfigurer {

    private final JaspringEventHandler handler;

    public WebSocketTransportConfiguration(JaspringEventHandler handler) {
        this.handler = handler;
    }

    @Bean
    @ConditionalOnMissingBean
    public static JaspringEventHandler jaspringEventHandler(JaspringRuntime runtime, LiveSessions sessions, ProtocolCodec codec) {
        return new JaspringEventHandler(runtime, sessions, codec);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/_jaspring/ws").addInterceptors(new HttpSessionHandshakeInterceptor());
    }
}
