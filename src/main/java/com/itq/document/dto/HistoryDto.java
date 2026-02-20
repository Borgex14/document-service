package com.itq.document.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class HistoryDto {
    private Long id;
    private String initiator;
    private String action;
    private String comment;
    private LocalDateTime createdAt;
}