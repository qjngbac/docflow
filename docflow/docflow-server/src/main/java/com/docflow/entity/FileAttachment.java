package com.docflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("file_attachment")
public class FileAttachment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private Long uploaderId;
    private String originalName;
    private String fileUrl;
    private Long fileSize;
    private String mimeType;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
