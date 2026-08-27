CREATE TABLE todos (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT todos_status_check CHECK (
        status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'ARCHIVED')
    )
);

CREATE INDEX todos_created_at_idx ON todos (created_at DESC);
