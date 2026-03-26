package io.github.duffyishere.gateway.filter;

import io.github.duffyishere.gateway.common.TokenBucketResolver;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriUtils;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class RateLimiterGatewayFilterFactory extends AbstractGatewayFilterFactory<RateLimiterGatewayFilterFactory.Config> {

    private static final String CURRENT_PAGE_URI_HEADER = "X-Current-Page-Uri";
    private static final String CURRENT_PAGE_URI_PARAM = "currentPageUri";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenBucketResolver tokenBucketResolver;
    private final ReactiveJwtDecoder jwtDecoder;
    private final boolean rateLimiterEnabled;
    private final long redirectThreshold;

    public RateLimiterGatewayFilterFactory(
            TokenBucketResolver tokenBucketResolver,
            ReactiveJwtDecoder jwtDecoder,
            @Value("${rate-limiter.enabled:true}") boolean rateLimiterEnabled,
            @Value("${rate-limiter.bucket.redirect-threshold}") long redirectThreshold
    ) {
        super(Config.class);
        this.tokenBucketResolver = tokenBucketResolver;
        this.jwtDecoder = jwtDecoder;
        this.rateLimiterEnabled = rateLimiterEnabled;
        this.redirectThreshold = redirectThreshold;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            if (!rateLimiterEnabled) {
                return chain.filter(exchange);
            }

            return extractBearerToken(exchange)
                    .map(token -> validateQueueToken(token, exchange, chain, config))
                    .orElseGet(() -> applyAdmissionControl(exchange, chain, config));
        };
    }

    private Mono<Void> validateQueueToken(
            String token,
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            Config config
    ) {
        return jwtDecoder.decode(token)
                .flatMap(jwt -> chain.filter(exchange))
                .onErrorResume(error -> {
                    log.debug("Queue redirect because token validation failed: {}", error.getMessage());
                    return redirectToWaitRoom(exchange, config);
                });
    }

    private Mono<Void> applyAdmissionControl(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            Config config
    ) {
        return tokenBucketResolver.tryConsumeAboveThreshold(redirectThreshold)
                .flatMap(allowed -> allowed ? chain.filter(exchange) : redirectToWaitRoom(exchange, config))
                .onErrorResume(error -> {
                    log.warn("Queue redirect because admission check failed: {}", error.getMessage());
                    return redirectToWaitRoom(exchange, config);
                });
    }

    private Mono<Void> redirectToWaitRoom(ServerWebExchange exchange, Config config) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SEE_OTHER);
        response.getHeaders().setLocation(buildRedirectLocation(config, resolveRequestedUri(exchange)));
        return response.setComplete();
    }

    private URI buildRedirectLocation(Config config, String requestedUri) {
        UriComponentsBuilder locationBuilder = UriComponentsBuilder.fromUriString(config.getRedirectUri())
                .queryParam("requestId", UUID.randomUUID());

        if (requestedUri != null) {
            locationBuilder.queryParam("requestedUri", UriUtils.encode(requestedUri, StandardCharsets.UTF_8));
        }

        return locationBuilder.build(true).toUri();
    }

    private Optional<String> extractBearerToken(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }

        return Optional.of(authHeader.substring(BEARER_PREFIX.length()));
    }

    private String resolveRequestedUri(ServerWebExchange exchange) {
        String currentPageUri = exchange.getRequest().getHeaders().getFirst(CURRENT_PAGE_URI_HEADER);
        if (!StringUtils.hasText(currentPageUri)) {
            currentPageUri = exchange.getRequest().getQueryParams().getFirst(CURRENT_PAGE_URI_PARAM);
        }

        URI currentPage = parseUriOrNull(currentPageUri);
        if (currentPage != null) {
            return normalizePathAndQuery(currentPage);
        }

        return normalizePathAndQuery(exchange.getRequest().getURI());
    }

    private URI parseUriOrNull(String uriValue) {
        if (!StringUtils.hasText(uriValue)) {
            return null;
        }

        try {
            return URI.create(uriValue);
        } catch (IllegalArgumentException e) {
            log.debug("Invalid current page uri value: {}", uriValue);
            return null;
        }
    }

    private String normalizePathAndQuery(URI uri) {
        if (uri == null) {
            return "/";
        }

        String path = uri.getRawPath();
        if (!StringUtils.hasText(path)) {
            return "/";
        }

        String query = uri.getRawQuery();
        return query == null ? path : path + "?" + query;
    }

    @Data
    public static class Config {
        private String redirectUri;
    }
}
