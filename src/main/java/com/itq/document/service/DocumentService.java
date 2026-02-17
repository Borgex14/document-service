package com.itq.document.service;

import com.itq.document.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface DocumentService {
    DocumentDto createDocument(CreateDocumentRequest request);
    DocumentWithHistoryDto getDocumentWithHistory(Long id);
    List<DocumentDto> getDocumentsBatch(List<Long> ids, Pageable pageable);
    List<OperationResult> submitDocuments(BatchOperationRequest request);
    List<OperationResult> approveDocuments(BatchOperationRequest request);
    Page<DocumentDto> searchDocuments(DocumentSearchCriteria criteria, Pageable pageable);
    ConcurrencyTestResult testConcurrentApproval(ConcurrencyTestRequest request);
}