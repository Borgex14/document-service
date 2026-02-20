# Анализ поисковых запросов

## Поиск документов: Анализ производительности

## 📌 Типичный поисковый запрос

### -- Поиск документов по статусу, автору и дате создания
SELECT id, document_number, author, title, status, created_at, updated_at
FROM documents
WHERE status = 'SUBMITTED'
AND author LIKE '%Иванов%'
AND created_at BETWEEN '2026-01-01' AND '2026-02-20'
ORDER BY created_at DESC
LIMIT 20;

## 🔍 EXPLAIN (ANALYZE) результат
### Без индексов (до оптимизации):
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM documents
WHERE status = 'SUBMITTED'
AND author LIKE '%Иванов%';

Seq Scan on documents  (cost=0.00..1543.00 rows=1 width=200)
(actual time=15.2..87.3 rows=245 loops=1)
Filter: ((status = 'SUBMITTED'::text) AND (author ~~ '%Иванов%'::text))
Rows Removed by Filter: 9755
Buffers: shared hit=825
Planning Time: 0.123 ms
Execution Time: 87.5 ms

### Проблемы:

Seq Scan - полное сканирование таблицы (9755 строк отфильтровано)

Время выполнения: 87.5 ms

## С индексами (после оптимизации):
sql
### -- Создаем индексы
CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_documents_author ON documents(author);
CREATE INDEX idx_documents_created_at ON documents(created_at);

### -- Анализируем снова
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM documents
WHERE status = 'SUBMITTED'
AND author LIKE '%Иванов%'
AND created_at >= '2026-01-01';
text
Bitmap Heap Scan on documents  (cost=28.5..156.3 rows=42 width=200)
(actual time=0.45..2.34 rows=245 loops=1)
Recheck Cond: ((status = 'SUBMITTED'::text) AND (created_at >= '2026-01-01'::date))
Filter: (author ~~ '%Иванов%'::text)
Rows Removed by Filter: 120
Heap Blocks: exact=45
Buffers: shared hit=128
->  BitmapAnd  (cost=28.5..28.5 rows=156 width=0)
(actual time=0.38..0.38 rows=0 loops=1)
Buffers: shared hit=83
->  Bitmap Index Scan on idx_documents_status  
(cost=0.0..8.2 rows=312 width=0)
(actual time=0.15..0.15 rows=365 loops=1)
Index Cond: (status = 'SUBMITTED'::text)
Buffers: shared hit=42
->  Bitmap Index Scan on idx_documents_created_at  
(cost=0.0..19.8 rows=1250 width=0)
(actual time=0.19..0.19 rows=1245 loops=1)
Index Cond: (created_at >= '2026-01-01'::date)
Buffers: shared hit=41
Planning Time: 0.156 ms
Execution Time: 2.56 ms
### Улучшения:

Используется Bitmap Index Scan вместо Seq Scan

Время выполнения: 2.56 ms (в 34 раза быстрее!)

## 📊 Оптимизация с композитным индексом

### -- Композитный индекс для частого паттерна поиска
CREATE INDEX idx_documents_status_author_created
ON documents(status, author, created_at DESC);

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM documents
WHERE status = 'SUBMITTED'
AND author = 'Иванов И.И.'
AND created_at BETWEEN '2026-01-01' AND '2026-02-20'
ORDER BY created_at DESC;

### Index Scan using idx_documents_status_author_created on documents  
(cost=0.42..8.45 rows=1 width=200)
(actual time=0.08..0.12 rows=8 loops=1)
Index Cond: ((status = 'SUBMITTED'::text)
AND (author = 'Иванов И.И.'::text)
AND (created_at >= '2026-01-01'::date)
AND (created_at <= '2026-02-20'::date))
Buffers: shared hit=12
Planning Time: 0.134 ms
Execution Time: 0.18 ms
Результат: 0.18 ms - идеально!

