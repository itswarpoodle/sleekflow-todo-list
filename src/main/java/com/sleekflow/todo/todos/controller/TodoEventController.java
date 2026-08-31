package com.sleekflow.todo.todos.controller;

import com.sleekflow.todo.todos.service.TodoEventStream;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Exposes the instance-local stream of committed TODO invalidations. */
@RestController
@RequestMapping("/api/todos/events")
public class TodoEventController {

    private final TodoEventStream events;

    /**
     * Creates the event-stream controller.
     *
     * @param events broadcaster for committed TODO changes
     */
    public TodoEventController(TodoEventStream events) {
        this.events = events;
    }

    /**
     * Opens a long-lived Server-Sent Events connection.
     *
     * @return emitter registered with the application-instance broadcaster
     */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream committed TODO changes from this application instance")
    public SseEmitter subscribe() {
        return events.subscribe();
    }
}
