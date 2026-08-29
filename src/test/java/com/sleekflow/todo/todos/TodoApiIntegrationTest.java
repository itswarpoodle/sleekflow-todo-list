package com.sleekflow.todo.todos;

import com.jayway.jsonpath.JsonPath;
import com.sleekflow.todo.todos.repository.TodoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name").value("Prepare assessment demo"))
                .andExpect(jsonPath("$.description").value("Walk through the core requirements"))
                .andExpect(jsonPath("$.dueDate").value("2026-09-30"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.dependencyIds", hasSize(0)))
                .andExpect(jsonPath("$.blocked").value(false))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()))
                .andReturn();

        String id = JsonPath.read(response.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/todos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.priority").value("HIGH"));

        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id));
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
                .andExpect(jsonPath("$.priority").value("LOW"));

        mockMvc.perform(get("/api/todos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated name"));
    }

    @Test
    void keepsArchivedTodosVisibleAndSoftDeletedTodosHidden() throws Exception {
        String archivedId = createTodo("""
                {"name":"Archived but visible","status":"ARCHIVED","priority":"LOW"}
                """);
        String deletedId = createTodo("""
                {"name":"Delete without data loss","priority":"HIGH"}
                """);

        mockMvc.perform(delete("/api/todos/{id}", deletedId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/todos/{id}", deletedId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TODO_NOT_FOUND"));

        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(archivedId))
                .andExpect(jsonPath("$[0].status").value("ARCHIVED"));

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
    void returnsNotFoundForEverySingleTodoOperation() throws Exception {
        var missingId = UUID.randomUUID();

        mockMvc.perform(get("/api/todos/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TODO_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/todos/" + missingId));

        mockMvc.perform(put("/api/todos/{id}", missingId)
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

        mockMvc.perform(delete("/api/todos/{id}", missingId))
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
                .andExpect(jsonPath("$.components.schemas.TodoResponse.properties.blocked").exists());
    }

    private String createTodo(String requestBody) throws Exception {
        var response = mockMvc.perform(post("/api/todos")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(response.getResponse().getContentAsString(), "$.id");
    }
}