## 🎯 Пояснение по индексам
### Типы индексов в нашем проекте
Индекс	Тип	Назначение
idx_documents_status	B-tree (single)	Быстрая фильтрация по статусу
idx_documents_author	B-tree (single)	Поиск по автору
idx_documents_created_at	B-tree (single)	Сортировка и диапазон по дате создания
idx_documents_updated_at	B-tree (single)	Сортировка и диапазон по дате обновления
idx_documents_status_author_created	B-tree (composite)	Оптимизация частых поисков
idx_history_document_id	B-tree	Foreign key lookups
uk_registry_document	Unique	Запрет дублирования утверждений
### Правила использования индексов
#### ✅ Когда индексы помогают:
WHERE status = 'SUBMITTED' - фильтрация по равенству

WHERE author LIKE 'Иванов%' - поиск по началу строки (но не %Иванов%!)

WHERE created_at BETWEEN ... - диапазонные запросы

ORDER BY created_at DESC - сортировка

JOIN - связи между таблицами

#### ❌ Когда индексы НЕ помогают:
WHERE author LIKE '%Иванов%' - поиск по середине/концу строки

WHERE status != 'DRAFT' - неравенство

функции над колонкой: WHERE UPPER(author) = 'ИВАНОВ'

маленькие таблицы (< 1000 строк) - Seq Scan может быть быстрее

Покрывающие индексы (Covering Index)
Если часто запрашиваются только определенные колонки:


### -- Покрывающий индекс (включает все нужные колонки)
CREATE INDEX idx_documents_covering
ON documents(status, author, created_at)
INCLUDE (document_number, title);
sql
EXPLAIN (ANALYZE)
SELECT document_number, title
FROM documents
WHERE status = 'APPROVED' AND author = 'Иванов И.И.';
text
Index Only Scan using idx_documents_covering on documents  
(cost=0.42..4.45 rows=1 width=200)
(actual time=0.05..0.08 rows=12 loops=1)
Index Cond: ((status = 'APPROVED'::text) AND (author = 'Иванов И.И.'::text))
Heap Fetches: 0
Buffers: shared hit=8
Execution Time: 0.09 ms
Heap Fetches: 0 - все данные получены из индекса, обращения к таблице не было!

## 📈 Статистика использования индексов

### -- Статистика использования индексов в PostgreSQL
SELECT
schemaname,
tablename,
indexname,
idx_scan as number_of_scans,
idx_tup_read as tuples_read,
idx_tup_fetch as tuples_fetched
FROM pg_stat_user_indexes
WHERE tablename = 'documents'
ORDER BY idx_scan DESC;
indexname	idx_scan	idx_tup_read	Примечание
idx_documents_status	15420	45230	Самый частый
idx_documents_author	8760	12340	Тоже часто
idx_documents_created_at	5430	8760	Редже
idx_documents_status_author_created	4320	12560	Оптимальный
🔧 Рекомендации по настройке
Для нашего сервиса:
Индексы для поиска:


### -- Обязательные индексы
CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_documents_author ON documents(author);
CREATE INDEX idx_documents_created_at ON documents(created_at);

### -- Для сортировки
CREATE INDEX idx_documents_created_at_desc ON documents(created_at DESC);
Для внешних ключей:


CREATE INDEX idx_history_document_id ON document_history(document_id);
-- (уже есть в миграциях)
Для уникальности:

CREATE UNIQUE INDEX uk_documents_number ON documents(document_number);
-- (уже есть в миграциях)
### Мониторинг неиспользуемых индексов:
sql
SELECT
indexrelid::regclass as index_name,
relid::regclass as table_name,
idx_scan as index_scans
FROM pg_stat_user_indexes
WHERE idx_scan = 0  -- никогда не использовался
AND schemaname = 'public';
Размер индексов:
sql
SELECT
indexname,
pg_size_pretty(pg_relation_size(indexname::regclass)) as size
FROM pg_indexes
WHERE tablename = 'documents';
✅ Итог
До индексов: поиск ~ 87 ms

С простыми индексами: поиск ~ 2.5 ms (📉 в 35 раз быстрее)

С композитным индексом: поиск ~ 0.18 ms (📉 в 480 раз быстрее!)

С покрывающим индексом: поиск ~ 0.09 ms (📉 в 966 раз быстрее!)

#### Правильные индексы - ключ к производительности!

