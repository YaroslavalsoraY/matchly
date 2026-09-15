package com.matchly.admin;

import com.matchly.settings.SettingsService;
import com.matchly.settings.dto.SettingsResponse;
import com.matchly.settings.dto.UpdateSettingsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin: settings", description = "Настройки приложения")
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final SettingsService settingsService;

    @Operation(summary = "Текущие настройки")
    @GetMapping
    public SettingsResponse get() {
        return settingsService.get();
    }

    @Operation(summary = "Изменить алгоритм рекомендаций по умолчанию")
    @PutMapping
    public SettingsResponse update(@Valid @RequestBody UpdateSettingsRequest request) {
        return settingsService.update(request);
    }
}
