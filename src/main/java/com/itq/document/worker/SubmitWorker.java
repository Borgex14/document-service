package com.itq.document.worker;

import com.itq.document.dto.BatchOperationRequest;
import com.itq.document.dto.OperationResult;
import com.itq.document.model.Document;
import com.itq.document.model.DocumentStatus;
import com.itq.document.repository.DocumentRepository;
import com.itq.document.service.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SubmitWorker {

    private final DocumentRepository documentRepository;
    private final DocumentService documentService;
    private final long fixedDelay;
    private final int batchSize;

    // Исправленный конструктор - принимает все необходимые зависимости
    public SubmitWorker(
            DocumentRepository documentRepository,
            DocumentService documentService,
            long fixedDelay,
            int batchSize) {
        this.documentRepository = documentRepository;
        this.documentService = documentService;
        this.fixedDelay = fixedDelay;
        this.batchSize = batchSize;
        log.info("SubmitWorker initialized with fixedDelay={}, batchSize={}",
                fixedDelay, batchSize);
    }

    @Scheduled(fixedDelayString = "${app.worker.submit.fixed-delay:60000}")
    public void processDraftDocuments() {
        log.info("SUBMIT-worker started. Checking for DRAFT documents to submit");
        long startTime = System.currentTimeMillis();

        try {
            int totalProcessed = 0;
            int totalSuccess = 0;
            int totalFailed = 0;

            while (true) {
                // Find batch of DRAFT documents
                List<Document> draftDocuments = documentRepository
                        .findByStatus(DocumentStatus.DRAFT, org.springframework.data.domain.PageRequest.of(0, batchSize));

                if (draftDocuments.isEmpty()) {
                    log.info("No more DRAFT documents found");
                    break;
                }

                List<Long> documentIds = draftDocuments.stream()
                        .map(Document::getId)
                        .collect(Collectors.toList());

                log.info("Processing batch of {} DRAFT documents", documentIds.size());

                // Create batch request
                BatchOperationRequest request = new BatchOperationRequest();
                request.setIds(documentIds);
                request.setInitiator("SUBMIT-WORKER");
                request.setComment("Auto-submitted by background worker");

                // Submit documents via API
                List<OperationResult> results = documentService.submitDocuments(request);

                // Count results
                long successCount = results.stream()
                        .filter(r -> r.getStatus() == OperationResult.OperationStatus.SUCCESS)
                        .count();

                totalProcessed += documentIds.size();
                totalSuccess += successCount;
                totalFailed += (documentIds.size() - successCount);

                log.info("Batch results - Success: {}, Failed: {}", successCount, (documentIds.size() - successCount));

                // Log any errors for monitoring
                results.stream()
                        .filter(r -> r.getStatus() != OperationResult.OperationStatus.SUCCESS)
                        .forEach(r -> log.warn("Document {}: {}", r.getDocumentId(), r.getMessage()));

                // If we got fewer documents than batch size, we're done
                if (draftDocuments.size() < batchSize) {
                    break;
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("SUBMIT-worker completed. Processed: {}, Success: {}, Failed: {}, Duration: {} ms",
                    totalProcessed, totalSuccess, totalFailed, duration);

        } catch (Exception e) {
            log.error("SUBMIT-worker encountered an error: {}", e.getMessage(), e);
        }
    }
}