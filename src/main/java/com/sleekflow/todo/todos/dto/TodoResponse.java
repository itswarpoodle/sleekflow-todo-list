package com.sleekflow.todo.todos.dto;

import com.sleekflow.todo.todos.model.Todo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record TodoResponse(
        UUID id,
        String name,
        String description,
        LocalDate dueDate,
        Todo.Status status,
        Todo.Priority priority,
        Set<UUID> dependencyIds,
        boolean blocked,
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
                todo.dependencies().stream()
                        .map(Todo::id)
                        .sorted(Comparator.comparing(UUID::toString))
                        .collect(Collectors.toCollection(LinkedHashSet::new)),
                todo.dependencies().stream()
                        .anyMatch(dependency -> dependency.status() != Todo.Status.COMPLETED),
                todo.createdAt(),
                todo.updatedAt()
        );
    }
}
