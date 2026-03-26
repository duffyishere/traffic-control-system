package io.github.duffyishere.gateway.common;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.AsyncBucketProxy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class TokenBucketResolver {

    private static final long REQUEST_TOKENS = 1L;

    private final LettuceBasedProxyManager<String> proxyManager;
    private final Supplier<CompletableFuture<BucketConfiguration>> bucketConfiguration;

    @Value("${rate-limiter.bucket.key}")
    private String rateLimiterBucketKey;

    private AsyncBucketProxy asyncBucket;

    @PostConstruct
    public void initializeBucket() {
        this.asyncBucket = proxyManager.asAsync().builder().build(rateLimiterBucketKey, bucketConfiguration);
    }

    public Mono<Boolean> tryConsumeAboveThreshold(long redirectThreshold) {
        return consumeRequestToken()
                .flatMap(probe -> {
                    if (!probe.isConsumed()) {
                        return Mono.just(Boolean.FALSE);
                    }

                    if (probe.getRemainingTokens() < redirectThreshold) {
                        return refundRequestToken().thenReturn(Boolean.FALSE);
                    }

                    return Mono.just(Boolean.TRUE);
                });
    }

    private Mono<io.github.bucket4j.ConsumptionProbe> consumeRequestToken() {
        return Mono.fromFuture(() -> asyncBucket.tryConsumeAndReturnRemaining(REQUEST_TOKENS));
    }

    private Mono<Void> refundRequestToken() {
        return Mono.fromFuture(() -> asyncBucket.addTokens(REQUEST_TOKENS)).then();
    }
}
