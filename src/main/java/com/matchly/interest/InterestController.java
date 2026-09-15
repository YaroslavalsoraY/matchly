package com.matchly.interest;

import com.matchly.interest.dto.InterestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Interests", description = "Справочник интересов для заполнения анкеты")
@RestController
@RequestMapping("/api/interests")
@RequiredArgsConstructor
public class InterestController {

    private final InterestService interestService;

    @Operation(summary = "Все интересы, сгруппированные по категории")
    @GetMapping
    public List<InterestResponse> list() {
        return interestService.findAll();
    }
}
