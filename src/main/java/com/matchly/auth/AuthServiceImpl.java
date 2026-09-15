package com.matchly.auth;

import com.matchly.auth.dto.AuthResponse;
import com.matchly.auth.dto.DeleteAccountRequest;
import com.matchly.auth.dto.LoginRequest;
import com.matchly.auth.dto.RegisterRequest;
import com.matchly.common.exception.AccountBlockedException;
import com.matchly.common.exception.BusinessRuleException;
import com.matchly.common.exception.ConflictException;
import com.matchly.common.exception.InvalidCredentialsException;
import com.matchly.common.exception.NotFoundException;
import com.matchly.security.AuthenticatedUser;
import com.matchly.security.JwtTokenService;
import com.matchly.user.User;
import com.matchly.user.UserMapper;
import com.matchly.user.UserRepository;
import com.matchly.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered");
        }
        User user = userRepository.save(User.register(email, passwordEncoder.encode(request.password())));
        log.info("New user registered: id={}, email={}", user.getId(), user.getEmail());
        return toAuthResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Failed login attempt for email={}", user.getEmail());
            throw new InvalidCredentialsException();
        }
        if (user.isBlocked()) {
            log.warn("Blocked user tried to log in: id={}", user.getId());
            throw new AccountBlockedException();
        }
        log.info("User logged in: id={}", user.getId());
        return toAuthResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse currentUser(AuthenticatedUser principal) {
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new NotFoundException("User", principal.id()));
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public void deleteAccount(AuthenticatedUser principal, DeleteAccountRequest request) {
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new NotFoundException("User", principal.id()));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (user.isAdmin()) {
            throw new BusinessRuleException("Administrator account cannot be deleted this way");
        }
        userRepository.delete(user);
        log.info("User id={} deleted their own account", user.getId());
    }

    private AuthResponse toAuthResponse(User user) {
        JwtTokenService.IssuedToken issued = tokenService.issue(user);
        return new AuthResponse(issued.token(), issued.expiresAt(), userMapper.toResponse(user));
    }
}
