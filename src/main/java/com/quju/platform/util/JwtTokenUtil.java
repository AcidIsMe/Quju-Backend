package com.quju.platform.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtTokenUtil {

    private final SecretKey secretKey;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;

    public JwtTokenUtil(@Value("${quju.jwt.secret}") String secret,
                        @Value("${quju.jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds,
                        @Value("${quju.jwt.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public String createAccessToken(String userId, String role) {
        return createToken(userId, Map.of("role", role, "type", "access"), accessTokenTtlSeconds);
    }

    public String createRefreshToken(String userId) {
        return createToken(userId, Map.of(
                "type", "refresh",
                "jti", UUID.randomUUID().toString()), refreshTokenTtlSeconds);
    }

    public String createToken(String subject, Map<String, Object> claims, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(secretKey)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String parseSubjectQuietly(String token) {
        try {
            return parse(token).getSubject();
        } catch (Exception ignored) {
            return null;
        }
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }
}
