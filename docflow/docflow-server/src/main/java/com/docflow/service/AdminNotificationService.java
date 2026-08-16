package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.entity.User;
import com.docflow.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class AdminNotificationService {
    private static final int MAX_CONTENT_LENGTH = 500;

    @Autowired private UserMapper userMapper;
    @Autowired private NotificationService notificationService;
    @Autowired private AdminService adminService;

    @Transactional
    public SendResult send(Long adminId, String targetType, String username,
                           LocalDateTime registeredFrom, LocalDateTime registeredTo, String content) {
        String normalizedContent = normalizeContent(content);
        String normalizedTargetType = StringUtils.hasText(targetType)
                ? targetType.trim().toUpperCase(Locale.ROOT) : "";

        List<User> recipients;
        String auditTarget;
        switch (normalizedTargetType) {
            case "ALL" -> {
                recipients = selectUsers(null, null);
                auditTarget = "全部用户";
            }
            case "USER" -> {
                String normalizedUsername = StringUtils.hasText(username) ? username.trim() : "";
                if (normalizedUsername.isEmpty()) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入接收人的用户名");
                }
                User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, normalizedUsername)
                        .eq(User::getIsDeleted, 0)
                        .last("LIMIT 1"));
                if (user == null) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "没有找到该用户名对应的用户");
                }
                recipients = List.of(user);
                auditTarget = "用户 " + user.getUsername();
            }
            case "REGISTERED_AT" -> {
                if (registeredFrom == null && registeredTo == null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择注册时间的开始时间或结束时间");
                }
                if (registeredFrom != null && registeredTo != null && registeredFrom.isAfter(registeredTo)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "注册开始时间不能晚于结束时间");
                }
                recipients = selectUsers(registeredFrom, registeredTo);
                auditTarget = "注册时间 " + valueOrOpen(registeredFrom) + " 至 " + valueOrOpen(registeredTo);
            }
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择正确的提示发送范围");
        }

        if (recipients.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前发送范围内没有符合条件的用户");
        }
        recipients.forEach(user -> notificationService.createAdminMessage(user.getId(), adminId, normalizedContent));
        adminService.audit(adminId, "ADMIN_NOTIFICATION_SEND",
                auditTarget + "；接收人数：" + recipients.size());
        return new SendResult(recipients.size());
    }

    private List<User> selectUsers(LocalDateTime registeredFrom, LocalDateTime registeredTo) {
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<User>()
                .eq(User::getIsDeleted, 0)
                .orderByAsc(User::getId);
        if (registeredFrom != null) query.ge(User::getCreatedAt, registeredFrom);
        if (registeredTo != null) query.le(User::getCreatedAt, registeredTo);
        return userMapper.selectList(query);
    }

    private String normalizeContent(String content) {
        String value = StringUtils.hasText(content) ? content.trim() : "";
        if (value.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "提示内容不能为空");
        }
        if (value.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "提示内容不能超过 500 个字");
        }
        return value;
    }

    private String valueOrOpen(LocalDateTime value) {
        return value == null ? "不限" : value.toString();
    }

    public record SendResult(int sentCount) {}
}
