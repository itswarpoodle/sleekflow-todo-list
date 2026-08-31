package com.sleekflow.todo.todos.dto;

import com.sleekflow.todo.todos.model.Todo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Complete API representation of an active TODO.
 *
 * @param id stable identifier
 * @param name display name
 * @param description optional supporting detail
 * @param dueDate optional calendar due date
 * @param status lifecycle status
 * @param priority domain priority
 * @param version optimistic-lock version used in the ETag
 * @param dependencyIds prerequisite TODO identifiers
 * @param blocked whether any prerequisite is not completed
 * @param recurrence optional normalized recurrence rule
 * @param previousOccurrenceId source occurrence for a generated recurring TODO
 * @param createdAt creation timestamp
 * @param updatedAt latest persistence timestamp
 */
public record TodoResponse(
        UUID id,
        String name,
        String description,
        LocalDate dueDate,
        Todo.Status status,
        Todo.Priority priority,
        long version,
        Set<UUID> dependencyIds,
        boolean blocked,
        RecurrenceRule recurrence,
        UUID previousOccurrenceId,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Builds the external read model and derives dependency state from the aggregate.
     * Dependency IDs are sorted to keep serialized responses deterministic.
     *
     * @param todo hydrated domain aggregate
     * @return API response
     */
    public static TodoResponse from(Todo todo) {
        return new TodoResponse(
                todo.id(),
                todo.name(),
                todo.description(),
                todo.dueDate(),
                todo.status(),
                todo.priority(),
                todo.version(),
                todo.dependencies().stream()
                        .map(Todo::id)
                        .sorted(Comparator.comparing(UUID::toString))
                        .collect(Collectors.toCollection(LinkedHashSet::new)),
                todo.dependencies().stream()
                        .anyMatch(dependency -> dependency.status() != Todo.Status.COMPLETED),
                RecurrenceRule.from(todo.recurrence()),
                todo.previousOccurrenceId(),
                todo.createdAt(),
                todo.updatedAt()
        );
    }
}
