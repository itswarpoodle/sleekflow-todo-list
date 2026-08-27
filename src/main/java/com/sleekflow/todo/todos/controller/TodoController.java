package com.sleekflow.todo.todos.controller;

import com.sleekflow.todo.todos.dto.ApiErrorResponse;
import com.sleekflow.todo.todos.dto.CreateTodoRequest;
import com.sleekflow.todo.todos.dto.TodoResponse;
import com.sleekflow.todo.todos.dto.UpdateTodoRequest;
import com.sleekflow.todo.todos.service.TodoService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/todos")
public class TodoController {

    private final TodoService service;

    public TodoController(TodoService service) {
        this.service = service;
    }

    @GetMapping
    @ApiResponse(responseCode = "200", description = "Active TODOs returned")
    public List<TodoResponse> findAll() {
        return service.findAll().stream().map(TodoResponse::from).toList();
    }

    @GetMapping("/{id}")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "TODO returned"),
            @ApiResponse(
                    responseCode = "404",
                    description = "TODO not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public TodoResponse findById(@PathVariable UUID id) {
        return TodoResponse.from(service.findById(id));
    }

    @PostMapping
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "TODO created"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<TodoResponse> create(@Valid @RequestBody CreateTodoRequest request) {
        var created = TodoResponse.from(service.create(
                request.name(),
                request.description(),
                request.dueDate(),
                request.status(),
                request.priority()
        ));
        return ResponseEntity.created(URI.create("/api/todos/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "TODO updated"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "TODO not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public TodoResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTodoRequest request
    ) {
        return TodoResponse.from(service.update(
                id,
                request.name(),
                request.description(),
                request.dueDate(),
                request.status(),
                request.priority()
        ));
    }

    @DeleteMapping("/{id}")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "TODO soft-deleted"),
            @ApiResponse(
                    responseCode = "404",
                    description = "TODO not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
