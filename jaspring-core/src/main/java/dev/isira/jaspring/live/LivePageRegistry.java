package dev.isira.jaspring.live;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/// The live pages of this server instance. A page id is bound to the HTTP session that created it, which is what
/// stops another session from driving it. Pages die when their client stays disconnected past the TTL, when they
/// idle too long, or when a session holds too many.
public final class LivePageRegistry {

    private static final Duration IDLE_TTL = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, LivePage> pages = new ConcurrentHashMap<>();
    private final Map<String, List<String>> pagesBySession = new ConcurrentHashMap<>();
    private final Map<String, java.time.Instant> disconnectedSince = new ConcurrentHashMap<>();
    private final Duration disconnectedTtl;
    private final int maxPagesPerSession;
    private final Clock clock;

    public LivePageRegistry(Duration disconnectedTtl, int maxPagesPerSession, Clock clock) {
        this.disconnectedTtl = disconnectedTtl;
        this.maxPagesPerSession = maxPagesPerSession;
        this.clock = clock;
    }

    public static String newPageId() {
        final var bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public void register(LivePage page) {
        pages.put(page.id(), page);
        disconnectedSince.put(page.id(), clock.instant());
        final var sessionPages = pagesBySession.computeIfAbsent(page.sessionId(), s -> new ArrayList<>());
        synchronized (sessionPages) {
            sessionPages.add(page.id());
            while (sessionPages.size() > maxPagesPerSession) {
                final var oldest = sessionPages.stream().map(pages::get).filter(java.util.Objects::nonNull)
                        .min(Comparator.comparing(LivePage::lastActivity));
                if (oldest.isEmpty()) break;
                evict(oldest.get());
            }
        }
    }

    /// Finds a page the given session owns; a wrong session sees nothing, which doubles as the CSRF guard.
    public Optional<LivePage> find(String pageId, String sessionId) {
        final var page = pages.get(pageId);
        if (page == null || page.isDestroyed() || !page.sessionId().equals(sessionId)) return Optional.empty();
        return Optional.of(page);
    }

    public void connected(String pageId) {
        disconnectedSince.remove(pageId);
    }

    public void disconnected(String pageId) {
        if (pages.containsKey(pageId)) disconnectedSince.put(pageId, clock.instant());
    }

    /// Runs the eviction rules once; the starter schedules it periodically on a virtual thread.
    public int evictExpired() {
        final var now = clock.instant();
        var evicted = 0;
        for (final var page : List.copyOf(pages.values())) {
            final var disconnectedAt = disconnectedSince.get(page.id());
            final var disconnectedTooLong = disconnectedAt != null && Duration.between(disconnectedAt, now).compareTo(disconnectedTtl) > 0;
            final var idleTooLong = Duration.between(page.lastActivity(), now).compareTo(IDLE_TTL) > 0;
            if (disconnectedTooLong || idleTooLong) {
                evict(page);
                evicted++;
            }
        }
        return evicted;
    }

    public void evict(LivePage page) {
        pages.remove(page.id());
        disconnectedSince.remove(page.id());
        final var sessionPages = pagesBySession.get(page.sessionId());
        if (sessionPages != null) {
            synchronized (sessionPages) {
                sessionPages.remove(page.id());
            }
        }
        page.destroy();
    }

    public int size() {
        return pages.size();
    }

    public void destroyAll() {
        for (final var page : List.copyOf(pages.values())) {
            evict(page);
        }
    }
}
