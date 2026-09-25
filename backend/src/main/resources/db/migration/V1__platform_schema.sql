CREATE TABLE projects (
    id CHAR(36) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

CREATE TABLE topologies (
    project_id CHAR(36) PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_topology_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE TABLE topology_nodes (
    id CHAR(36) PRIMARY KEY,
    project_id CHAR(36) NOT NULL,
    component_type VARCHAR(80) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    position_x DOUBLE NOT NULL,
    position_y DOUBLE NOT NULL,
    config_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_nodes_project (project_id),
    CONSTRAINT fk_node_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE TABLE topology_edges (
    id CHAR(36) PRIMARY KEY,
    project_id CHAR(36) NOT NULL,
    source_node_id CHAR(36) NOT NULL,
    target_node_id CHAR(36) NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_edges_project (project_id),
    CONSTRAINT fk_edge_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_edge_source FOREIGN KEY (source_node_id) REFERENCES topology_nodes(id) ON DELETE CASCADE,
    CONSTRAINT fk_edge_target FOREIGN KEY (target_node_id) REFERENCES topology_nodes(id) ON DELETE CASCADE,
    CONSTRAINT uq_edge UNIQUE (project_id, source_node_id, target_node_id, relation_type)
);
