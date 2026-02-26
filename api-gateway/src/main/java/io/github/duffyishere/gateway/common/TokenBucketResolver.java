package io.github.duffyishere.gateway.common;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenBucketResolver {

    private final LettuceBasedProxyManager<String> proxyManager;
    private final BucketConfiguration bucketConfiguration;

    @Value("${rate-limiter.bucket.key}")
    private String RATE_LIMITER_BUCKET_KEY;

    public boolean tryConsume() {
        Bucket bucket;
        bucket = proxyManager.builder().build(RATE_LIMITER_BUCKET_KEY, () -> bucketConfiguration);
        ConsumptionProbe probe = consumeToken(bucket);
        return probe.isConsumed();
    }

    private ConsumptionProbe consumeToken(Bucket bucket) {
        return bucket.tryConsumeAndReturnRemaining(1);
    }
}
