CREATE TABLE projects
(
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    github_repo_url VARCHAR(255) NOT NULL,
    branch VARCHAR(50) DEFAULT 'main',
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);