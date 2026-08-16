package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.entity.Document;
import com.docflow.entity.User;
import com.docflow.entity.UserNotification;
import com.docflow.mapper.DocumentMapper;
import com.docflow.mapper.UserMapper;
import com.docflow.mapper.UserNotificationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {
    @Autowired private UserNotificationMapper notificationMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private DocumentMapper documentMapper;

    public List<UserNotification> list(Long userId, boolean unreadOnly) {
        return list(userId, unreadOnly, null);
    }

    public List<UserNotification> list(Long userId, boolean unreadOnly, String type) {
        LambdaQueryWrapper<UserNotification> query = new LambdaQueryWrapper<UserNotification>()
                .eq(UserNotification::getUserId, userId)
                .orderByDesc(UserNotification::getCreatedAt)
                .last("LIMIT 100");
        if (unreadOnly) query.eq(UserNotification::getIsRead, 0);
        if (type != null && !type.isBlank()) query.eq(UserNotification::getType, type.trim());
        return notificationMapper.selectList(query).stream().map(this::enrich).toList();
    }

    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<UserNotification>()
                .eq(UserNotification::getUserId, userId).eq(UserNotification::getIsRead, 0));
    }

    @Transactional
    public void markRead(Long id, Long userId) {
        UserNotification notification = notificationMapper.selectById(id);
        if (notification == null || !userId.equals(notification.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "通知不存在或不属于当前用户");
        }
        notification.setIsRead(1);
        notificationMapper.updateById(notification);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserNotification>()
                .eq(UserNotification::getUserId, userId).eq(UserNotification::getIsRead, 0)
                .set(UserNotification::getIsRead, 1));
    }

    public void create(Long userId, Long actorId, Long docId, Long commentId, String type, String content) {
        if (userId == null || userId.equals(actorId)) return;
        UserNotification notification = new UserNotification();
        notification.setUserId(userId);
        notification.setActorId(actorId);
        notification.setDocId(docId);
        notification.setCommentId(commentId);
        notification.setType(type);
        notification.setContent(content.length() > 500 ? content.substring(0, 500) : content);
        notification.setIsRead(0);
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);
    }

    public void createAdminMessage(Long userId, Long adminId, String content) {
        UserNotification notification = new UserNotification();
        notification.setUserId(userId);
        notification.setActorId(adminId);
        notification.setType("ADMIN_MESSAGE");
        notification.setContent(content);
        notification.setIsRead(0);
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);
    }

    private UserNotification enrich(UserNotification notification) {
        User actor = notification.getActorId() == null ? null : userMapper.selectById(notification.getActorId());
        Document document = notification.getDocId() == null ? null : documentMapper.selectById(notification.getDocId());
        if (actor != null) notification.setActorName(actor.getNickname() == null || actor.getNickname().isBlank()
                ? actor.getUsername() : actor.getNickname());
        if (document != null) notification.setDocumentTitle(document.getTitle());
        return notification;
    }
}
