package com.itq.document.repository;

import com.itq.document.model.Document;
import com.itq.document.model.DocumentStatus;
import org.springframework.data.domain.Page;  // ИСПРАВЛЕНО: правильный импорт
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByDocumentNumber(String documentNumber);

    List<Document> findByStatus(DocumentStatus status, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE " +
            "(:status IS NULL OR d.status = :status) AND " +
            "(:author IS NULL OR d.author = :author) AND " +
            "(:dateFrom IS NULL OR d.createdAt >= :dateFrom) AND " +
            "(:dateTo IS NULL OR d.createdAt <= :dateTo)")
    Page<Document> searchByCreatedAt(
            @Param("status") String status,
            @Param("author") String author,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable);

    @Query("SELECT d FROM Document d WHERE " +
            "(:status IS NULL OR d.status = :status) AND " +
            "(:author IS NULL OR d.author = :author) AND " +
            "(:dateFrom IS NULL OR d.updatedAt >= :dateFrom) AND " +
            "(:dateTo IS NULL OR d.updatedAt <= :dateTo)")
    Page<Document> searchByUpdatedAt(
            @Param("status") String status,
            @Param("author") String author,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable);

    long countByStatus(DocumentStatus status);
}