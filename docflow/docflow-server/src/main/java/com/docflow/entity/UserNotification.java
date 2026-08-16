package com.docflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_notification")
public class UserNotification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long actorId;
    private Long docId;
    private Long commentId;
    private String type;
    private String content;
    private Integer isRead;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String actorName;
    @TableField(exist = false)
    private String documentTitle;
}
