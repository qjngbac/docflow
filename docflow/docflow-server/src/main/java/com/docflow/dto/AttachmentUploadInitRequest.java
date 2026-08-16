package com.docflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AttachmentUploadInitRequest {
    @NotBlank @Size(max = 500) private String fileName;
    @Size(max = 200) private String mimeType;
    @NotNull @Min(1) @Max(536870912) private Long size;
    @NotNull @Min(1) @Max(4096) private Integer totalChunks;
    @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "sha256 must be a SHA-256 hex digest") private String sha256;
}
