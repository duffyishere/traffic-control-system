package io.github.duffyishere.turnstile.queue;

import reactor.core.publisher.Mono;

import java.time.Duration;

public interface QueueRepository {
    Mono<Boolean> register(String queueName, String requestId);
    Mono<Long> getRank(String queueName, String requestId);
    Mono<Long> remove(String queueName, String requestId);
    Mono<Long> getAllowedLimit(String bucketName);
    Mono<String> peekHead(String queueName);
    Mono<Void> saveGrant(String requestId, String token, Duration ttl);
    Mono<String> getGrant(String requestId);
}
