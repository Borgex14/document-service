# Document Service
## Сервис для управления документами с поддержкой жизненного цикла (DRAFT → SUBMITTED → APPROVED) и история изменений.

## 📋 Содержание
### Требования

### Быстрый старт

### Запуск с Docker

### Утилита генерации документов

### API Endpoints

### Мониторинг и логи

### Тестирование

## 🔧 Требования
### Java 17+

### Maven 3.8+

### Docker

### PostgreSQL 15+ (для продакшена)

## 🚀 Быстрый старт
### 1. Клонирование и сборка
   bash
   git clone <repository-url>
   cd document-service
   ./mvnw clean package -DskipTests
### 2. Запуск с H2 (для разработки)
bash
#### Linux/Mac
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

#### Windows
mvnw spring-boot:run -Dspring-boot.run.profiles=dev
Приложение будет доступно: http://localhost:8080/api/documents/1

### 3. H2 Console
   URL: http://localhost:8080/h2-console

JDBC URL: jdbc:h2:mem:documentdb

User: sa

Password: (пусто)

## 🐳 Запуск с Docker
Запуск с PostgreSQL (prod профиль)
bash
### Собрать образ
docker build -t document-service .

### Запустить с PostgreSQL
docker-compose up -d

### Проверить статус
docker-compose ps
docker-compose logs -f
Остановка
bash
docker-compose down
### Полная очистка (удалить volumes)
docker-compose down -v
## 🛠 Утилита генерации документов
#### Утилита для массовой генерации тестовых документов.
### 1. Сборка утилиты
   bash
   cd utility
   ../mvnw clean package assembly:single
### Количество документов для генерации
document.count=100

## URL API сервиса
### api.url=http://localhost:8080

## Автор документов
### author=Generator Bot

### Задержка между запросами (мс)
delay.ms=10
### 3. Запуск генерации
   bash
### Из папки utility
java -jar target/document-generator-1.0.0-jar-with-dependencies.jar config/config.properties
Пример вывода:
text
Starting generation of 100 documents...
Progress: 10/100 documents created
Progress: 20/100 documents created
...
Completed! Created 100 documents in 2345 ms
## 📊 Мониторинг и логи
### Логи приложения
Логи сохраняются в logs/ директории:

application.log - основные логи

error.log - только ошибки

worker.log - логи воркеров

api.log - HTTP запросы

application.json - структурированные логи в JSON

Просмотр логов в реальном времени
bash
### Все логи
tail -f logs/application.log

### Только ошибки
tail -f logs/error.log

### Логи воркеров
tail -f logs/worker.log

### В Docker
docker logs -f document-app
Проверка прогресса воркеров
Воркеры автоматически обрабатывают документы каждые 10 секунд (в dev режиме):

text
[DEV] 15:45:23 - SubmitWorker started. Checking for DRAFT documents to submit
[DEV] 15:45:23 - Found 5 DRAFT documents to process
[DEV] 15:45:23 - Processed document 1: DRAFT -> SUBMITTED
[DEV] 15:45:23 - Processed document 2: DRAFT -> SUBMITTED
[DEV] 15:45:23 - SubmitWorker completed. Processed: 5, Success: 5, Failed: 0

[DEV] 15:45:23 - ApproveWorker started. Checking for SUBMITTED documents to approve
[DEV] 15:45:23 - Found 3 SUBMITTED documents to process
[DEV] 15:45:23 - Processed document 3: SUBMITTED -> APPROVED
[DEV] 15:45:23 - ApproveWorker completed. Processed: 3, Success: 3, Failed: 0

## 📚 API Endpoints
### Документы
#### Метод	Endpoint	Описание
#### POST	/api/documents	Создать документ
#### GET	/api/documents/{id}	Получить документ с историей
#### POST	/api/documents/batch	Получить несколько документов
#### GET	/api/documents/search	Поиск документов

### Workflow
#### Метод	Endpoint	Описание
#### POST	/api/documents/submit	Отправить на утверждение
#### POST	/api/documents/approve	Утвердить документы
#### POST	/api/documents/concurrency-test	Тест конкурентного доступа
### Примеры запросов
#### Создать документ
bash
curl -X POST http://localhost:8080/api/documents \
-H "Content-Type: application/json" \
-d '{
"author": "Иванов Иван",
"title": "Тестовый документ"
}'
#### Отправить на утверждение
bash
curl -X POST http://localhost:8080/api/documents/submit \
-H "Content-Type: application/json" \
-d '{
"ids": [1],
"initiator": "Иванов Иван"
}'
#### Утвердить документ
bash
curl -X POST http://localhost:8080/api/documents/approve \
-H "Content-Type: application/json" \
-d '{
"ids": [1],
"initiator": "Петров Петр"
}'
#### Поиск документов
bash
##### По статусу
curl "http://localhost:8080/api/documents/search?status=APPROVED"

##### По автору
curl "http://localhost:8080/api/documents/search?author=Иванов"

##### По дате
curl "http://localhost:8080/api/documents/search?dateFrom=2026-02-01T00:00:00&dateTo=2026-02-20T23:59:59"

## 🧪 Тестирование
### Запуск всех тестов
bash
./mvnw test
### Запуск конкретного теста
bash
./mvnw test -Dtest=DocumentServiceTest
./mvnw test -Dtest=DocumentServiceImplTest
./mvnw test -Dtest=DocumentControllerTest
### Покрытие тестами
bash
./mvnw verify
# Отчет: target/site/jacoco/index.html

## 📁 Структура проекта
text
document-service/
├── src/
│   ├── main/
│   │   ├── java/com/itq/document/
│   │   │   ├── controller/     # REST API
│   │   │   ├── service/        # Бизнес-логика
│   │   │   ├── repository/     # JPA репозитории
│   │   │   ├── model/          # Сущности
│   │   │   ├── dto/            # Data Transfer Objects
│   │   │   ├── config/         # Конфигурации
│   │   │   ├── exception/      # Обработка ошибок
│   │   │   └── worker/         # Фоновые задачи
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       ├── db/changelog/   # Liquibase миграции
│   │       └── logback.xml     # Настройки логирования
│   └── test/                    # Тесты
├── docker/
│   └── docker-compose.yml       # Docker Compose
├── utility/                      # Утилита генерации
│   └── src/...
├── logs/                         # Логи (создаётся автоматически)
├── pom.xml
└── README.md

## 🔍 Отладка
Включить debug логи
В application-dev.yml:

yaml
logging:
level:
com.itq.document: DEBUG
org.springframework.web: DEBUG
org.hibernate.SQL: DEBUG
Проверка статуса воркеров
bash
### Посмотреть активные потоки
jstack <pid> | grep worker

### В Docker
docker exec -it document-app jstack 1 | grep worker
Профили
dev - H2 in-memory, подробные логи

prod - PostgreSQL, минимальное логирование

## 📝 Лицензия
Copyright © 2026 Borgex Team

