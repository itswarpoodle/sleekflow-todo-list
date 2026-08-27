package com.sleekflow.todo.todos.repository;

import com.sleekflow.todo.todos.model.Todo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TodoRepository extends JpaRepository<Todo, UUID> {

    List<Todo> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

    Optional<Todo> findByIdAndDeletedAtIsNull(UUID id);
}
