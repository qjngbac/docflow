package com.docflow.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VersionSaveRequest {
    @Size(max = 5_000_000, message = "Version content is too large")
    private String content;

    @Size(max = 100, message = "Version name must not exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Version description must not exceed 500 characters")
    private String description;
}
