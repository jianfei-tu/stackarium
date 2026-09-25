CREATE TABLE runtime_instances (
    id CHAR(36) PRIMARY KEY,
    project_id CHAR(36) NOT NULL,
    topology_revision BIGINT NOT NULL,
    compose_project_name VARCHAR(80) NOT NULL UNIQUE,
    artifact_path VARCHAR(700) NOT NULL,
    plan_json JSON NOT NULL,
    status VARCHAR(24) NOT NULL,
    error_phase VARCHAR(32),
    error_node_id CHAR(36),
    error_message VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    started_at DATETIME(6),
    stopped_at DATETIME(6),
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_runtime_project (project_id, created_at),
    CONSTRAINT fk_runtime_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE TABLE runtime_events (
    id CHAR(36) PRIMARY KEY,
    runtime_id CHAR(36) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    node_id CHAR(36),
    payload_json JSON NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    INDEX idx_events_runtime (runtime_id, occurred_at),
    CONSTRAINT fk_event_runtime FOREIGN KEY (runtime_id) REFERENCES runtime_instances(id) ON DELETE CASCADE
);
