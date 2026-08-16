package com.docflow.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class DocumentUpdateRequest {

    @Size(max = 500)
    private String title;

    private String content;

    private Long folderId;

    private Boolean pinned;

    private Boolean favorite;

    @Size(max = 100)
    private String category;

    private String contentFormat;

    @Size(max = 1000)
    private String coverImage;

    private String pageFormat;

    private Integer marginTop;

    private Integer marginRight;

    private Integer marginBottom;

    private Integer marginLeft;

    @Size(max = 500)
    private String pageHeader;

    @Size(max = 500)
    private String pageFooter;

    @PositiveOrZero
    private Long revision;
}
