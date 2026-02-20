package com.itq.document.controller;

import com.itq.document.dto.*;
import com.itq.document.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    public ResponseEntity<DocumentDto> createDocument(
            @Valid @RequestBody CreateDocumentRequest request) {
        return ResponseEntity.ok(documentService.createDocument(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentWithHistoryDto> getDocument(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocumentWithHistory(id));
    }

    @PostMapping("/batch")
    public ResponseEntity<List<DocumentDto>> getDocumentsBatch(
            @RequestBody List<Long> ids,
            Pageable pageable) {
        return ResponseEntity.ok(documentService.getDocumentsBatch(ids, pageable));
    }

    @PostMapping("/submit")
    public ResponseEntity<List<OperationResult>> submitDocuments(
            @Valid @RequestBody BatchOperationRequest request) {
        return ResponseEntity.ok(documentService.submitDocuments(request));
    }

    @PostMapping("/approve")
    public ResponseEntity<List<OperationResult>> approveDocuments(
            @Valid @RequestBody BatchOperationRequest request) {
        return ResponseEntity.ok(documentService.approveDocuments(request));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<DocumentDto>> searchDocuments(
            @ModelAttribute DocumentSearchCriteria criteria,
            Pageable pageable) {
        return ResponseEntity.ok(documentService.searchDocuments(criteria, pageable));
    }

    @PostMapping("/concurrency-test")
    public ResponseEntity<ConcurrencyTestResult> testConcurrentApproval(
            @RequestBody ConcurrencyTestRequest request) {
        return ResponseEntity.ok(documentService.testConcurrentApproval(request));
    }
}