package io.github.duffyishere.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JwtDecoderConfigTest {

    @Autowired
    private ReactiveJwtDecoder jwtDecoder;

    @Test
    public void testJwtDecoder() {
        System.out.println(jwtDecoder.decode("eyJraWQiOiI5MTlmMzM0Yi1iMWY3LTQzYTMtYWIxZS1mNzc5ZGYwYTg2YWMiLCJhbGciOiJSUzI1NiJ9.SW4gUlNBIHdlIHRydXN0IQ.ClaHlisX7mGqpGmQ9DPnqqFZ08PfSpZlXnE6YOb-p_RIyaCX449I6nIRVBWwKp8n34XIkLpr_AVDuK6cO_plUwTg_R8Lc6yRvXGwRvxc2OXjMqjxjDeq-oGdej9SuFwVAqxgUodHMqr2McJGyQQLh1aM7kmZUgSbxCL4ipyBjaU9R82Bpm_x6_ky96MY2hyuX4_uPcTfqKJjDS-ZRWiUGr4dgz-AXCtVg6bxd-t8KVPPhAVHdCDgkIZjRvJ--5647TjT8oKSpzN8qzp3HciCJ0rbktIfknxvw1BJvUsabuh833x3H8xFSbtGKo2Frwo5vM0g2YYMVpFNHVYO1WV_GA").block().getTokenValue());
    }
}