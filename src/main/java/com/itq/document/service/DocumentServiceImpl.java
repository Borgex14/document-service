package com.itq.document.service;

import com.itq.document.dto.*;
import com.itq.document.exception.*;
import com.itq.document.model.*;
import com.itq.document.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Реализация сервиса для управления документами.
 * Предоставляет функциональность для создания, отправки на утверждение,
 * утверждения и поиска документов с поддержкой истории изменений.
 *
 * @author Borgex Team
 * @version 1.0
 * @since 2026-02-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final HistoryRepository historyRepository;
    private final RegistryRepository registryRepository;
    private final DocumentNumberGenerator numberGenerator;

    /**
     * {@inheritDoc}
     * <p>
     * Создает новый документ со статусом DRAFT.
     * Генерирует уникальный номер документа через {@link DocumentNumberGenerator}.
     * </p>
     *
     * @param request запрос на создание документа, содержащий автора и название
     * @return DTO созданного документа
     * @throws IllegalArgumentException если запрос некорректен
     */
    @Override
    @Transactional
    public DocumentDto createDocument(CreateDocumentRequest request) {
        log.info("Creating new document by author: {}", request.getAuthor());

        Document document = new Document();
        document.setDocumentNumber(numberGenerator.generate());
        document.setAuthor(request.getAuthor());
        document.setTitle(request.getTitle());
        document.setStatus(DocumentStatus.DRAFT);

        Document savedDocument = documentRepository.save(document);
        log.info("Document created with id: {}, number: {}",
                savedDocument.getId(), savedDocument.getDocumentNumber());

        return mapToDto(savedDocument);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Возвращает документ по его идентификатору вместе с полной историей изменений.
     * История сортируется по дате создания в обратном порядке (сначала новые).
     * </p>
     *
     * @param id идентификатор документа
     * @return DTO документа с историей
     * @throws DocumentNotFoundException если документ с указанным id не найден
     */
    @Override
    @Transactional(readOnly = true)
    public DocumentWithHistoryDto getDocumentWithHistory(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));

        List<History> history = historyRepository.findByDocumentIdOrderByCreatedAtDesc(id);

        return DocumentWithHistoryDto.builder()
                .document(mapToDto(document))
                .history(history.stream().map(this::mapToHistoryDto).collect(Collectors.toList()))
                .build();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Возвращает список документов по переданным идентификаторам.
     * Порядок элементов в результате не гарантируется.
     * </p>
     *
     * @param ids список идентификаторов документов
     * @param pageable параметры пагинации
     * @return список DTO документов
     */
    @Override
    @Transactional(readOnly = true)
    public List<DocumentDto> getDocumentsBatch(List<Long> ids, Pageable pageable) {
        return documentRepository.findAllById(ids).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Обрабатывает каждый документ в пакете независимо.
     * Для успешной обработки документ должен находиться в статусе DRAFT.
     * После успешной отправки:
     * <ul>
     *   <li>Статус документа меняется на SUBMITTED</li>
     *   <li>Создается запись в истории с действием SUBMIT</li>
     * </ul>
     * </p>
     *
     * @param request запрос на отправку документов
     * @return список результатов операции для каждого документа
     */
    @Override
    @Transactional
    public List<OperationResult> submitDocuments(BatchOperationRequest request) {
        log.info("Processing submit batch for {} documents", request.getIds().size());
        long startTime = System.currentTimeMillis();

        List<OperationResult> results = new ArrayList<>();

        for (Long documentId : request.getIds()) {
            try {
                Document document = documentRepository.findById(documentId)
                        .orElseThrow(() -> new DocumentNotFoundException(documentId));

                if (document.getStatus() != DocumentStatus.DRAFT) {
                    results.add(OperationResult.builder()
                            .documentId(documentId)
                            .status(OperationResult.OperationStatus.CONFLICT)
                            .message(String.format("Document is in %s status, expected DRAFT",
                                    document.getStatus()))
                            .build());
                    continue;
                }

                // Update status
                document.setStatus(DocumentStatus.SUBMITTED);
                documentRepository.save(document);

                // Create history entry
                History history = new History();
                history.setDocument(document);
                history.setInitiator(request.getInitiator());
                history.setAction(DocumentAction.SUBMIT);
                history.setComment(request.getComment());
                historyRepository.save(history);

                results.add(OperationResult.builder()
                        .documentId(documentId)
                        .status(OperationResult.OperationStatus.SUCCESS)
                        .message("Document submitted successfully")
                        .build());

            } catch (DocumentNotFoundException e) {
                results.add(OperationResult.builder()
                        .documentId(documentId)
                        .status(OperationResult.OperationStatus.NOT_FOUND)
                        .message("Document not found")
                        .build());
            } catch (Exception e) {
                log.error("Error submitting document {}: {}", documentId, e.getMessage());
                results.add(OperationResult.builder()
                        .documentId(documentId)
                        .status(OperationResult.OperationStatus.CONFLICT)
                        .message("Error processing document: " + e.getMessage())
                        .build());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Submit batch completed in {} ms", duration);

        return results;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Обрабатывает каждый документ в пакете независимо.
     * Для успешного утверждения:
     * <ol>
     *   <li>Документ должен находиться в статусе SUBMITTED</li>
     *   <li>Сначала создается запись в реестре утверждений</li>
     *   <li>При успехе статус документа меняется на APPROVED</li>
     *   <li>Создается запись в истории с действием APPROVE</li>
     * </ol>
     * Если запись в реестре не удалась, статус документа не меняется.
     * </p>
     *
     * @param request запрос на утверждение документов
     * @return список результатов операции для каждого документа
     */
    @Override
    @Transactional
    public List<OperationResult> approveDocuments(BatchOperationRequest request) {
        log.info("Processing approve batch for {} documents", request.getIds().size());
        long startTime = System.currentTimeMillis();

        List<OperationResult> results = new ArrayList<>();

        for (Long documentId : request.getIds()) {
            try {
                Document document = documentRepository.findById(documentId)
                        .orElseThrow(() -> new DocumentNotFoundException(documentId));

                if (document.getStatus() != DocumentStatus.SUBMITTED) {
                    results.add(OperationResult.builder()
                            .documentId(documentId)
                            .status(OperationResult.OperationStatus.CONFLICT)
                            .message(String.format("Document is in %s status, expected SUBMITTED",
                                    document.getStatus()))
                            .build());
                    continue;
                }

                // Create registry entry first
                try {
                    RegistryEntry registryEntry = new RegistryEntry();
                    registryEntry.setDocumentId(documentId);
                    registryEntry.setApprovedBy(request.getInitiator());
                    registryEntry.setApprovedAt(LocalDateTime.now());
                    registryRepository.save(registryEntry);
                } catch (Exception e) {
                    log.error("Failed to create registry entry for document {}: {}",
                            documentId, e.getMessage());
                    results.add(OperationResult.builder()
                            .documentId(documentId)
                            .status(OperationResult.OperationStatus.REGISTRY_ERROR)
                            .message("Failed to register approval: " + e.getMessage())
                            .build());
                    continue;
                }

                // Update document status
                document.setStatus(DocumentStatus.APPROVED);
                documentRepository.save(document);

                // Create history entry
                History history = new History();
                history.setDocument(document);
                history.setInitiator(request.getInitiator());
                history.setAction(DocumentAction.APPROVE);
                history.setComment(request.getComment());
                historyRepository.save(history);

                results.add(OperationResult.builder()
                        .documentId(documentId)
                        .status(OperationResult.OperationStatus.SUCCESS)
                        .message("Document approved successfully")
                        .build());

            } catch (DocumentNotFoundException e) {
                results.add(OperationResult.builder()
                        .documentId(documentId)
                        .status(OperationResult.OperationStatus.NOT_FOUND)
                        .message("Document not found")
                        .build());
            } catch (Exception e) {
                log.error("Error approving document {}: {}", documentId, e.getMessage());
                results.add(OperationResult.builder()
                        .documentId(documentId)
                        .status(OperationResult.OperationStatus.CONFLICT)
                        .message("Error processing document: " + e.getMessage())
                        .build());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Approve batch completed in {} ms", duration);

        return results;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Выполняет поиск документов по заданным критериям.
     * Поддерживает фильтрацию по:
     * <ul>
     *   <li>Статусу документа</li>
     *   <li>Автору (частичное совпадение)</li>
     *   <li>Диапазону дат создания или обновления</li>
     * </ul>
     * Результаты возвращаются с пагинацией.
     * </p>
     *
     * @param criteria критерии поиска
     * @param pageable параметры пагинации
     * @return страница с документами, соответствующими критериям
     */
    @Override
    @Transactional(readOnly = true)
    public Page<DocumentDto> searchDocuments(DocumentSearchCriteria criteria, Pageable pageable) {
        log.info("Searching documents with criteria: {}", criteria);

        Page<Document> documents;

        if (criteria.isSearchByCreatedAt()) {
            documents = documentRepository.searchByCreatedAt(
                    criteria.getStatus(),
                    criteria.getAuthor(),
                    criteria.getDateFrom(),
                    criteria.getDateTo(),
                    pageable
            );
        } else {
            documents = documentRepository.searchByUpdatedAt(
                    criteria.getStatus(),
                    criteria.getAuthor(),
                    criteria.getDateFrom(),
                    criteria.getDateTo(),
                    pageable
            );
        }

        return documents.map(this::mapToDto);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Выполняет тестирование конкурентного доступа к документу.
     * Создает указанное количество потоков, которые одновременно пытаются
     * утвердить один и тот же документ. Тест позволяет проверить корректность
     * работы механизмов блокировок и транзакций.
     * </p>
     * <p>
     * Документ перед тестом сбрасывается в статус SUBMITTED, если он был APPROVED.
     * </p>
     *
     * @param request параметры теста (id документа, количество потоков и попыток)
     * @return результаты теста: количество успешных, конфликтных и ошибочных попыток
     * @throws DocumentNotFoundException если документ не найден
     */
    @Override
    @Transactional
    public ConcurrencyTestResult testConcurrentApproval(ConcurrencyTestRequest request) {
        log.info("Starting concurrency test for document {} with {} threads and {} attempts",
                request.getDocumentId(), request.getThreads(), request.getAttempts());

        Document document = documentRepository.findById(request.getDocumentId())
                .orElseThrow(() -> new DocumentNotFoundException(request.getDocumentId()));

        // Reset document to SUBMITTED state for testing
        if (document.getStatus() == DocumentStatus.APPROVED) {
            document.setStatus(DocumentStatus.SUBMITTED);
            documentRepository.save(document);
        }

        ExecutorService executor = Executors.newFixedThreadPool(request.getThreads());
        CountDownLatch latch = new CountDownLatch(request.getAttempts());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < request.getAttempts(); i++) {
            executor.submit(() -> {
                try {
                    BatchOperationRequest approveRequest = new BatchOperationRequest();
                    approveRequest.setIds(List.of(request.getDocumentId()));
                    approveRequest.setInitiator(request.getInitiator());
                    approveRequest.setComment("Concurrency test attempt");

                    List<OperationResult> operationResults = approveDocuments(approveRequest);
                    OperationResult result = operationResults.get(0);

                    switch (result.getStatus()) {
                        case SUCCESS:
                            successCount.incrementAndGet();
                            break;
                        case CONFLICT:
                        case REGISTRY_ERROR:
                            conflictCount.incrementAndGet();
                            break;
                        default:
                            errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    log.error("Error in concurrency test thread: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Concurrency test interrupted");
        }

        executor.shutdown();

        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }

        Document finalDocument = documentRepository.findById(request.getDocumentId())
                .orElse(document);
        long registryCount = registryRepository.countByDocumentId(request.getDocumentId());

        log.info("Concurrency test completed. Success: {}, Conflict: {}, Error: {}, Final status: {}",
                successCount.get(), conflictCount.get(), errorCount.get(), finalDocument.getStatus());

        return ConcurrencyTestResult.builder()
                .documentId(request.getDocumentId())
                .totalAttempts(request.getAttempts())
                .successfulAttempts(successCount.get())
                .conflictAttempts(conflictCount.get())
                .errorAttempts(errorCount.get())
                .finalStatus(finalDocument.getStatus().name())
                .registryEntriesCount(registryCount)
                .build();
    }

    /**
     * Преобразует сущность Document в DTO.
     *
     * @param document сущность документа
     * @return DTO документа
     */
    private DocumentDto mapToDto(Document document) {
        return DocumentDto.builder()
                .id(document.getId())
                .documentNumber(document.getDocumentNumber())
                .author(document.getAuthor())
                .title(document.getTitle())
                .status(document.getStatus().name())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }

    /**
     * Преобразует сущность History в DTO.
     *
     * @param history сущность истории
     * @return DTO истории
     */
    private HistoryDto mapToHistoryDto(History history) {
        return HistoryDto.builder()
                .id(history.getId())
                .initiator(history.getInitiator())
                .action(history.getAction().name())
                .comment(history.getComment())
                .createdAt(history.getCreatedAt())
                .build();
    }
}