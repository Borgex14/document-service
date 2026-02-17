package com.itq.document.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class BatchOperationRequest {

    @NotEmpty(message = "Document IDs list cannot be empty")
    @Size(max = 1000, message = "Cannot process more than 1000 documents at once")
    private List<Long> ids;

    @NotNull(message = "Initiator is required")
    @Size(min = 2, max = 100, message = "Initiator must be between 2 and 100 characters")
    private String initiator;

    private String comment; // Optional comment
}