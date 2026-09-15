package com.matchly.admin;

import com.matchly.common.dto.PageResponse;
import com.matchly.security.AuthenticatedUser;
import com.matchly.user.UserMapper;
import com.matchly.user.UserService;
import com.matchly.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Управление аккаунтами. Доступно только роли ADMIN (правило задано в SecurityConfig). */
@Tag(name = "Admin: users", description = "Просмотр, блокировка и удаление аккаунтов")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserService userService;
    private final UserMapper userMapper;

    @Operation(summary = "Список аккаунтов с поиском по email")
    @GetMapping
    public PageResponse<UserResponse> list(@Parameter(description = "Часть email") @RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(userService.search(q, pageable).map(userMapper::toResponse));
    }

    @Operation(summary = "Аккаунт по идентификатору")
    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return userMapper.toResponse(userService.getById(id));
    }

    @Operation(summary = "Заблокировать аккаунт")
    @PostMapping("/{id}/block")
    public UserResponse block(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser admin) {
        return userMapper.toResponse(userService.block(id, admin.id()));
    }

    @Operation(summary = "Разблокировать аккаунт")
    @PostMapping("/{id}/unblock")
    public UserResponse unblock(@PathVariable Long id) {
        return userMapper.toResponse(userService.unblock(id));
    }

    @Operation(summary = "Удалить аккаунт со всеми данными")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser admin) {
        userService.delete(id, admin.id());
    }
}
