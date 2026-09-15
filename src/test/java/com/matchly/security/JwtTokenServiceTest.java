package com.matchly.security;

import com.matchly.config.JwtConfig;
import com.matchly.config.MatchlyProperties;
import com.matchly.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Выданный токен проверяется тем же ключом и содержит нужные утверждения; чужой ключ отклоняется. */
class JwtTokenServiceTest {

    private static final String SECRET = "unit-test-secret-key-0123456789-abcdefghij";

    private final JwtConfig jwtConfig = new JwtConfig();

    @Test
    void issuedToken_isDecodable_andCarriesUserClaims() {
        MatchlyProperties props = properties(SECRET, Duration.ofMinutes(30));
        SecretKey key = jwtConfig.jwtSecretKey(props);
        JwtTokenService service = new JwtTokenService(jwtConfig.jwtEncoder(key), props);
        User user = User.register("Jane@Example.com", "hash");
        ReflectionTestUtils.setField(user, "id", 42L);

        JwtTokenService.IssuedToken issued = service.issue(user);
        Jwt jwt = jwtConfig.jwtDecoder(key).decode(issued.token());

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString(JwtTokenService.CLAIM_EMAIL)).isEqualTo("jane@example.com");
        assertThat(jwt.getClaimAsString(JwtTokenService.CLAIM_ROLE)).isEqualTo("USER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(JwtTokenService.ISSUER);
        assertThat(jwt.getExpiresAt()).isCloseTo(issued.expiresAt(), within(1, ChronoUnit.SECONDS));
    }

    @Test
    void tokenSignedWithAnotherKey_isRejected() {
        MatchlyProperties props = properties(SECRET, Duration.ofMinutes(5));
        SecretKey key = jwtConfig.jwtSecretKey(props);
        JwtTokenService service = new JwtTokenService(jwtConfig.jwtEncoder(key), props);
        User user = User.register("x@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", 1L);
        String token = service.issue(user).token();

        SecretKey otherKey = jwtConfig.jwtSecretKey(properties("another-secret-key-0123456789-abcdefghij", Duration.ofMinutes(5)));

        assertThatThrownBy(() -> jwtConfig.jwtDecoder(otherKey).decode(token))
                .isInstanceOf(JwtException.class);
    }

    private static MatchlyProperties properties(String secret, Duration ttl) {
        return new MatchlyProperties(
                new MatchlyProperties.Security(secret, ttl),
                new MatchlyProperties.Seed(false, 0),
                new MatchlyProperties.Admin("admin@test.local", "secret1"));
    }
}
