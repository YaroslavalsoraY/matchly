package com.matchly.reaction;

import com.matchly.reaction.dto.ReactionRequest;
import com.matchly.reaction.dto.ReactionResponse;
import com.matchly.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reactions", description = "Лайки и пропуски карточек")
@RestController
@RequestMapping("/api/reactions")
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionService reactionService;

    @Operation(summary = "Лайкнуть или пропустить анкету",
            description = "При взаимном лайке в ответе приходит matched=true и данные матча с контактом")
    @PostMapping
    public ReactionResponse react(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody ReactionRequest request) {
        return reactionService.react(user.id(), request);
    }
}
