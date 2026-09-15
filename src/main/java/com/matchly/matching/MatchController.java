package com.matchly.matching;

import com.matchly.matching.dto.MatchResponse;
import com.matchly.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Matches", description = "Взаимные симпатии и контакты")
@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @Operation(summary = "Мои матчи с контактами собеседников")
    @GetMapping
    public List<MatchResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return matchService.listMatches(user.id());
    }

    @Operation(summary = "Разорвать матч")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unmatch(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        matchService.unmatch(user.id(), id);
    }
}
