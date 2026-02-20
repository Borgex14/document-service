-- ТАБЛИЦА: documents (Документы)
-- =====================================================
CREATE TABLE IF NOT EXISTS documents (
    id BIGSERIAL PRIMARY KEY,
    document_number VARCHAR(50) NOT NULL UNIQUE,
    author VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Комментарии к таблице и колонкам
COMMENT ON TABLE documents IS 'Основная таблица документов';
COMMENT ON COLUMN documents.id IS 'Внутренний идентификатор документа';
COMMENT ON COLUMN documents.document_number IS 'Уникальный номер документа (генерируется автоматически)';
COMMENT ON COLUMN documents.author IS 'Автор документа';
COMMENT ON COLUMN documents.title IS 'Название документа';
COMMENT ON COLUMN documents.status IS 'Статус документа (DRAFT, SUBMITTED, APPROVED)';
COMMENT ON COLUMN documents.created_at IS 'Дата создания документа';
COMMENT ON COLUMN documents.updated_at IS 'Дата последнего обновления документа';

-- =====================================================
-- ТАБЛИЦА: document_history (История изменений статусов)
-- =====================================================
CREATE TABLE IF NOT EXISTS document_history (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    initiator VARCHAR(100) NOT NULL,
    action VARCHAR(20) NOT NULL,
    comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_document FOREIGN KEY (document_id)
        REFERENCES documents(id) ON DELETE CASCADE
);

-- Комментарии
COMMENT ON TABLE document_history IS 'История изменений статусов документов';
COMMENT ON COLUMN document_history.id IS 'Идентификатор записи истории';
COMMENT ON COLUMN document_history.document_id IS 'Ссылка на документ';
COMMENT ON COLUMN document_history.initiator IS 'Инициатор действия';
COMMENT ON COLUMN document_history.action IS 'Выполненное действие (SUBMIT, APPROVE)';
COMMENT ON COLUMN document_history.comment IS 'Комментарий к действию';
COMMENT ON COLUMN document_history.created_at IS 'Дата и время действия';

-- =====================================================
-- ТАБЛИЦА: approval_registry (Реестр утверждений)
-- =====================================================
CREATE TABLE IF NOT EXISTS approval_registry (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    approved_by VARCHAR(100) NOT NULL,
    approved_at TIMESTAMP NOT NULL,
    comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Комментарии
COMMENT ON TABLE approval_registry IS 'Реестр утвержденных документов';
COMMENT ON COLUMN approval_registry.id IS 'Идентификатор записи в реестре';
COMMENT ON COLUMN approval_registry.document_id IS 'Идентификатор утвержденного документа';
COMMENT ON COLUMN approval_registry.approved_by IS 'Кто утвердил документ';
COMMENT ON COLUMN approval_registry.approved_at IS 'Дата и время утверждения';
COMMENT ON COLUMN approval_registry.comment IS 'Комментарий к утверждению';
COMMENT ON COLUMN approval_registry.created_at IS 'Дата создания записи в реестре';

-- =====================================================
-- ИНДЕКСЫ ДЛЯ ОПТИМИЗАЦИИ ПРОИЗВОДИТЕЛЬНОСТИ
-- =====================================================

-- Индексы для таблицы documents
CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_documents_author ON documents(author);
CREATE INDEX idx_documents_created_at ON documents(created_at);
CREATE INDEX idx_documents_updated_at ON documents(updated_at);
CREATE INDEX idx_documents_document_number ON documents(document_number);

-- Составной индекс для частых запросов поиска
CREATE INDEX idx_documents_status_author_created ON documents(status, author, created_at);
CREATE INDEX idx_documents_status_author_updated ON documents(status, author, updated_at);

-- Индексы для таблицы document_history
CREATE INDEX idx_history_document_id ON document_history(document_id);
CREATE INDEX idx_history_created_at ON document_history(created_at);
CREATE INDEX idx_history_initiator ON document_history(initiator);
CREATE INDEX idx_history_action ON document_history(action);

-- Составной индекс для истории
CREATE INDEX idx_history_document_created ON document_history(document_id, created_at DESC);

-- Индексы для таблицы approval_registry
CREATE INDEX idx_registry_document_id ON approval_registry(document_id);
CREATE INDEX idx_registry_approved_at ON approval_registry(approved_at);
CREATE INDEX idx_registry_approved_by ON approval_registry(approved_by);

-- Уникальное ограничение для предотвращения дублирования утверждений
CREATE UNIQUE INDEX idx_registry_document_unique ON approval_registry(document_id);

-- =====================================================
-- ФУНКЦИИ И ТРИГГЕРЫ
-- =====================================================

-- Функция для автоматического обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Триггер для автоматического обновления updated_at в таблице documents
DROP TRIGGER IF EXISTS update_documents_updated_at ON documents;
CREATE TRIGGER update_documents_updated_at
    BEFORE UPDATE ON documents
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();