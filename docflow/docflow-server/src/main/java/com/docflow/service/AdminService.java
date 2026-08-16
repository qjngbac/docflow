package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.entity.AttachmentUploadSession;
import com.docflow.entity.Document;
import com.docflow.entity.DocumentTemplate;
import com.docflow.entity.FileAttachment;
import com.docflow.entity.OperationLog;
import com.docflow.entity.User;
import com.docflow.entity.UserFeedback;
import com.docflow.entity.UserSession;
import com.docflow.mapper.AttachmentUploadSessionMapper;
import com.docflow.mapper.DocumentMapper;
import com.docflow.mapper.DocumentTemplateMapper;
import com.docflow.mapper.FileAttachmentMapper;
import com.docflow.mapper.OperationLogMapper;
import com.docflow.mapper.UserFeedbackMapper;
import com.docflow.mapper.UserMapper;
import com.docflow.mapper.UserSessionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminService {
    @Autowired private UserMapper userMapper;
    @Autowired private UserSessionMapper sessionMapper;
    @Autowired private DocumentMapper documentMapper;
    @Autowired private UserFeedbackMapper feedbackMapper;
    @Autowired private FileAttachmentMapper attachmentMapper;
    @Autowired private AttachmentUploadSessionMapper uploadSessionMapper;
    @Autowired private OperationLogMapper operationLogMapper;
    @Autowired private DocumentTemplateMapper templateMapper;
    @Autowired private AccountSecurityService accountSecurityService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Value("${app.trash.retention-days:30}") private int trashRetentionDays;

    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("users", userMapper.selectCount(new LambdaQueryWrapper<>()));
        data.put("disabledUsers", userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getStatus, 0)));
        data.put("documents", documentMapper.selectCount(new LambdaQueryWrapper<Document>().eq(Document::getIsDeleted, 0)));
        data.put("trashDocuments", documentMapper.selectCount(new LambdaQueryWrapper<Document>().eq(Document::getIsDeleted, 1)));
        data.put("openFeedback", feedbackMapper.selectCount(new LambdaQueryWrapper<UserFeedback>()
                .in(UserFeedback::getStatus, List.of("OPEN", "PROCESSING"))));
        data.put("activeSessions", sessionMapper.selectCount(new LambdaQueryWrapper<UserSession>()
                .isNull(UserSession::getRevokedAt).gt(UserSession::getExpiresAt, LocalDateTime.now())));
        data.put("attachments", attachmentMapper.selectCount(new LambdaQueryWrapper<>()));
        data.put("attachmentBytes", sum("SELECT COALESCE(SUM(file_size),0) FROM file_attachment"));
        return data;
    }

    public List<AdminUserView> users(String query, Integer status, String role) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .orderByDesc(User::getCreatedAt).last("LIMIT 200");
        if (StringUtils.hasText(query)) {
            String value = query.trim();
            wrapper.and(item -> item.like(User::getUsername, value)
                    .or().like(User::getNickname, value).or().like(User::getEmail, value));
        }
        if (status != null) wrapper.eq(User::getStatus, status);
        if (StringUtils.hasText(role)) wrapper.eq(User::getSystemRole, role.trim().toUpperCase(Locale.ROOT));
        return userMapper.selectList(wrapper).stream().map(user -> new AdminUserView(
                user.getId(), user.getUsername(), user.getNickname(), user.getEmail(), user.getAvatar(),
                user.getStatus(), user.getSystemRole(), user.getBanReason(), user.getBannedAt(),
                user.getBanExpiresAt(), user.getLastLoginAt(), user.getCreatedAt())).toList();
    }

    @Transactional
    public void setUserStatus(Long actorId, Long userId, boolean enabled, String reason, LocalDateTime expiresAt) {
        if (actorId.equals(userId) && !enabled) throw new BusinessException(ErrorCode.BAD_REQUEST, "不能封禁当前管理员账号");
        User target = requireUser(userId);
        userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, userId)
                .set(User::getStatus, enabled ? 1 : 0)
                .set(User::getBanReason, enabled ? null : normalizeReason(reason))
                .set(User::getBannedAt, enabled ? null : LocalDateTime.now())
                .set(User::getBanExpiresAt, enabled ? null : expiresAt));
        if (!enabled) accountSecurityService.revokeAllSessions(userId, null);
        audit(actorId, enabled ? "ADMIN_USER_ENABLE" : "ADMIN_USER_BAN",
                target.getUsername() + (enabled ? "" : "；原因：" + normalizeReason(reason)));
    }

    @Transactional
    public void setUserRole(Long actorId, Long userId, String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (!List.of("USER", "ADMIN").contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统角色只能是 USER 或 ADMIN");
        }
        if (actorId.equals(userId) && !"ADMIN".equals(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能取消当前账号的管理员身份");
        }
        User target = requireUser(userId);
        userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, userId)
                .set(User::getSystemRole, normalized));
        audit(actorId, "ADMIN_USER_ROLE", target.getUsername() + " -> " + normalized);
    }

    public int revokeSessions(Long actorId, Long userId) {
        User target = requireUser(userId);
        int count = accountSecurityService.revokeAllSessions(userId, null);
        audit(actorId, "ADMIN_REVOKE_SESSIONS", target.getUsername() + "；设备数：" + count);
        return count;
    }

    public List<AdminLogView> logs(String action) {
        LambdaQueryWrapper<OperationLog> query = new LambdaQueryWrapper<OperationLog>()
                .orderByDesc(OperationLog::getCreatedAt).last("LIMIT 300");
        if (StringUtils.hasText(action)) query.likeRight(OperationLog::getAction, action.trim());
        return operationLogMapper.selectList(query).stream().map(log -> {
            User user = userMapper.selectById(log.getUserId());
            return new AdminLogView(log.getId(), log.getUserId(), user == null ? null : user.getUsername(),
                    log.getDocId(), log.getAction(), log.getDetail(), log.getIp(), log.getCreatedAt());
        }).toList();
    }

    public List<DocumentTemplate> systemTemplates() {
        return templateMapper.selectList(new LambdaQueryWrapper<DocumentTemplate>()
                .isNull(DocumentTemplate::getOwnerId).orderByAsc(DocumentTemplate::getName));
    }

    @Transactional
    public DocumentTemplate saveSystemTemplate(Long actorId, Long id, DocumentTemplate request) {
        if (!StringUtils.hasText(request.getName()) || request.getName().trim().length() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模板名称不能为空且不能超过 100 字");
        }
        DocumentTemplate template = id == null ? new DocumentTemplate() : templateMapper.selectById(id);
        if (template == null || template.getOwnerId() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "系统模板不存在");
        }
        template.setOwnerId(null);
        template.setName(request.getName().trim());
        template.setDescription(request.getDescription());
        template.setCategory(request.getCategory());
        template.setContent(request.getContent() == null ? "" : request.getContent());
        template.setContentFormat("HTML".equals(request.getContentFormat()) ? "HTML" : "MARKDOWN");
        if (id == null) templateMapper.insert(template); else templateMapper.updateById(template);
        audit(actorId, id == null ? "ADMIN_TEMPLATE_CREATE" : "ADMIN_TEMPLATE_UPDATE", template.getName());
        return template;
    }

    @Transactional
    public void deleteSystemTemplate(Long actorId, Long id) {
        DocumentTemplate template = templateMapper.selectById(id);
        if (template == null || template.getOwnerId() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "系统模板不存在");
        }
        templateMapper.deleteById(id);
        audit(actorId, "ADMIN_TEMPLATE_DELETE", template.getName());
    }

    public Map<String, Object> systemStatus() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("trashRetentionDays", trashRetentionDays);
        data.put("pendingUploads", uploadSessionMapper.selectCount(new LambdaQueryWrapper<AttachmentUploadSession>()
                .eq(AttachmentUploadSession::getStatus, "UPLOADING")));
        data.put("attachments", attachmentMapper.selectCount(new LambdaQueryWrapper<FileAttachment>()));
        data.put("attachmentBytes", sum("SELECT COALESCE(SUM(file_size),0) FROM file_attachment"));
        data.put("feedbackImages", count("feedback_image"));
        data.put("crdtUpdates", count("doc_crdt_update"));
        data.put("crdtCheckpoints", count("crdt_checkpoint"));
        data.put("crdtCheckpointHistory", count("crdt_checkpoint_history"));
        return data;
    }

    public void audit(Long actorId, String action, String detail) {
        OperationLog log = new OperationLog();
        log.setUserId(actorId);
        log.setAction(action);
        log.setDetail(detail != null && detail.length() > 500 ? detail.substring(0, 500) : detail);
        log.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        return user;
    }

    private String normalizeReason(String reason) {
        String value = StringUtils.hasText(reason) ? reason.trim() : "违反平台使用规范";
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private long count(String table) {
        try { return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
        catch (Exception ignored) { return 0L; }
    }

    private long sum(String sql) {
        try { return jdbcTemplate.queryForObject(sql, Long.class); }
        catch (Exception ignored) { return 0L; }
    }

    public record AdminUserView(Long id, String username, String nickname, String email, String avatar,
                                Integer status, String systemRole, String banReason, LocalDateTime bannedAt,
                                LocalDateTime banExpiresAt, LocalDateTime lastLoginAt, LocalDateTime createdAt) {}
    public record AdminLogView(Long id, Long userId, String username, Long docId, String action,
                               String detail, String ip, LocalDateTime createdAt) {}
}
