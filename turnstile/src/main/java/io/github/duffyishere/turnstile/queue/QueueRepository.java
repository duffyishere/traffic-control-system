package io.github.duffyishere.turnstile.queue;

import reactor.core.publisher.Mono;

public interface QueueRepository {
    Mono<Boolean> register(String queueName, String requestId);
    Mono<Long> getRank(String queueName, String requestId);
    Mono<Long> remove(String queueName, String requestId);
    Mono<Long> getAllowedLimit(String bucketName);
}
