-- Idempotent reviewer data. Only rows in the fixed demo set and successors
-- generated from its recurring TODO are replaced.
BEGIN;

CREATE TEMPORARY TABLE demo_todo_ids (
    id UUID PRIMARY KEY
) ON COMMIT DROP;

INSERT INTO demo_todo_ids (id) VALUES
    ('10000000-0000-0000-0000-000000000001'),
    ('10000000-0000-0000-0000-000000000002'),
    ('10000000-0000-0000-0000-000000000003'),
    ('10000000-0000-0000-0000-000000000004'),
    ('10000000-0000-0000-0000-000000000005'),
    ('10000000-0000-0000-0000-000000000006');

WITH RECURSIVE generated_occurrences AS (
    SELECT id
    FROM todos
    WHERE previous_occurrence_id = '10000000-0000-0000-0000-000000000004'

    UNION ALL

    SELECT todo.id
    FROM todos todo
    JOIN generated_occurrences occurrence
      ON todo.previous_occurrence_id = occurrence.id
)
INSERT INTO demo_todo_ids (id)
SELECT id FROM generated_occurrences
ON CONFLICT DO NOTHING;

DELETE FROM todo_dependencies
WHERE todo_id IN (SELECT id FROM demo_todo_ids)
   OR dependency_id IN (SELECT id FROM demo_todo_ids);

UPDATE todos
SET previous_occurrence_id = NULL
WHERE id IN (SELECT id FROM demo_todo_ids);

DELETE FROM todos
WHERE id IN (SELECT id FROM demo_todo_ids);

INSERT INTO todos (
    id,
    name,
    description,
    due_date,
    status,
    priority,
    version,
    recurrence_frequency,
    recurrence_interval,
    recurrence_unit,
    previous_occurrence_id,
    created_at,
    updated_at,
    deleted_at
) VALUES
    (
        '10000000-0000-0000-0000-000000000001',
        'Define assessment acceptance criteria',
        'Completed prerequisite used to demonstrate unblocked work.',
        DATE '2026-09-01',
        'COMPLETED',
        'HIGH',
        0,
        NULL,
        NULL,
        NULL,
        NULL,
        TIMESTAMPTZ '2026-08-30 09:00:00+00',
        TIMESTAMPTZ '2026-08-30 09:00:00+00',
        NULL
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        'Confirm live demo environment',
        'Complete this TODO to unblock the dependency workflow.',
        DATE '2026-09-02',
        'NOT_STARTED',
        'MEDIUM',
        0,
        NULL,
        NULL,
        NULL,
        NULL,
        TIMESTAMPTZ '2026-08-30 09:05:00+00',
        TIMESTAMPTZ '2026-08-30 09:05:00+00',
        NULL
    ),
    (
        '10000000-0000-0000-0000-000000000003',
        'Present dependency workflow',
        'Starts blocked until the live demo environment is completed.',
        DATE '2026-09-03',
        'NOT_STARTED',
        'HIGH',
        0,
        NULL,
        NULL,
        NULL,
        NULL,
        TIMESTAMPTZ '2026-08-30 09:10:00+00',
        TIMESTAMPTZ '2026-08-30 09:10:00+00',
        NULL
    ),
    (
        '10000000-0000-0000-0000-000000000004',
        'Run weekly product review',
        'Completing this TODO creates the next weekly occurrence.',
        DATE '2026-09-04',
        'NOT_STARTED',
        'MEDIUM',
        0,
        'WEEKLY',
        1,
        'WEEKS',
        NULL,
        TIMESTAMPTZ '2026-08-30 09:15:00+00',
        TIMESTAMPTZ '2026-08-30 09:15:00+00',
        NULL
    ),
    (
        '10000000-0000-0000-0000-000000000005',
        'Archived onboarding note',
        'Archived remains visible and is separate from deletion.',
        NULL,
        'ARCHIVED',
        'LOW',
        0,
        NULL,
        NULL,
        NULL,
        NULL,
        TIMESTAMPTZ '2026-08-30 09:20:00+00',
        TIMESTAMPTZ '2026-08-30 09:20:00+00',
        NULL
    ),
    (
        '10000000-0000-0000-0000-000000000006',
        'Polish reviewer documentation',
        'An unblocked in-progress TODO for filtering and sorting.',
        DATE '2026-09-05',
        'IN_PROGRESS',
        'HIGH',
        0,
        NULL,
        NULL,
        NULL,
        NULL,
        TIMESTAMPTZ '2026-08-30 09:25:00+00',
        TIMESTAMPTZ '2026-08-30 09:25:00+00',
        NULL
    );

INSERT INTO todo_dependencies (todo_id, dependency_id) VALUES
    (
        '10000000-0000-0000-0000-000000000003',
        '10000000-0000-0000-0000-000000000002'
    );

COMMIT;
