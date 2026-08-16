package com.docflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private String summary;

    private Long ownerId;

    private Long folderId;

    private Integer isPinned;

    @TableField(exist = false)
    private Integer isFavorite;

    private String category;

    private String contentFormat;

    private String coverImage;

    private String pageFormat;

    private Integer marginTop;

    private Integer marginRight;

    private Integer marginBottom;

    private Integer marginLeft;

    private String pageHeader;

    private String pageFooter;

    private Integer isDeleted;

    private LocalDateTime deletedAt;

    private Long lastEditBy;

    private String collabMode;

    private Long revision;

    private Long persistedRevision;

    private String contentHash;

    private LocalDateTime lastPersistedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
