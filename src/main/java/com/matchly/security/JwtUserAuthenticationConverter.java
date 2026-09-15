package com.matchly.security;

import com.matchly.user.User;
import com.matchly.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Превращает проверенный JWT в объект аутентификации Spring Security.
 * Пользователь читается из базы на каждом запросе: так удалённые и заблокированные аккаунты
 * теряют доступ немедленно, а не по истечении срока токена.
 */
@Component
@RequiredArgsConstructor
public class JwtUserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new BadCredentialsException("Malformed token subject");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));
        if (user.isBlocked()) {
            throw new LockedException("Account is blocked");
        }
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        return UsernamePasswordAuthenticationToken.authenticated(principal, jwt.getTokenValue(), authorities);
    }
}
