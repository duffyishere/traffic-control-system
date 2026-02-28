package io.github.duffyishere.turnstile.common;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Component
public class TokenProvider {
    private RSAKey rsaJWK;

    @PostConstruct
    public void initKey() throws JOSEException {
        this.rsaJWK = new RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .keyID(UUID.randomUUID().toString())
                .issueTime(new Date())
                .generate();
    }

    public Map<String, Object> getPublicKey() {
        JWKSet jwkSet = new JWKSet(rsaJWK.toPublicJWK());
        return jwkSet.toJSONObject(true);
    }

    public String generateToken(String requestId) throws JOSEException {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("turnstile")
                .claim("requestId", requestId)
                .issueTime(new Date())
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);
        signedJWT.sign(new RSASSASigner(rsaJWK));
        return signedJWT.serialize();
    }
}
