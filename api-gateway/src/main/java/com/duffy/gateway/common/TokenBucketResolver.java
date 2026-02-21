package com.duffy.gateway.common;

import com.duffy.gateway.config.RateLimiterConfig;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenBucketResolver {

    private final RateLimiterConfig rateLimiterConfig;

    public boolean tryConsume(String key) {
        Bucket bucket = getOrCreateBucket(key);
        ConsumptionProbe probe = consumeToken(bucket);
        return probe.isConsumed();
    }

    private Bucket getOrCreateBucket(String key) {
        return rateLimiterConfig.lettuceBasedProxyManager().builder()
                .build(key, rateLimiterConfig::bucketConfiguration);
    }

    private ConsumptionProbe consumeToken(Bucket bucket) {
        return bucket.tryConsumeAndReturnRemaining(1);
    }
}
