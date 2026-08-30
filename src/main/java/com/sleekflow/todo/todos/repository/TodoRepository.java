package com.sleekflow.todo.todos.repository;

import com.sleekflow.todo.todos.model.Todo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence operations for active and historical TODOs. Normal reads explicitly
 * exclude rows with a deletion timestamp; the base repository remains available for
 * retention checks and administrative recovery.
 */
public interface TodoRepository extends JpaRepository<Todo, UUID> {

    /**
     * Applies optional filters and domain-aware sorting in PostgreSQL. Boolean flags
     * distinguish an omitted filter from the non-null placeholder values required by
     * JPQL, and the final ID ordering keeps page boundaries stable when values tie.
     */
    @Query(
            value = """
                    SELECT todo
                    FROM Todo todo
                    WHERE todo.deletedAt IS NULL
                      AND (:filterStatus = false OR todo.status = :status)
                      AND (:filterPriority = false OR todo.priority = :priority)
                      AND (:filterDueDate = false OR todo.dueDate = :dueDate)
                      AND (:filterName = false OR lower(todo.name) LIKE lower(concat('%', :name, '%')))
                      AND (
                          :filterBlocked = false
                          OR (:blocked = true AND EXISTS (
                              SELECT dependency.id
                              FROM Todo candidate JOIN candidate.dependencies dependency
                              WHERE candidate = todo AND dependency.status <> :completedStatus
                          ))
                          OR (:blocked = false AND NOT EXISTS (
                              SELECT dependency.id
                              FROM Todo candidate JOIN candidate.dependencies dependency
                              WHERE candidate = todo AND dependency.status <> :completedStatus
                          ))
                      )
                    ORDER BY
                      CASE WHEN :sortField = 'dueDate' AND :direction = 'asc' THEN todo.dueDate END ASC NULLS LAST,
                      CASE WHEN :sortField = 'dueDate' AND :direction = 'desc' THEN todo.dueDate END DESC NULLS LAST,
                      CASE WHEN :sortField = 'priority' AND :direction = 'asc' THEN
                          CASE cast(todo.priority AS String) WHEN 'LOW' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'HIGH' THEN 3 END
                      END ASC,
                      CASE WHEN :sortField = 'priority' AND :direction = 'desc' THEN
                          CASE cast(todo.priority AS String) WHEN 'LOW' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'HIGH' THEN 3 END
                      END DESC,
                      CASE WHEN :sortField = 'status' AND :direction = 'asc' THEN
                          CASE cast(todo.status AS String) WHEN 'NOT_STARTED' THEN 1 WHEN 'IN_PROGRESS' THEN 2 WHEN 'COMPLETED' THEN 3 WHEN 'ARCHIVED' THEN 4 END
                      END ASC,
                      CASE WHEN :sortField = 'status' AND :direction = 'desc' THEN
                          CASE cast(todo.status AS String) WHEN 'NOT_STARTED' THEN 1 WHEN 'IN_PROGRESS' THEN 2 WHEN 'COMPLETED' THEN 3 WHEN 'ARCHIVED' THEN 4 END
                      END DESC,
                      CASE WHEN :sortField = 'name' AND :direction = 'asc' THEN lower(todo.name) END ASC,
                      CASE WHEN :sortField = 'name' AND :direction = 'desc' THEN lower(todo.name) END DESC,
                      CASE WHEN :sortField = 'createdAt' THEN todo.createdAt END DESC,
                      todo.id ASC
                    """,
            countQuery = """
                    SELECT count(todo)
                    FROM Todo todo
                    WHERE todo.deletedAt IS NULL
                      AND (:filterStatus = false OR todo.status = :status)
                      AND (:filterPriority = false OR todo.priority = :priority)
                      AND (:filterDueDate = false OR todo.dueDate = :dueDate)
                      AND (:filterName = false OR lower(todo.name) LIKE lower(concat('%', :name, '%')))
                      AND (
                          :filterBlocked = false
                          OR (:blocked = true AND EXISTS (
                              SELECT dependency.id
                              FROM Todo candidate JOIN candidate.dependencies dependency
                              WHERE candidate = todo AND dependency.status <> :completedStatus
                          ))
                          OR (:blocked = false AND NOT EXISTS (
                              SELECT dependency.id
                              FROM Todo candidate JOIN candidate.dependencies dependency
                              WHERE candidate = todo AND dependency.status <> :completedStatus
                          ))
                      )
                    """
    )
    Page<Todo> findPage(
            @Param("filterStatus") boolean filterStatus,
            @Param("status") Todo.Status status,
            @Param("filterPriority") boolean filterPriority,
            @Param("priority") Todo.Priority priority,
            @Param("filterDueDate") boolean filterDueDate,
            @Param("dueDate") LocalDate dueDate,
            @Param("filterBlocked") boolean filterBlocked,
            @Param("blocked") boolean blocked,
            @Param("filterName") boolean filterName,
            @Param("name") String name,
            @Param("sortField") String sortField,
            @Param("direction") String direction,
            @Param("completedStatus") Todo.Status completedStatus,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "dependencies")
    @Query("SELECT DISTINCT todo FROM Todo todo WHERE todo.id IN :ids")
    List<Todo> findWithDependenciesByIdIn(@Param("ids") Collection<UUID> ids);

    @EntityGraph(attributePaths = "dependencies")
    Optional<Todo> findByIdAndDeletedAtIsNull(UUID id);

    List<Todo> findAllByIdInAndDeletedAtIsNull(Collection<UUID> ids);

    boolean existsByPreviousOccurrenceId(UUID previousOccurrenceId);

    Optional<Todo> findByPreviousOccurrenceId(UUID previousOccurrenceId);
}
