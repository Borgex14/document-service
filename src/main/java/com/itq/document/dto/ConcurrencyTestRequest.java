package com.itq.document.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ConcurrencyTestRequest {

    @NotNull(message = "Document ID is required")
    private Long documentId;

    @Min(value = 1, message = "Threads must be at least 1")
    private int threads = 10;

    @Min(value = 1, message = "Attempts must be at least 1")
    private int attempts = 100;

    private String initiator = "concurrency-test";
}