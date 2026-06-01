package com.devtoolcopilot.common.jwt;

import com.devtoolcopilot.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtTokenServiceStub implements JwtTokenService {
    private final JwtProperties jwtProperties;

    public JwtTokenServiceStub(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Override
    public String generateToken(Long userId) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(jwtProperties.getExpireSeconds());
        return Jwts.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey())
                .compact();
    }

    @Override
    public Optional<Long> parseUserId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .requireIssuer(jwtProperties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String sub = claims.getSubject();
            if (sub == null || sub.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(sub));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private SecretKey signingKey() {
        byte[] keyBytes = sha256(jwtProperties.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private static byte[] sha256(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest((secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
