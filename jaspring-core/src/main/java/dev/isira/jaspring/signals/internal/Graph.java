package dev.isira.jaspring.signals.internal;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import dev.isira.jaspring.context.CurrentContext;

/// The reactive graph algorithms, ported from Angular's `graph.ts`. Internal API.
public final class Graph {

    private static final CurrentContext<ReactiveNode> ACTIVE_CONSUMER = CurrentContext.threadLocal();
    private static final CurrentContext<Boolean> NOTIFYING = CurrentContext.threadLocal();
    /// Incremented on every source signal write. Global across pages: a foreign increment only costs one extra poll.
    private static final AtomicLong EPOCH = new AtomicLong(1);

    private Graph() {}

    public static ReactiveNode activeConsumer() {
        return ACTIVE_CONSUMER.get();
    }

    public static boolean inNotificationPhase() {
        return Boolean.TRUE.equals(NOTIFYING.get());
    }

    public static <T> T untracked(Supplier<T> body) {
        return ACTIVE_CONSUMER.runWith(null, body);
    }

    /// Runs `body` with `consumer` active, rebuilding its producer list incrementally and dropping
    /// the producers it no longer reads afterwards.
    public static <T> T computeAs(ReactiveNode consumer, Supplier<T> body) {
        consumer.producersTail = null;
        consumer.recomputing = true;
        try {
            return ACTIVE_CONSUMER.runWith(consumer, body);
        } finally {
            finalizeConsumerAfterComputation(consumer);
        }
    }

    public static void producerAccessed(ReactiveNode node) {
        if (inNotificationPhase()) {
            throw new IllegalStateException("Signal read during notification phase");
        }
        final var consumer = ACTIVE_CONSUMER.get();
        if (consumer == null) return;

        consumer.consumerOnSignalRead(node);

        final var prevProducerLink = consumer.producersTail;
        if (prevProducerLink != null && prevProducerLink.producer == node) return;

        ReactiveLink nextProducerLink = null;
        final var isRecomputing = consumer.recomputing;
        if (isRecomputing) {
            nextProducerLink = prevProducerLink != null ? prevProducerLink.nextProducer : consumer.producers;
            if (nextProducerLink != null && nextProducerLink.producer == node) {
                consumer.producersTail = nextProducerLink;
                nextProducerLink.lastReadVersion = node.version;
                return;
            }
        }

        final var prevConsumerLink = node.consumersTail;
        if (prevConsumerLink != null && prevConsumerLink.consumer == consumer
                && (!isRecomputing || isValidLink(prevConsumerLink, consumer))) {
            return;
        }

        final var isLive = consumerIsLive(consumer);
        final var newLink = new ReactiveLink(node, consumer);
        newLink.nextProducer = nextProducerLink;
        newLink.prevConsumer = prevConsumerLink;
        newLink.lastReadVersion = node.version;
        consumer.producersTail = newLink;
        if (prevProducerLink != null) {
            prevProducerLink.nextProducer = newLink;
        } else {
            consumer.producers = newLink;
        }
        if (isLive) {
            producerAddLiveConsumer(node, newLink);
        }
    }

    public static void producerIncrementEpoch() {
        EPOCH.incrementAndGet();
    }

    public static void producerUpdateValueVersion(ReactiveNode node) {
        if (consumerIsLive(node) && !node.dirty) return;
        if (!node.dirty && node.lastCleanEpoch == EPOCH.get()) return;

        if (!node.producerMustRecompute() && !consumerPollProducersForChange(node)) {
            producerMarkClean(node);
            return;
        }
        node.producerRecomputeValue();
        producerMarkClean(node);
    }

    public static void producerNotifyConsumers(ReactiveNode node) {
        if (node.consumers == null) return;
        NOTIFYING.runWith(Boolean.TRUE, () -> {
            for (var link = node.consumers; link != null; link = link.nextConsumer) {
                final var consumer = link.consumer;
                if (!consumer.dirty) {
                    consumerMarkDirty(consumer);
                }
            }
        });
    }

