# SleekFlow TODO List

A Java 21 and Spring Boot API with a deliberately small React and TypeScript client for the SleekFlow software engineer assessment.

## What is included

- Complete TODO CRUD with name, description, due date, status, and priority
- Daily, weekly, monthly, and custom recurrence
- Automatic successor creation when recurring work is completed
- Multiple dependencies with cycle detection and blocked-state enforcement
- Server-side filtering, sorting, name search, and bounded pagination
- Timestamp-based soft deletion, separate from the visible `ARCHIVED` status
- Optimistic locking and a database uniqueness guard for concurrent completion
- PostgreSQL indexes and a verified 10,000-row list path
- React workflows for creating, editing, deleting, filtering, sorting, and pagination
- Swagger UI, a checked-in OpenAPI snapshot, deterministic demo data, and integration tests

## Prerequisites

- Java 21
- Node.js 24 LTS and npm 11+
- Docker Desktop with Docker Compose

Maven does not need to be installed separately because the repository includes `./mvnw`.

## Quick start

Start PostgreSQL and wait for it to become healthy:

```shell
docker compose up -d --wait postgres
```

Start the backend in the first terminal:

```shell
./mvnw spring-boot:run
```

Install the locked frontend dependencies and start Vite in a second terminal:

```shell
cd frontend
npm ci
npm run dev
```

Open the application at `http://localhost:5173`. Vite proxies `/api` requests to Spring Boot at `http://localhost:8080`.

Useful local endpoints:

- Application: `http://localhost:5173`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health check: `http://localhost:8080/actuator/health`

The database volume is retained when `docker compose down` is used.

### Existing PostgreSQL installation

The backend defaults to the Compose credentials below. Override them when using another PostgreSQL instance:

```shell
export DATABASE_URL=jdbc:postgresql://localhost:5432/sleekflow_todo
export DATABASE_USERNAME=sleekflow
export DATABASE_PASSWORD=sleekflow
```

Flyway applies all required schema migrations when the backend starts.

## Deterministic demo data

After PostgreSQL is running and Flyway has created the schema, load the reviewer dataset:

```shell
docker compose exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U sleekflow -d sleekflow_todo \
  < scripts/demo-data.sql
```

The script is idempotent. It replaces only six fixed demo TODOs and any recurring successors generated from the demo recurrence. It does not clear unrelated TODOs.

## Five-minute demo route

1. Open the list and point out all four statuses, three priorities, due dates, recurrence, and the blocked badge.
2. Filter for blocked TODOs, then edit **Present dependency workflow** and try to move it to **In progress**. Show the readable `TODO_BLOCKED` response.
3. Complete **Confirm live demo environment**, return to the dependent TODO, and move it to **In progress** successfully.
4. Complete **Run weekly product review** and show the automatically created occurrence due one week later.
5. Create a disposable TODO, edit it, then delete it. Explain that it leaves active views while its database row is retained with `deleted_at`.
6. Demonstrate status, priority, due-date, and blocked filters; switch sort fields and direction; then show bounded pagination.
7. Open Swagger UI and finish with the automated verification commands below.

Run `scripts/demo-data.sql` again whenever the demo needs to return to its initial state.

## TODO API

| Method | Path | Behavior |
| --- | --- | --- |
| `GET` | `/api/todos` | Lists active TODOs in a bounded page, including archived TODOs |
| `GET` | `/api/todos/{id}` | Retrieves one active TODO |
| `POST` | `/api/todos` | Creates a TODO |
| `PUT` | `/api/todos/{id}` | Replaces the editable TODO fields |
| `DELETE` | `/api/todos/{id}` | Soft-deletes a TODO and returns `204` |

Names are required. Descriptions and due dates are optional. Create requests default to `NOT_STARTED` and `MEDIUM` when status and priority are omitted. Update requests require both fields. Deleted rows are retained with a deletion timestamp but excluded from normal reads. `ARCHIVED` is visible and is not deletion.

Create and update requests accept a `dependencyIds` array. Responses return those IDs and a derived `blocked` flag. Dependencies must reference active TODOs, cannot reference the TODO itself, and cannot form a direct or transitive cycle. A TODO with any dependency that is not `COMPLETED` cannot move to `IN_PROGRESS`.

Recurring TODOs use a `recurrence` object. `DAILY`, `WEEKLY`, and `MONTHLY` are canonical one-unit rules. `CUSTOM` requires a positive `interval` and a `unit` of `DAYS`, `WEEKS`, or `MONTHS`. Recurrence requires a due date. The first transition to `COMPLETED` creates one `NOT_STARTED` successor with a calendar-adjusted due date and a `previousOccurrenceId` link.

The list endpoint accepts zero-based `page` and `size` parameters. Size defaults to 20 and is capped at 100. Optional `status`, `priority`, `dueDate`, `blocked`, and case-insensitive partial `name` parameters can be combined. The UI uses `name` for bounded dependency search. Sorting is limited to `dueDate`, `priority`, `status`, or `name` through `sort`, with `direction=asc|desc`. Every order includes an ID tie-breaker so rows do not drift between pages.

Runtime errors use a consistent envelope containing `status`, `code`, `message`, `path`, and field-specific validation errors.

## API documentation

- Interactive documentation is available through Swagger UI while the backend is running.
- [docs/openapi.json](docs/openapi.json) is a checked-in snapshot for offline review.
- Refresh the snapshot after API changes with:

```shell
curl --fail --silent --show-error \
  http://localhost:8080/v3/api-docs \
  --output docs/openapi.json
```

## Verification

Run the backend build and PostgreSQL-backed integration suite:

```shell
./mvnw verify
```

Run the frontend component tests and production build:

```shell
cd frontend
npm test -- --run
npm run build
```

The backend suite uses an isolated PostgreSQL 18.6 Testcontainer. It covers CRUD, defaults, validation, error envelopes, durable deletion, missing resources, multiple dependencies, cycle rejection, blocked transitions, all recurrence schedules, repeat and concurrent completion, filters, name search, domain sorting, stable pagination, the 10,000-row path, index use, and the published OpenAPI contract.

## Repository guide

- `src/main/java/.../controller/`: REST endpoints
- `src/main/java/.../dto/`: validated API contracts
- `src/main/java/.../model/`: persistent TODO aggregate
- `src/main/java/.../repository/`: bounded queries and persistence
- `src/main/java/.../service/`: application and lifecycle rules
- `src/main/resources/db/migration/`: Flyway schema history
- `frontend/src/`: React UI, explicit API client, types, and component tests
- `scripts/demo-data.sql`: repeatable live-demo dataset
- `decision-log.md`: requirement interpretations and engineering decisions
- `AGENTS.md`: repository working rules for AI-assisted development

## AI assistance

AI coding tools were used during planning, implementation, documentation, and verification. All generated changes were reviewed against the assessment brief, exercised through automated tests, and tested through the browser before handoff.
