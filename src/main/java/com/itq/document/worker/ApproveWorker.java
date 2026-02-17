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
public class ApproveWorker {

    private final DocumentRepository documentRepository;
    private final DocumentService documentService;

    @Value("${app.worker.approve.batch-size:100}")
    private int batchSize;

    @Value("${app.worker.approve.fixed-delay:60000}")
    private long fixedDelay;

    @Scheduled(fixedDelayString = "${app.worker.approve.fixed-delay:60000}")
    public void processSubmittedDocuments() {
        log.info("APPROVE-worker started. Checking for SUBMITTED documents to approve");
        long startTime = System.currentTimeMillis();

        try {
            int totalProcessed = 0;
            int totalSuccess = 0;
            int totalFailed = 0;
            int totalRegistryErrors = 0;

            while (true) {
                // Find batch of SUBMITTED documents
                List<Document> submittedDocuments = documentRepository
                        .findByStatus(DocumentStatus.SUBMITTED, PageRequest.of(0, batchSize));

                if (submittedDocuments.isEmpty()) {
                    log.info("No more SUBMITTED documents found");
                    break;
                }

                List<Long> documentIds = submittedDocuments.stream()
                        .map(Document::getId)
                        .collect(Collectors.toList());

                log.info("Processing batch of {} SUBMITTED documents", documentIds.size());

                // Create batch request
                BatchOperationRequest request = new BatchOperationRequest();
                request.setIds(documentIds);
                request.setInitiator("APPROVE-WORKER");
                request.setComment("Auto-approved by background worker");

                // Approve documents via API
                List<OperationResult> results = documentService.approveDocuments(request);

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

                long registryErrorCount = results.stream()
                        .filter(r -> r.getStatus() == OperationResult.OperationStatus.REGISTRY_ERROR)
                        .count();

                totalProcessed += documentIds.size();
                totalSuccess += successCount;
                totalFailed += (conflictCount + notFoundCount);
                totalRegistryErrors += registryErrorCount;

                log.info("Batch results - Success: {}, Conflict: {}, Not Found: {}, Registry Errors: {}",
                        successCount, conflictCount, notFoundCount, registryErrorCount);

                // Log any errors for monitoring
                results.stream()
                        .filter(r -> r.getStatus() != OperationResult.OperationStatus.SUCCESS)
                        .forEach(r -> log.warn("Document {}: {} ({})",
                                r.getDocumentId(), r.getMessage(), r.getStatus()));

                // If we got fewer documents than batch size, we're done
                if (submittedDocuments.size() < batchSize) {
                    break;
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("APPROVE-worker completed. Processed: {}, Success: {}, Failed: {}, Registry Errors: {}, Duration: {} ms",
                    totalProcessed, totalSuccess, totalFailed, totalRegistryErrors, duration);

        } catch (Exception e) {
            log.error("APPROVE-worker encountered an error: {}", e.getMessage(), e);
        }
    }
}