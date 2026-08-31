package com.sleekflow.todo.todos.dto;

import java.util.UUID;

/** A small invalidation message; clients refetch their own bounded query. */
public record TodoChangeEvent(
        long sequence,
        Type type,
        UUID todoId,
        long version
) {
    public enum Type {
        CREATED,
        UPDATED,
        DELETED
    }
}
