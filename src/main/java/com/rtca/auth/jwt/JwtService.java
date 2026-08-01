package com.rtca.auth.jwt;

import com.rtca.auth.AuthUser;
import com.rtca.common.ids.PublicIds;
import com.rtca.user.Role;
import com.rtca.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class JwtService {

    private final JwtProperties props;
    private final SecretKey key;
    private final PublicIds ids;

    public JwtService(JwtProperties props, PublicIds ids) {
        this.props = props;
        this.ids = ids;
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.secret()));
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getPublicId().toString())
                .issuer(props.issuer())
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.accessTokenTtl())))
                .signWith(key)
                .compact();
    }

    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(props.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid jwt: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** The subject is the public id; the principal carries the internal one. */
    public Optional<AuthUser> authenticate(String token) {
        return parse(token).flatMap(c -> internalId(c.getSubject()).map(id -> new AuthUser(
                id,
                c.get("username", String.class),
                Role.valueOf(c.get("role", String.class))
        )));
    }

    private Optional<Long> internalId(String subject) {
        try {
            return ids.findUserId(UUID.fromString(subject));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getAccessTokenTtlSeconds() {
        return props.accessTokenTtl().toSeconds();
    }
}
