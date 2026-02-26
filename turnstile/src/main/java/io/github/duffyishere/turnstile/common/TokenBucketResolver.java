package io.github.duffyishere.turnstile.common;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.AsyncBucketProxy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBucketResolver {

    private final LettuceBasedProxyManager<String> proxyManager;
    private final Supplier<CompletableFuture<BucketConfiguration>> bucketConfiguration;

    @Value("${rate-limiter.bucket.key}")
    private String RATE_LIMITER_BUCKET_KEY;

    public Mono<Boolean> checkAccess() {
        AsyncBucketProxy asyncBucket = proxyManager.asAsync().builder().build(RATE_LIMITER_BUCKET_KEY, bucketConfiguration);
        return Mono.fromFuture(() -> asyncBucket.tryConsumeAndReturnRemaining(1))
                .map(probe -> {
                    log.info("Remain Token: {}", probe.getRemainingTokens());
                    return  probe.isConsumed();
                });
    }

    private ConsumptionProbe consumeToken(Bucket bucket) {
        return bucket.tryConsumeAndReturnRemaining(1);
    }
}
