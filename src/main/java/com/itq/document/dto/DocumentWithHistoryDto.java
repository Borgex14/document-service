package com.itq.document.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class DocumentWithHistoryDto {
    private DocumentDto document;
    private List<HistoryDto> history;
}