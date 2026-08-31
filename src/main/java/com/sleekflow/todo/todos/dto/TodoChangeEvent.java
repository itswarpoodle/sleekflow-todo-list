package com.sleekflow.todo.todos.dto;

import java.util.UUID;

/**
 * Small invalidation message that tells clients to refetch their bounded query.
 *
 * @param sequence instance-local monotonic event ID
 * @param type kind of committed change
 * @param todoId changed TODO identifier
 * @param version entity version after the change
 */
public record TodoChangeEvent(
        long sequence,
        Type type,
        UUID todoId,
        long version
) {
    /** Describes the committed change that invalidated client state. */
    public enum Type {
        /** A new TODO became visible. */
        CREATED,
        /** An existing TODO changed. */
        UPDATED,
        /** A TODO left active views through soft deletion. */
        DELETED
    }
}
