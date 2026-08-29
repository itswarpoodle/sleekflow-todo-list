package com.sleekflow.todo.todos.service;

import com.sleekflow.todo.todos.dto.RecurrenceRule;
import com.sleekflow.todo.todos.exception.TodoNotFoundException;
import com.sleekflow.todo.todos.exception.TodoRuleViolationException;
import com.sleekflow.todo.todos.model.Todo;
import com.sleekflow.todo.todos.repository.TodoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TodoService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORT_FIELDS = Set.of("dueDate", "priority", "status", "name");

    private final TodoRepository repository;
    private final TodoLifecycleService lifecycle;

    public TodoService(TodoRepository repository, TodoLifecycleService lifecycle) {
        this.repository = repository;
        this.lifecycle = lifecycle;
    }

    @Transactional(readOnly = true)
    public Page<Todo> findAll(
            int page,
            int size,
            Todo.Status status,
            Todo.Priority priority,
            LocalDate dueDate,
            Boolean blocked,
            String sort,
            String direction
    ) {
        validatePage(page, size);
        var sortField = normalizeSortField(sort);
        var sortDirection = normalizeDirection(direction);
        var pageable = PageRequest.of(page, size);
        var result = repository.findPage(
                status != null,
                status == null ? Todo.Status.NOT_STARTED : status,
                priority != null,
                priority == null ? Todo.Priority.MEDIUM : priority,
                dueDate != null,
                dueDate == null ? LocalDate.of(1970, 1, 1) : dueDate,
                blocked != null,
                Boolean.TRUE.equals(blocked),
                sortField,
                sortDirection,
                Todo.Status.COMPLETED,
                pageable
        );

        if (result.isEmpty()) {
            return result;
        }

        var ids = result.getContent().stream().map(Todo::id).toList();
        Map<UUID, Todo> hydratedById = repository.findWithDependenciesByIdIn(ids).stream()
                .collect(Collectors.toMap(Todo::id, Function.identity()));
        var hydrated = ids.stream().map(hydratedById::get).toList();
        return new PageImpl<>(hydrated, pageable, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Todo findById(UUID id) {
        return findActive(id);
    }

    @Transactional
    public Todo create(
            String name,
            String description,
            LocalDate dueDate,
            Todo.Status status,
            Todo.Priority priority,
            Set<UUID> dependencyIds,
            RecurrenceRule recurrenceRule
    ) {
        var effectiveStatus = status == null ? Todo.Status.NOT_STARTED : status;
        var dependencies = lifecycle.resolveDependencies(null, dependencyIds);
        lifecycle.validateStatus(effectiveStatus, dependencies);
        var recurrence = lifecycle.normalizeRecurrence(recurrenceRule, dueDate);

        var todo = new Todo(
                name.trim(),
                normalizeDescription(description),
                dueDate,
                effectiveStatus,
                priority == null ? Todo.Priority.MEDIUM : priority,
                recurrence
        );
        todo.replaceDependencies(dependencies);
        return repository.save(todo);
    }

    @Transactional
    public Todo update(
            UUID id,
            String name,
            String description,
            LocalDate dueDate,
            Todo.Status status,
            Todo.Priority priority,
            Set<UUID> dependencyIds,
            RecurrenceRule recurrenceRule
    ) {
        var todo = findActive(id);
        var previousStatus = todo.status();
        var dependencies = lifecycle.resolveDependencies(id, dependencyIds);
        lifecycle.validateUpdate(todo, dependencies, status);
        var recurrence = lifecycle.normalizeRecurrence(recurrenceRule, dueDate);
        todo.update(name.trim(), normalizeDescription(description), dueDate, status, priority, recurrence);
        todo.replaceDependencies(dependencies);

        if (previousStatus != Todo.Status.COMPLETED
                && status == Todo.Status.COMPLETED
                && recurrence != null
                && !repository.existsByPreviousOccurrenceId(todo.id())) {
            var nextOccurrence = todo.nextOccurrence();
            nextOccurrence.replaceDependencies(dependencies);
            repository.save(nextOccurrence);
        }
        return todo;
    }

    @Transactional
    public void delete(UUID id) {
        findActive(id).softDelete();
    }

    private Todo findActive(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new TodoNotFoundException(id));
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new TodoRuleViolationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGINATION",
                    "Page must be zero or greater and size must be between 1 and " + MAX_PAGE_SIZE
            );
        }
    }

    private String normalizeSortField(String sort) {
        if (sort == null || sort.isBlank()) {
            return "createdAt";
        }
        if (!SORT_FIELDS.contains(sort)) {
            throw new TodoRuleViolationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SORT",
                    "Sort must be one of: dueDate, priority, status, name"
            );
        }
        return sort;
    }

    private String normalizeDirection(String direction) {
        var normalized = direction == null ? "asc" : direction.toLowerCase(Locale.ROOT);
        if (!normalized.equals("asc") && !normalized.equals("desc")) {
            throw new TodoRuleViolationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SORT",
                    "Direction must be asc or desc"
            );
        }
        return normalized;
    }
}
