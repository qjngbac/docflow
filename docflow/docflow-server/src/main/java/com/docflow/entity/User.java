package com.docflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String nickname;

    private String password;

    private String email;

    private String avatar;

    private Integer status;

    private String systemRole;

    private String banReason;

    private LocalDateTime bannedAt;

    private LocalDateTime banExpiresAt;

    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

    private LocalDateTime lastLoginAt;

    private Integer welcomeDismissed;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
