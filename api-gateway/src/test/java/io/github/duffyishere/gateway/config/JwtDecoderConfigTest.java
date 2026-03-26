package io.github.duffyishere.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtDecoderConfigTest {

    @Test
    void createsReactiveJwtDecoderFromConfiguredJwkSetUri() {
        JwtDecoderConfig config = new JwtDecoderConfig();
        ReflectionTestUtils.setField(config, "jwkSetUri", "https://example.com/.well-known/jwks.json");

        ReactiveJwtDecoder decoder = config.jwtDecoder();

        assertThat(decoder).isNotNull();
    }
}
