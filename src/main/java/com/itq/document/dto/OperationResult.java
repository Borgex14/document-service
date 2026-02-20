package com.itq.document.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OperationResult {
    private Long documentId;
    private OperationStatus status;
    private String message;

    public enum OperationStatus {
        SUCCESS,
        CONFLICT,
        NOT_FOUND,
        REGISTRY_ERROR
    }
}