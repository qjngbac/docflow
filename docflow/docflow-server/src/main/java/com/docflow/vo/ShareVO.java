package com.docflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShareVO {

    private String token;
    private String url;
    private String permission;
    private LocalDateTime expireTime;
}
