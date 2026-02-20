package com.itq.generator;

import com.itq.document.dto.CreateDocumentRequest;
import com.itq.document.dto.DocumentDto;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.io.FileSystemResource;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Утилита для массовой генерации тестовых документов через API Document Service.
 * <p>
 * Данный класс предназначен для создания большого количества тестовых документов
 * путем вызова REST API сервиса документов. Позволяет быстро наполнить базу данных
 * тестовыми данными для нагрузочного тестирования и отладки.
 * </p>
 *
 * <h2>Формат конфигурационного файла (properties):</h2>
 * <pre>
 * # Количество документов для генерации
 * document.count=1000
 *
 * # Базовый URL API сервиса
 * api.url=http://localhost:8080
 *
 * # Автор создаваемых документов
 * author=Generator Bot
 * </pre>
 *
 * <h2>Использование:</h2>
 * <pre>
 * java -jar document-generator.jar config/config.properties
 * </pre>
 *
 * <h2>Пример вывода:</h2>
 * <pre>
 * Starting generation of 100 documents...
 * Progress: 10/100 documents created
 * Progress: 20/100 documents created
 * ...
 * Completed! Created 100 documents in 2345 ms
 * </pre>
 *
 * <h2>Особенности:</h2>
 * <ul>
 *   <li>Использует {@link RestTemplate} для вызова API</li>
 *   <li>Отображает прогресс каждые 10 созданных документов</li>
 *   <li>Измеряет и выводит общее время выполнения</li>
 *   <li>Корректно обрабатывает ошибки и завершается с кодом ошибки</li>
 * </ul>
 *
 * @author Borgex Team
 * @version 1.0
 * @since 2026-02-20
 * @see CreateDocumentRequest
 * @see DocumentDto
 * @see RestTemplate
 */
public class DocumentGenerator {

    /**
     * Точка входа в утилиту генерации документов.
     * <p>
     * Ожидает один аргумент командной строки - путь к файлу конфигурации в формате .properties.
     * </p>
     *
     * <h3>Алгоритм работы:</h3>
     * <ol>
     *   <li>Проверяет наличие аргумента командной строки</li>
     *   <li>Загружает конфигурацию из указанного файла</li>
     *   <li>Парсит параметры: количество документов, URL API, автора</li>
     *   <li>Создает указанное количество документов через POST /api/documents</li>
     *   <li>Отображает прогресс выполнения каждые 10 документов</li>
     *   <li>Выводит итоговую статистику и время выполнения</li>
     * </ol>
     *
     * @param args аргументы командной строки (ожидается путь к config.properties)
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar generator.jar <config.properties>");
            System.exit(1);
        }

        try {
            // Загрузка конфигурации
            Properties config = loadConfiguration(args[0]);

            // Парсинг параметров
            int count = parseIntParameter(config, "document.count", 100);
            String apiUrl = getRequiredParameter(config, "api.url");
            String author = getRequiredParameter(config, "author");

            // Генерация документов
            generateDocuments(apiUrl, author, count);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Загружает конфигурацию из файла.
     *
     * @param configPath путь к файлу конфигурации
     * @return объект {@link Properties} с загруженными настройками
     * @throws Exception если файл не найден или не может быть прочитан
     */
    private static Properties loadConfiguration(String configPath) throws Exception {
        Properties config = new Properties();
        config.load(new FileSystemResource(configPath).getInputStream());
        return config;
    }

    /**
     * Получает обязательный параметр из конфигурации.
     *
     * @param config объект с настройками
     * @param key ключ параметра
     * @return значение параметра
     * @throws IllegalArgumentException если параметр отсутствует
     */
    private static String getRequiredParameter(Properties config, String key) {
        String value = config.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return value;
    }

    /**
     * Парсит целочисленный параметр из конфигурации с значением по умолчанию.
     *
     * @param config объект с настройками
     * @param key ключ параметра
     * @param defaultValue значение по умолчанию
     * @return распарсенное значение или значение по умолчанию
     */
    private static int parseIntParameter(Properties config, String key, int defaultValue) {
        String value = config.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    /**
     * Генерирует документы через API.
     *
     * @param apiUrl базовый URL API
     * @param author автор документов
     * @param count количество документов для генерации
     */
    private static void generateDocuments(String apiUrl, String author, int count) {
        RestTemplate restTemplate = new RestTemplate();
        AtomicInteger progress = new AtomicInteger(0);

        System.out.println("Starting generation of " + count + " documents...");
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            CreateDocumentRequest request = createDocumentRequest(author, i + 1);

            try {
                restTemplate.postForObject(
                        apiUrl + "/api/documents",
                        request,
                        DocumentDto.class
                );
            } catch (Exception e) {
                System.err.printf("Failed to create document %d: %s%n", i + 1, e.getMessage());
                // Продолжаем создание следующих документов
            }

            int current = progress.incrementAndGet();
            if (current % 10 == 0 || current == count) {
                System.out.printf("Progress: %d/%d documents created%n", current, count);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.printf("Completed! Created %d documents in %d ms%n", count, duration);
    }

    /**
     * Создает запрос на создание документа.
     *
     * @param author автор документа
     * @param index порядковый номер документа
     * @return подготовленный запрос
     */
    private static CreateDocumentRequest createDocumentRequest(String author, int index) {
        CreateDocumentRequest request = new CreateDocumentRequest();
        request.setAuthor(author);
        request.setTitle("Generated Document " + index);
        return request;
    }
}