package com.sleekflow.todo.todos.exception;

import org.springframework.http.HttpStatus;

/**
 * Domain rejection carrying the HTTP status and stable API code chosen by the rule.
 */
public class TodoRuleViolationException extends RuntimeException {

    /** HTTP status returned to the caller. */
    private final HttpStatus status;
    /** Stable machine-readable error code. */
    private final String code;

    /**
     * Creates a rule violation that can be rendered without interpreting its message.
     *
     * @param status HTTP status appropriate for the rejected operation
     * @param code stable machine-readable error code
     * @param message concise human-readable explanation
     */
    public TodoRuleViolationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /**
     * Returns the HTTP status selected by the domain rule.
     *
     * @return response status
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * Returns the stable machine-readable error code.
     *
     * @return error code
     */
    public String code() {
        return code;
    }
}
