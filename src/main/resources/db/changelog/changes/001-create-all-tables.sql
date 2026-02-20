-- liquibase formatted sql

-- changeset developer:1
-- preconditions onFail:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'documents'
CREATE TABLE documents (
    id BIGSERIAL PRIMARY KEY,
    document_number VARCHAR(50) NOT NULL UNIQUE,
    author VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- changeset developer:2
-- preconditions onFail:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'document_history'
CREATE TABLE document_history (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    initiator VARCHAR(100) NOT NULL,
    action VARCHAR(20) NOT NULL,
    comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
);

-- changeset developer:3
-- preconditions onFail:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'approval_registry'
CREATE TABLE approval_registry (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    approved_by VARCHAR(100) NOT NULL,
    approved_at TIMESTAMP NOT NULL,
    comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- changeset developer:4
-- preconditions onFail:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_documents_status'
CREATE INDEX idx_documents_status ON documents(status);
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_documents_author'
CREATE INDEX idx_documents_author ON documents(author);
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_documents_created_at'
CREATE INDEX idx_documents_created_at ON documents(created_at);
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_documents_updated_at'
CREATE INDEX idx_documents_updated_at ON documents(updated_at);
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_history_document_id'
CREATE INDEX idx_history_document_id ON document_history(document_id);
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_registry_document_id'
CREATE INDEX idx_registry_document_id ON approval_registry(document_id);
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_registry_document_unique'
CREATE UNIQUE INDEX idx_registry_document_unique ON approval_registry(document_id);

-- changeset developer:5
-- preconditions onFail:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.routines WHERE routine_name = 'update_updated_at_column' AND routine_type = 'FUNCTION'
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- preconditions onFail:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_name = 'update_documents_updated_at'
CREATE TRIGGER update_documents_updated_at
    BEFORE UPDATE ON documents
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();