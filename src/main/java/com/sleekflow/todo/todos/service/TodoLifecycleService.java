package com.sleekflow.todo.todos.service;

import com.sleekflow.todo.todos.dto.RecurrenceRule;
import com.sleekflow.todo.todos.exception.TodoRuleViolationException;
import com.sleekflow.todo.todos.model.Todo;
import com.sleekflow.todo.todos.repository.TodoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Applies rules that involve more than one TODO, such as dependency validation,
 * cycle detection, blocked transitions, and recurrence normalization.
 */
@Service
public class TodoLifecycleService {

    private final TodoRepository repository;

    public TodoLifecycleService(TodoRepository repository) {
        this.repository = repository;
    }

    /**
     * Resolves only active dependencies and reports every missing ID together so
     * callers receive one useful validation response instead of failing repeatedly.
     */
    public Set<Todo> resolveDependencies(UUID todoId, Set<UUID> dependencyIds) {
        var requestedIds = dependencyIds == null ? Set.<UUID>of() : dependencyIds;

        if (todoId != null && requestedIds.contains(todoId)) {
            throw new TodoRuleViolationException(
                    HttpStatus.BAD_REQUEST,
                    "SELF_DEPENDENCY",
                    "A TODO cannot depend on itself"
            );
        }

        var dependencies = repository.findAllByIdInAndDeletedAtIsNull(requestedIds);
        if (dependencies.size() != requestedIds.size()) {
            var foundIds = dependencies.stream().map(Todo::id).collect(java.util.stream.Collectors.toSet());
            var missingIds = requestedIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .sorted(Comparator.comparing(UUID::toString))
                    .map(UUID::toString)
                    .toList();
            throw new TodoRuleViolationException(
                    HttpStatus.BAD_REQUEST,
                    "DEPENDENCY_NOT_FOUND",
                    "Active dependencies were not found: " + String.join(", ", missingIds)
            );
        }

        dependencies.sort(Comparator.comparing(todo -> todo.id().toString()));
        return new LinkedHashSet<>(dependencies);
    }

    /**
     * Validates dependency changes before the aggregate is mutated.
     */
    public void validateUpdate(Todo todo, Collection<Todo> dependencies, Todo.Status status) {
        if (dependencies.stream().anyMatch(dependency -> reaches(dependency, todo.id()))) {
            throw new TodoRuleViolationException(
                    HttpStatus.CONFLICT,
                    "CYCLIC_DEPENDENCY",
                    "The dependency change would create a cycle"
            );
        }
        validateStatus(status, dependencies);
    }

    public void validateStatus(Todo.Status status, Collection<Todo> dependencies) {
        if (status == Todo.Status.IN_PROGRESS && isBlocked(dependencies)) {
            throw new TodoRuleViolationException(
                    HttpStatus.CONFLICT,
                    "TODO_BLOCKED",
                    "A blocked TODO cannot be moved to IN_PROGRESS"
            );
        }
    }

    /**
     * Rejects newly assigned past dates while allowing an overdue TODO to keep its
     * existing date. Without that exception, overdue work could not be completed,
     * archived, or otherwise edited unless it was first rescheduled.
     */
    public void validateDueDate(LocalDate dueDate, LocalDate existingDueDate) {
        if (dueDate != null && dueDate.isBefore(LocalDate.now()) && !dueDate.equals(existingDueDate)) {
            throw new TodoRuleViolationException(
                    HttpStatus.BAD_REQUEST,
                    "TODO_DUE_DATE_IN_PAST",
                    "Due date must be today or later"
            );
        }
    }

    /**
     * Converts the API recurrence shape into the canonical domain representation.
     * Named schedules always mean one unit; only CUSTOM accepts an interval and unit.
     */
    public Todo.Recurrence normalizeRecurrence(RecurrenceRule rule, LocalDate dueDate) {
        if (rule == null) {
            return null;
        }
        if (dueDate == null) {
            throw invalidRecurrence("A recurring TODO requires a due date");
        }
        if (rule.frequency() == null) {
            throw invalidRecurrence("Recurrence frequency is required");
        }

        return switch (rule.frequency()) {
            case DAILY -> standardRecurrence(rule, 1, Todo.RecurrenceUnit.DAYS);
            case WEEKLY -> standardRecurrence(rule, 1, Todo.RecurrenceUnit.WEEKS);
            case MONTHLY -> standardRecurrence(rule, 1, Todo.RecurrenceUnit.MONTHS);
            case CUSTOM -> customRecurrence(rule);
        };
    }

    private Todo.Recurrence standardRecurrence(
            RecurrenceRule rule,
            int interval,
            Todo.RecurrenceUnit unit
    ) {
        if ((rule.interval() != null && rule.interval() != interval)
                || (rule.unit() != null && rule.unit() != unit)) {
            throw invalidRecurrence(rule.frequency() + " recurrence must use interval 1 " + unit);
        }
        return new Todo.Recurrence(rule.frequency(), interval, unit);
    }

    private Todo.Recurrence customRecurrence(RecurrenceRule rule) {
        if (rule.interval() == null || rule.interval() <= 0 || rule.unit() == null) {
            throw invalidRecurrence("Custom recurrence requires a positive interval and unit");
        }
        return new Todo.Recurrence(rule.frequency(), rule.interval(), rule.unit());
    }

    private TodoRuleViolationException invalidRecurrence(String message) {
        return new TodoRuleViolationException(HttpStatus.BAD_REQUEST, "INVALID_RECURRENCE", message);
    }

    private boolean isBlocked(Collection<Todo> dependencies) {
        return dependencies.stream().anyMatch(dependency -> dependency.status() != Todo.Status.COMPLETED);
    }

    private boolean reaches(Todo start, UUID targetId) {
        // Iterative traversal avoids call-stack growth for long dependency chains.
        var pending = new ArrayDeque<Todo>();
        var visited = new HashSet<UUID>();
        pending.push(start);

        while (!pending.isEmpty()) {
            var current = pending.pop();
            if (current.id().equals(targetId)) {
                return true;
            }
            if (visited.add(current.id())) {
                pending.addAll(current.dependencies());
            }
        }
        return false;
    }
}
