package com.matchly.admin;

import com.matchly.admin.dto.AdminStatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin: stats", description = "Сводная статистика сервиса")
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService statsService;

    @Operation(summary = "Показатели: пользователи, анкеты, лайки, матчи, популярные интересы")
    @GetMapping
    public AdminStatsResponse stats() {
        return statsService.collect();
    }
}
