package io.github.duffyishere.turnstile.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class RedisQueueRepository implements QueueRepository {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @Override
    public Mono<Boolean> register(String queueName, String requestId) {
        return redisTemplate.opsForZSet().add(queueName, requestId, System.currentTimeMillis());
    }

    @Override
    public Mono<Long> getRank(String queueName, String requestId) {
        return redisTemplate.opsForZSet().rank(queueName, requestId);
    }

    @Override
    public Mono<Long> remove(String queueName, String requestId) {
        return redisTemplate.opsForZSet().remove(queueName, requestId);
    }

    @Override
    public Mono<Long> getAllowedLimit(String bucketName) {
        return redisTemplate.opsForValue().get(bucketName)
                .map(Long::parseLong);
    }
}
