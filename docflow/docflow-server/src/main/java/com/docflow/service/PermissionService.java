package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.dto.ShareRequest;
import com.docflow.entity.DocPermission;
import com.docflow.entity.Document;
import com.docflow.entity.User;
import com.docflow.entity.OperationLog;
import com.docflow.mapper.DocPermissionMapper;
import com.docflow.mapper.DocumentMapper;
import com.docflow.mapper.UserMapper;
import com.docflow.mapper.OperationLogMapper;
import com.docflow.vo.ShareVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.docflow.security.SignedFileService;
import com.docflow.security.ClientIpResolver;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HexFormat;
import java.util.Locale;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 文档权限的唯一业务入口：所有者拥有隐式最高权限，协作者和分享链接使用显式权限记录。
 */
@Service
public class PermissionService {

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private DocPermissionMapper docPermissionMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SignedFileService signedFileService;

    @Autowired
    private CrdtCheckpointService crdtCheckpointService;

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClientIpResolver clientIpResolver;

    private final SecureRandom secureRandom = new SecureRandom();

    /** 常规访问统一隐藏回收站文档，防止旧链接或旧连接继续读取。 */
    public Document requireDocument(Long docId) {
        Document document = requireDocumentIncludingDeleted(docId);
        if (Integer.valueOf(1).equals(document.getIsDeleted())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Document not found");
        }
        return document;
    }

