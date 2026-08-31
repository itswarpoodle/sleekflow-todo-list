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

    @Scheduled(fixedDelayString = "${todo.events.heartbeat-ms:25000}")
    void heartbeat() {
        clients.forEach((clientId, emitter) -> send(clientId, emitter, SseEmitter.event().comment("heartbeat")));
    }

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

    private void send(UUID clientId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException exception) {
            clients.remove(clientId);
            emitter.complete();
        }
    }
}
