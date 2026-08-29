CREATE TABLE todo_dependencies (
    todo_id UUID NOT NULL REFERENCES todos (id),
    dependency_id UUID NOT NULL REFERENCES todos (id),
    PRIMARY KEY (todo_id, dependency_id),
    CONSTRAINT todo_dependencies_no_self_reference CHECK (todo_id <> dependency_id)
);

CREATE INDEX todo_dependencies_dependency_id_idx
    ON todo_dependencies (dependency_id);
