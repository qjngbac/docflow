package com.docflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("doc_version")
public class DocVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long docId;

    private String title;

    private String content;

    private String contentFormat;

    private Integer versionNum;

    private Long sourceRevision;

    private String versionType;

    private String versionName;

    private String description;

    private Long createdBy;

    private LocalDateTime createdAt;
}
