ALTER TABLE todos
    ADD COLUMN description VARCHAR(2000),
    ADD COLUMN due_date DATE,
    ADD COLUMN priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE todos
    ADD CONSTRAINT todos_priority_check CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH'));

DROP INDEX todos_created_at_idx;

CREATE INDEX todos_active_created_at_idx
    ON todos (created_at DESC, id)
    WHERE deleted_at IS NULL;
