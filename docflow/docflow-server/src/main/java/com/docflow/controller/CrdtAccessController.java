package com.docflow.controller;

import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.common.Result;
import com.docflow.entity.Document;
import com.docflow.entity.User;
import com.docflow.security.UserContext;
import com.docflow.service.PermissionService;
import com.docflow.service.DocService;
import com.docflow.service.UserService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/v1/docs/{docId}/crdt")
public class CrdtAccessController {

    @Autowired private PermissionService permissionService;
    @Autowired private UserService userService;
    @Autowired private DocService docService;
    @Value("${app.crdt.admin-secret}") private String crdtAdminSecret;

    @GetMapping("/access")
    public Result<CrdtAccess> access(@PathVariable Long docId) {
        Long userId = UserContext.getRequiredUserId();
        Document document = permissionService.requireReadable(docId, userId);
        if (!"CRDT".equals(document.getCollabMode())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Document is not in CRDT collaboration mode");
        }
        User user = userService.getById(userId);
        return Result.success(new CrdtAccess(
                1,
                document.getId(),
                userId,
                user.getNickname() == null ? user.getUsername() : user.getNickname(),
                user.getAvatar(),
                permissionService.canWrite(document, userId),
                permissionService.canComment(document, userId),
                permissionService.canAdmin(document, userId),
                document.getContent(),
                document.getContentFormat()));
    }

    @PutMapping("/snapshot")
    public Result<Void> updateSnapshot(@PathVariable Long docId,
                                       @RequestHeader(value = "X-CRDT-Admin-Secret", required = false) String suppliedSecret,
                                       @RequestBody CrdtSnapshot snapshot) {
        if (!constantTimeEquals(crdtAdminSecret, suppliedSecret)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "CRDT 服务身份校验失败");
        }
        docService.updateCrdtSnapshotFromCollaboration(docId, snapshot.getEditorUserId(), snapshot.getHtml());
        return Result.success();
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        if (expected == null || supplied == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    @Data
    public static class CrdtSnapshot {
        private String html;
        private Long editorUserId;
    }

    @Data
    @AllArgsConstructor
    public static class CrdtAccess {
        private int protocolVersion;
        private Long documentId;
        private Long userId;
        private String displayName;
        private String avatar;
        private boolean writable;
        private boolean commentable;
        private boolean admin;
        private String initialContent;
        private String initialContentFormat;
    }
}
