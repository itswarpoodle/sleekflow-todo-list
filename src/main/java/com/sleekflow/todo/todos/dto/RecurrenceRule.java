package com.sleekflow.todo.todos.dto;

import com.sleekflow.todo.todos.model.Todo;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * API representation of a recurrence schedule.
 *
 * @param frequency named or custom schedule
 * @param interval number of units between custom occurrences
 * @param unit calendar unit used by a custom schedule
 */
public record RecurrenceRule(
        @NotNull(message = "Recurrence frequency is required")
        Todo.RecurrenceFrequency frequency,
        @Positive(message = "Recurrence interval must be positive")
        Integer interval,
        Todo.RecurrenceUnit unit
) {

    /**
     * Maps the domain recurrence value to its API representation.
     *
     * @param recurrence domain value, or {@code null} for a non-recurring TODO
     * @return mapped rule, or {@code null}
     */
    public static RecurrenceRule from(Todo.Recurrence recurrence) {
        return recurrence == null
                ? null
                : new RecurrenceRule(recurrence.frequency(), recurrence.interval(), recurrence.unit());
    }
}
