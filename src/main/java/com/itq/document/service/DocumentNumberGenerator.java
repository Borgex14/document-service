package com.itq.document.service;

import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Генератор уникальных номеров для документов.
 * <p>
 * Генерирует номера документов в формате: DOC-ГГГГММДД-XXXXXX
 * где:
 * <ul>
 *   <li>ГГГГММДД - текущая дата (год, месяц, день)</li>
 *   <li>XXXXXX - шестизначный порядковый номер (с ведущими нулями)</li>
 * </ul>
 * </p>
 * <p>
 * Пример: DOC-20260220-000001
 * </p>
 *
 * <p>
 * Генератор является потокобезопасным благодаря использованию {@link AtomicLong}.
 * Номера гарантированно уникальны в пределах одной JVM.
 * </p>
 *
 * @author Borgex Team
 * @version 1.0
 * @since 2026-02-20
 * @see AtomicLong
 * @see DateTimeFormatter
 */
@Component
public class DocumentNumberGenerator {

    /**
     * Счетчик для генерации порядковых номеров.
     * Используется AtomicLong для обеспечения потокобезопасности
     * при параллельных вызовах из нескольких потоков.
     */
    private final AtomicLong counter = new AtomicLong(1);

    /**
     * Форматтер для преобразования даты в строку вида ГГГГММДД.
     * Например: 2026-02-20 -> "20260220"
     */
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Генерирует уникальный номер документа.
     * <p>
     * Формат номера: DOC-ГГГГММДД-XXXXXX
     * </p>
     * <p>
     * Номер состоит из трех частей:
     * <ol>
     *   <li>Префикс "DOC" - идентификатор типа документа</li>
     *   <li>Дата в формате ГГГГММДД - позволяет группировать документы по дате</li>
     *   <li>Шестизначный порядковый номер с ведущими нулями</li>
     * </ol>
     * </p>
     *
     * @return уникальный номер документа в формате DOC-ГГГГММДД-XXXXXX
     *
     * @example
     * <pre>
     * DocumentNumberGenerator generator = new DocumentNumberGenerator();
     * String number1 = generator.generate(); // "DOC-20260220-000001"
     * String number2 = generator.generate(); // "DOC-20260220-000002"
     * </pre>
     */
    public String generate() {
        String date = LocalDateTime.now().format(formatter);
        long sequence = counter.getAndIncrement();
        return String.format("DOC-%s-%06d", date, sequence);
    }
}