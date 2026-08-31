package com.sleekflow.todo.todos.service;

import com.sleekflow.todo.todos.dto.TodoChangeEvent;
import com.sleekflow.todo.todos.model.Todo;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Broadcasts committed TODO changes to browsers connected to this application
 * instance. Clients receive invalidations rather than duplicated TODO state.
 */
@Service
public class TodoEventStream {

    private final Map<UUID, SseEmitter> clients = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    /** Creates an empty instance-local client registry and event sequence. */
    public TodoEventStream() {
    }

    /**
     * Registers a browser connection and sends an initial event so the caller knows
     * the stream is established. Completion and error callbacks remove stale clients.
     *
     * @return long-lived emitter for one browser connection
     */
    public SseEmitter subscribe() {
        var clientId = UUID.randomUUID();
        var emitter = new SseEmitter(0L);
        clients.put(clientId, emitter);
        emitter.onCompletion(() -> clients.remove(clientId));
        emitter.onTimeout(() -> clients.remove(clientId));
        emitter.onError(ignored -> clients.remove(clientId));

        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("status", "connected")));
        } catch (IOException exception) {
            clients.remove(clientId);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    /**
     * Defers publication until the surrounding transaction commits. The entity
     * reference is intentional so Hibernate's committed version reaches the event.
     *
     * @param type committed change type
     * @param todo changed aggregate
     * @throws IllegalStateException when called outside a transaction
     */
    public void publishAfterCommit(TodoChangeEvent.Type type, Todo todo) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("TODO events must be registered inside a transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                broadcast(new TodoChangeEvent(sequence.incrementAndGet(), type, todo.id(), todo.version()));
            }
        });
    }

    /** Sends a lightweight comment to keep intermediaries from closing idle streams. */
    @Scheduled(fixedDelayString = "${todo.events.heartbeat-ms:25000}")
    void heartbeat() {
        clients.forEach((clientId, emitter) -> send(clientId, emitter, SseEmitter.event().comment("heartbeat")));
    }

    /** Sends one invalidation to every currently registered client. */
    private void broadcast(TodoChangeEvent event) {
        clients.forEach((clientId, emitter) -> send(
                clientId,
                emitter,
                SseEmitter.event()
                        .id(Long.toString(event.sequence()))
                        .name("todo-change")
                        .data(event)
        ));
    }

    /**
     * Sends an event and removes the client when its connection is no longer writable.
     */
    private void send(UUID clientId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException exception) {
            clients.remove(clientId);
            emitter.complete();
        }
    }
}
