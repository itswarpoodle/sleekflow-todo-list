package com.sleekflow.todo.todos.controller;

import com.sleekflow.todo.todos.dto.ApiErrorResponse;
import com.sleekflow.todo.todos.dto.CreateTodoRequest;
import com.sleekflow.todo.todos.dto.PageResponse;
import com.sleekflow.todo.todos.dto.TodoResponse;
import com.sleekflow.todo.todos.dto.UpdateTodoRequest;
import com.sleekflow.todo.todos.exception.TodoRuleViolationException;
import com.sleekflow.todo.todos.model.Todo;
import com.sleekflow.todo.todos.service.TodoService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
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
    public PageResponse<TodoResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Todo.Status status,
            @RequestParam(required = false) Todo.Priority priority,
            @RequestParam(required = false) LocalDate dueDate,
            @RequestParam(required = false) Boolean blocked,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        var todos = service.findAll(page, size, status, priority, dueDate, blocked, name, sort, direction)
                .map(TodoResponse::from);
        return PageResponse.from(todos);
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
    public ResponseEntity<TodoResponse> findById(@PathVariable UUID id) {
        var todo = service.findById(id);
        return ResponseEntity.ok()
                .eTag(entityTag(todo.version()))
                .body(TodoResponse.from(todo));
    }

    @PostMapping
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "TODO created"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Dependency or lifecycle rule violated",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<TodoResponse> create(@Valid @RequestBody CreateTodoRequest request) {
        var created = TodoResponse.from(service.create(
                request.name(),
                request.description(),
                request.dueDate(),
                request.status(),
                request.priority(),
                request.dependencyIds(),
                request.recurrence()
        ));
        return ResponseEntity.created(URI.create("/api/todos/" + created.id()))
                .eTag(entityTag(created.version()))
                .body(created);
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
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Dependency or lifecycle rule violated",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "412",
                    description = "If-Match refers to a stale TODO version",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "428",
                    description = "If-Match header is required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<TodoResponse> update(
            @PathVariable UUID id,
            @Parameter(required = true, description = "Strong ETag from the latest TODO representation")
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdateTodoRequest request
    ) {
        var updated = service.update(
                id,
                requiredVersion(ifMatch),
                request.name(),
                request.description(),
                request.dueDate(),
                request.status(),
                request.priority(),
                request.dependencyIds(),
                request.recurrence()
        );
        return ResponseEntity.ok()
                .eTag(entityTag(updated.version()))
                .body(TodoResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "TODO soft-deleted"),
            @ApiResponse(
                    responseCode = "404",
                    description = "TODO not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "412",
                    description = "If-Match refers to a stale TODO version",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "428",
                    description = "If-Match header is required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @Parameter(required = true, description = "Strong ETag from the latest TODO representation")
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch
    ) {
        service.delete(id, requiredVersion(ifMatch));
        return ResponseEntity.noContent().build();
    }

    private long requiredVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new TodoRuleViolationException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "IF_MATCH_REQUIRED",
                    "If-Match is required for updates and deletion"
            );
        }
        if (ifMatch.length() < 3 || ifMatch.charAt(0) != '"' || ifMatch.charAt(ifMatch.length() - 1) != '"') {
            throw invalidIfMatch();
        }
        try {
            return Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1));
        } catch (NumberFormatException exception) {
            throw invalidIfMatch();
        }
    }

    private TodoRuleViolationException invalidIfMatch() {
        return new TodoRuleViolationException(
                HttpStatus.BAD_REQUEST,
                "INVALID_IF_MATCH",
                "If-Match must contain one strong numeric ETag, for example \"0\""
        );
    }

    private String entityTag(long version) {
        return "\"" + version + "\"";
    }
}
