package com.docflow.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("attachment_upload_session")
public class AttachmentUploadSession {
    @TableId private String id;
    private Long docId;
    private Long userId;
    private String originalName;
    private String mimeType;
    private Long totalSize;
    private Integer totalChunks;
    private Integer receivedChunks;
    private String sha256;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
