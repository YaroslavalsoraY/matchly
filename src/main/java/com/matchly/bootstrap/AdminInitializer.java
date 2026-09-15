package com.matchly.bootstrap;

import com.matchly.config.MatchlyProperties;
import com.matchly.user.User;
import com.matchly.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Создаёт администратора при первом запуске (email и пароль из настроек matchly.admin).
 * Выполняется раньше генератора демо-данных.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final MatchlyProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        MatchlyProperties.Admin admin = properties.admin();
        if (userRepository.existsByEmailIgnoreCase(admin.email())) {
            log.debug("Administrator {} already exists", admin.email());
            return;
        }
        userRepository.save(User.createAdmin(admin.email(), passwordEncoder.encode(admin.password())));
        log.info("Administrator account created: {}", admin.email());
    }
}
