package com.docflow.entity;

import lombok.Data;

@Data
public class StoredFile {
    private String fileUrl;
    private Long ownerId;
    private String fileKind;
}
