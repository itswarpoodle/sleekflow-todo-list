# Decision Log

I treated the required features, non-functional requirements, and deliverables as the baseline. I completed and verified that baseline before starting any optional work. My main constraint was to build a system I can explain, operate, and defend in an interview, while still handling the failure modes around recurrence, dependencies, deletion, scale, and concurrent edits.

## 1. How I interpreted ambiguous or underspecified requirements, and why

**One shared list, not invented user ownership.** The brief says multiple users may access the same TODO list concurrently, while authentication is optional. I implemented one shared list and made overlapping reads and writes safe. Adding owners without an identity model would have created product rules that were not requested.

**Archived and deleted are separate concepts.** `ARCHIVED` is a required visible status. Delete sets `deleted_at`, retains the row, and excludes it from normal API and UI reads. This satisfies the data-retention requirement without making deleted work look like ordinary archived work.

**Due dates are calendar dates.** The brief asks for a due date, not a deadline or reminder time. I used Java `LocalDate` and PostgreSQL `DATE`, avoiding timezone shifts and matching what the UI displays. User-assigned dates must be today or later, while an existing overdue date may remain unchanged so overdue work does not become uneditable.

**Recurrence is completion-driven.** The required trigger is marking a recurring TODO complete, so I did not add a scheduler. Daily, weekly, and monthly mean an interval of one. Custom recurrence requires a positive interval and a unit of days, weeks, or months. Calendar arithmetic safely handles month ends. The successor copies the task content, recurrence, priority, and dependencies, starts as `NOT_STARTED`, and links to its source through `previousOccurrenceId`.

**Dependency enforcement follows the stated transition.** A TODO is blocked while any dependency is not `COMPLETED`, and a blocked TODO cannot enter `IN_PROGRESS`. I also reject missing dependencies, self-dependencies, and transitive cycles because accepting them would create an invalid or permanently blocked graph. I did not invent restrictions on direct completion or archiving because the brief only constrains entry into `IN_PROGRESS`.

**Ten thousand items means bounded server work.** The API caps page size at 100, performs filtering and sorting in PostgreSQL, and uses stable ID tie-breakers and targeted indexes. Dependency search is also bounded and server-side. The browser never downloads the complete list.

**Status and priority have domain order.** Alphabetical enum order is not useful. Priority sorts `LOW`, `MEDIUM`, `HIGH`; status sorts `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`, `ARCHIVED`; missing due dates sort last; names sort case-insensitively.

## 2. Key architectural decisions and trade-offs considered

**Java 21 and Spring Boot.** This is the stack in which I can deliver, debug, and explain the strongest result under assessment time pressure. It provides mature validation, transactions, persistence, testing, health checks, and OpenAPI support. It carries more runtime and framework machinery than a small Go or Node service, but correctness and reviewability mattered more than minimal memory use for this assessment.

**A modular monolith with responsibility-based packages.** Controllers own HTTP concerns, DTOs define the contract, services coordinate lifecycle rules, the model owns single-aggregate state changes, and repositories own persistence. This gives one deployable unit and one clear transaction boundary. I avoided microservices, separate application modules, generic repository wrappers, and a ports-and-adapters framework because the domain does not justify their cognitive or operational cost.

**React, TypeScript, and Vite as a separate client.** React makes the complete workflow easy to demonstrate without coupling HTML rendering to Spring MVC. TypeScript keeps API shapes explicit, and Vite keeps local iteration fast. A separate client adds a second build process, so I contained that cost with local component state, one small API module, a development proxy, and no UI framework or global state library.

**PostgreSQL with Flyway.** Dependencies, recurrence lineage, soft deletion, uniqueness, and optimistic locking benefit from relational constraints and transactions. Flyway makes the schema reproducible and reviewable. An in-memory database could hide PostgreSQL query and locking behavior, while a document database would make cross-record graph and uniqueness guarantees harder to express.

**Transactional recurrence with two safeguards.** Successor creation runs in the completion transaction. JPA optimistic locking detects competing updates, and a unique constraint on `previous_occurrence_id` is the final database guarantee against duplicate successors. A losing request receives an explicit conflict instead of silently overwriting state.

**Explicit queries and integration tests.** The list contract has a known filter set, sort whitelist, bounded pages, and domain ordering, so one visible repository query is clearer than a general query framework. The backend suite uses PostgreSQL Testcontainers because the highest risks cross HTTP, validation, JPA, transactions, and database constraints. This is slower than isolated mocks but gives stronger evidence for the behavior under review.

### Optional enhancements added after the core baseline passed

