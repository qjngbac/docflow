package com.docflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CommentStatusRequest {
    @NotNull
    private Boolean resolved;
}
