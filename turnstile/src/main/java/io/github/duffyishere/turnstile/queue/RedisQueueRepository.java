package io.github.duffyishere.turnstile.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class RedisQueueRepository implements QueueRepository {

    private static final String GRANT_PREFIX = "queue:grant:";

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

    @Override
    public Mono<String> peekHead(String queueName) {
        return redisTemplate.opsForZSet().range(queueName, Range.closed(0L, 0L)).next();
    }

    @Override
    public Mono<Void> saveGrant(String requestId, String token, Duration ttl) {
        return redisTemplate.opsForValue()
                .set(grantKey(requestId), token, ttl)
                .then();
    }

    @Override
    public Mono<String> getGrant(String requestId) {
        return redisTemplate.opsForValue().get(grantKey(requestId));
    }

    private String grantKey(String requestId) {
        return GRANT_PREFIX + requestId;
    }
}
