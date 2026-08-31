package com.sleekflow.todo.todos.exception;

import java.util.UUID;

/** Raised when a requested TODO does not exist or has been soft-deleted. */
public class TodoNotFoundException extends RuntimeException {

    /**
     * Creates a message that identifies the missing TODO.
     *
     * @param id requested identifier
     */
    public TodoNotFoundException(UUID id) {
        super("TODO " + id + " was not found");
    }
}
