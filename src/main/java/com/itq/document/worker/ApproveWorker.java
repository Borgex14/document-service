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
 * Фоновый воркер для автоматического утверждения документов.
 * <p>
 * Данный компонент периодически сканирует базу данных в поисках документов,
 * находящихся на утверждении (статус {@link DocumentStatus#SUBMITTED}), и
 * автоматически утверждает их (переводит в статус {@link DocumentStatus#APPROVED}).
 * </p>
 *
 * <h2>Принцип работы:</h2>
 * <ol>
 *   <li>Запускается по расписанию с фиксированной задержкой</li>
 *   <li>Ищет документы в статусе SUBMITTED пачками (batchSize)</li>
 *   <li>Для каждой пачки вызывает {@link DocumentService#approveDocuments}</li>
 *   <li>Отслеживает различные типы ошибок (конфликты, ошибки регистрации)</li>
 *   <li>Логирует результаты обработки для мониторинга</li>
 * </ol>
 *
 * <h2>Особенности:</h2>
 * <ul>
 *   <li>Работает с пачками документов для оптимизации производительности</li>
 *   <li>Различает обычные ошибки и ошибки регистрации (REGISTRY_ERROR)</li>
 *   <li>Устойчив к ошибкам - при ошибке в одной пачке продолжает обработку следующих</li>
 *   <li>Детальное логирование для мониторинга и отладки</li>
 *   <li>Использует инициатор "APPROVE-WORKER" для отслеживания автоматических операций</li>
 * </ul>
 *
 * <h2>Логирование ошибок:</h2>
 * <ul>
 *   <li><b>CONFLICT</b> - документ не в статусе SUBMITTED (возможно уже утвержден)</li>
 *   <li><b>REGISTRY_ERROR</b> - проблема с записью в реестр утверждений</li>
 *   <li><b>NOT_FOUND</b> - документ не найден (возможно удален)</li>
 * </ul>
 *
 * @author Borgex Team
 * @version 1.0
 * @since 2026-02-20
 * @see DocumentService
 * @see DocumentRepository
 * @see SubmitWorker
 * @see OperationResult.OperationStatus#REGISTRY_ERROR
 */
@Slf4j
@Component
public class ApproveWorker {

    private final DocumentRepository documentRepository;
    private final DocumentService documentService;
    private final long fixedDelay;
    private final int batchSize;

    /**
     * Конструктор воркера для утверждения документов.
     *
     * @param documentRepository репозиторий для доступа к документам
     * @param documentService сервис для выполнения операций утверждения
     * @param fixedDelay задержка между запусками в миллисекундах
     * @param batchSize размер пачки документов для обработки за один раз
     */
    public ApproveWorker(
            DocumentRepository documentRepository,
            DocumentService documentService,
            long fixedDelay,
            int batchSize) {
        this.documentRepository = documentRepository;
        this.documentService = documentService;
        this.fixedDelay = fixedDelay;
        this.batchSize = batchSize;
        log.info("ApproveWorker initialized with fixedDelay={}, batchSize={}",
                fixedDelay, batchSize);
    }

    /**
     * Периодическая задача по утверждению документов.
     * <p>
     * Запускается с фиксированной задержкой, значение которой берется из конфигурации
     * {@code app.worker.approve.fixed-delay} (по умолчанию 60000 мс = 1 минута).
     * </p>
     *
     * <h3>Алгоритм работы:</h3>
     * <ol>
     *   <li>Засекает время начала выполнения для подсчета длительности</li>
     *   <li>В цикле получает пачки документов со статусом {@link DocumentStatus#SUBMITTED}</li>
     *   <li>Для каждой пачки вызывает {@link DocumentService#approveDocuments}</li>
     *   <li>Подсчитывает успешные операции и различные типы ошибок:
     *     <ul>
     *       <li>SUCCESS - документ успешно утвержден</li>
     *       <li>REGISTRY_ERROR - ошибка при записи в реестр</li>
     *       <li>CONFLICT/NOT_FOUND - прочие ошибки</li>
     *     </ul>
     *   </li>
     *   <li>Логирует предупреждения для документов с ошибками</li>
     *   <li>Завершается, когда документы SUBMITTED закончились или пачка меньше batchSize</li>
     *   <li>В конце логирует общую статистику выполнения</li>
     * </ol>
     *
     * <h3>Пример логов:</h3>
     * <pre>
     * INFO  - APPROVE-worker started. Checking for SUBMITTED documents to approve
     * INFO  - Processing batch of 50 SUBMITTED documents
     * INFO  - Batch results - Success: 48, Registry Errors: 2, Other: 0
     * WARN  - Document 123: Failed to register approval (REGISTRY_ERROR)
     * INFO  - No more SUBMITTED documents found
     * INFO  - APPROVE-worker completed. Processed: 150, Success: 145, Failed: 3, Registry Errors: 2, Duration: 1250 ms
     * </pre>
     *
     * @see #processSubmittedDocuments()
     * @see DocumentStatus#SUBMITTED
     * @see OperationResult.OperationStatus#SUCCESS
     * @see OperationResult.OperationStatus#REGISTRY_ERROR
     */
    @Scheduled(fixedDelayString = "${app.worker.approve.fixed-delay:60000}")
    public void processSubmittedDocuments() {
        log.info("APPROVE-worker started. Checking for SUBMITTED documents to approve");
        long startTime = System.currentTimeMillis();

        try {
            int totalProcessed = 0;
            int totalSuccess = 0;
            int totalFailed = 0;
            int totalRegistryErrors = 0;

            while (true) {
                // Find batch of SUBMITTED documents
                List<Document> submittedDocuments = documentRepository
                        .findByStatus(DocumentStatus.SUBMITTED, PageRequest.of(0, batchSize));

                if (submittedDocuments.isEmpty()) {
                    log.info("No more SUBMITTED documents found");
                    break;
                }

                List<Long> documentIds = submittedDocuments.stream()
                        .map(Document::getId)
                        .collect(Collectors.toList());

                log.info("Processing batch of {} SUBMITTED documents", documentIds.size());

                // Create batch request
                BatchOperationRequest request = new BatchOperationRequest();
                request.setIds(documentIds);
                request.setInitiator("APPROVE-WORKER");
                request.setComment("Auto-approved by background worker");

                // Approve documents via API
                List<OperationResult> results = documentService.approveDocuments(request);

                // Count results
                long successCount = results.stream()
                        .filter(r -> r.getStatus() == OperationResult.OperationStatus.SUCCESS)
                        .count();

                long registryErrorCount = results.stream()
                        .filter(r -> r.getStatus() == OperationResult.OperationStatus.REGISTRY_ERROR)
                        .count();

                totalProcessed += documentIds.size();
                totalSuccess += successCount;
                totalRegistryErrors += registryErrorCount;
                totalFailed += (documentIds.size() - successCount - registryErrorCount);

                log.info("Batch results - Success: {}, Registry Errors: {}, Other: {}",
                        successCount, registryErrorCount,
                        (documentIds.size() - successCount - registryErrorCount));

                // Log any errors for monitoring
                results.stream()
                        .filter(r -> r.getStatus() != OperationResult.OperationStatus.SUCCESS)
                        .forEach(r -> log.warn("Document {}: {} ({})",
                                r.getDocumentId(), r.getMessage(), r.getStatus()));

                // If we got fewer documents than batch size, we're done
                if (submittedDocuments.size() < batchSize) {
                    break;
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("APPROVE-worker completed. Processed: {}, Success: {}, Failed: {}, Registry Errors: {}, Duration: {} ms",
                    totalProcessed, totalSuccess, totalFailed, totalRegistryErrors, duration);

        } catch (Exception e) {
            log.error("APPROVE-worker encountered an error: {}", e.getMessage(), e);
        }
    }
}