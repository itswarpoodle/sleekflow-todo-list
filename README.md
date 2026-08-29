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

## TODO API

| Method | Path | Behavior |
| --- | --- | --- |
| `GET` | `/api/todos` | Lists active TODOs, including archived TODOs |
| `GET` | `/api/todos/{id}` | Retrieves one active TODO |
| `POST` | `/api/todos` | Creates a TODO |
| `PUT` | `/api/todos/{id}` | Replaces the editable TODO fields |
| `DELETE` | `/api/todos/{id}` | Soft-deletes a TODO and returns `204` |

Names are required. Descriptions and due dates are optional. A create request defaults to `NOT_STARTED` status and `MEDIUM` priority when those fields are omitted. Update requests require both fields. Deleted rows are retained with a deletion timestamp but excluded from normal reads; `ARCHIVED` is a visible status and is not deletion.

Create and update requests accept a `dependencyIds` array. Responses return those IDs and a derived `blocked` flag. Dependencies must reference active TODOs, cannot reference the TODO itself, and cannot form a direct or transitive cycle. A TODO with any dependency that is not `COMPLETED` cannot be moved to `IN_PROGRESS`.

Recurring TODOs use a `recurrence` object. `DAILY`, `WEEKLY`, and `MONTHLY` are canonical one-unit rules; `CUSTOM` requires a positive `interval` and a `unit` of `DAYS`, `WEEKS`, or `MONTHS`. A recurring TODO requires a due date. Its first transition to `COMPLETED` creates one `NOT_STARTED` successor with a calendar-adjusted due date and a `previousOccurrenceId` link. Optimistic locking plus a unique database constraint keeps competing or repeated completion requests from creating duplicate successors.

The request and response schemas and enum values are available through Swagger UI. Runtime errors use a consistent envelope containing `status`, `code`, `message`, `path`, and field-specific validation errors.

## Verification

```shell
./mvnw verify
cd frontend
npm test
npm run build
```

Backend integration tests start an isolated PostgreSQL 18.6 container through Testcontainers.
