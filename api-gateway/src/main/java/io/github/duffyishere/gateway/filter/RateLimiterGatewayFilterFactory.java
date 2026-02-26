package io.github.duffyishere.gateway.filter;

import io.github.duffyishere.gateway.common.TokenBucketResolver;
import lombok.Data;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.UUID;

@Component
public class RateLimiterGatewayFilterFactory extends AbstractGatewayFilterFactory<RateLimiterGatewayFilterFactory.Config> {

    private TokenBucketResolver tokenBucketResolver;

    public RateLimiterGatewayFilterFactory(TokenBucketResolver tokenBucketResolver) {
        super(Config.class);
        this.tokenBucketResolver = tokenBucketResolver;

    }

    @Override
    public GatewayFilter apply(Config config) {
        return  (exchange, chain) -> {
            System.out.println("RateLimiterFilter 실행 됨. 요청 URI: " + exchange.getRequest().getURI());
            if (tokenBucketResolver.tryConsume()) {
                return chain.filter(exchange);
            } else {
                ServerHttpResponse response = exchange.getResponse();
                response.setStatusCode(HttpStatus.SEE_OTHER);
                response.getHeaders().setLocation(URI.create(config.getRedirectUri() + "?requestId=" + UUID.randomUUID()));
                return response.setComplete();
            }
        };
    }

    @Data
    public static class Config {
        private String redirectUri;
    }
}
