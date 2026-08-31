package com.sleekflow.todo.todos.service;

import com.sleekflow.todo.todos.dto.RecurrenceRule;
import com.sleekflow.todo.todos.dto.TodoChangeEvent;
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

/**
 * Defines the transaction boundary for TODO use cases and coordinates persistence
 * with the lifecycle rules that span multiple aggregates.
 */
@Service
public class TodoService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORT_FIELDS = Set.of("dueDate", "priority", "status", "name");

    private final TodoRepository repository;
    private final TodoLifecycleService lifecycle;
    private final TodoEventStream events;

    /**
     * Creates the transactional TODO application service.
     *
     * @param repository TODO persistence operations
     * @param lifecycle cross-TODO validation and recurrence normalization
     * @param events after-commit browser invalidations
     */
    public TodoService(TodoRepository repository, TodoLifecycleService lifecycle, TodoEventStream events) {
        this.repository = repository;
        this.lifecycle = lifecycle;
        this.events = events;
    }

    /**
     * Returns one bounded page. The first query keeps pagination cheap; the second
     * hydrates dependencies only for IDs already selected into that page.
     *
     * @param page zero-based page number
     * @param size page size from 1 to 100
     * @param status optional exact status filter
     * @param priority optional exact priority filter
     * @param dueDate optional exact due-date filter
     * @param blocked optional dependency-state filter
     * @param name optional case-insensitive partial name
     * @param sort optional supported sort field
     * @param direction ascending or descending direction
     * @return hydrated active TODO page
     * @throws TodoRuleViolationException for invalid pagination or sorting
     */
    @Transactional(readOnly = true)
    public Page<Todo> findAll(
            int page,
            int size,
            Todo.Status status,
            Todo.Priority priority,
            LocalDate dueDate,
            Boolean blocked,
            String name,
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
                name != null && !name.isBlank(),
                name == null ? "" : name.trim(),
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

    /**
     * Retrieves one active TODO with its dependencies.
     *
     * @param id TODO identifier
     * @return active aggregate
     * @throws TodoNotFoundException when absent or soft-deleted
     */
    @Transactional(readOnly = true)
    public Todo findById(UUID id) {
        return findActive(id);
    }

    /**
     * Creates a validated TODO and schedules an invalidation after commit.
     *
     * @param name display name
     * @param description optional description
     * @param dueDate optional calendar due date
     * @param status optional initial status, defaulting to not started
     * @param priority optional priority, defaulting to medium
     * @param dependencyIds requested prerequisite identifiers
     * @param recurrenceRule optional API recurrence rule
     * @return persisted aggregate
     */
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
        lifecycle.validateDueDate(dueDate, null);
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
        var created = repository.save(todo);
        events.publishAfterCommit(TodoChangeEvent.Type.CREATED, created);
        return created;
    }

    /**
     * Updates a TODO and creates the next recurring occurrence only on its first
     * transition to COMPLETED. Optimistic locking and the database uniqueness guard
     * make competing completion requests fail safely instead of creating duplicates.
     *
     * @param id TODO identifier
     * @param expectedVersion version supplied through {@code If-Match}
     * @param name replacement display name
     * @param description replacement description
     * @param dueDate replacement due date
     * @param status replacement status
     * @param priority replacement priority
     * @param dependencyIds replacement prerequisite identifiers
     * @param recurrenceRule replacement recurrence rule
     * @return updated aggregate
     * @throws TodoNotFoundException when the TODO is absent or soft-deleted
     * @throws TodoRuleViolationException when the version or lifecycle rules reject the update
     */
    @Transactional
    public Todo update(
            UUID id,
            long expectedVersion,
            String name,
            String description,
            LocalDate dueDate,
            Todo.Status status,
            Todo.Priority priority,
            Set<UUID> dependencyIds,
            RecurrenceRule recurrenceRule
    ) {
        var todo = findActive(id);
        verifyVersion(todo, expectedVersion);
        lifecycle.validateDueDate(dueDate, todo.dueDate());
        var previousStatus = todo.status();
        var dependencies = lifecycle.resolveDependencies(todo, dependencyIds);
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
            var created = repository.save(nextOccurrence);
            events.publishAfterCommit(TodoChangeEvent.Type.CREATED, created);
        }
        events.publishAfterCommit(TodoChangeEvent.Type.UPDATED, todo);
        return todo;
    }

    /**
     * Marks a TODO as deleted while retaining its row for audit and recovery.
     *
     * @param id TODO identifier
     * @param expectedVersion version supplied through {@code If-Match}
     * @throws TodoNotFoundException when the TODO is absent or already soft-deleted
     * @throws TodoRuleViolationException when the supplied version is stale
     */
    @Transactional
    public void delete(UUID id, long expectedVersion) {
        var todo = findActive(id);
        verifyVersion(todo, expectedVersion);
        todo.softDelete();
        events.publishAfterCommit(TodoChangeEvent.Type.DELETED, todo);
    }

    /** @return active aggregate or a domain-specific not-found exception */
    private Todo findActive(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new TodoNotFoundException(id));
    }

    /** Verifies the explicit HTTP precondition before any mutation occurs. */
    private void verifyVersion(Todo todo, long expectedVersion) {
        if (todo.version() != expectedVersion) {
            throw new TodoRuleViolationException(
                    HttpStatus.PRECONDITION_FAILED,
                    "TODO_VERSION_CONFLICT",
                    "The TODO changed after it was loaded; reload the current version and try again"
            );
        }
    }

    /** @return trimmed description, or {@code null} when blank */
    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    /** Enforces the bounded page contract before querying PostgreSQL. */
    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new TodoRuleViolationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGINATION",
                    "Page must be zero or greater and size must be between 1 and " + MAX_PAGE_SIZE
            );
        }
    }

    /** @return whitelisted sort field, defaulting to creation time */
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

    /** @return lowercase {@code asc} or {@code desc} */
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
