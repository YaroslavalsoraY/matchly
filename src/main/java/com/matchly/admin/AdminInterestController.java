package com.matchly.admin;

import com.matchly.interest.InterestService;
import com.matchly.interest.dto.InterestRequest;
import com.matchly.interest.dto.InterestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** CRUD справочника интересов. Только ADMIN. */
@Tag(name = "Admin: interests", description = "Ведение справочника интересов")
@RestController
@RequestMapping("/api/admin/interests")
@RequiredArgsConstructor
public class AdminInterestController {

    private final InterestService interestService;

    @Operation(summary = "Добавить интерес")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterestResponse create(@Valid @RequestBody InterestRequest request) {
        return interestService.create(request);
    }

    @Operation(summary = "Переименовать интерес или сменить категорию")
    @PutMapping("/{id}")
    public InterestResponse update(@PathVariable Long id, @Valid @RequestBody InterestRequest request) {
        return interestService.update(id, request);
    }

    @Operation(summary = "Удалить интерес (пропадает из всех анкет)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        interestService.delete(id);
    }
}
