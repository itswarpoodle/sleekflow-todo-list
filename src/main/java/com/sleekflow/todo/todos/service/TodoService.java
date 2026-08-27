package com.sleekflow.todo.todos.service;

import com.sleekflow.todo.todos.exception.TodoNotFoundException;
import com.sleekflow.todo.todos.model.Todo;
import com.sleekflow.todo.todos.repository.TodoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class TodoService {

    private final TodoRepository repository;

    public TodoService(TodoRepository repository) {
        this.repository = repository;
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
            Todo.Priority priority
    ) {
        return repository.save(new Todo(
                name.trim(),
                normalizeDescription(description),
                dueDate,
                status == null ? Todo.Status.NOT_STARTED : status,
                priority == null ? Todo.Priority.MEDIUM : priority
        ));
    }

    @Transactional
    public Todo update(
            UUID id,
            String name,
            String description,
            LocalDate dueDate,
            Todo.Status status,
            Todo.Priority priority
    ) {
        var todo = findActive(id);
        todo.update(name.trim(), normalizeDescription(description), dueDate, status, priority);
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