    public Document requireDocumentIncludingDeleted(Long docId) {
        Document document = documentMapper.selectById(docId);
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Document not found");
        }
        return document;
    }

    public Document requireAdminIncludingDeleted(Long docId, Long userId) {
        Document document = requireDocumentIncludingDeleted(docId);
        if (!canAdmin(document, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "No document admin permission");
        }
        return document;
    }

    public Document requireReadable(Long docId, Long userId) {
        Document document = requireDocument(docId);
        if (!canRead(document, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "No document read permission");
        }
        return document;
    }

    public Document requireWritable(Long docId, Long userId) {
        Document document = requireDocument(docId);
        if (!canWrite(document, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "No document write permission");
        }
        return document;
    }

    public Document requireCommentable(Long docId, Long userId) {
        Document document = requireDocument(docId);
        if (!canComment(document, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "No document comment permission");
        }
        return document;
    }

    public Document requireAdmin(Long docId, Long userId) {
        Document document = requireDocument(docId);
        if (!canAdmin(document, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "No document admin permission");
        }
        return document;
    }

    public boolean canRead(Document document, Long userId) {
        return document.getOwnerId().equals(userId) || hasPermission(document.getId(), userId,
                "READ", "COMMENT", "WRITE", "ADMIN");
    }

    public boolean canComment(Document document, Long userId) {
        return document.getOwnerId().equals(userId) || hasPermission(document.getId(), userId,
                "COMMENT", "WRITE", "ADMIN");
    }

    public boolean canWrite(Document document, Long userId) {
        return document.getOwnerId().equals(userId) || hasPermission(document.getId(), userId,
                "WRITE", "ADMIN");
    }

    public boolean canAdmin(Document document, Long userId) {
        return document.getOwnerId().equals(userId) || hasPermission(document.getId(), userId, "ADMIN");
    }

    @Transactional
    public ShareVO createShare(Long docId, Long operatorId, ShareRequest request) {
        requireAdmin(docId, operatorId);

        DocPermission permission = new DocPermission();
        permission.setDocId(docId);
        permission.setUserId(request.getUserId());
        permission.setPermission(normalizePermission(request.getPermission()));
        permission.setSharePassword(StringUtils.hasText(request.getPassword())
                ? passwordEncoder.encode(request.getPassword()) : null);
        permission.setExpireTime(request.getExpireTime());
        permission.setCreatedBy(operatorId);

        if (request.getUserId() == null) {
            permission.setShareToken(generateToken());
        }

        docPermissionMapper.insert(permission);
        audit(operatorId, docId, "PERMISSION_SHARE_CREATE", request.getUserId(), null, permission.getPermission());

        ShareVO vo = new ShareVO();
        vo.setToken(permission.getShareToken());
        vo.setUrl(permission.getShareToken() == null ? null : "/api/v1/share/" + permission.getShareToken());
        vo.setPermission(permission.getPermission());
        vo.setExpireTime(permission.getExpireTime());
        return vo;
    }

    public Document getByShareToken(String token, String password) {
        DocPermission permission = docPermissionMapper.selectOne(new LambdaQueryWrapper<DocPermission>()
                .eq(DocPermission::getShareToken, token));
        if (permission == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Share link not found");
        }
        if (permission.getExpireTime() != null && permission.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Share link expired");
        }
        if (StringUtils.hasText(permission.getSharePassword())) {
            if (!StringUtils.hasText(password) || !matchesSharePassword(password, permission)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Invalid share password");
            }
        }
        Document document = requireDocument(permission.getDocId());
        document.setContent(signedFileService.rewriteContent(document.getContent()));
        document.setCoverImage(signedFileService.sign(document.getCoverImage()));
        return document;
    }

    @Transactional
    public DocPermission addCollaborator(Long docId, Long operatorId,
                                         String username, String permissionName) {
        Document document = requireAdmin(docId, operatorId);
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Username is required");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username.trim())
                .last("LIMIT 1"));
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "User not found");
        }
        if (document.getOwnerId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Document owner already has full permission");
        }

        String permission = normalizePermission(permissionName);
        DocPermission existing = findCollaborator(docId, user.getId());
        if (existing != null) {
            String oldPermission = existing.getPermission();
            existing.setPermission(permission);
            docPermissionMapper.updateById(existing);
            if (!permission.equals(oldPermission)) {
                crdtCheckpointService.disconnect(docId);
                audit(operatorId, docId, "PERMISSION_COLLABORATOR_UPDATE", user.getId(), oldPermission, permission);
            }
            return existing;
        }

        DocPermission collaborator = new DocPermission();
        collaborator.setDocId(docId);
        collaborator.setUserId(user.getId());
        collaborator.setPermission(permission);
        collaborator.setCreatedBy(operatorId);
        docPermissionMapper.insert(collaborator);
        audit(operatorId, docId, "PERMISSION_COLLABORATOR_ADD", user.getId(), null, permission);
        return collaborator;
    }

    public List<CollaboratorInfo> getCollaborators(Long docId, Long operatorId) {
        requireAdmin(docId, operatorId);
        return docPermissionMapper.selectList(new LambdaQueryWrapper<DocPermission>()
                        .eq(DocPermission::getDocId, docId)
                        .isNotNull(DocPermission::getUserId)
                        .orderByAsc(DocPermission::getCreatedAt))
                .stream()
                .map(permission -> {
                    User user = userMapper.selectById(permission.getUserId());
                    return new CollaboratorInfo(
                            permission.getId(),
                            permission.getUserId(),
                            user == null ? null : user.getUsername(),
                            user == null ? null : user.getEmail(),
                            permission.getPermission(),
                            permission.getCreatedAt());
                })
                .toList();
    }

    @Transactional
    public void updatePermission(Long docId, Long operatorId,
                                 Long collaboratorUserId, String permissionName) {
        requireAdmin(docId, operatorId);
        DocPermission collaborator = requireCollaborator(docId, collaboratorUserId);
        String oldPermission = collaborator.getPermission();
        String newPermission = normalizePermission(permissionName);
        if (newPermission.equals(oldPermission)) return;
        collaborator.setPermission(newPermission);
        docPermissionMapper.updateById(collaborator);
        crdtCheckpointService.disconnect(docId);
        audit(operatorId, docId, "PERMISSION_COLLABORATOR_UPDATE", collaboratorUserId,
                oldPermission, newPermission);
    }

    @Transactional
    public void removeCollaborator(Long docId, Long operatorId, Long collaboratorUserId) {
        requireAdmin(docId, operatorId);
        DocPermission collaborator = requireCollaborator(docId, collaboratorUserId);
        docPermissionMapper.deleteById(collaborator.getId());
        crdtCheckpointService.disconnect(docId);
        audit(operatorId, docId, "PERMISSION_COLLABORATOR_REMOVE", collaboratorUserId,
                collaborator.getPermission(), null);
    }

    public List<Long> listAccessibleDocIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return docPermissionMapper.selectList(new LambdaQueryWrapper<DocPermission>()
                        .eq(DocPermission::getUserId, userId))
                .stream()
                .map(DocPermission::getDocId)
                .distinct()
                .toList();
    }

    private DocPermission requireCollaborator(Long docId, Long userId) {
        DocPermission collaborator = findCollaborator(docId, userId);
        if (collaborator == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Collaborator not found");
        }
        return collaborator;
    }

    private DocPermission findCollaborator(Long docId, Long userId) {
        if (userId == null) {
            return null;
        }
        return docPermissionMapper.selectOne(new LambdaQueryWrapper<DocPermission>()
                .eq(DocPermission::getDocId, docId)
                .eq(DocPermission::getUserId, userId)
                .last("LIMIT 1"));
    }

    private String normalizePermission(String permission) {
        String normalized = StringUtils.hasText(permission)
                ? permission.trim().toUpperCase(Locale.ROOT)
                : "READ";
        if (!List.of("READ", "COMMENT", "WRITE", "ADMIN").contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Permission must be READ, COMMENT, WRITE or ADMIN");
        }
        return normalized;
    }

    private boolean hasPermission(Long docId, Long userId, String... allowed) {
        if (userId == null) return false;
        DocPermission permission = findCollaborator(docId, userId);
        return permission != null && List.of(allowed).contains(permission.getPermission());
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private boolean matchesSharePassword(String rawPassword, DocPermission permission) {
        String stored = permission.getSharePassword();
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            return passwordEncoder.matches(rawPassword, stored);
        }
        if (!MessageDigest.isEqual(rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                stored.getBytes(java.nio.charset.StandardCharsets.UTF_8))) return false;
        permission.setSharePassword(passwordEncoder.encode(rawPassword));
        docPermissionMapper.updateById(permission);
        return true;
    }

    /** 权限变更与业务事务共同写入结构化操作日志，便于安全追溯。 */
    private void audit(Long operatorId, Long docId, String action, Long targetUserId,
                       String oldPermission, String newPermission) {
        OperationLog log = new OperationLog();
        log.setUserId(operatorId);
        log.setDocId(docId);
        log.setAction(action);
        var detail = objectMapper.createObjectNode();
        if (targetUserId != null) detail.put("targetUserId", targetUserId);
        if (oldPermission != null) detail.put("oldPermission", oldPermission);
        if (newPermission != null) detail.put("newPermission", newPermission);
        log.setDetail(detail.toString());
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            log.setIp(clientIpResolver.resolve(attributes.getRequest()));
        }
        log.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    @Data
    @AllArgsConstructor
    public static class CollaboratorInfo {
        private Long permissionId;
        private Long userId;
        private String username;
        private String email;
        private String permission;
        private LocalDateTime createdAt;
    }
}
