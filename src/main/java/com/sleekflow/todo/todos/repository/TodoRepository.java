package com.sleekflow.todo.todos.repository;

import com.sleekflow.todo.todos.model.Todo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TodoRepository extends JpaRepository<Todo, UUID> {

    @EntityGraph(attributePaths = "dependencies")
    List<Todo> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "dependencies")
    Optional<Todo> findByIdAndDeletedAtIsNull(UUID id);

    List<Todo> findAllByIdInAndDeletedAtIsNull(Collection<UUID> ids);
}
