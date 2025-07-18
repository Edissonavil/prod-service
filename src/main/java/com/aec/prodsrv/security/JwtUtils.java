package com.aec.prodsrv.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtils {

    private final Key key;
    private final long accessMs;

    public JwtUtils(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.accessMs}") long accessMs
    ) {
        // construye la clave a partir del secret base64 (o raw)
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessMs = accessMs;
    }

    /** Genera un token con el subject=username y la fecha de expiración */
    public String generateToken(String username) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + accessMs);
        return Jwts.builder()
                   .setSubject(username)
                   .setIssuedAt(now)
                   .setExpiration(exp)
                   .signWith(key, SignatureAlgorithm.HS256)
                   .compact();
    }

    /** Extrae el username (subject) de un JWT válido */
    public String getUsernameFromToken(String token) {
        return Jwts.parserBuilder()
                   .setSigningKey(key)
                   .build()
                   .parseClaimsJws(token)
                   .getBody()
                   .getSubject();
    }

    /** Valida firma y expiración */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
