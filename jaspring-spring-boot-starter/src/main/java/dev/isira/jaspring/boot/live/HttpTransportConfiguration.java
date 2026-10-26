package dev.isira.jaspring.boot.live;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.isira.jaspring.boot.JaspringRuntime;

/// The HTTP transport: `POST /_jaspring/event` and `GET /_jaspring/poll`. Active for `http` and, as the fallback,
/// for `websocket`; absent when live events are off.
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${jaspring.live.transport:websocket}' != 'off'")
public class HttpTransportConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LiveSessions liveSessions() {
        return new LiveSessions();
    }

    @Bean
    @ConditionalOnMissingBean
    public LiveEventController liveEventController(JaspringRuntime runtime, LiveSessions sessions, ProtocolCodec codec) {
        return new LiveEventController(runtime, sessions, codec);
    }
}
