CREATE TABLE deployments
(
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    commit_sha VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    k8s_namespace VARCHAR(100),
    live_url VARCHAR(255),
    error_logs TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_project FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE
);