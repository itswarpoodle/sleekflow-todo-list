package com.sleekflow.todo.todos.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Transport-neutral representation of one bounded result page.
 *
 * @param content items on the current page
 * @param page zero-based page number
 * @param size requested page size
 * @param totalElements number of matching items
 * @param totalPages number of available pages
 * @param <T> response item type
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /**
     * Copies Spring Data page content and metadata into the API contract.
     *
     * @param page source page
     * @param <T> item type
     * @return serializable page response
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
