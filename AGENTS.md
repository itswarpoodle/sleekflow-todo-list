# Project Instructions

## Project overview

- This repository is Austin's SleekFlow Software Engineer technical assessment.
- Build a complete, runnable TODO application with a Spring Boot API and a simple React web interface.
- Treat `../SleekFlow Software Engineer Interview Project.pdf` as the primary source of requirements.
- Distinguish requirements in the assessment PDF from direct instructions given by Austin.
- Implement every core requirement before considering optional or nice-to-have work.
- Do not begin optional enhancements unless Austin explicitly gives the green light.
- Keep the solution easy to understand, demonstrate, and justify during the interview.
- Keep architecture trade-offs and interview talking points out of this file; capture them in the decision log and later interview preparation.

## Working method

- Work in milestone order and keep each milestone demonstrable before moving forward.
- Track milestone scope and completion accurately in the SleekFlow Linear project.
- Mark a milestone complete only after its implementation and required verification have passed.
- Stop at requested commit checkpoints and do not commit unrelated future-milestone work together.
- Before staging, inspect the working tree and confirm that every selected file belongs to the current milestone.
- Preserve existing user changes and never discard or overwrite unrelated work.
- Use Graphify's knowledge tree to locate code, understand relationships, and assess impact before editing.
- Refresh Graphify after meaningful structural or code changes.
- Make reasonable in-scope assumptions, but pause when a choice would materially expand scope or change product behaviour.

## Priority order

- First: satisfy the assessment's core functional requirements.
- Second: satisfy correctness, concurrency, data-retention, validation, and scale requirements.
- Third: provide a functional, accessible, and restrained browser experience.
- Fourth: complete verification, reviewer documentation, and demo readiness.
- Last: optional enhancements, only after explicit approval from Austin.

## Technology constraints

- Use Java 21 and Spring Boot for the backend.
- Use Maven through the committed Maven wrapper.
- Use React, TypeScript, and Vite for the frontend.
- Use PostgreSQL for persistence and Flyway for schema migrations.
- Use Docker and Testcontainers for PostgreSQL-backed integration testing.
- Keep Swagger/OpenAPI available for API inspection and manual testing.
- Keep frontend state local and API access explicit.
- Do not add a global state library, UI framework, or general-purpose abstraction without a demonstrated need.

## Repository structure

- Keep backend packages separated by responsibility under `src/main/java/com/sleekflow/todo/todos/`.
- Place HTTP endpoints in `controller/`.
- Place request and response contracts in `dto/`.
- Place API and domain exceptions in `exception/`.
- Place the persistent TODO aggregate and domain enums in `model/`.
- Place Spring Data interfaces and persistence queries in `repository/`.
- Place application orchestration and cross-TODO lifecycle rules in `service/`.
- Keep Flyway migrations in `src/main/resources/db/migration/`.
- Keep PostgreSQL-backed backend integration tests in `src/test/java/`.
- Keep React components, API access, shared types, styling, and component tests in `frontend/src/`.
- Keep `README.md` as the reviewer's entry point for setup, local development, API behaviour, and verification commands.
- Keep `decision-log.md` aligned with the implementation and explicit about optional enhancements and their value.

## Coding conventions

- Write lead-level or better code while keeping the implementation direct and intuitive.
- Prefer the smallest clear solution over extra layers, patterns, or classes.
- Maintain clear separation of concerns without fragmenting one behaviour across unnecessary files.
- Keep controllers thin and lifecycle rules centralized in the service layer.
- Keep domain state changes explicit and persistence rules visible.
- Treat `ARCHIVED` as a visible status and soft deletion as a separate retention mechanism.
- Exclude soft-deleted rows from normal reads while retaining their database records.
- Preserve bounded pagination and stable tie-break ordering for list queries.
- Preserve calendar-safe recurrence calculations and exactly-one successor creation.
- Preserve dependency validation for missing, self-referencing, and cyclic dependencies.
- Reject newly assigned past due dates while allowing existing overdue dates to remain unchanged.
- Preserve the rule that blocked TODOs cannot move to `IN_PROGRESS` or `COMPLETED`, while ordinary `NOT_STARTED` edits, archiving, and deletion remain available.
- Treat only `COMPLETED` dependencies as satisfied; archiving or deleting an incomplete prerequisite does not unblock its dependents.
- Preserve optimistic locking and the database uniqueness guard for concurrent recurring completion.
- Validate at system boundaries and return consistent, useful error responses.
- Avoid silently swallowing errors unless the UI has a deliberate safe fallback.
- Keep secrets and machine-specific configuration out of source control.

