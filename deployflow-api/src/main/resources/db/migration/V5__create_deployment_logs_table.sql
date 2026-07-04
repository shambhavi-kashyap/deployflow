CREATE TABLE deployment_logs
(
    id BIGSERIAL PRIMARY KEY,
    deployment_id BIGINT NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_deployment_log FOREIGN KEY(deployment_id) REFERENCES deployments(id) ON DELETE CASCADE
);