    public static boolean producerUpdatesAllowed() {
        final var consumer = ACTIVE_CONSUMER.get();
        return consumer == null || consumer.consumerAllowSignalWrites;
    }

    public static void consumerMarkDirty(ReactiveNode node) {
        node.dirty = true;
        producerNotifyConsumers(node);
        node.consumerMarkedDirty();
    }

    public static void producerMarkClean(ReactiveNode node) {
        node.dirty = false;
        node.lastCleanEpoch = EPOCH.get();
    }

    private static void finalizeConsumerAfterComputation(ReactiveNode node) {
        node.recomputing = false;
        final var producersTail = node.producersTail;
        var toRemove = producersTail != null ? producersTail.nextProducer : node.producers;
        if (toRemove == null) return;
        if (consumerIsLive(node)) {
            do {
                toRemove = producerRemoveLiveConsumerLink(toRemove);
            } while (toRemove != null);
        }
        if (producersTail != null) {
            producersTail.nextProducer = null;
        } else {
            node.producers = null;
        }
    }

    public static boolean consumerPollProducersForChange(ReactiveNode node) {
        for (var link = node.producers; link != null; link = link.nextProducer) {
            final var producer = link.producer;
            final var seenVersion = link.lastReadVersion;
            if (seenVersion != producer.version) return true;
            producerUpdateValueVersion(producer);
            if (seenVersion != producer.version) return true;
        }
        return false;
    }

    public static void consumerDestroy(ReactiveNode node) {
        if (consumerIsLive(node)) {
            var link = node.producers;
            while (link != null) {
                link = producerRemoveLiveConsumerLink(link);
            }
        }
        node.producers = null;
        node.producersTail = null;
        node.consumers = null;
        node.consumersTail = null;
    }

    private static void producerAddLiveConsumer(ReactiveNode node, ReactiveLink link) {
        final var consumersTail = node.consumersTail;
        final var wasLive = consumerIsLive(node);
        if (consumersTail != null) {
            link.nextConsumer = consumersTail.nextConsumer;
            consumersTail.nextConsumer = link;
        } else {
            link.nextConsumer = null;
            node.consumers = link;
        }
        link.prevConsumer = consumersTail;
        node.consumersTail = link;
        if (!wasLive) {
            // Becoming live is transitive: this node now needs push notifications from its own producers.
            for (var producerLink = node.producers; producerLink != null; producerLink = producerLink.nextProducer) {
                producerAddLiveConsumer(producerLink.producer, producerLink);
            }
        }
    }

    private static ReactiveLink producerRemoveLiveConsumerLink(ReactiveLink link) {
        final var producer = link.producer;
        final var nextProducer = link.nextProducer;
        final var nextConsumer = link.nextConsumer;
        final var prevConsumer = link.prevConsumer;
        link.nextConsumer = null;
        link.prevConsumer = null;
        if (nextConsumer != null) {
            nextConsumer.prevConsumer = prevConsumer;
        } else {
            producer.consumersTail = prevConsumer;
        }
        if (prevConsumer != null) {
            prevConsumer.nextConsumer = nextConsumer;
        } else {
            producer.consumers = nextConsumer;
            if (!consumerIsLive(producer)) {
                var producerLink = producer.producers;
                while (producerLink != null) {
                    producerLink = producerRemoveLiveConsumerLink(producerLink);
                }
            }
        }
        return nextProducer;
    }

    static boolean consumerIsLive(ReactiveNode node) {
        return node.consumerIsAlwaysLive || node.consumers != null;
    }

    // A recomputing consumer has not dropped its stale links yet; only links up to producersTail are current.
    private static boolean isValidLink(ReactiveLink checkLink, ReactiveNode consumer) {
        final var producersTail = consumer.producersTail;
        if (producersTail == null) return false;
        var link = consumer.producers;
        while (link != null) {
            if (link == checkLink) return true;
            if (link == producersTail) break;
            link = link.nextProducer;
        }
        return false;
    }
}
