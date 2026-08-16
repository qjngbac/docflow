package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.entity.DocPermission;
import com.docflow.entity.Document;
import com.docflow.mapper.DocPermissionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ShareService {

    @Autowired
    private DocPermissionMapper permissionMapper;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public String createShareLink(Long docId, Long userId, String permissionName,
                                  String password, Integer expireHours) {
        permissionService.requireAdmin(docId, userId);

        String permission = StringUtils.hasText(permissionName)
                ? permissionName.trim().toUpperCase(Locale.ROOT)
                : "READ";
        if (!List.of("READ", "WRITE").contains(permission)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Share permission must be READ or WRITE");
        }

        DocPermission share = new DocPermission();
        share.setDocId(docId);
        share.setPermission(permission);
        share.setShareToken(UUID.randomUUID().toString().replace("-", ""));
        share.setSharePassword(StringUtils.hasText(password) ? passwordEncoder.encode(password) : null);
        share.setCreatedBy(userId);
        if (expireHours != null && expireHours > 0) {
            share.setExpireTime(LocalDateTime.now().plusHours(expireHours));
        }
        permissionMapper.insert(share);
        return share.getShareToken();
    }

    public Document getDocByShareToken(String shareToken, String password) {
        return permissionService.getByShareToken(shareToken, password);
    }

    public List<DocPermission> getShareLinks(Long docId, Long userId) {
        permissionService.requireAdmin(docId, userId);
        return permissionMapper.selectList(new LambdaQueryWrapper<DocPermission>()
                .eq(DocPermission::getDocId, docId)
                .isNotNull(DocPermission::getShareToken)
                .orderByDesc(DocPermission::getCreatedAt));
    }

    public void deleteShareLink(Long docId, Long userId, Long permissionId) {
        permissionService.requireAdmin(docId, userId);
        DocPermission share = permissionMapper.selectById(permissionId);
        if (share == null || !docId.equals(share.getDocId()) || share.getShareToken() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Share link not found");
        }
        permissionMapper.deleteById(permissionId);
    }

    public Document getDocById(Long docId) {
        return permissionService.requireDocument(docId);
    }
}
