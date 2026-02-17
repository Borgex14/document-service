package com.itq.document.worker;

import com.itq.document.dto.BatchOperationRequest;
import com.itq.document.dto.OperationResult;
import com.itq.document.model.Document;
import com.itq.document.model.DocumentStatus;
import com.itq.document.repository.DocumentRepository;
import com.itq.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmitWorker {

    private final DocumentRepository documentRepository;
    private final DocumentService documentService;

    @Value("${app.worker.submit.batch-size:100}")
    private int batchSize;

    @Value("${app.worker.submit.fixed-delay:60000}")
    private long fixedDelay;

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
                        .findByStatus(DocumentStatus.DRAFT, PageRequest.of(0, batchSize));

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

                long conflictCount = results.stream()
                        .filter(r -> r.getStatus() == OperationResult.OperationStatus.CONFLICT)
                        .count();

                long notFoundCount = results.stream()
                        .filter(r -> r.getStatus() == OperationResult.OperationStatus.NOT_FOUND)
                        .count();

                totalProcessed += documentIds.size();
                totalSuccess += successCount;
                totalFailed += (conflictCount + notFoundCount);

                log.info("Batch results - Success: {}, Conflict: {}, Not Found: {}",
                        successCount, conflictCount, notFoundCount);

                // Log any errors for monitoring
                results.stream()
                        .filter(r -> r.getStatus() != OperationResult.OperationStatus.SUCCESS)
                        .forEach(r -> log.warn("Document {}: {}",
                                r.getDocumentId(), r.getMessage()));

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