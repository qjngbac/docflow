package com.docflow.vo;

import com.docflow.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String avatar;
    private Integer status;
    private String systemRole;
    private LocalDateTime lastLoginAt;
    private Integer welcomeDismissed;

    public static UserVO from(User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setSystemRole(user.getSystemRole());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setWelcomeDismissed(user.getWelcomeDismissed());
        return vo;
    }
}
