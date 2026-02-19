# Этап 1: сборка приложения
FROM maven:3.8.4-openjdk-17 AS builder

WORKDIR /app

# Копируем файлы для сборки
COPY pom.xml .
COPY src ./src

# Собираем приложение
RUN mvn clean package -DskipTests

# Этап 2: создание образа для запуска
FROM openjdk:17-slim

WORKDIR /app

# Копируем собранный jar из первого этапа
COPY --from=builder /app/target/*.jar app.jar

# Порт, на котором работает приложение
EXPOSE 8080

# Команда для запуска
ENTRYPOINT ["java", "-jar", "app.jar"]