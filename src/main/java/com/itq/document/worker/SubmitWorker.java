package com.itq.document.worker;

import com.itq.document.dto.BatchOperationRequest;
import com.itq.document.dto.OperationResult;
import com.itq.document.model.Document;
import com.itq.document.model.DocumentStatus;
import com.itq.document.repository.DocumentRepository;
import com.itq.document.service.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Фоновый воркер для автоматической отправки документов на утверждение.
 * <p>
 * Данный компонент периодически сканирует базу данных в поисках документов
 * со статусом {@link DocumentStatus#DRAFT} и автоматически отправляет их
 * на утверждение (переводит в статус {@link DocumentStatus#SUBMITTED}).
 * </p>
 *
 * <h2>Принцип работы:</h2>
 * <ol>
 *   <li>Запускается по расписанию с фиксированной задержкой</li>
 *   <li>Ищет документы в статусе DRAFT пачками (batchSize)</li>
 *   <li>Для каждой пачки вызывает {@link DocumentService#submitDocuments}</li>
 *   <li>Логирует результаты обработки</li>
 *   <li>Продолжает обработку пока есть документы DRAFT</li>
 * </ol>
 *
 * <h2>Особенности:</h2>
 * <ul>
 *   <li>Работает с пачками документов для оптимизации производительности</li>
 *   <li>Устойчив к ошибкам - при ошибке в одной пачке продолжает обработку следующих</li>
 *   <li>Детальное логирование для мониторинга</li>
 *   <li>Использует инициатор "SUBMIT-WORKER" для отслеживания автоматических операций</li>
 * </ul>
 *
 * @author Borgex Team
 * @version 1.0
 * @since 2026-02-20
 * @see DocumentService
 * @see DocumentRepository
 * @see ApproveWorker
 */
@Slf4j
@Component
public class SubmitWorker {

    private final DocumentRepository documentRepository;
    private final DocumentService documentService;
    private final long fixedDelay;
    private final int batchSize;

    /**
     * Конструктор воркера для отправки документов.
     *
     * @param documentRepository репозиторий для доступа к документам
     * @param documentService сервис для выполнения операций отправки
     * @param fixedDelay задержка между запусками в миллисекундах
     * @param batchSize размер пачки документов для обработки за один раз
     */
    public SubmitWorker(
            DocumentRepository documentRepository,
            DocumentService documentService,
            long fixedDelay,
            int batchSize) {
        this.documentRepository = documentRepository;
        this.documentService = documentService;
        this.fixedDelay = fixedDelay;
        this.batchSize = batchSize;
        log.info("SubmitWorker initialized with fixedDelay={}, batchSize={}",
                fixedDelay, batchSize);
    }

    /**
     * Периодическая задача по отправке черновиков на утверждение.
     * <p>
     * Запускается с фиксированной задержкой, значение которой берется из конфигурации
     * {@code app.worker.submit.fixed-delay} (по умолчанию 60000 мс = 1 минута).
     * </p>
     *
     * <h3>Алгоритм работы:</h3>
     * <ol>
     *   <li>Засекает время начала выполнения</li>
     *   <li>В цикле получает пачки документов со статусом DRAFT</li>
     *   <li>Для каждой пачки вызывает {@link DocumentService#submitDocuments}</li>
     *   <li>Подсчитывает успешные и неуспешные операции</li>
     *   <li>Логирует предупреждения для документов с ошибками</li>
     *   <li>Завершается, когда документы DRAFT закончились или пачка меньше batchSize</li>
     *   <li>В конце логирует общую статистику выполнения</li>
     * </ol>
     *
     * <h3>Логирование:</h3>
     * <ul>
     *   <li>INFO - начало и завершение работы, результаты по пачкам</li>
     *   <li>WARN - документы, которые не удалось обработать</li>
     *   <li>ERROR - критические ошибки в работе воркера</li>
     * </ul>
     *
     * @see #processDraftDocuments()
     * @see DocumentStatus#DRAFT
     * @see OperationResult.OperationStatus#SUCCESS
     */
    @Scheduled(fixedDelayString = "${app.worker.submit.fixed-delay:60000}")
    public void processDraftDocuments() {
        log.info("SUBMIT-worker started. Checking for DRAFT documents to submit");
        long startTime = System.currentTimeMillis();

        try {
            int totalProcessed = 0;
            int totalSuccess = 0;
            int totalFailed = 0;

            while (true) {
                // Find batch of DRAFT documents
                List<Document> draftDocuments = documentRepository
                        .findByStatus(DocumentStatus.DRAFT, PageRequest.of(0, batchSize));

                if (draftDocuments.isEmpty()) {
                    log.info("No more DRAFT documents found");
                    break;
                }

                List<Long> documentIds = draftDocuments.stream()
                        .map(Document::getId)
                        .collect(Collectors.toList());

                log.info("Processing batch of {} DRAFT documents", documentIds.size());

                // Create batch request
                BatchOperationRequest request = new BatchOperationRequest();
                request.setIds(documentIds);
                request.setInitiator("SUBMIT-WORKER");
                request.setComment("Auto-submitted by background worker");

                // Submit documents via API
                List<OperationResult> results = documentService.submitDocuments(request);

                // Count results
                long successCount = results.stream()
                        .filter(r -> r.getStatus() == OperationResult.OperationStatus.SUCCESS)
                        .count();

                totalProcessed += documentIds.size();
                totalSuccess += successCount;
                totalFailed += (documentIds.size() - successCount);

                log.info("Batch results - Success: {}, Failed: {}",
                        successCount, (documentIds.size() - successCount));

                // Log any errors for monitoring
                results.stream()
                        .filter(r -> r.getStatus() != OperationResult.OperationStatus.SUCCESS)
                        .forEach(r -> log.warn("Document {}: {}",
                                r.getDocumentId(), r.getMessage()));

                // If we got fewer documents than batch size, we're done
                if (draftDocuments.size() < batchSize) {
                    break;
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("SUBMIT-worker completed. Processed: {}, Success: {}, Failed: {}, Duration: {} ms",
                    totalProcessed, totalSuccess, totalFailed, duration);

        } catch (Exception e) {
            log.error("SUBMIT-worker encountered an error: {}", e.getMessage(), e);
        }
    }
}