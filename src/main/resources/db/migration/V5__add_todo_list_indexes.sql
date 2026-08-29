CREATE INDEX todos_active_status_idx
    ON todos (status, id)
    WHERE deleted_at IS NULL;

CREATE INDEX todos_active_priority_idx
    ON todos (priority, id)
    WHERE deleted_at IS NULL;

CREATE INDEX todos_active_due_date_idx
    ON todos (due_date, id)
    WHERE deleted_at IS NULL;

CREATE INDEX todos_active_name_idx
    ON todos (LOWER(name), id)
    WHERE deleted_at IS NULL;