## Comments and documentation

- Document non-obvious intent, invariants, transaction boundaries, and surprising edge cases.
- Prefer comments that explain why a rule exists or how a contract behaves.
- Do not comment every line, restate readable code, or add documentation solely to increase comment count.
- Keep Javadocs and inline comments concise, human-readable, and immediately useful to reviewers.
- Use clear domain language instead of framework jargon where possible.
- Update the README when setup steps, commands, API behaviour, or user workflows change.
- Keep comments accurate when implementation changes; stale documentation is a defect.

## Frontend and UI rules

- Keep the interface simple, functional, and suitable for an engineering assessment.
- Follow the established `minimalist-ui` direction: restrained editorial styling, warm neutral colours, flat surfaces, and clear typography.
- Do not add gradients, heavy shadows, decorative animation, or unnecessary visual complexity.
- Prioritize readable hierarchy, clear labels, predictable controls, and sensible spacing.
- Provide loading, empty, filtered-empty, validation, blocked, error, and delete-confirmation states.
- Keep keyboard focus visible and support keyboard dismissal of modal interactions.
- Keep layouts usable on desktop and narrow screens.
- Display blocked-state reasons and soft-delete consequences in plain language.
- Keep filtering, sorting, and pagination server-backed rather than loading the full dataset into the browser.

## Testing expectations

- Run `./mvnw verify` for the full backend build and PostgreSQL-backed integration suite.
- Run `npm test -- --run` from `frontend/` for frontend behaviour.
- Run `npm run build` from `frontend/` before handoff or commit.
- Run `git diff --check` before committing.
- Test meaningful core behaviour and edge cases rather than chasing superficial coverage.
- Cover CRUD, future-only due-date assignment, overdue editing, validation, soft deletion, dependencies, cycle rejection, blocked progress and completion, permitted blocked-task edits and retirement, recurrence dates, repeated and concurrent completion, filtering, sorting, and pagination.
- Keep large-list verification representative of at least 10,000 TODOs.
- Test every completed milestone in a real browser.
- Make browser testing cumulative: completing a later milestone must regress earlier milestone workflows.
- Do not rely on Austin's manual testing as a substitute for agent-run browser verification.
- Use disposable, clearly named test data for browser workflows.
- Confirm retained database state directly when verifying soft deletion.

## Git and milestone discipline

- Use focused conventional commits that describe the milestone outcome.
- Include the milestone identifier in milestone-completion commit messages.
- Use `feat` for new user-facing capability and `chore` for repository or maintenance work.
- Do not commit generated build output, temporary PDF renders, secrets, or unrelated files.
- Verify the staged file list and staged diff before every commit.
- Report the resulting commit hash and verification results to Austin.
- Leave the working tree clean after a requested milestone commit.

## M6 decision-log requirements

- Produce a concise one-to-two-page decision log during M6.
- Use four clearly labelled sections that directly answer every required question.
- Explain how ambiguous or underspecified requirements were interpreted and why.
- Explain the key architectural decisions and the trade-offs considered.
- Explain what was deliberately not built and why.
- Explain what would be done differently with more time.
- Do not treat a general architecture summary as a substitute for any of the four answers.
- Keep the writing candid, specific to this implementation, and easy to discuss in the live interview.

## Definition of done

- Every core requirement in the assessment PDF is implemented and traceable to working behaviour.
- Backend and frontend verification commands pass from the documented local setup.
- The complete core workflow passes cumulative browser testing.
- PostgreSQL retains soft-deleted data while active API and UI views exclude it.
- The list remains bounded and usable for 10,000 or more TODOs.
- Swagger/OpenAPI and README instructions are accurate and reviewer-friendly.
- The M6 decision log answers all four required questions.
- Every implemented optional enhancement is identified in the decision log with its rationale and added value.
- Linear reflects the real milestone state.
- No optional work is included without explicit approval.
- The final working tree contains only intentional, reviewable files.
