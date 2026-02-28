package io.github.duffyishere.gateway.common;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenBucketResolver {

    private final LettuceBasedProxyManager<String> proxyManager;
    private final BucketConfiguration bucketConfiguration;

    private Bucket bucket;

    @Value("${rate-limiter.bucket.key}")
    private String RATE_LIMITER_BUCKET_KEY;

    @PostConstruct
    public void initBucket() {
        this.bucket = proxyManager.builder().build(RATE_LIMITER_BUCKET_KEY, () -> bucketConfiguration);
    }

    public boolean tryConsume() {
        ConsumptionProbe probe = consumeToken();
        return probe.isConsumed();
    }

    public long getRemainTokens() {
        return bucket.getAvailableTokens();
    }

    private ConsumptionProbe consumeToken() {
        return bucket.tryConsumeAndReturnRemaining(1);
    }
}
