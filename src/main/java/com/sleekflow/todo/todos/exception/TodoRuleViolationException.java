package com.sleekflow.todo.todos.exception;

import org.springframework.http.HttpStatus;

public class TodoRuleViolationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public TodoRuleViolationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
