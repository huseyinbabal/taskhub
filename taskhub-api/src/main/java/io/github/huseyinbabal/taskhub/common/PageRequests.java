package io.github.huseyinbabal.taskhub.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Builds a bounded {@link PageRequest} from raw {@code page}/{@code size} query
 * params (SPEC §Session 2 acceptance #4): negative pages floor to 0, missing or
 * non-positive sizes fall back to {@link #DEFAULT_SIZE}, and oversized sizes are
 * capped at {@link #MAX_SIZE} so a client can never request an unbounded page.
 */
public final class PageRequests {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    public static PageRequest of(Integer page, Integer size) {
        return of(page, size, Sort.unsorted());
    }

    public static PageRequest of(Integer page, Integer size, Sort sort) {
        int safePage = (page == null || page < 0) ? 0 : page;
        int safeSize = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(safePage, safeSize, sort);
    }
}
