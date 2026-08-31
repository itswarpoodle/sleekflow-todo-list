package com.sleekflow.todo.todos;

import com.jayway.jsonpath.JsonPath;
import com.sleekflow.todo.todos.repository.TodoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TodoApiIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TodoRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearTodos() {
        jdbcTemplate.update("DELETE FROM todo_dependencies");
        jdbcTemplate.update("UPDATE todos SET previous_occurrence_id = NULL");
        repository.deleteAllInBatch();
    }

    @Test
    void createsReadsAndListsCompleteTodoContract() throws Exception {
        var response = mockMvc.perform(post("/api/todos")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Prepare assessment demo",
                                  "description":"Walk through the core requirements",
                                  "dueDate":"2026-09-30",
                                  "status":"IN_PROGRESS",
                                  "priority":"HIGH"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/todos/.+")))
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name").value("Prepare assessment demo"))
                .andExpect(jsonPath("$.description").value("Walk through the core requirements"))
                .andExpect(jsonPath("$.dueDate").value("2026-09-30"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.dependencyIds", hasSize(0)))
                .andExpect(jsonPath("$.blocked").value(false))
                .andExpect(jsonPath("$.recurrence").value(nullValue()))
                .andExpect(jsonPath("$.previousOccurrenceId").value(nullValue()))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()))
                .andReturn();

        String id = JsonPath.read(response.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/todos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.priority").value("HIGH"));

        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void appliesDefaultsAndNormalizesCreateInput() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .contentType("application/json")
                        .content("""
                                {"name":"  Prepare demo  ","description":"   "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Prepare demo"))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.dueDate").value(nullValue()))
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"));
    }

    @Test
    void updatesAnExistingTodo() throws Exception {
        String id = createTodo("""
                {"name":"Initial name"}
                """);

        mockMvc.perform(put("/api/todos/{id}", id)
                        .header(HttpHeaders.IF_MATCH, currentEtag(id))
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Updated name",
                                  "description":"Updated description",
                                  "dueDate":"2026-10-15",
                                  "status":"COMPLETED",
                                  "priority":"LOW"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Updated name"))
                .andExpect(jsonPath("$.description").value("Updated description"))
                .andExpect(jsonPath("$.dueDate").value("2026-10-15"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.priority").value("LOW"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""));

        mockMvc.perform(get("/api/todos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated name"));
    }

    @Test
    void requiresAValidCurrentIfMatchForMutations() throws Exception {
        String id = createTodo("""
                {"name":"Versioned TODO"}
                """);
        String updateBody = """
                {
                  "name":"Versioned TODO updated",
                  "status":"NOT_STARTED",
                  "priority":"MEDIUM"
                }
                """;

        mockMvc.perform(put("/api/todos/{id}", id)
                        .contentType("application/json")
                        .content(updateBody))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("IF_MATCH_REQUIRED"));

        mockMvc.perform(put("/api/todos/{id}", id)
                        .header(HttpHeaders.IF_MATCH, "not-an-etag")
                        .contentType("application/json")
                        .content(updateBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IF_MATCH"));

        mockMvc.perform(put("/api/todos/{id}", id)
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType("application/json")
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""));

        mockMvc.perform(put("/api/todos/{id}", id)
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType("application/json")
                        .content(updateBody))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("TODO_VERSION_CONFLICT"));

        mockMvc.perform(delete("/api/todos/{id}", id)
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("TODO_VERSION_CONFLICT"));

        mockMvc.perform(delete("/api/todos/{id}", id)
                        .header(HttpHeaders.IF_MATCH, "\"1\""))
                .andExpect(status().isNoContent());
    }

    @Test
    void streamsOnlyCommittedTodoChangesAsServerSentEvents() throws Exception {
        var stream = mockMvc.perform(get("/api/todos/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        String id = createTodo("""
                {"name":"Cross-tab update"}
                """);

        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            while (!stream.getResponse().getContentAsString().contains("event:todo-change")) {
                Thread.sleep(20);
            }
        });
        String events = stream.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(events)
                .contains("event:connected")
                .contains("event:todo-change")
                .contains("\"type\":\"CREATED\"")
                .contains("\"todoId\":\"" + id + "\"")
                .contains("\"version\":0");
        stream.getRequest().getAsyncContext().complete();
    }

    @Test
    void keepsArchivedTodosVisibleAndSoftDeletedTodosHidden() throws Exception {
        String archivedId = createTodo("""
                {"name":"Archived but visible","status":"ARCHIVED","priority":"LOW"}
                """);
        String deletedId = createTodo("""
                {"name":"Delete without data loss","priority":"HIGH"}
                """);

        mockMvc.perform(delete("/api/todos/{id}", deletedId)
                        .header(HttpHeaders.IF_MATCH, currentEtag(deletedId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/todos/{id}", deletedId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TODO_NOT_FOUND"));

        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(archivedId))
                .andExpect(jsonPath("$.content[0].status").value("ARCHIVED"));

        var deleted = repository.findById(UUID.fromString(deletedId)).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(deleted.deletedAt()).isNotNull();
    }

    @Test
    void returnsConsistentValidationAndMalformedRequestErrors() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .contentType("application/json")
                        .content("""
                                {"name":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/todos"))
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"));

        mockMvc.perform(post("/api/todos")
                        .contentType("application/json")
                        .content("""
                                {"name":"Valid name","description":"%s"}
                                """.formatted("x".repeat(2001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.description")
                        .value("Description must be 2000 characters or fewer"));

        mockMvc.perform(post("/api/todos")
                        .contentType("application/json")
                        .content("""
                                {"name":"Invalid priority","priority":"URGENT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/todos"));

        mockMvc.perform(get("/api/todos/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/todos/not-a-uuid"));
    }

    @Test
    void rejectsNewPastDueDatesButKeepsExistingOverdueTodosEditable() throws Exception {
        var today = LocalDate.now();
        var yesterday = today.minusDays(1);

        mockMvc.perform(post("/api/todos")
                        .contentType("application/json")
                        .content("""
                                {"name":"Invalid past due date","dueDate":"%s"}
                                """.formatted(yesterday)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TODO_DUE_DATE_IN_PAST"))
                .andExpect(jsonPath("$.message").value("Due date must be today or later"));

        String id = createTodo("""
                {"name":"Becomes overdue","dueDate":"%s"}
                """.formatted(today));
        jdbcTemplate.update("UPDATE todos SET due_date = ? WHERE id = ?", yesterday, UUID.fromString(id));

        mockMvc.perform(put("/api/todos/{id}", id)
                        .header(HttpHeaders.IF_MATCH, currentEtag(id))
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Complete overdue work",
                                  "dueDate":"%s",
                                  "status":"COMPLETED",
                                  "priority":"MEDIUM"
                                }
                                """.formatted(yesterday)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value(yesterday.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(put("/api/todos/{id}", id)
                        .header(HttpHeaders.IF_MATCH, currentEtag(id))
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Move further into the past",
                                  "dueDate":"%s",
                                  "status":"COMPLETED",
                                  "priority":"MEDIUM"
                                }
                                """.formatted(yesterday.minusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TODO_DUE_DATE_IN_PAST"));
    }

    @Test
    void returnsNotFoundForEverySingleTodoOperation() throws Exception {
        var missingId = UUID.randomUUID();

        mockMvc.perform(get("/api/todos/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TODO_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/todos/" + missingId));

        mockMvc.perform(put("/api/todos/{id}", missingId)
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Missing",
                                  "status":"NOT_STARTED",
                                  "priority":"MEDIUM"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TODO_NOT_FOUND"));

        mockMvc.perform(delete("/api/todos/{id}", missingId)
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TODO_NOT_FOUND"));
    }

    @Test
    void createsAndReadsMultipleDependenciesWithBlockedState() throws Exception {
        String incompleteId = createTodo("""
                {"name":"Incomplete prerequisite"}
                """);
        String completedId = createTodo("""
                {"name":"Completed prerequisite","status":"COMPLETED"}
                """);

        var response = mockMvc.perform(post("/api/todos")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Dependent task",
                                  "dependencyIds":["%s","%s"]
                                }
                                """.formatted(incompleteId, completedId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dependencyIds", containsInAnyOrder(incompleteId, completedId)))
                .andExpect(jsonPath("$.blocked").value(true))
                .andReturn();

        String dependentId = JsonPath.read(response.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(get("/api/todos/{id}", dependentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dependencyIds", containsInAnyOrder(incompleteId, completedId)))
                .andExpect(jsonPath("$.blocked").value(true));
    }

    @Test
    void rejectsMissingSelfAndTransitiveCyclicDependencies() throws Exception {
        var missingId = UUID.randomUUID();
        mockMvc.perform(post("/api/todos")
                        .contentType("application/json")
                        .content("""
                                {"name":"Missing dependency","dependencyIds":["%s"]}
                                """.formatted(missingId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_NOT_FOUND"));

        String firstId = createTodo("""
                {"name":"First"}
                """);
        mockMvc.perform(put("/api/todos/{id}", firstId)
                        .header(HttpHeaders.IF_MATCH, currentEtag(firstId))
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"First",
                                  "status":"NOT_STARTED",
                                  "priority":"MEDIUM",
                                  "dependencyIds":["%s"]
                                }
                                """.formatted(firstId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_DEPENDENCY"));

        String secondId = createTodo("""
                {"name":"Second","dependencyIds":["%s"]}
                """.formatted(firstId));
        String thirdId = createTodo("""
                {"name":"Third","dependencyIds":["%s"]}
                """.formatted(secondId));

        mockMvc.perform(put("/api/todos/{id}", firstId)
                        .header(HttpHeaders.IF_MATCH, currentEtag(firstId))
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"First",
                                  "status":"NOT_STARTED",
                                  "priority":"MEDIUM",
                                  "dependencyIds":["%s"]
                                }
                                """.formatted(thirdId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CYCLIC_DEPENDENCY"));
    }

    @Test
    void blocksInProgressUntilEveryDependencyIsCompleted() throws Exception {
        String prerequisiteId = createTodo("""
                {"name":"Prerequisite"}
                """);
        String dependentId = createTodo("""
                {"name":"Dependent","dependencyIds":["%s"]}
                """.formatted(prerequisiteId));

        mockMvc.perform(put("/api/todos/{id}", dependentId)
                        .header(HttpHeaders.IF_MATCH, currentEtag(dependentId))
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Dependent",
                                  "status":"IN_PROGRESS",
                                  "priority":"MEDIUM",
                                  "dependencyIds":["%s"]
                                }
                                """.formatted(prerequisiteId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TODO_BLOCKED"));

        mockMvc.perform(put("/api/todos/{id}", prerequisiteId)
                        .header(HttpHeaders.IF_MATCH, currentEtag(prerequisiteId))
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Prerequisite",
                                  "status":"COMPLETED",
                                  "priority":"MEDIUM"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(put("/api/todos/{id}", dependentId)
                        .header(HttpHeaders.IF_MATCH, currentEtag(dependentId))
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Dependent",
                                  "status":"IN_PROGRESS",
                                  "priority":"MEDIUM",
                                  "dependencyIds":["%s"]
                                }
                                """.formatted(prerequisiteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.blocked").value(false));
    }

    @Test
    void createsNextOccurrenceForEverySupportedRecurrence() throws Exception {
        assertNextOccurrence(
                "Daily leap-year task",
                "2028-02-28",
                "{\"frequency\":\"DAILY\"}",
                LocalDate.parse("2028-02-29"),
                "DAILY",
                1,
                "DAYS"
        );
        assertNextOccurrence(
                "Weekly task",
                "2027-04-05",
                "{\"frequency\":\"WEEKLY\"}",
                LocalDate.parse("2027-04-12"),
                "WEEKLY",
                1,
                "WEEKS"
        );
        assertNextOccurrence(
                "Monthly month-end task",
                "2027-01-31",
                "{\"frequency\":\"MONTHLY\"}",
                LocalDate.parse("2027-02-28"),
                "MONTHLY",
                1,
                "MONTHS"
        );
        assertNextOccurrence(
                "Custom task",
                "2027-01-31",
                "{\"frequency\":\"CUSTOM\",\"interval\":2,\"unit\":\"MONTHS\"}",
                LocalDate.parse("2027-03-31"),
                "CUSTOM",
                2,
                "MONTHS"
        );
    }

    @Test
    void rejectsIncompleteAndConflictingRecurrenceRules() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"No due date",
                                  "recurrence":{"frequency":"DAILY"}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RECURRENCE"));

        mockMvc.perform(post("/api/todos")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Incomplete custom rule",
                                  "dueDate":"2027-01-01",
                                  "recurrence":{"frequency":"CUSTOM","interval":2}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RECURRENCE"));

        mockMvc.perform(post("/api/todos")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Conflicting daily rule",
                                  "dueDate":"2027-01-01",
                                  "recurrence":{"frequency":"DAILY","interval":2,"unit":"DAYS"}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RECURRENCE"));
    }

    @Test
    void recompletingAnOccurrenceDoesNotCreateAnotherSuccessor() throws Exception {
        String id = createTodo("""
                {
                  "name":"Idempotent recurrence",
                  "dueDate":"2027-06-01",
                  "recurrence":{"frequency":"DAILY"}
                }
                """);

        updateRecurringTodo(id, "NOT_STARTED");
        updateRecurringTodo(id, "COMPLETED");
        updateRecurringTodo(id, "NOT_STARTED");
        updateRecurringTodo(id, "COMPLETED");

        long successors = repository.findAll().stream()
                .filter(todo -> UUID.fromString(id).equals(todo.previousOccurrenceId()))
                .count();
        org.assertj.core.api.Assertions.assertThat(successors).isEqualTo(1);
    }

    @Test
    void concurrentCompletionCreatesExactlyOneSuccessor() throws Exception {
        String id = createTodo("""
                {
                  "name":"Concurrent recurrence",
                  "dueDate":"2027-08-15",
                  "recurrence":{"frequency":"WEEKLY"}
                }
                """);
        int requestCount = 8;
        String etag = currentEtag(id);
        var ready = new CountDownLatch(requestCount);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(requestCount);

        try {
            var futures = IntStream.range(0, requestCount)
                    .mapToObj(ignored -> executor.submit(() -> {
                        ready.countDown();
                        start.await(10, TimeUnit.SECONDS);
                        return mockMvc.perform(put("/api/todos/{id}", id)
                                        .header(HttpHeaders.IF_MATCH, etag)
                                        .contentType("application/json")
                                        .content("""
                                                {
                                                  "name":"Concurrent recurrence",
                                                  "dueDate":"2027-08-15",
                                                  "status":"COMPLETED",
                                                  "priority":"MEDIUM",
                                                  "recurrence":{"frequency":"WEEKLY"}
                                                }
                                                """))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                    }))
                    .toList();

            org.assertj.core.api.Assertions.assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = futures.stream().map(future -> {
                try {
                    return future.get(20, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).toList();

            org.assertj.core.api.Assertions.assertThat(statuses)
                    .contains(200)
                    .allMatch(code -> code == 200 || code == 409 || code == 412);
            org.assertj.core.api.Assertions.assertThat(
                    repository.findAll().stream()
                            .filter(todo -> UUID.fromString(id).equals(todo.previousOccurrenceId()))
                            .count()
            ).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void filtersByStatusPriorityDueDateAndBlockedState() throws Exception {
        String incompleteId = createTodo("""
                {"name":"Incomplete prerequisite","priority":"LOW","dueDate":"2027-01-10"}
                """);
        createTodo("""
                {"name":"Completed match","status":"COMPLETED","priority":"HIGH","dueDate":"2027-02-20"}
                """);
        String blockedId = createTodo("""
                {
                  "name":"Blocked match",
                  "priority":"HIGH",
                  "dueDate":"2027-02-20",
                  "dependencyIds":["%s"]
                }
                """.formatted(incompleteId));

        mockMvc.perform(get("/api/todos").param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Completed match"));

        mockMvc.perform(get("/api/todos")
                        .param("priority", "HIGH")
                        .param("dueDate", "2027-02-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        mockMvc.perform(get("/api/todos").param("blocked", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(blockedId))
                .andExpect(jsonPath("$.content[0].blocked").value(true));

        mockMvc.perform(get("/api/todos").param("blocked", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].blocked", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(false))));
    }

    @Test
    void searchesActiveTodosByPartialNameForBoundedDependencySelection() throws Exception {
        createTodo("""
                {"name":"Prepare release notes"}
                """);
        createTodo("""
                {"name":"Review RELEASE checklist"}
                """);
        createTodo("""
                {"name":"Unrelated task"}
                """);

        mockMvc.perform(get("/api/todos")
                        .param("name", "release")
                        .param("sort", "name")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].name").value("Prepare release notes"))
                .andExpect(jsonPath("$.content[1].name").value("Review RELEASE checklist"));
    }

    @Test
    void sortsEveryAllowedFieldUsingDomainOrderAndNullsLast() throws Exception {
        createTodo("""
                {"name":"alpha","status":"NOT_STARTED","priority":"LOW"}
                """);
        createTodo("""
                {"name":"Bravo","status":"IN_PROGRESS","priority":"MEDIUM","dueDate":"2027-01-10"}
                """);
        createTodo("""
                {"name":"charlie","status":"ARCHIVED","priority":"HIGH","dueDate":"2027-03-10"}
                """);

        org.assertj.core.api.Assertions.assertThat(listNames("name", "asc"))
                .containsExactly("alpha", "Bravo", "charlie");
        org.assertj.core.api.Assertions.assertThat(listNames("priority", "desc"))
                .containsExactly("charlie", "Bravo", "alpha");
        org.assertj.core.api.Assertions.assertThat(listNames("status", "asc"))
                .containsExactly("alpha", "Bravo", "charlie");
        org.assertj.core.api.Assertions.assertThat(listNames("dueDate", "asc"))
                .containsExactly("Bravo", "charlie", "alpha");
    }

    @Test
    void paginatesWithStableNonOverlappingResults() throws Exception {
        IntStream.rangeClosed(1, 5).forEach(index -> {
            try {
                createTodo("""
                        {"name":"Same sort key"}
                        """);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });

        var firstPage = listIds(0, 2, "name", "asc");
        var secondPage = listIds(1, 2, "name", "asc");

        org.assertj.core.api.Assertions.assertThat(firstPage).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(secondPage).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(new HashSet<>(firstPage))
                .doesNotContainAnyElementsOf(secondPage);
        org.assertj.core.api.Assertions.assertThat(listIds(0, 2, "name", "asc"))
                .containsExactlyElementsOf(firstPage);

        mockMvc.perform(get("/api/todos").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void rejectsUnboundedPaginationAndUnknownSorts() throws Exception {
        mockMvc.perform(get("/api/todos").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
        mockMvc.perform(get("/api/todos").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
        mockMvc.perform(get("/api/todos").param("sort", "createdAt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
        mockMvc.perform(get("/api/todos").param("sort", "name").param("direction", "sideways"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
    }

    @Test
    void keepsAFilteredPageBoundedOnTenThousandRowsAndUsesTheStatusIndex() {
        jdbcTemplate.update("""
                INSERT INTO todos (id, name, status, version, created_at, updated_at, priority, due_date)
                SELECT md5('scale-' || series)::uuid,
                       'Scale TODO ' || series,
                       CASE WHEN series % 100 = 0 THEN 'IN_PROGRESS' ELSE 'NOT_STARTED' END,
                       0,
                       clock_timestamp(),
                       clock_timestamp(),
                       CASE WHEN series % 3 = 0 THEN 'HIGH' WHEN series % 3 = 1 THEN 'LOW' ELSE 'MEDIUM' END,
                       DATE '2027-01-01' + (series % 365)
                FROM generate_series(1, 10000) AS series
                """);
        jdbcTemplate.execute("ANALYZE todos");

        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                mockMvc.perform(get("/api/todos")
                                .param("status", "IN_PROGRESS")
                                .param("page", "0")
                                .param("size", "50"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content", hasSize(50)))
                        .andExpect(jsonPath("$.totalElements").value(100))
                        .andExpect(jsonPath("$.totalPages").value(2))
                        .andExpect(jsonPath("$.content[*].status",
                                org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("IN_PROGRESS"))))
        );

        var plan = jdbcTemplate.queryForList("""
                EXPLAIN SELECT id
                FROM todos
                WHERE deleted_at IS NULL AND status = 'IN_PROGRESS'
                ORDER BY id
                LIMIT 50
                """, String.class);
        org.assertj.core.api.Assertions.assertThat(String.join("\n", plan))
                .contains("todos_active_status_idx");
    }

    @Test
    void publishesEveryCrudOperationInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/todos'].get").exists())
                .andExpect(jsonPath("$.paths['/api/todos'].post").exists())
                .andExpect(jsonPath("$.paths['/api/todos/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/todos/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/todos/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/todos'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/todos'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/todos'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/todos/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/todos/{id}'].put.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/todos/{id}'].put.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/todos/{id}'].put.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/todos/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/todos/{id}'].delete.responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse").exists())
                .andExpect(jsonPath("$.components.schemas.CreateTodoRequest.properties.dependencyIds").exists())
                .andExpect(jsonPath("$.components.schemas.CreateTodoRequest.properties.recurrence").exists())
                .andExpect(jsonPath("$.components.schemas.TodoResponse.properties.blocked").exists())
                .andExpect(jsonPath("$.components.schemas.TodoResponse.properties.previousOccurrenceId").exists())
                .andExpect(jsonPath("$.components.schemas.RecurrenceRule").exists());
    }

    private void assertNextOccurrence(
            String name,
            String dueDate,
            String recurrence,
            LocalDate expectedNextDate,
            String expectedFrequency,
            int expectedInterval,
            String expectedUnit
    ) throws Exception {
        String id = createTodo("""
                {
                  "name":"%s",
                  "dueDate":"%s",
                  "recurrence":%s
                }
                """.formatted(name, dueDate, recurrence));

        mockMvc.perform(put("/api/todos/{id}", id)
                        .header(HttpHeaders.IF_MATCH, currentEtag(id))
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"%s",
                                  "dueDate":"%s",
                                  "status":"COMPLETED",
                                  "priority":"MEDIUM",
                                  "recurrence":%s
                                }
                                """.formatted(name, dueDate, recurrence)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.recurrence.frequency").value(expectedFrequency));

        var successor = repository.findByPreviousOccurrenceId(UUID.fromString(id)).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(successor.dueDate()).isEqualTo(expectedNextDate);
        org.assertj.core.api.Assertions.assertThat(successor.status()).isEqualTo(com.sleekflow.todo.todos.model.Todo.Status.NOT_STARTED);

        mockMvc.perform(get("/api/todos/{id}", successor.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousOccurrenceId").value(id))
                .andExpect(jsonPath("$.dueDate").value(expectedNextDate.toString()))
                .andExpect(jsonPath("$.recurrence.frequency").value(expectedFrequency))
                .andExpect(jsonPath("$.recurrence.interval").value(expectedInterval))
                .andExpect(jsonPath("$.recurrence.unit").value(expectedUnit));
    }

    private void updateRecurringTodo(String id, String statusValue) throws Exception {
        mockMvc.perform(put("/api/todos/{id}", id)
                        .header(HttpHeaders.IF_MATCH, currentEtag(id))
                        .contentType("application/json")
                        .content(recurringUpdateBody(statusValue)))
                .andExpect(status().isOk());
    }

    private String recurringUpdateBody(String statusValue) {
        return """
                {
                  "name":"Idempotent recurrence",
                  "dueDate":"2027-06-01",
                  "status":"%s",
                  "priority":"MEDIUM",
                  "recurrence":{"frequency":"DAILY"}
                }
                """.formatted(statusValue);
    }

    private String createTodo(String requestBody) throws Exception {
        var response = mockMvc.perform(post("/api/todos")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(response.getResponse().getContentAsString(), "$.id");
    }

    private String currentEtag(String id) {
        var version = repository.findById(UUID.fromString(id)).orElseThrow().version();
        return "\"" + version + "\"";
    }

    private List<String> listNames(String sort, String direction) throws Exception {
        var response = mockMvc.perform(get("/api/todos")
                        .param("sort", sort)
                        .param("direction", direction))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(response.getResponse().getContentAsString(), "$.content[*].name");
    }

    private List<String> listIds(int page, int size, String sort, String direction) throws Exception {
        var response = mockMvc.perform(get("/api/todos")
                        .param("page", Integer.toString(page))
                        .param("size", Integer.toString(size))
                        .param("sort", sort)
                        .param("direction", direction))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(response.getResponse().getContentAsString(), "$.content[*].id");
    }
}
