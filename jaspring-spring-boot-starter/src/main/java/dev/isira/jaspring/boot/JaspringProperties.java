package dev.isira.jaspring.boot;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import dev.isira.jaspring.zone.Zone;

/// `jaspring.*` settings. Only operator-level choices are exposed; internal limits are constants.
@ConfigurationProperties("jaspring")
public class JaspringProperties {

    public enum Transport { WEBSOCKET, HTTP, OFF }

    /// `zone` re-renders Default views on every turn like Angular with zone.js; `zoneless` only dirty views.
    private Zone.Mode changeDetection = Zone.Mode.ZONE;

    private final Live live = new Live();

    /// How long the first render waits for zone-forked work before responding.
    private Duration stabilityTimeout = Duration.ofSeconds(10);

    /// How long a page survives without a connected client.
    private Duration pageTtl = Duration.ofSeconds(60);

    private int maxPagesPerSession = 20;

    /// Detailed error messages to the browser. Defaults to on when DevTools is on the classpath.
    private Boolean devMode;

    /// Packages to scan for components, pipes and directives; defaults to the application's auto-configuration packages.
    private List<String> basePackages = List.of();

    /// The HTML document the root component is placed into.
    private String layout = "classpath:/jaspring/index.html";

    public static class Live {
        /// `websocket` (with HTTP fallback), `http` (POST plus polling), or `off` (plain server rendering).
        private Transport transport = Transport.WEBSOCKET;

        public Transport getTransport() {
            return transport;
        }

        public void setTransport(Transport transport) {
            this.transport = transport;
        }
    }

    public Zone.Mode getChangeDetection() {
        return changeDetection;
    }

    public void setChangeDetection(Zone.Mode changeDetection) {
        this.changeDetection = changeDetection;
    }

    public Live getLive() {
        return live;
    }

    public Duration getStabilityTimeout() {
        return stabilityTimeout;
    }

    public void setStabilityTimeout(Duration stabilityTimeout) {
        this.stabilityTimeout = stabilityTimeout;
    }

    public Duration getPageTtl() {
        return pageTtl;
    }

    public void setPageTtl(Duration pageTtl) {
        this.pageTtl = pageTtl;
    }

    public int getMaxPagesPerSession() {
        return maxPagesPerSession;
    }

    public void setMaxPagesPerSession(int maxPagesPerSession) {
        this.maxPagesPerSession = maxPagesPerSession;
    }

    public Boolean getDevMode() {
        return devMode;
    }

    public void setDevMode(Boolean devMode) {
        this.devMode = devMode;
    }

    public List<String> getBasePackages() {
        return basePackages;
    }

    public void setBasePackages(List<String> basePackages) {
        this.basePackages = basePackages;
    }

    public String getLayout() {
        return layout;
    }

    public void setLayout(String layout) {
        this.layout = layout;
    }

    public boolean isDevMode() {
        return devMode != null ? devMode : isDevToolsPresent();
    }

    private static boolean isDevToolsPresent() {
        try {
            Class.forName("org.springframework.boot.devtools.restart.Restarter");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
