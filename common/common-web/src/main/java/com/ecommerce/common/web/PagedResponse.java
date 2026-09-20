package com.ecommerce.common.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Stable pagination envelope, shared by every service.
 *
 * <p>Returning Spring Data's {@code Page} straight from a controller works but is a
 * mistake worth avoiding: its JSON structure is an implementation detail that has
 * changed between versions, and Boot 3.3+ logs a warning saying exactly that.
 * Declaring our own envelope insulates the Angular client and any generated OpenAPI
 * clients from Spring Data's internals.
 *
 * <p>It lives in common-web rather than per service so there is one wire contract
 * rather than several identical ones drifting apart - the frontend has a single
 * TypeScript interface mirroring this.
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <E, T> PagedResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PagedResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
