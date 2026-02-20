package com.itq.document.service;

import com.itq.document.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Сервис для управления документами.
 * Определяет основные операции для работы с документами, включая
 * создание, получение, отправку на утверждение, утверждение и поиск.
 *
 * @author Borgex Team
 * @version 1.0
 * @since 2026-02-20
 */
public interface DocumentService {

    /**
     * Создает новый документ.
     *
     * @param request запрос с данными для создания документа
     * @return созданный документ в виде DTO
     */
    DocumentDto createDocument(CreateDocumentRequest request);

    /**
     * Получает документ по его идентификатору вместе с историей изменений.
     *
     * @param id идентификатор документа
     * @return DTO документа с историей
     * @throws com.itq.document.exception.DocumentNotFoundException если документ не найден
     */
    DocumentWithHistoryDto getDocumentWithHistory(Long id);

    /**
     * Получает список документов по их идентификаторам.
     *
     * @param ids список идентификаторов документов
     * @param pageable параметры пагинации
     * @return список DTO документов
     */
    List<DocumentDto> getDocumentsBatch(List<Long> ids, Pageable pageable);

    /**
     * Отправляет документы на утверждение.
     * Изменяет статус документов с DRAFT на SUBMITTED.
     *
     * @param request запрос с идентификаторами документов и инициатором
     * @return список результатов операции для каждого документа
     */
    List<OperationResult> submitDocuments(BatchOperationRequest request);

    /**
     * Утверждает документы.
     * Изменяет статус документов с SUBMITTED на APPROVED и создает
     * записи в реестре утверждений.
     *
     * @param request запрос с идентификаторами документов и инициатором
     * @return список результатов операции для каждого документа
     */
    List<OperationResult> approveDocuments(BatchOperationRequest request);

    /**
     * Выполняет поиск документов по заданным критериям.
     *
     * @param criteria критерии поиска
     * @param pageable параметры пагинации
     * @return страница с документами, соответствующими критериям
     */
    Page<DocumentDto> searchDocuments(DocumentSearchCriteria criteria, Pageable pageable);

    /**
     * Выполняет тестирование конкурентного доступа к документу.
     * Позволяет проверить корректность работы механизмов блокировок
     * при одновременных попытках утверждения документа.
     *
     * @param request параметры теста
     * @return результаты теста
     */
    ConcurrencyTestResult testConcurrentApproval(ConcurrencyTestRequest request);
}