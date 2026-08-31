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

    /** User-visible lifecycle states; archiving is distinct from soft deletion. */
    public enum Status {
        /** Work has not started and may still be blocked. */
        NOT_STARTED,
        /** Work is actively underway. */
        IN_PROGRESS,
        /** Work has finished and can satisfy dependencies. */
        COMPLETED,
        /** Work is retained in active reads but set aside. */
        ARCHIVED
    }

    /** Relative importance used by filtering and domain-ordered sorting. */
    public enum Priority {
        /** Lowest urgency. */
        LOW,
        /** Default urgency. */
        MEDIUM,
        /** Highest urgency. */
        HIGH
    }

    /** Supported named recurrence schedules. */
    public enum RecurrenceFrequency {
        /** Repeat every day. */
        DAILY,
        /** Repeat every week. */
        WEEKLY,
        /** Repeat every month using calendar arithmetic. */
        MONTHLY,
        /** Repeat using a caller-supplied interval and unit. */
        CUSTOM
    }

    /** Calendar unit used to calculate a recurrence interval. */
    public enum RecurrenceUnit {
        /** Calendar days. */
        DAYS,
        /** Seven-day calendar weeks. */
        WEEKS,
        /** Calendar months with month-end adjustment. */
        MONTHS
    }

    /**
     * Canonical recurrence value stored across three database columns.
     *
     * @param frequency named or custom schedule
     * @param interval positive number of units between occurrences
     * @param unit calendar unit applied to the previous due date
     */
    public record Recurrence(
            RecurrenceFrequency frequency,
            int interval,
            RecurrenceUnit unit
    ) {
        /**
         * Uses calendar arithmetic so month-end dates follow {@link LocalDate}
         * semantics instead of assuming that every month has a fixed length.
         *
         * @param dueDate current occurrence due date
         * @return due date for the next occurrence
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

    /** Required by JPA; application code should use the explicit constructor. */
    protected Todo() {
    }

    /**
     * Creates a new aggregate before persistence assigns its ID, timestamps, and version.
     *
     * @param name normalized display name
     * @param description optional normalized description
     * @param dueDate optional calendar due date
     * @param status initial lifecycle status
     * @param priority initial priority
     * @param recurrence optional canonical recurrence
     */
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

    /**
     * Replaces all directly editable scalar fields. Cross-TODO validation occurs
     * in the service before this mutation is called.
     *
     * @param name normalized display name
     * @param description optional normalized description
     * @param dueDate optional calendar due date
     * @param status replacement lifecycle status
     * @param priority replacement priority
     * @param recurrence optional canonical recurrence
     */
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

    /**
     * Replaces the dependency set after the lifecycle service validates it.
     *
     * @param dependencies validated prerequisites
     */
    public void replaceDependencies(Collection<Todo> dependencies) {
        this.dependencies.clear();
        this.dependencies.addAll(dependencies);
    }

    /**
     * Copies the recurring work into a fresh NOT_STARTED TODO linked to this one.
     * The service copies dependencies after constructing the occurrence.
     *
     * @return unpersisted successor occurrence
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

    /** Keeps the nullable persistence columns synchronized with one domain value. */
    private void setRecurrence(Recurrence recurrence) {
        recurrenceFrequency = recurrence == null ? null : recurrence.frequency();
        recurrenceInterval = recurrence == null ? null : recurrence.interval();
        recurrenceUnit = recurrence == null ? null : recurrence.unit();
    }

    /** Initializes audit timestamps immediately before the first insert. */
    @PrePersist
    void onCreate() {
        var now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    /** Refreshes the modification timestamp immediately before an update. */
    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Returns the stable identifier assigned by persistence.
     *
     * @return TODO identifier
     */
    public UUID id() {
        return id;
    }

    /**
     * Returns the display name.
     *
     * @return TODO name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the optional supporting description.
     *
     * @return description, or {@code null}
     */
    public String description() {
        return description;
    }

    /**
     * Returns the optional calendar due date.
     *
     * @return due date, or {@code null}
     */
    public LocalDate dueDate() {
        return dueDate;
    }

    /**
     * Returns the current visible lifecycle status.
     *
     * @return status
     */
    public Status status() {
        return status;
    }

    /**
     * Returns the current domain priority.
     *
     * @return priority
     */
    public Priority priority() {
        return priority;
    }

    /**
     * Returns the optimistic-lock version exposed through the API ETag.
     *
     * @return entity version
     */
    public long version() {
        return version;
    }

    /**
     * Returns the persistence creation time.
     *
     * @return creation timestamp
     */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Returns the latest persistence update time.
     *
     * @return update timestamp
     */
    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * Returns when the TODO left active views.
     *
     * @return soft-deletion timestamp, or {@code null} while active
     */
    public Instant deletedAt() {
        return deletedAt;
    }

    /**
     * Returns a read-only view of the current prerequisites.
     *
     * @return dependency set
     */
    public Set<Todo> dependencies() {
        return Collections.unmodifiableSet(dependencies);
    }

    /**
     * Reconstructs the recurrence value from its nullable persistence columns.
     *
     * @return canonical recurrence, or {@code null} for a one-off TODO
     */
    public Recurrence recurrence() {
        return recurrenceFrequency == null
                ? null
                : new Recurrence(recurrenceFrequency, recurrenceInterval, recurrenceUnit);
    }

    /**
     * Returns the occurrence that generated this TODO.
     *
     * @return source occurrence ID, or {@code null} when created directly
     */
    public UUID previousOccurrenceId() {
        return previousOccurrenceId;
    }
}
