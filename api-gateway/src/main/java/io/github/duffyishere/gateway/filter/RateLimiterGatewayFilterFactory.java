package io.github.duffyishere.gateway.filter;

import io.github.duffyishere.gateway.common.TokenBucketResolver;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;

@Component
@Slf4j
public class RateLimiterGatewayFilterFactory extends AbstractGatewayFilterFactory<RateLimiterGatewayFilterFactory.Config> {

    private static final String CURRENT_PAGE_URI_HEADER = "X-Current-Page-Uri";
    private static final String CURRENT_PAGE_URI_PARAM = "currentPageUri";

    private TokenBucketResolver tokenBucketResolver;
    private ReactiveJwtDecoder jwtDecoder;

    @Value("${rate-limiter.enabled:true}")
    private boolean rateLimiterEnabled;

    @Value("${rate-limiter.bucket.redirect-threshold}")
    private long REDIRECT_THRESHOLD = 100L;

    public RateLimiterGatewayFilterFactory(TokenBucketResolver tokenBucketResolver, ReactiveJwtDecoder jwtDecoder) {
        super(Config.class);
        this.tokenBucketResolver = tokenBucketResolver;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return  (exchange, chain) -> {
            if (!rateLimiterEnabled) {
                return chain.filter(exchange);
            }

            log.info("RateLimiterFilter 실행 됨. 요청 URI: {}", exchange.getRequest().getURI());

            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                return jwtDecoder.decode(token)
                        .flatMap(jwt -> chain.filter(exchange))
                        .onErrorResume(e ->{
                            log.error(">>> Error: {}", e.getMessage());
                            return redirectToWaitRoom(exchange, config);
                        });
            } else {
                if (REDIRECT_THRESHOLD < tokenBucketResolver.getRemainTokens() && tokenBucketResolver.tryConsume()) {
                    return chain.filter(exchange);
                } else {
                    return redirectToWaitRoom(exchange, config);
                }
            }
        };
    }

    private Mono<Void> redirectToWaitRoom(ServerWebExchange exchange, Config config) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SEE_OTHER);
        String requestId = UUID.randomUUID().toString();
        String requestedUri = resolveRequestedUri(exchange);

        URI location = UriComponentsBuilder.fromUriString(config.getRedirectUri())
                .queryParam("requestId", requestId)
                .queryParam("requestedUri", requestedUri)
                .build(true)
                .toUri();

        response.getHeaders().setLocation(location);
        return response.setComplete();
    }

    private String resolveRequestedUri(ServerWebExchange exchange) {
        String currentPageUri = exchange.getRequest().getHeaders().getFirst(CURRENT_PAGE_URI_HEADER);
        if (currentPageUri == null || currentPageUri.isBlank())
            currentPageUri = exchange.getRequest().getQueryParams().getFirst(CURRENT_PAGE_URI_PARAM);

        URI currentPage = parseUriOrNull(currentPageUri);
        if (currentPage != null) {
            return normalizePathAndQuery(currentPage);
        }

        return normalizePathAndQuery(exchange.getRequest().getURI());
    }

    private URI parseUriOrNull(String uriValue) {
        if (uriValue == null || uriValue.isBlank()) return null;
        try {
            return URI.create(uriValue);
        } catch (IllegalArgumentException e) {
            log.debug("Invalid current page uri value: {}", uriValue);
            return null;
        }
    }

    private String normalizePathAndQuery(URI uri) {
        if (uri == null) return "/";
        String path = uri.getRawPath();
        if (path == null || path.isBlank()) return "/";
        String query = uri.getRawQuery();
        return query == null ? path : path + "?" + query;
    }

    @Data
    public static class Config {
        private String redirectUri;
    }
}
