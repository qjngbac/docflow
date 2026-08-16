package com.docflow.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocumentExportRequest {
    @Size(max = 5_000_000, message = "Export content is too large")
    private String content;
}
