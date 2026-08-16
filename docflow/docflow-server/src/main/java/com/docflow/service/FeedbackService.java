package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.entity.Document;
import com.docflow.entity.FeedbackImage;
import com.docflow.entity.User;
import com.docflow.entity.UserFeedback;
import com.docflow.mapper.DocumentMapper;
import com.docflow.mapper.FeedbackImageMapper;
import com.docflow.mapper.UserFeedbackMapper;
import com.docflow.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class FeedbackService {
    private static final List<String> TYPES = List.of("BUG", "UI", "SUGGESTION", "OTHER");
    private static final List<String> STATUSES = List.of("OPEN", "PROCESSING", "RESOLVED", "CLOSED");

    @Autowired private UserFeedbackMapper feedbackMapper;
    @Autowired private FeedbackImageMapper imageMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private DocumentMapper documentMapper;
    @Autowired private FileService fileService;
    @Autowired private PermissionService permissionService;
    @Autowired private NotificationService notificationService;

    @Transactional
    public UserFeedback create(Long userId, Long docId, String type, String description, MultipartFile[] images) {
        String normalizedDescription = description == null ? "" : description.trim();
        if (!StringUtils.hasText(normalizedDescription) || normalizedDescription.length() > 5000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "问题描述需要填写，且不能超过 5000 字");
        }
        String normalizedType = normalize(type, TYPES, "反馈类型不正确");
        if (docId != null) permissionService.requireReadable(docId, userId);
        MultipartFile[] files = images == null ? new MultipartFile[0] : images;
        if (files.length > 6) throw new BusinessException(ErrorCode.BAD_REQUEST, "一次最多上传 6 张图片");

        UserFeedback feedback = new UserFeedback();
        feedback.setUserId(userId);
        feedback.setDocId(docId);
        feedback.setType(normalizedType);
        feedback.setDescription(normalizedDescription);
        feedback.setStatus("OPEN");
        feedback.setCreatedAt(LocalDateTime.now());
        feedback.setUpdatedAt(LocalDateTime.now());
        feedbackMapper.insert(feedback);

        for (MultipartFile file : files) {
            FeedbackImage image = new FeedbackImage();
            image.setFeedbackId(feedback.getId());
            image.setUserId(userId);
            image.setFileUrl(fileService.uploadFeedbackImage(file));
            image.setOriginalName(file.getOriginalFilename());
            image.setFileSize(file.getSize());
            image.setMimeType(file.getContentType());
            image.setCreatedAt(LocalDateTime.now());
            imageMapper.insert(image);
        }
        return enrich(feedback);
    }

    public List<UserFeedback> listMine(Long userId) {
        return feedbackMapper.selectList(new LambdaQueryWrapper<UserFeedback>()
                        .eq(UserFeedback::getUserId, userId)
                        .orderByDesc(UserFeedback::getCreatedAt)
                        .last("LIMIT 100"))
                .stream().map(this::enrich).toList();
    }

    public List<UserFeedback> listForAdmin(String status, String type) {
        LambdaQueryWrapper<UserFeedback> query = new LambdaQueryWrapper<UserFeedback>()
                .orderByAsc(UserFeedback::getStatus)
                .orderByDesc(UserFeedback::getCreatedAt)
                .last("LIMIT 200");
        if (StringUtils.hasText(status)) query.eq(UserFeedback::getStatus, normalize(status, STATUSES, "反馈状态不正确"));
        if (StringUtils.hasText(type)) query.eq(UserFeedback::getType, normalize(type, TYPES, "反馈类型不正确"));
        return feedbackMapper.selectList(query).stream().map(this::enrich).toList();
    }

    @Transactional
    public UserFeedback handle(Long feedbackId, Long adminId, String status, String adminReply) {
        UserFeedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) throw new BusinessException(ErrorCode.NOT_FOUND, "反馈记录不存在");
        String normalizedStatus = normalize(status, STATUSES, "反馈状态不正确");
        String normalizedReply = adminReply == null ? null : adminReply.trim();
        if (normalizedReply != null && normalizedReply.length() > 2000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "处理回复不能超过 2000 字");
        }
        feedback.setStatus(normalizedStatus);
        feedback.setAdminReply(StringUtils.hasText(normalizedReply) ? normalizedReply : null);
        feedback.setHandlerId(adminId);
        feedback.setHandledAt(LocalDateTime.now());
        feedback.setUpdatedAt(LocalDateTime.now());
        feedbackMapper.updateById(feedback);
        String message = "您的问题反馈已更新为“" + statusLabel(normalizedStatus) + "”";
        if (StringUtils.hasText(normalizedReply)) message += "：" + normalizedReply;
        notificationService.create(feedback.getUserId(), adminId, feedback.getDocId(), null,
                "FEEDBACK_UPDATED", message);
        return enrich(feedback);
    }

    private UserFeedback enrich(UserFeedback feedback) {
        User user = userMapper.selectById(feedback.getUserId());
        Document document = feedback.getDocId() == null ? null : documentMapper.selectById(feedback.getDocId());
        feedback.setUsername(user == null ? null : user.getUsername());
        feedback.setNickname(user == null ? null : user.getNickname());
        feedback.setDocumentTitle(document == null ? null : document.getTitle());
        feedback.setImages(imageMapper.selectList(new LambdaQueryWrapper<FeedbackImage>()
                .eq(FeedbackImage::getFeedbackId, feedback.getId())
                .orderByAsc(FeedbackImage::getId)));
        return feedback;
    }

    private String normalize(String value, List<String> allowed, String message) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : allowed.get(0);
        if (!allowed.contains(normalized)) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        return normalized;
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "PROCESSING" -> "处理中";
            case "RESOLVED" -> "已解决";
            case "CLOSED" -> "已关闭";
            default -> "待处理";
        };
    }
}
