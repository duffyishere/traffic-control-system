package io.github.duffyishere.turnstile.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Configuration
@RequiredArgsConstructor
public class RateLimiterConfig {

    @Value("${rate-limiter.bucket.capacity}")
    private long CAPACITY;
    @Value("${rate-limiter.bucket.refill-token-amount}")
    private long REFILL_TOKEN_AMOUNT;
    @Value("${rate-limiter.bucket.refill-interval-seconds}")
    private long REFILL_INTERVAL_SECONDS;
    private Duration REFILL_INTERVAL;

    @PostConstruct
    public void init() {
        this.REFILL_INTERVAL = Duration.ofSeconds(REFILL_INTERVAL_SECONDS);
    }

    @Bean
    public LettuceBasedProxyManager<String> lettuceBasedProxyManager(RedisClient redisClient) {
        StatefulRedisConnection<String, byte[]> connection = redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        return LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(REFILL_INTERVAL))
                .build();
    }

    @Bean
    public Supplier<CompletableFuture<BucketConfiguration>> bucketConfiguration() {
        return () -> CompletableFuture.completedFuture(BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(CAPACITY)
                        .refillIntervally(REFILL_TOKEN_AMOUNT, REFILL_INTERVAL)
                        .build()
                ).build());
    }
}
