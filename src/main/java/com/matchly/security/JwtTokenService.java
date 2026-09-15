package com.matchly.security;

import com.matchly.config.MatchlyProperties;
import com.matchly.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Выдача JWT для аутентифицированного пользователя.
 * В токен попадают идентификатор (subject), email и роль; срок жизни задаётся настройкой matchly.security.jwt-ttl.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    public static final String ISSUER = "matchly";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLE = "role";

    private final JwtEncoder jwtEncoder;
    private final MatchlyProperties properties;

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.security().jwtTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        log.debug("Issued JWT for user id={} valid until {}", user.getId(), expiresAt);
        return new IssuedToken(token, expiresAt);
    }

    /** Выданный токен и момент истечения его срока. */
    public record IssuedToken(String token, Instant expiresAt) {
    }
}
