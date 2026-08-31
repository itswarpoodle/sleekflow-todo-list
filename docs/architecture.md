# Architecture

The application is a small modular monolith with a separate React client. REST remains the source of truth; Server-Sent Events only tell connected clients when to refetch their current bounded query.

```mermaid
flowchart LR
    reviewer[Browser]

    subgraph web[Frontend container]
        nginx[Nginx\nstatic files and reverse proxy]
        react[React and TypeScript\nlocal UI state]
        nginx --> react
    end

    subgraph app[Spring Boot container]
        controllers[REST and SSE controllers\nvalidation, ETag, If-Match]
        service[TodoService\ntransactions and lifecycle rules]
        events[TodoEventStream\nafter-commit invalidations]
        repository[TodoRepository\nbounded queries]
        controllers --> service
        service --> repository
        service -->|after commit| events
        events --> controllers
    end

    database[(PostgreSQL\nFlyway-managed schema)]

    reviewer -->|HTTP| nginx
    react -->|bounded REST requests| nginx
    nginx -->|/api proxy| controllers
    controllers -->|SSE todo-change| nginx
    nginx -->|SSE| react
    repository --> database
```

## Important runtime contracts

- `GET`, `POST`, `PUT`, and `DELETE` use the REST API. The list endpoint performs filtering, domain sorting, and bounded pagination in PostgreSQL.
- Each TODO version is exposed as a strong `ETag`. Mutations require the matching value in `If-Match`; stale clients receive `412 TODO_VERSION_CONFLICT` and reload before retrying.
- Recurrence, dependency validation, and soft deletion run inside service transactions. The database adds optimistic-version and unique-successor safeguards.
- Change events are registered during the transaction but emitted only after commit. An event contains an ID, change type, TODO ID, and version, not a second copy of TODO state.
- The in-memory SSE broadcaster intentionally targets one application instance. A multi-instance deployment would require a transactional outbox and shared event transport.
- Docker Compose starts PostgreSQL, the backend, and the production Nginx frontend in health-check order. GitHub Actions runs backend integration tests, frontend tests and build, Playwright, and image builds.

## Code map

```text
frontend/src/                         React UI, API client, and component tests
frontend/e2e/                         Playwright browser workflows
src/main/java/.../todos/controller/   REST and SSE transport
src/main/java/.../todos/dto/          Request, response, error, and event contracts
src/main/java/.../todos/service/      Transactions and cross-TODO lifecycle rules
src/main/java/.../todos/model/        Persistent aggregate and domain enums
src/main/java/.../todos/repository/   PostgreSQL queries and persistence
src/main/resources/db/migration/      Versioned Flyway schema
```
