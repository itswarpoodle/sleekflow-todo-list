package com.sleekflow.todo.todos.service;

import com.sleekflow.todo.todos.exception.TodoNotFoundException;
import com.sleekflow.todo.todos.model.Todo;
import com.sleekflow.todo.todos.repository.TodoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TodoService {

    private final TodoRepository repository;
    private final TodoLifecycleService lifecycle;

    public TodoService(TodoRepository repository, TodoLifecycleService lifecycle) {
        this.repository = repository;
        this.lifecycle = lifecycle;
    }

    @Transactional(readOnly = true)
    public List<Todo> findAll() {
        return repository.findAllByDeletedAtIsNullOrderByCreatedAtDesc();
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
            Set<UUID> dependencyIds
    ) {
        var effectiveStatus = status == null ? Todo.Status.NOT_STARTED : status;
        var dependencies = lifecycle.resolveDependencies(null, dependencyIds);
        lifecycle.validateStatus(effectiveStatus, dependencies);

        var todo = new Todo(
                name.trim(),
                normalizeDescription(description),
                dueDate,
                effectiveStatus,
                priority == null ? Todo.Priority.MEDIUM : priority
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
            Set<UUID> dependencyIds
    ) {
        var todo = findActive(id);
        var dependencies = lifecycle.resolveDependencies(id, dependencyIds);
        lifecycle.validateUpdate(todo, dependencies, status);
        todo.update(name.trim(), normalizeDescription(description), dueDate, status, priority);
        todo.replaceDependencies(dependencies);
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
}
