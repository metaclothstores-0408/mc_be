package com.metacloth.security;

import com.metacloth.config.AppProperties;
import com.metacloth.domain.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final AppProperties p;
    private final SecretKey key;

    public JwtService(AppProperties p) {
        this.p = p;
        byte[] b = p.security().jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (b.length < 32) throw new IllegalStateException("JWT_SECRET must be >=32 bytes");
        key = Keys.hmacShaKeyFor(b);
    }

    public String generate(User u) {
        Instant n = Instant.now(), e = n.plusSeconds(p.security().jwt().expirationMinutes() * 60);
        return Jwts.builder().subject(u.getId().toString()).claim("role", u.getRole().name()).claim("email", u.getEmail()).issuedAt(Date.from(n)).expiration(Date.from(e)).signWith(key).compact();
    }

    public Long userId(String t) {
        return Long.valueOf(parse(t).getPayload().getSubject());
    }

    public boolean valid(String t) {
        try {
            parse(t);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Jws<Claims> parse(String t) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(t);
    }

    public long expires() {
        return p.security().jwt().expirationMinutes() * 60;
    }
}
