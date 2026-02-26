package com.itq.document.repository;

import com.itq.document.model.Document;
import com.itq.document.model.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с сущностью {@link Document}.
 * Предоставляет методы для доступа к данным документов в базе данных,
 * включая поиск с фильтрацией, пагинацию и оптимизированную загрузку связанных сущностей.
 *
 * @author Borgex Team
 * @version 2.0
 * @see Document
 * @see DocumentStatus
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    // ========================================================================
    // Методы с оптимизированной загрузкой (EntityGraph)
    // ========================================================================

    /**
     * Находит документ по его идентификатору с предварительной загрузкой истории изменений.
     *
     * @param id идентификатор документа
     * @return {@link Optional}, содержащий документ с предзагруженной историей
     */
    @EntityGraph(attributePaths = {"history"})
    @Query("SELECT d FROM Document d WHERE d.id = :id")
    Optional<Document> findByIdWithHistory(@Param("id") Long id);

    /**
     * Находит все документы по списку идентификаторов с предварительной загрузкой истории.
     *
     * @param ids список идентификаторов документов
     * @return список документов с предзагруженной историей
     */
    @EntityGraph(attributePaths = {"history"})
    @Query("SELECT d FROM Document d WHERE d.id IN :ids")
    List<Document> findAllByIdWithHistory(@Param("ids") List<Long> ids);

    // ========================================================================
    // Методы поиска по полям
    // ========================================================================

    /**
     * Находит документ по его уникальному номеру.
     *
     * @param documentNumber уникальный номер документа
     * @return {@link Optional}, содержащий документ с указанным номером
     */
    Optional<Document> findByDocumentNumber(String documentNumber);

    /**
     * Находит все документы с указанным статусом с поддержкой пагинации.
     *
     * @param status   статус документа (не может быть null)
     * @param pageable параметры пагинации
     * @return страница документов с указанным статусом
     */
    List<Document> findByStatus(DocumentStatus status, Pageable pageable);

    // ========================================================================
    // ИСПРАВЛЕННЫЕ методы поиска с фильтрацией
    // ========================================================================

    /**
     * Выполняет поиск документов по заданным критериям с фильтрацией по дате создания.
     *
     * <p>Особенности:</p>
     * <ul>
     *   <li>Статус передаётся как {@link DocumentStatus} (не String) для типобезопасности</li>
     *   <li>Автор ищется по частичному совпадению (LIKE %author%)</li>
     *   <li>Все параметры опциональны (могут быть null)</li>
     * </ul>
     *
     * @param status   статус документа для фильтрации (может быть null)
     * @param author   автор документа для фильтрации (частичное совпадение, может быть null)
     * @param dateFrom начальная дата диапазона (включительно, может быть null)
     * @param dateTo   конечная дата диапазона (включительно, может быть null)
     * @param pageable параметры пагинации
     * @return страница документов, соответствующих критериям поиска
     */
    @Query("SELECT d FROM Document d WHERE " +
            "(:status IS NULL OR d.status = :status) AND " +
            "(:author IS NULL OR LOWER(d.author) LIKE LOWER(CONCAT('%', :author, '%'))) AND " +
            "(:dateFrom IS NULL OR d.createdAt >= :dateFrom) AND " +
            "(:dateTo IS NULL OR d.createdAt <= :dateTo)")
    Page<Document> searchByCreatedAt(
            @Param("status") DocumentStatus status,
            @Param("author") String author,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable);

    /**
     * Выполняет поиск документов по заданным критериям с фильтрацией по дате обновления.
     *
     * <p>Особенности:</p>
     * <ul>
     *   <li>Статус передаётся как {@link DocumentStatus} (не String) для типобезопасности</li>
     *   <li>Автор ищется по частичному совпадению (LIKE %author%)</li>
     *   <li>Все параметры опциональны (могут быть null)</li>
     * </ul>
     *
     * @param status   статус документа для фильтрации (может быть null)
     * @param author   автор документа для фильтрации (частичное совпадение, может быть null)
     * @param dateFrom начальная дата диапазона (включительно, может быть null)
     * @param dateTo   конечная дата диапазона (включительно, может быть null)
     * @param pageable параметры пагинации
     * @return страница документов, соответствующих критериям поиска
     */
    @Query("SELECT d FROM Document d WHERE " +
            "(:status IS NULL OR d.status = :status) AND " +
            "(:author IS NULL OR LOWER(d.author) LIKE LOWER(CONCAT('%', :author, '%'))) AND " +
            "(:dateFrom IS NULL OR d.updatedAt >= :dateFrom) AND " +
            "(:dateTo IS NULL OR d.updatedAt <= :dateTo)")
    Page<Document> searchByUpdatedAt(
            @Param("status") DocumentStatus status,
            @Param("author") String author,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable);

    // ========================================================================
    // Агрегатные функции
    // ========================================================================

    /**
     * Подсчитывает количество документов с указанным статусом.
     *
     * @param status статус документа для подсчёта
     * @return количество документов с указанным статусом
     */
    long countByStatus(DocumentStatus status);
}