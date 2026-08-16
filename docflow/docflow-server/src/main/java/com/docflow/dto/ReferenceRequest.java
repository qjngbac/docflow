package com.docflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReferenceRequest {
    @NotBlank @Size(max = 100) private String citeKey;
    @Size(max = 255) private String doi;
    @NotBlank @Size(max = 50000) private String cslJson;
}
