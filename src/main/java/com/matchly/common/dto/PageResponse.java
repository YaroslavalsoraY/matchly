package com.matchly.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Страница результатов в стабильном формате API (не зависит от внутреннего представления Spring Data).
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
