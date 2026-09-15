package com.matchly.interest.dto;

import com.matchly.interest.InterestCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Создание или изменение элемента справочника интересов. */
public record InterestRequest(@NotBlank @Size(min = 2, max = 50) String name, @NotNull InterestCategory category) {
}
