package com.docflow.service;

import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.entity.User;
import com.docflow.mapper.UserMapper;
import com.docflow.security.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthorizationService {
    @Autowired private UserMapper userMapper;

    public User requireAdmin() {
        Long userId = UserContext.getRequiredUserId();
        User user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())
                || !"ADMIN".equals(user.getSystemRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅系统管理员可以访问管理后台");
        }
        return user;
    }
}
