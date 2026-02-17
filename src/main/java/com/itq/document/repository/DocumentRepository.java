package com.itq.document.repository;

import com.itq.document.model.Document;
import com.itq.document.model.DocumentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByDocumentNumber(String documentNumber);

    List<Document> findByStatus(DocumentStatus status, Pageable pageable);

    // Другие методы...
}