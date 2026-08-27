package com.sleekflow.todo.todos.dto;

import com.sleekflow.todo.todos.model.Todo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TodoResponse(
        UUID id,
        String name,
        String description,
        LocalDate dueDate,
        Todo.Status status,
        Todo.Priority priority,
        Instant createdAt,
        Instant updatedAt
) {

    public static TodoResponse from(Todo todo) {
        return new TodoResponse(
                todo.id(),
                todo.name(),
                todo.description(),
                todo.dueDate(),
                todo.status(),
                todo.priority(),
                todo.createdAt(),
                todo.updatedAt()
        );
    }
}
