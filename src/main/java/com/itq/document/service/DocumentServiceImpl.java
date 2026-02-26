package com.itq.document.service;

import com.itq.document.dto.*;
import com.itq.document.exception.*;
import com.itq.document.model.*;
import com.itq.document.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
 * <p>Основные возможности:</p>
 * <ul>
 *   <li>Создание документов с уникальными номерами</li>
 *   <li>Отправка документов на утверждение (DRAFT → SUBMITTED)</li>
 *   <li>Утверждение документов с атомарной записью в реестр (SUBMITTED → APPROVED)</li>
 *   <li>Пакетная обработка документов с изоляцией транзакций</li>
 *   <li>Поиск с фильтрацией по статусу, автору и датам</li>
 *   <li>Тестирование конкурентного доступа</li>
 * </ul>
 *
 * <p>Важные особенности:</p>
 * <ul>
 *   <li>Каждый документ обрабатывается в отдельной транзакции для изоляции</li>
 *   <li>При утверждении документа сначала создаётся запись в реестре (атомарно)</li>
 *   <li>Используется оптимистичная блокировка для защиты от конкурентных изменений</li>
 *   <li>Все изменения логируются для отслеживания</li>
 * </ul>
 *
 * @author Borgex Team
 * @version 2.0
 * @see DocumentService
 * @see Document
 * @see History
 * @see RegistryEntry
 * @since 2026-02-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    /** Репозиторий для работы с документами */
    private final DocumentRepository documentRepository;

    /** Репозиторий для работы с историей изменений */
    private final HistoryRepository historyRepository;

    /** Репозиторий для работы с реестром утверждений */
    private final RegistryRepository registryRepository;

    /** Генератор уникальных номеров документов */
    private final DocumentNumberGenerator numberGenerator;

    // ========================================================================
    // Публичные методы (реализация интерфейса DocumentService)
    // ========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Детали реализации:</p>
     * <ul>
     *   <li>Генерирует уникальный номер документа через {@link DocumentNumberGenerator}</li>
     *   <li>Устанавливает статус {@link DocumentStatus#DRAFT} для нового документа</li>
     *   <li>Сохраняет документ в базе данных</li>
     *   <li>Логирует создание документа с id и номером</li>
     * </ul>
     *
     * @param request запрос на создание документа, содержащий автора и название
     * @return DTO созданного документа
     * @throws IllegalArgumentException если {@code request} равен {@code null}
     * @see DocumentNumberGenerator#generate()
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
     *
     * <p>Детали реализации:</p>
     * <ul>
     *   <li>Ищет документ по идентификатору с предзагрузкой истории (исправление N+1)</li>
     *   <li>Если документ не найден, выбрасывает {@link DocumentNotFoundException}</li>
     *   <li>Объединяет документ и предзагруженную историю в один DTO</li>
     * </ul>
     *
     * @param id идентификатор документа
     * @return DTO документа с историей
     * @throws DocumentNotFoundException если документ с указанным id не найден
     */
    @Override
    @Transactional(readOnly = true)
    public DocumentWithHistoryDto getDocumentWithHistory(Long id) {
        // Используем метод с EntityGraph для предзагрузки истории (один запрос вместо двух)
        Document document = documentRepository.findByIdWithHistory(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));

        return DocumentWithHistoryDto.builder()
                .document(mapToDto(document))
                .history(document.getHistory().stream()  // История уже загружена!
                        .map(this::mapToHistoryDto)
                        .collect(Collectors.toList()))
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Детали реализации:</p>
     * <ul>
     *   <li>Ищет все документы с указанными идентификаторами</li>
     *   <li>Использует JOIN FETCH для предзагрузки истории (предотвращает N+1)</li>
     *   <li>Возвращает только найденные документы (отсутствующие игнорируются)</li>
     * </ul>
     *
     * @param ids список идентификаторов документов
     * @param pageable параметры пагинации
     * @return список DTO документов
     */
    @Override
    @Transactional(readOnly = true)
    public List<DocumentDto> getDocumentsBatch(List<Long> ids, Pageable pageable) {
        // Используем метод с предзагрузкой истории
        return documentRepository.findAllByIdWithHistory(ids).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Детали реализации:</p>
     * <ul>
     *   <li>Каждый документ обрабатывается независимо</li>
     *   <li>Проверяет, что документ находится в статусе {@link DocumentStatus#DRAFT}</li>
     *   <li>При успехе меняет статус на {@link DocumentStatus#SUBMITTED}</li>
     *   <li>Создаёт запись в истории с действием {@link DocumentAction#SUBMIT}</li>
     *   <li>Ошибки для каждого документа логируются и возвращаются в результате</li>
     * </ul>
     *
     * @param request запрос на отправку документов
     * @return список результатов операции для каждого документа
     * @see OperationResult
     * @see OperationResult.OperationStatus
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

                // Обновляем статус документа
                document.setStatus(DocumentStatus.SUBMITTED);
                documentRepository.save(document);

                // Создаём запись в истории
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
     *
     * <p>Детали реализации:</p>
     * <ul>
     *   <li>Каждый документ обрабатывается в отдельной транзакции (через {@link #approveSingleDocument})</li>
     *   <li>Обеспечивает атомарность: запись в реестр + обновление статуса + запись в истории</li>
     *   <li>При ошибке регистрации возвращает статус {@link OperationResult.OperationStatus#REGISTRY_ERROR}</li>
     *   <li>При оптимистичной блокировке возвращает статус {@link OperationResult.OperationStatus#CONFLICT}</li>
     *   <li>Все ошибки логируются для дальнейшего анализа</li>
     * </ul>
     *
     * @param request запрос на утверждение документов
     * @return список результатов операции для каждого документа
     * @see #approveSingleDocument(Long, BatchOperationRequest)
     * @see OperationResult
     * @see ObjectOptimisticLockingFailureException
     */
    @Override
    @Transactional
    public List<OperationResult> approveDocuments(BatchOperationRequest request) {
        log.info("Processing approve batch for {} documents", request.getIds().size());
        long startTime = System.currentTimeMillis();

        List<OperationResult> results = new ArrayList<>();

        for (Long documentId : request.getIds()) {
            try {
                // Используем отдельную транзакцию для каждого документа для изоляции
                OperationResult result = approveSingleDocument(documentId, request);
                results.add(result);

            } catch (DocumentNotFoundException e) {
                results.add(OperationResult.builder()
                        .documentId(documentId)
                        .status(OperationResult.OperationStatus.NOT_FOUND)
                        .message("Document not found")
                        .build());
            } catch (Exception e) {
                log.error("Error approving document {}: {}", documentId, e.getMessage());

                // Определяем тип ошибки по сообщению для правильной категоризации
                if (e.getMessage() != null && e.getMessage().contains("Failed to register")) {
                    results.add(OperationResult.builder()
                            .documentId(documentId)
                            .status(OperationResult.OperationStatus.REGISTRY_ERROR)
                            .message(e.getMessage())
                            .build());
                } else {
                    results.add(OperationResult.builder()
                            .documentId(documentId)
                            .status(OperationResult.OperationStatus.CONFLICT)
                            .message("Error processing document: " + e.getMessage())
                            .build());
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Approve batch completed in {} ms", duration);

        return results;
    }

    // ========================================================================
    // Вспомогательные методы с собственной транзакцией
    // ========================================================================

    /**
     * Утверждает один документ в отдельной транзакции.
     *
     * <p>Этот метод выполняется в новой транзакции ({@code REQUIRES_NEW}) для обеспечения
     * изоляции между документами при пакетной обработке. Реализует атомарную операцию:</p>
     * <ol>
     *   <li>Проверяет статус документа (должен быть {@link DocumentStatus#SUBMITTED})</li>
     *   <li>Проверяет отсутствие записи в реестре для этого документа</li>
     *   <li>Создаёт запись в реестре утверждений ({@link RegistryEntry})</li>
     *   <li>Обновляет статус документа на {@link DocumentStatus#APPROVED}</li>
     *   <li>Создаёт запись в истории с действием {@link DocumentAction#APPROVE}</li>
     * </ol>
     *
     * <p>Если любой из шагов завершается ошибкой, вся транзакция откатывается,
     * и ни одно изменение не фиксируется в базе данных.</p>
     *
     * @param documentId идентификатор документа для утверждения
     * @param request запрос на утверждение с инициатором и комментарием
     * @return результат операции для документа
     * @throws DocumentNotFoundException если документ не найден
     * @throws RuntimeException с сообщением "Failed to register..." при ошибке регистрации
     * @throws ObjectOptimisticLockingFailureException при конфликте версий
     * @see Propagation#REQUIRES_NEW
     * @see RegistryRepository#existsByDocumentId(Long)
     * @see RegistryRepository#saveAndFlush(Object)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected OperationResult approveSingleDocument(Long documentId, BatchOperationRequest request) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        // Проверка статуса документа
        if (document.getStatus() != DocumentStatus.SUBMITTED) {
            return OperationResult.builder()
                    .documentId(documentId)
                    .status(OperationResult.OperationStatus.CONFLICT)
                    .message(String.format("Document is in %s status, expected SUBMITTED",
                            document.getStatus()))
                    .build();
        }

        // Проверка на дублирование в реестре
        if (registryRepository.existsByDocumentId(documentId)) {
            return OperationResult.builder()
                    .documentId(documentId)
                    .status(OperationResult.OperationStatus.CONFLICT)
                    .message("Document already has registry entry")
                    .build();
        }

        // Атомарная операция утверждения
        try {
            // 1. Создаём запись в реестре (сначала, так как это самый важный шаг)
            RegistryEntry registryEntry = new RegistryEntry();
            registryEntry.setDocumentId(documentId);
            registryEntry.setApprovedBy(request.getInitiator());
            registryEntry.setApprovedAt(LocalDateTime.now());
            registryEntry.setCreatedAt(LocalDateTime.now());

            registryRepository.saveAndFlush(registryEntry);

            // 2. Обновляем статус документа
            document.setStatus(DocumentStatus.APPROVED);
            documentRepository.save(document);

            // 3. Создаём запись в истории
            History history = new History();
            history.setDocument(document);
            history.setInitiator(request.getInitiator());
            history.setAction(DocumentAction.APPROVE);
            history.setComment(request.getComment());
            history.setCreatedAt(LocalDateTime.now());
            historyRepository.save(history);

            return OperationResult.builder()
                    .documentId(documentId)
                    .status(OperationResult.OperationStatus.SUCCESS)
                    .message("Document approved successfully")
                    .build();

        } catch (ObjectOptimisticLockingFailureException e) {
            // Специфичная обработка оптимистичной блокировки
            log.warn("Optimistic lock exception for document {}: {}", documentId, e.getMessage());
            throw new RuntimeException("Document was modified by another transaction", e);

        } catch (Exception e) {
            log.error("Failed to approve document {}: {}", documentId, e.getMessage());

            // Пробрасываем с маркером REGISTRY_ERROR, если ошибка при сохранении registry
            if (e.getMessage() != null && e.getMessage().contains("registry")) {
                throw new RuntimeException("Failed to register approval: " + e.getMessage(), e);
            }
            throw e;
        }
    }

    // ========================================================================
    // Поиск и тестирование
    // ========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Детали реализации:</p>
     * <ul>
     *   <li>В зависимости от параметра {@code searchByCreatedAt} выбирает соответствующий метод репозитория</li>
     *   <li>Поддерживает частичное совпадение по автору (LIKE %author%)</li>
     *   <li>Поддерживает диапазонные запросы по датам</li>
     *   <li>Возвращает результаты с пагинацией</li>
     *   <li>Логирует критерии поиска для отладки</li>
     * </ul>
     *
     * @param criteria критерии поиска
     * @param pageable параметры пагинации
     * @return страница с документами, соответствующими критериям
     * @see DocumentRepository#searchByCreatedAt(String, String, LocalDateTime, LocalDateTime, Pageable)
     * @see DocumentRepository#searchByUpdatedAt(String, String, LocalDateTime, LocalDateTime, Pageable)
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
     *
     * <p>Детали реализации:</p>
     * <ul>
     *   <li>Сбрасывает статус документа в {@link DocumentStatus#SUBMITTED} для тестирования</li>
     *   <li>Создаёт пул потоков с указанным размером</li>
     *   <li>Запускает указанное количество конкурентных попыток утверждения</li>
     *   <li>Использует {@link CountDownLatch} для синхронизации</li>
     *   <li>Собирает статистику успешных, конфликтных и ошибочных попыток</li>
     *   <li>Проверяет финальное состояние документа и количество записей в реестре</li>
     * </ul>
     *
     * <p>Ожидаемое поведение при корректной реализации:</p>
     * <ul>
     *   <li>Только одна попытка должна завершиться успешно (successfulAttempts = 1)</li>
     *   <li>Остальные должны завершиться с конфликтом (conflictAttempts = attempts - 1)</li>
     *   <li>Финальный статус документа должен быть {@link DocumentStatus#APPROVED}</li>
     *   <li>В реестре должна быть ровно одна запись (registryEntriesCount = 1)</li>
     * </ul>
     *
     * @param request параметры теста (id документа, количество потоков и попыток)
     * @return результаты теста: количество успешных, конфликтных и ошибочных попыток
     * @throws DocumentNotFoundException если документ не найден
     * @see CountDownLatch
     * @see ExecutorService
     * @see ConcurrencyTestResult
     */
    @Override
    @Transactional
    public ConcurrencyTestResult testConcurrentApproval(ConcurrencyTestRequest request) {
        log.info("Starting concurrency test for document {} with {} threads and {} attempts",
                request.getDocumentId(), request.getThreads(), request.getAttempts());

        Document document = documentRepository.findById(request.getDocumentId())
                .orElseThrow(() -> new DocumentNotFoundException(request.getDocumentId()));

        // Сброс документа в статус SUBMITTED для тестирования
        if (document.getStatus() == DocumentStatus.APPROVED) {
            document.setStatus(DocumentStatus.SUBMITTED);
            documentRepository.save(document);
        }

        ExecutorService executor = Executors.newFixedThreadPool(request.getThreads());
        CountDownLatch latch = new CountDownLatch(request.getAttempts());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Запуск конкурентных попыток утверждения
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

        // Ожидание завершения всех попыток
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

    // ========================================================================
    // Приватные вспомогательные методы
    // ========================================================================

    /**
     * Преобразует сущность {@link Document} в {@link DocumentDto}.
     *
     * <p>Выполняет маппинг всех полей документа в соответствующие поля DTO.
     * Статус документа преобразуется из enum в строку через {@link Enum#name()}.</p>
     *
     * @param document сущность документа (не может быть null)
     * @return DTO документа с заполненными полями
     * @throws NullPointerException если document равен null
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
     * Преобразует сущность {@link History} в {@link HistoryDto}.
     *
     * <p>Выполняет маппинг всех полей истории в соответствующие поля DTO.
     * Действие преобразуется из enum в строку через {@link Enum#name()}.</p>
     *
     * @param history сущность истории (не может быть null)
     * @return DTO истории с заполненными полями
     * @throws NullPointerException если history равен null
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