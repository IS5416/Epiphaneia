CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

CREATE TABLE admin (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE api_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id UUID NOT NULL REFERENCES admin(id),
    name VARCHAR(100) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    prefix VARCHAR(12) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ
);
CREATE INDEX idx_api_token_admin_id ON api_token(admin_id);

CREATE TABLE application (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    actuator_url VARCHAR(500),
    prometheus_label VARCHAR(200) NOT NULL,
    tags JSONB DEFAULT '[]',
    actuator_info JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_application_prometheus_label ON application(prometheus_label);

CREATE TABLE data_source (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    url VARCHAR(500) NOT NULL,
    auth_type VARCHAR(20) NOT NULL DEFAULT 'NONE',
    auth_config JSONB,
    is_connected BOOLEAN DEFAULT FALSE,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_data_source_type ON data_source(type);

CREATE TABLE llm_provider (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(50) NOT NULL,
    model_name VARCHAR(200) NOT NULL,
    api_key_encrypted TEXT,
    base_url VARCHAR(500),
    is_connected BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE conversation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES application(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_conversation_application_id ON conversation(application_id);
CREATE INDEX idx_conversation_updated_at ON conversation(updated_at DESC);
CREATE INDEX idx_conversation_title_trgm ON conversation USING GIN (title gin_trgm_ops);

CREATE TABLE message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    role VARCHAR(10) NOT NULL CHECK (role IN ('USER', 'AGENT')),
    content TEXT NOT NULL,
    diagnosis_state VARCHAR(20),
    failure_reason TEXT,
    risk_level VARCHAR(20),
    risk_impact TEXT,
    risk_urgency TEXT,
    token_count INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_message_conversation_order ON message(conversation_id, created_at);
CREATE INDEX idx_message_diagnosis_state ON message(diagnosis_state)
    WHERE diagnosis_state IN ('CREATED','PLANNING','QUERYING','ANALYZING');

CREATE TABLE evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES message(id) ON DELETE CASCADE,
    source VARCHAR(50) NOT NULL,
    query_text TEXT NOT NULL,
    summary TEXT NOT NULL,
    anomaly_start TIMESTAMPTZ,
    anomaly_end TIMESTAMPTZ,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_evidence_message_id ON evidence(message_id);

CREATE TABLE root_cause_hypothesis (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES message(id) ON DELETE CASCADE,
    rank SMALLINT NOT NULL CHECK (rank BETWEEN 1 AND 3),
    description TEXT NOT NULL,
    confidence DOUBLE PRECISION CHECK (confidence BETWEEN 0.0 AND 1.0),
    supporting_evidence_ids JSONB DEFAULT '[]',
    UNIQUE (message_id, rank)
);
CREATE INDEX idx_hypothesis_message_id ON root_cause_hypothesis(message_id);

CREATE TABLE fix_suggestion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES message(id) ON DELETE CASCADE,
    description TEXT NOT NULL,
    auto_execution_allowed BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_suggestion_message_id ON fix_suggestion(message_id);
