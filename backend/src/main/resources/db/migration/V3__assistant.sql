CREATE TABLE assistant_conversations (
    id CHAR(36) PRIMARY KEY,
    project_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_assistant_conversations_project (project_id, updated_at),
    CONSTRAINT fk_assistant_conversation_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE TABLE assistant_messages (
    id CHAR(36) PRIMARY KEY,
    conversation_id CHAR(36) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_assistant_messages_conversation (conversation_id, created_at),
    CONSTRAINT fk_assistant_message_conversation FOREIGN KEY (conversation_id) REFERENCES assistant_conversations(id) ON DELETE CASCADE
);

CREATE TABLE assistant_proposals (
    id CHAR(36) PRIMARY KEY,
    project_id CHAR(36) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    expected_revision BIGINT NOT NULL,
    summary VARCHAR(500) NOT NULL,
    operations_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_assistant_proposals_conversation (conversation_id, created_at),
    CONSTRAINT fk_assistant_proposal_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_assistant_proposal_conversation FOREIGN KEY (conversation_id) REFERENCES assistant_conversations(id) ON DELETE CASCADE
);
