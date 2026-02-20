package com.itq.document.repository;

import com.itq.document.model.History;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistoryRepository extends JpaRepository<History, Long> {

    List<History> findByDocumentIdOrderByCreatedAtDesc(Long documentId);

    List<History> findByDocumentId(Long documentId);

    void deleteByDocumentId(Long documentId);
}