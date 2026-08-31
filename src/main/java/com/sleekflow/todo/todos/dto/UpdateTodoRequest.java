package com.sleekflow.todo.todos.dto;

import com.sleekflow.todo.todos.model.Todo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Full replacement contract for editable TODO fields.
 *
 * @param name required display name
 * @param description optional supporting detail
 * @param dueDate optional calendar due date
 * @param status required lifecycle status
 * @param priority required domain priority
 * @param dependencyIds desired prerequisite identifiers
 * @param recurrence optional recurrence rule
 */
public record UpdateTodoRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name must be 120 characters or fewer")
        String name,
        @Size(max = 2000, message = "Description must be 2000 characters or fewer")
        String description,
        LocalDate dueDate,
        @NotNull(message = "Status is required")
        Todo.Status status,
        @NotNull(message = "Priority is required")
        Todo.Priority priority,
        Set<UUID> dependencyIds,
        @Valid RecurrenceRule recurrence
) {
}
