# Document Service

Backend service for document management with status workflow and history tracking.

## Tech Stack
- Java 17
- Spring Boot 3.1.x
- PostgreSQL 15
- Liquibase
- Maven

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17
- Maven

## Build and run the service
./mvnw clean package
java -jar target/document-service-1.0.0.jar

## API Examples
curl -X POST http://localhost:8080/api/documents \
-H "Content-Type: application/json" \
-d '{"author": "john.doe", "title": "Test Document"}'

## Submit documents
curl -X POST http://localhost:8080/api/documents/submit \
-H "Content-Type: application/json" \
-d '{"ids": [1, 2, 3], "initiator": "john.doe"}'

## Run Document Generator
java -jar utility/target/generator.jar config.properties

## Configuration
app.batch.submit-size - batch size for submit worker

app.batch.approve-size - batch size for approve worker

Worker intervals



### Run with Docker Compose

1. Start PostgreSQL:
```bash
cd docker
docker-compose up -d

