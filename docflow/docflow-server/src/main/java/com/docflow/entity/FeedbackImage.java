package com.docflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("feedback_image")
public class FeedbackImage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long feedbackId;
    private Long userId;
    private String fileUrl;
    private String originalName;
    private Long fileSize;
    private String mimeType;
    private LocalDateTime createdAt;
}
