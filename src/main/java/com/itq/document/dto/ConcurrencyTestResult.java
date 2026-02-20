package com.itq.document.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class ConcurrencyTestResult {
    private Long documentId;
    private int totalAttempts;
    private int successfulAttempts;
    private int conflictAttempts;
    private int errorAttempts;
    private String finalStatus;
    private long registryEntriesCount;
    private Map<String, Object> details;
}