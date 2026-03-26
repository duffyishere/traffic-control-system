package io.github.duffyishere.gateway;

import io.github.duffyishere.gateway.common.TokenBucketResolver;
import io.github.duffyishere.gateway.filter.RateLimiterGatewayFilterFactory;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiGatewayApplicationTests {

    @Test
    void allowsDirectAccessWhenAdmissionBucketAcceptsRequest() {
        TokenBucketResolver tokenBucketResolver = mock(TokenBucketResolver.class);
        ReactiveJwtDecoder jwtDecoder = mock(ReactiveJwtDecoder.class);
        when(tokenBucketResolver.tryConsumeAboveThreshold(2L)).thenReturn(Mono.just(Boolean.TRUE));

        RateLimiterGatewayFilterFactory filterFactory = filterFactory(tokenBucketResolver, jwtDecoder);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/concerts/seats").build()
        );
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filterFactory.apply(config()).filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void redirectsToQueueWhenAdmissionBucketRejectsRequest() {
        TokenBucketResolver tokenBucketResolver = mock(TokenBucketResolver.class);
        ReactiveJwtDecoder jwtDecoder = mock(ReactiveJwtDecoder.class);
        when(tokenBucketResolver.tryConsumeAboveThreshold(2L)).thenReturn(Mono.just(Boolean.FALSE));

        RateLimiterGatewayFilterFactory filterFactory = filterFactory(tokenBucketResolver, jwtDecoder);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/concerts/seats?concertId=1").build()
        );

        StepVerifier.create(filterFactory.apply(config()).filter(exchange, chain))
                .verifyComplete();

        verify(chain, never()).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        var location = exchange.getResponse().getHeaders().getLocation();
        assertThat(location).isNotNull();
        assertThat(location.getPath()).isEqualTo("/queue");
        var queryParams = UriComponentsBuilder.fromUri(location).build().getQueryParams();
        assertThat(queryParams.getFirst("requestId")).isNotBlank();
        assertThat(org.springframework.web.util.UriUtils.decode(
                queryParams.getFirst("requestedUri"),
                StandardCharsets.UTF_8
        )).isEqualTo("/api/v1/concerts/seats?concertId=1");
    }

    @Test
    void redirectsToQueueWhenBearerTokenIsInvalid() {
        TokenBucketResolver tokenBucketResolver = mock(TokenBucketResolver.class);
        ReactiveJwtDecoder jwtDecoder = mock(ReactiveJwtDecoder.class);
        when(jwtDecoder.decode(anyString())).thenReturn(Mono.error(new BadJwtException("invalid")));

        RateLimiterGatewayFilterFactory filterFactory = filterFactory(tokenBucketResolver, jwtDecoder);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/concerts/seats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer broken-token")
                        .build()
        );

        StepVerifier.create(filterFactory.apply(config()).filter(exchange, chain))
                .verifyComplete();

        verify(chain, never()).filter(exchange);
        verify(tokenBucketResolver, never()).tryConsumeAboveThreshold(2L);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    }

    @Test
    void forwardsRequestWhenBearerTokenIsValid() {
        TokenBucketResolver tokenBucketResolver = mock(TokenBucketResolver.class);
        ReactiveJwtDecoder jwtDecoder = mock(ReactiveJwtDecoder.class);
        when(jwtDecoder.decode("valid-token")).thenReturn(Mono.just(Jwt.withTokenValue("valid-token")
                .header("alg", "RS256")
                .subject("turnstile")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build()));

        RateLimiterGatewayFilterFactory filterFactory = filterFactory(tokenBucketResolver, jwtDecoder);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/concerts/seats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .build()
        );
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filterFactory.apply(config()).filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        verify(tokenBucketResolver, never()).tryConsumeAboveThreshold(2L);
    }

    private RateLimiterGatewayFilterFactory filterFactory(
            TokenBucketResolver tokenBucketResolver,
            ReactiveJwtDecoder jwtDecoder
    ) {
        return new RateLimiterGatewayFilterFactory(tokenBucketResolver, jwtDecoder, true, 2L);
    }

    private RateLimiterGatewayFilterFactory.Config config() {
        RateLimiterGatewayFilterFactory.Config config = new RateLimiterGatewayFilterFactory.Config();
        config.setRedirectUri("http://localhost:5173/queue");
        return config;
    }
}
