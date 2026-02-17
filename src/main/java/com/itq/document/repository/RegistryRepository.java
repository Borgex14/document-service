package com.itq.document.repository;

import com.itq.document.model.RegistryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegistryRepository extends JpaRepository<RegistryEntry, Long> {

    List<RegistryEntry> findByDocumentId(Long documentId);

    Optional<RegistryEntry> findByDocumentIdAndApprovedBy(Long documentId, String approvedBy);

    long countByDocumentId(Long documentId);

    boolean existsByDocumentId(Long documentId);

    void deleteByDocumentId(Long documentId);
}