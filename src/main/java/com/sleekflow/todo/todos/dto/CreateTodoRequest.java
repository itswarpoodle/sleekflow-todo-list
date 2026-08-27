package com.sleekflow.todo.todos.dto;

import com.sleekflow.todo.todos.model.Todo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateTodoRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name must be 120 characters or fewer")
        String name,
        @Size(max = 2000, message = "Description must be 2000 characters or fewer")
        String description,
        LocalDate dueDate,
        Todo.Status status,
        Todo.Priority priority
) {
}
