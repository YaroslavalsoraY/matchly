package com.matchly.admin;

import com.matchly.bootstrap.DemoDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ручной запуск генерации демо-данных, если при старте они не были созданы. Только ADMIN. */
@Tag(name = "Admin: demo data", description = "Создание демонстрационных анкет по запросу")
@RestController
@RequestMapping("/api/admin/demo-data")
@RequiredArgsConstructor
public class AdminDemoDataController {

    private final DemoDataService demoDataService;

    @Operation(summary = "Создать демо-анкеты",
            description = "Создаёт демо-аккаунты demoN@matchly.local с анкетами, аватарами, лайками и матчами. "
                    + "Если они уже есть, возвращает статус ALREADY_PRESENT и ничего не меняет.")
    @PostMapping
    public DemoDataService.SeedResult seed() {
        return demoDataService.seed();
    }
}
