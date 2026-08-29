package com.sleekflow.todo.todos.dto;

import com.sleekflow.todo.todos.model.Todo;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RecurrenceRule(
        @NotNull(message = "Recurrence frequency is required")
        Todo.RecurrenceFrequency frequency,
        @Positive(message = "Recurrence interval must be positive")
        Integer interval,
        Todo.RecurrenceUnit unit
) {

    public static RecurrenceRule from(Todo.Recurrence recurrence) {
        return recurrence == null
                ? null
                : new RecurrenceRule(recurrence.frequency(), recurrence.interval(), recurrence.unit());
    }
}
