package com.sleekflow.todo.todos.exception;

import java.util.UUID;

public class TodoNotFoundException extends RuntimeException {

    public TodoNotFoundException(UUID id) {
        super("TODO " + id + " was not found");
    }
}
