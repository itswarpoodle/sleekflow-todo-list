ALTER TABLE todos
    ADD COLUMN recurrence_frequency VARCHAR(16),
    ADD COLUMN recurrence_interval INTEGER,
    ADD COLUMN recurrence_unit VARCHAR(16),
    ADD COLUMN previous_occurrence_id UUID;

ALTER TABLE todos
    ADD CONSTRAINT todos_recurrence_complete_check CHECK (
        (recurrence_frequency IS NULL AND recurrence_interval IS NULL AND recurrence_unit IS NULL)
        OR
        (recurrence_frequency IS NOT NULL AND recurrence_interval > 0 AND recurrence_unit IS NOT NULL)
    ),
    ADD CONSTRAINT todos_recurrence_frequency_check CHECK (
        recurrence_frequency IS NULL
        OR recurrence_frequency IN ('DAILY', 'WEEKLY', 'MONTHLY', 'CUSTOM')
    ),
    ADD CONSTRAINT todos_recurrence_unit_check CHECK (
        recurrence_unit IS NULL
        OR recurrence_unit IN ('DAYS', 'WEEKS', 'MONTHS')
    ),
    ADD CONSTRAINT todos_standard_recurrence_check CHECK (
        recurrence_frequency IS NULL
        OR recurrence_frequency = 'CUSTOM'
        OR (recurrence_frequency = 'DAILY' AND recurrence_interval = 1 AND recurrence_unit = 'DAYS')
        OR (recurrence_frequency = 'WEEKLY' AND recurrence_interval = 1 AND recurrence_unit = 'WEEKS')
        OR (recurrence_frequency = 'MONTHLY' AND recurrence_interval = 1 AND recurrence_unit = 'MONTHS')
    ),
    ADD CONSTRAINT todos_previous_occurrence_fk FOREIGN KEY (previous_occurrence_id) REFERENCES todos (id),
    ADD CONSTRAINT todos_previous_occurrence_not_self CHECK (previous_occurrence_id IS NULL OR previous_occurrence_id <> id),
    ADD CONSTRAINT todos_previous_occurrence_unique UNIQUE (previous_occurrence_id);
