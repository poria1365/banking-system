package com.banking.infrastructure.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

// Generates signed JWTs using HMAC-SHA256.
// Secret must be at least 256 bits — the default in application.yml is for dev only,
// always override JWT_SECRET in production.
@Service
public class JwtTokenService {

    @Value("${banking.security.jwt.secret}")
    private String secret;

    @Value("${banking.security.jwt.expiration-ms}")
    private long expirationMs;

    public String generate(String subject) {
        return Jwts.builder()
            .subject(subject)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expirationMs))
            .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }
}
