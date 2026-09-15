package com.matchly.user;

import com.matchly.user.dto.UserResponse;
import org.springframework.stereotype.Component;

/** Преобразование сущности {@link User} в DTO. Сущности наружу не отдаются. */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getStatus(), user.getCreatedAt());
    }
}
