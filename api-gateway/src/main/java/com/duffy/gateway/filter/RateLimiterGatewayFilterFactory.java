package com.duffy.gateway.filter;

import com.duffy.gateway.common.TokenBucketResolver;
import lombok.Data;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;

import java.net.URI;

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
            if (tokenBucketResolver.tryConsume(config.getBucketKey())) {
                return chain.filter(exchange);
            } else {
                ServerHttpResponse response = exchange.getResponse();
                response.setStatusCode(HttpStatus.SEE_OTHER);
                response.getHeaders().setLocation(URI.create(config.getRedirectUri()));
                return response.setComplete();
            }
        };
    }

    @Data
    public static class Config {
        private String bucketKey;
        private String redirectUri;
    }
}
