package com.ecommerce.catalog.api.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Stable pagination envelope.
 *
 * <p>Returning Spring Data's {@code Page} straight from a controller works but is a
 * mistake worth avoiding: its JSON structure is an implementation detail that has
 * changed between versions, and Boot 3.3+ logs a warning telling you exactly that.
 * Declaring our own envelope means the Angular client and generated OpenAPI clients
 * are insulated from Spring Data's internals.
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
