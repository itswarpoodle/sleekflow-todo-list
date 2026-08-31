package com.sleekflow.todo.todos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent TODO aggregate. Cross-TODO rules live in {@code TodoLifecycleService};
 * this class owns state changes that affect one TODO and its next occurrence.
 */
@Entity
@Table(name = "todos")
public class Todo {

    public enum Status {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED,
        ARCHIVED
    }

    public enum Priority {
        LOW,
        MEDIUM,
        HIGH
    }

    public enum RecurrenceFrequency {
        DAILY,
        WEEKLY,
        MONTHLY,
        CUSTOM
    }

    public enum RecurrenceUnit {
        DAYS,
        WEEKS,
        MONTHS
    }

    public record Recurrence(
            RecurrenceFrequency frequency,
            int interval,
            RecurrenceUnit unit
    ) {
        /**
         * Uses calendar arithmetic so month-end dates follow {@link LocalDate}
         * semantics instead of assuming that every month has a fixed length.
         */
        public LocalDate nextDueDate(LocalDate dueDate) {
            return switch (unit) {
                case DAYS -> dueDate.plusDays(interval);
                case WEEKS -> dueDate.plusWeeks(interval);
                case MONTHS -> dueDate.plusMonths(interval);
            };
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status = Status.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Priority priority = Priority.MEDIUM;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_frequency", length = 16)
    private RecurrenceFrequency recurrenceFrequency;

    @Column(name = "recurrence_interval")
    private Integer recurrenceInterval;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_unit", length = 16)
    private RecurrenceUnit recurrenceUnit;

    @Column(name = "previous_occurrence_id", updatable = false)
    private UUID previousOccurrenceId;

    @ManyToMany
    @JoinTable(
            name = "todo_dependencies",
            joinColumns = @JoinColumn(name = "todo_id"),
            inverseJoinColumns = @JoinColumn(name = "dependency_id")
    )
    private Set<Todo> dependencies = new LinkedHashSet<>();

    protected Todo() {
    }

    public Todo(
            String name,
            String description,
            LocalDate dueDate,
            Status status,
            Priority priority,
            Recurrence recurrence
    ) {
        this.name = name;
        this.description = description;
        this.dueDate = dueDate;
        this.status = status;
        this.priority = priority;
        setRecurrence(recurrence);
    }

    public void update(
            String name,
            String description,
            LocalDate dueDate,
            Status status,
            Priority priority,
            Recurrence recurrence
    ) {
        this.name = name;
        this.description = description;
        this.dueDate = dueDate;
        this.status = status;
        this.priority = priority;
        setRecurrence(recurrence);
    }

    /**
     * Retains the row and records when it stopped belonging to active views.
     */
    public void softDelete() {
        deletedAt = Instant.now();
    }

    public void replaceDependencies(Collection<Todo> dependencies) {
        this.dependencies.clear();
        this.dependencies.addAll(dependencies);
    }

    /**
     * Copies the recurring work into a fresh NOT_STARTED TODO linked to this one.
     * The service copies dependencies after constructing the occurrence.
     */
    public Todo nextOccurrence() {
        var next = new Todo(
                name,
                description,
                recurrence().nextDueDate(dueDate),
                Status.NOT_STARTED,
                priority,
                recurrence()
        );
        next.previousOccurrenceId = id;
        return next;
    }

    private void setRecurrence(Recurrence recurrence) {
        recurrenceFrequency = recurrence == null ? null : recurrence.frequency();
        recurrenceInterval = recurrence == null ? null : recurrence.interval();
        recurrenceUnit = recurrence == null ? null : recurrence.unit();
    }

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public LocalDate dueDate() {
        return dueDate;
    }

    public Status status() {
        return status;
    }

    public Priority priority() {
        return priority;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant deletedAt() {
        return deletedAt;
    }

    public Set<Todo> dependencies() {
        return Collections.unmodifiableSet(dependencies);
    }

    public Recurrence recurrence() {
        return recurrenceFrequency == null
                ? null
                : new Recurrence(recurrenceFrequency, recurrenceInterval, recurrenceUnit);
    }

    public UUID previousOccurrenceId() {
        return previousOccurrenceId;
    }
}
