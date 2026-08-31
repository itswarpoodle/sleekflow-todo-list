package com.sleekflow.todo.todos.exception;

import com.sleekflow.todo.todos.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts framework and domain exceptions into the API's consistent error envelope.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Creates the stateless global exception handler. */
    public ApiExceptionHandler() {
    }

    /**
     * Reports database-level races that occur after optimistic checks or uniqueness validation.
     *
     * @param exception persistence exception raised by the losing request
     * @param request current HTTP request
     * @return conflict response instructing the caller to reload
     */
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ResponseEntity<ApiErrorResponse> handleConcurrentModification(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION",
                "The TODO was changed concurrently; refresh and retry",
                request,
                Map.of()
        );
    }

    /**
     * Preserves the status and stable code chosen by a lifecycle rule.
     *
     * @param exception rejected domain operation
     * @param request current HTTP request
     * @return rule-specific error response
     */
    @ExceptionHandler(TodoRuleViolationException.class)
    ResponseEntity<ApiErrorResponse> handleRuleViolation(
            TodoRuleViolationException exception,
            HttpServletRequest request
    ) {
        return response(
                exception.status(),
                exception.code(),
                exception.getMessage(),
                request,
                Map.of()
        );
    }

    /**
     * Maps reads or mutations of absent active TODOs to {@code 404}.
     *
     * @param exception missing-resource exception
     * @param request current HTTP request
     * @return not-found response
     */
    @ExceptionHandler(TodoNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(
            TodoNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.NOT_FOUND,
                "TODO_NOT_FOUND",
                exception.getMessage(),
                request,
                Map.of()
        );
    }

    /**
     * Collects bean-validation failures by field while retaining the first useful message.
     *
     * @param exception validation failure raised while binding the request body
     * @param request current HTTP request
     * @return bad-request response containing field errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        var fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        return response(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request validation failed",
                request,
                fieldErrors
        );
    }

    /**
     * Handles malformed JSON, enum values, dates, and path or query parameter types.
     *
     * @param exception request parsing failure
     * @param request current HTTP request
     * @return generic invalid-request response
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiErrorResponse> handleInvalidRequest(Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Request contains an invalid value",
                request,
                Map.of()
        );
    }

    /**
     * Creates one consistently shaped error response.
     */
    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                fieldErrors
        ));
    }

}
