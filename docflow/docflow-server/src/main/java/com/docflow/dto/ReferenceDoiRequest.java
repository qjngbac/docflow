package com.docflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReferenceDoiRequest {
    @NotBlank @Size(max = 255) private String doi;
    @Size(max = 100) private String citeKey;
}
