package com.smart_complaint_service.project.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.function.Function;

@Service
public class JWTService {

    private static final long EXPIRATION_TIME = 1000L * 60 * 60 * 24 * 2; // 2 days

    private Key key;

    @PostConstruct
    public void initKey() {
        String secret = System.getenv("JWT_SECRET");

        if (secret != null && !secret.isBlank()) {
            byte[] keyBytes = Base64.getDecoder().decode(secret);
            this.key = Keys.hmacShaKeyFor(keyBytes);
            System.out.println("JWTService: using externalized JWT_SECRET key.");
        } else {
            this.key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
            System.out.println("JWTService: JWT_SECRET not set — using in-memory key (local dev only).");
        }
    }

    public String generateToken(UserDetails userDetails) {
        String role = userDetails.getAuthorities()
                .iterator()
                .next()
                .getAuthority();

        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        return extractUsername(token).equals(userDetails.getUsername())
                && extractExpiration(token).after(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}