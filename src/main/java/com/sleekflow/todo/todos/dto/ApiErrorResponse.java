package com.sleekflow.todo.todos.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Stable error envelope returned for validation, lifecycle, and transport failures.
 *
 * @param timestamp time at which the API produced the response
 * @param status HTTP status code
 * @param code stable machine-readable error code
 * @param message concise explanation suitable for display
 * @param path request path that failed
 * @param fieldErrors validation messages keyed by request field
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
}
