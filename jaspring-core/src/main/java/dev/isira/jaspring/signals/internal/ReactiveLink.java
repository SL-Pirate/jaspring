package dev.isira.jaspring.signals.internal;

/// An edge of the graph: a consumer reading a producer. Lives in the consumer's producer list and,
/// for live consumers, in the producer's consumer list.
final class ReactiveLink {

    final ReactiveNode producer;
    final ReactiveNode consumer;
    long lastReadVersion;
    ReactiveLink prevConsumer;
    ReactiveLink nextConsumer;
    ReactiveLink nextProducer;

    ReactiveLink(ReactiveNode producer, ReactiveNode consumer) {
        this.producer = producer;
        this.consumer = consumer;
    }
}
