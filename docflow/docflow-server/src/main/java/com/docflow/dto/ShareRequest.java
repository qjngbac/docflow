package com.docflow.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShareRequest {

    @Pattern(regexp = "READ|WRITE|ADMIN")
    private String permission = "READ";

    private Long userId;

    private String password;

    private LocalDateTime expireTime;
}
