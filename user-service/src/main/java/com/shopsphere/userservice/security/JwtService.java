package com.shopsphere.userservice.security;

import com.shopsphere.userservice.entity.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationTime;

    public JwtService(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expirationTime) {

        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        this.expirationTime = expirationTime;
    }

    //    public String generateToken(String email) {
//
//        Date now = new Date();
//
//        Date expiration = new Date(
//                now.getTime() + expirationTime
//        );
//
//        return Jwts.builder()
//                .subject(email)
//                .issuedAt(now)
//                .expiration(expiration)
//                .signWith(secretKey)
//                .compact();
//    }
//    public String generateToken(String email, Role role) {
//
//        Map<String, Object> claims = new HashMap<>();
//        claims.put("role", role.name());
//
//        return Jwts.builder().claims(claims).subject(email).issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + expirationTime)).signWith(secretKey).compact();
//    }


    public String generateToken(String email, Long userId, Role role) {

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role.name());
        claims.put("userId", userId);

        return Jwts.builder()
                .claims(claims)
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(secretKey)
                .compact();
    }

    public String extractEmail(String token) {

        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().getSubject();
    }

    public String extractRole(String token) {

        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
    }
}