**Real-time browser updates.** This is an optional feature named in the brief. Spring emits a small Server-Sent Events invalidation only after a successful transaction commits; React then refetches its current bounded query. I chose SSE over WebSockets because communication is one-way and the REST API remains the source of truth. The added value is immediately visible collaboration across tabs without duplicating domain state or introducing a broker. The present implementation is intentionally single-instance.

**Explicit optimistic concurrency.** Safe database concurrency was required, but the HTTP version contract and conflict-recovery UI are an additional improvement. Each TODO exposes a version and strong `ETag`; update and delete require `If-Match`. A stale writer receives `412 TODO_VERSION_CONFLICT` and can reload the current TODO. This prevents a technically safe database conflict from becoming a vague user error, protects against lost edits, and makes the concurrency policy demonstrable.

**Playwright end-to-end tests.** The brief requires core tests, not browser automation. I added a cumulative browser workflow, two-tab synchronization coverage, and a stale-editor scenario. These tests exercise the actual React, Nginx or Vite proxy, Spring API, and PostgreSQL path. Their value is repeatable evidence that the layers work together, including the two optional collaboration behaviors.

**DevOps setup.** Docker and CI/CD are explicitly optional. Compose can now run PostgreSQL, the Spring Boot service, and the production React/Nginx client with health-based startup. Multi-stage images keep build tools out of runtime images, and application containers run as non-root users. GitHub Actions verifies backend, frontend, browser workflows, and image builds. This reduces reviewer setup risk and proves that a clean environment can reproduce the result.

**Architecture diagram.** This is explicitly optional. I created it last so it describes the verified system rather than an aspirational design. It gives a reviewer a fast map from browser requests through Nginx, controllers, service transactions, PostgreSQL, and committed SSE events, which makes the implementation easier to navigate and discuss.

**Reviewer and demo hardening.** The deterministic demo dataset, checked-in OpenAPI snapshot, health endpoint, five-minute demo route, accessible interaction states, and plain-language error messages go beyond the minimum feature list. They reduce demo variability, support offline review, and make failure states understandable instead of showing only a happy path. I kept them small because their purpose is confidence and communication, not product expansion.

**Future-only due-date assignment.** This is an additional product-integrity improvement rather than a core requirement. The date picker disables dates before today, and the API independently rejects newly assigned past dates so direct callers cannot bypass the UI. This prevents contradictory planning data and gives immediate, readable feedback. Existing overdue TODOs may keep their original date, preserving honest history and allowing users to complete or archive them without forced rescheduling.

## 3. What I chose not to build and why

I did not add authentication, registration, user ownership, or bulk operations. They are optional in the brief and each introduces product questions about authorization, sharing, group membership, partial failure, and audit history. Real-time visibility and conflict safety offered more value for demonstrating the existing shared-list model.

I did not add WebSockets, Redis, Kafka, a transactional outbox, or a distributed event bus. SSE is sufficient for one-way updates in this single-instance assessment. Claiming multi-instance delivery without shared event infrastructure would be misleading. I also did not add Kubernetes manifests or cloud deployment because no target platform, scaling policy, or secret-management environment was specified.

I did not add notifications, reminders, tags, groups, attachments, drag-and-drop ordering, or a general scheduling engine. These are adjacent products rather than evidence for the requested TODO lifecycle. Soft-deleted rows are retained, but restore remains administrative rather than another UI workflow.

I also avoided Redux, a component framework, generic service abstractions, and microservices. Each would add concepts and files without solving a current requirement. The request-to-database path remains deliberately traceable.

## 4. What I would do differently with more time

First, I would define identity and ownership with stakeholders, then add authentication, authorization, and per-user or shared-list boundaries. This decision should precede bulk actions, audit records, and notifications because all depend on knowing who can act on which TODOs.

Second, if the service needed multiple application instances, I would publish committed changes through a transactional outbox and shared broker, then add reconnect replay using event IDs. That would preserve the current SSE client contract while making delivery durable across instances and restarts.

Third, I would add an audited restore workflow and explicitly define what should happen when an active TODO depends on a deleted prerequisite. The current system retains the data and excludes it from normal reads, which meets the brief, but a production product needs recovery ownership and policy.

Fourth, I would add broader accessibility automation, security scanning, structured observability, and performance baselines. I would profile deep list navigation and consider cursor pagination only if usage grows well beyond 10,000 items; offset pagination is simpler and appropriate at the assessed scale.

Finally, as the suite grows, I would split the broad backend integration class into focused contract, lifecycle, concurrency, and query suites. I would keep the same real PostgreSQL coverage while making ownership and failure diagnosis clearer.
