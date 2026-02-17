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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final HistoryRepository historyRepository;
    private final RegistryRepository registryRepository;
    private final DocumentNumberGenerator numberGenerator;

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

    @Override
    @Transactional(readOnly = true)
    public List<DocumentDto> getDocumentsBatch(List<Long> ids, Pageable pageable) {
        return documentRepository.findAllById(ids).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

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

        ConcurrentHashMap<String, AtomicInteger> results = new ConcurrentHashMap<>();
        results.put("success", new AtomicInteger(0));
        results.put("conflict", new AtomicInteger(0));
        results.put("error", new AtomicInteger(0));

        for (int i = 0; i < request.getAttempts(); i++) {
            executor.submit(() -> {
                try {
                    BatchOperationRequest approveRequest = new BatchOperationRequest();
                    approveRequest.setIds(List.of(request.getDocumentId()));
                    approveRequest.setInitiator(request.getInitiator());

                    List<OperationResult> operationResults = approveDocuments(approveRequest);
                    OperationResult result = operationResults.get(0);

                    switch (result.getStatus()) {
                        case SUCCESS:
                            results.get("success").incrementAndGet();
                            break;
                        case CONFLICT:
                        case REGISTRY_ERROR:
                            results.get("conflict").incrementAndGet();
                            break;
                        default:
                            results.get("error").incrementAndGet();
                    }
                } catch (Exception e) {
                    results.get("error").incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        executor.shutdown();

        Document finalDocument = documentRepository.findById(request.getDocumentId()).orElse(document);
        long registryCount = registryRepository.countByDocumentId(request.getDocumentId());

        log.info("Concurrency test completed. Success: {}, Conflict: {}, Error: {}, Final status: {}",
                results.get("success").get(),
                results.get("conflict").get(),
                results.get("error").get(),
                finalDocument.getStatus());

        return ConcurrencyTestResult.builder()
                .documentId(request.getDocumentId())
                .totalAttempts(request.getAttempts())
                .successfulAttempts(results.get("success").get())
                .conflictAttempts(results.get("conflict").get())
                .errorAttempts(results.get("error").get())
                .finalStatus(finalDocument.getStatus().name())
                .registryEntriesCount(registryCount)
                .build();
    }

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