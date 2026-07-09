package io.github.huseyinbabal.taskhub.common;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Transport shape for a page of results (SPEC §Session 2 acceptance #4): the
 * content plus the metadata a client needs to page through — never a raw
 * unbounded list, never a serialized {@code Page} entity.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
