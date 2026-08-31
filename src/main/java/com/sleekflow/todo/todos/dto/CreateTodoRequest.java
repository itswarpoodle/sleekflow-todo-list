package com.sleekflow.todo.todos.dto;

import com.sleekflow.todo.todos.model.Todo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Request contract for creating a TODO. Status and priority may be omitted and
 * are defaulted by the service; collections may be omitted to mean no dependencies.
 *
 * @param name required display name
 * @param description optional supporting detail
 * @param dueDate optional calendar due date
 * @param status optional initial status
 * @param priority optional initial priority
 * @param dependencyIds active TODOs that must be completed first
 * @param recurrence optional recurrence rule
 */
public record CreateTodoRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name must be 120 characters or fewer")
        String name,
        @Size(max = 2000, message = "Description must be 2000 characters or fewer")
        String description,
        LocalDate dueDate,
        Todo.Status status,
        Todo.Priority priority,
        Set<UUID> dependencyIds,
        @Valid RecurrenceRule recurrence
) {
}
