package com.docflow.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("doc_permission")
public class DocPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long docId;

    private Long userId;

    private String permission;

    private String shareToken;

    @JsonIgnore
    private String sharePassword;

    private LocalDateTime expireTime;

    private Long createdBy;

    private LocalDateTime createdAt;
}
