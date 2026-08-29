# SleekFlow TODO List

A Java 21/Spring Boot backend and a deliberately small React/TypeScript client for the SleekFlow software engineer assessment.

## Prerequisites

- Java 21
- Maven 3.9+ (or use `./mvnw` after the wrapper is generated)
- Node.js 24 LTS and npm 11+
- Docker Desktop for PostgreSQL-backed integration tests
- PostgreSQL 18 for running the backend locally

## Local development

Start a local PostgreSQL database using your preferred installation, then export these values if they differ from the defaults:

```shell
export DATABASE_URL=jdbc:postgresql://localhost:5432/sleekflow_todo
export DATABASE_USERNAME=sleekflow
export DATABASE_PASSWORD=sleekflow
```

Run the backend:

```shell
./mvnw spring-boot:run
```

Run the frontend in a second terminal:

```shell
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/api` requests to Spring Boot on port 8080. Swagger UI is available at `http://localhost:8080/swagger-ui.html`.

The web interface covers the complete core workflow: create, edit, and soft-delete TODOs; choose dependencies and recurrence rules; see blocked-state feedback; filter by status, priority, due date, or dependency state; sort by the supported fields; and move through bounded result pages. State stays local to the React components and the API layer is intentionally explicit.

### Browser walkthrough

1. Select **New TODO**, enter a name, and optionally add a description, due date, priority, recurrence rule, and dependencies.
2. Use **Edit** to change a TODO's fields or lifecycle status. A blocked TODO will explain why it cannot move to **In progress** until its prerequisites are completed.
3. Use the filter and sort controls on the left. All controls are backed by the bounded server-side list endpoint rather than filtering an unbounded client-side collection.
4. Select **Delete**, review the confirmation, and confirm. The TODO leaves active views but remains retained in the database with its deletion timestamp.
5. Complete a recurring TODO and return to the list to see its next occurrence created automatically.

## TODO API

| Method | Path | Behavior |
| --- | --- | --- |
| `GET` | `/api/todos` | Lists active TODOs in a bounded page, including archived TODOs |
| `GET` | `/api/todos/{id}` | Retrieves one active TODO |
| `POST` | `/api/todos` | Creates a TODO |
| `PUT` | `/api/todos/{id}` | Replaces the editable TODO fields |
| `DELETE` | `/api/todos/{id}` | Soft-deletes a TODO and returns `204` |

Names are required. Descriptions and due dates are optional. A create request defaults to `NOT_STARTED` status and `MEDIUM` priority when those fields are omitted. Update requests require both fields. Deleted rows are retained with a deletion timestamp but excluded from normal reads; `ARCHIVED` is a visible status and is not deletion.

Create and update requests accept a `dependencyIds` array. Responses return those IDs and a derived `blocked` flag. Dependencies must reference active TODOs, cannot reference the TODO itself, and cannot form a direct or transitive cycle. A TODO with any dependency that is not `COMPLETED` cannot be moved to `IN_PROGRESS`.

Recurring TODOs use a `recurrence` object. `DAILY`, `WEEKLY`, and `MONTHLY` are canonical one-unit rules; `CUSTOM` requires a positive `interval` and a `unit` of `DAYS`, `WEEKS`, or `MONTHS`. A recurring TODO requires a due date. Its first transition to `COMPLETED` creates one `NOT_STARTED` successor with a calendar-adjusted due date and a `previousOccurrenceId` link. Optimistic locking plus a unique database constraint keeps competing or repeated completion requests from creating duplicate successors.

The list endpoint accepts zero-based `page` and `size` parameters. Size defaults to 20 and is capped at 100. Optional `status`, `priority`, `dueDate`, and `blocked` parameters can be combined. Sorting is deliberately limited to `dueDate`, `priority`, `status`, or `name` through `sort`, with `direction=asc|desc`; every order includes an ID tie-breaker so rows do not drift between pages. Responses contain `content`, `page`, `size`, `totalElements`, and `totalPages`.

The request and response schemas and enum values are available through Swagger UI. Runtime errors use a consistent envelope containing `status`, `code`, `message`, `path`, and field-specific validation errors.

## Verification

```shell
./mvnw verify
cd frontend
npm test
npm run build
```

Backend integration tests start an isolated PostgreSQL 18.6 container through Testcontainers.
