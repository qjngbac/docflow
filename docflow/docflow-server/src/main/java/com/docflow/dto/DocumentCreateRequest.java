package com.docflow.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocumentCreateRequest {

    @Size(max = 500)
    private String title;

    private String content;

    private Long folderId;

    private Long templateId;

    private String category;

    private String contentFormat;

    @Size(max = 1000)
    private String coverImage;

    private String pageFormat;
}
