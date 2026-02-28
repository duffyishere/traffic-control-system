package io.github.duffyishere.turnstile.token;

import com.nimbusds.jose.JOSEException;
import io.github.duffyishere.turnstile.common.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class TokenController {

    private final TokenProvider tokenProvider;

    @GetMapping(".well-known/openid-configuration")
    public Map<String, Object> getJwks() throws JOSEException {
        return tokenProvider.getPublicKey();
    }
}
