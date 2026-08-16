package com.docflow.service;

import com.docflow.common.BusinessException;
import com.docflow.entity.Document;
import com.docflow.entity.DocPermission;
import com.docflow.mapper.DocPermissionMapper;
import com.docflow.mapper.DocumentMapper;
import com.docflow.mapper.UserMapper;
import com.docflow.security.SignedFileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock private DocumentMapper documentMapper;
    @Mock private DocPermissionMapper docPermissionMapper;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SignedFileService signedFileService;

    @InjectMocks private PermissionService permissionService;

    @Test
    void ownerHasReadWriteAndAdminWithoutExplicitPermissionRecord() {
        Document document = document(30L, 4L);

        assertThat(permissionService.canRead(document, 4L)).isTrue();
        assertThat(permissionService.canWrite(document, 4L)).isTrue();
        assertThat(permissionService.canAdmin(document, 4L)).isTrue();
        verify(docPermissionMapper, never()).selectOne(any());
    }

    @Test
    void unrelatedUserCannotReadDocument() {
        Document document = document(30L, 4L);
        when(documentMapper.selectById(30L)).thenReturn(document);
        when(docPermissionMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> permissionService.requireReadable(30L, 5L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(403));
    }

    @Test
    void commentPermissionCanReadAndCommentButCannotEditBody() {
        Document document = document(30L, 4L);
        DocPermission permission = new DocPermission();
        permission.setDocId(30L);
        permission.setUserId(5L);
        permission.setPermission("COMMENT");
        when(docPermissionMapper.selectOne(any())).thenReturn(permission);

        assertThat(permissionService.canRead(document, 5L)).isTrue();
        assertThat(permissionService.canComment(document, 5L)).isTrue();
        assertThat(permissionService.canWrite(document, 5L)).isFalse();
        assertThat(permissionService.canAdmin(document, 5L)).isFalse();
    }

    @Test
    void legacyPlaintextSharePasswordIsUpgradedAfterSuccessfulAccess() {
        DocPermission permission = new DocPermission();
        permission.setId(12L);
        permission.setDocId(30L);
        permission.setShareToken("share-token");
        permission.setSharePassword("old-password");
        Document document = document(30L, 4L);
        when(docPermissionMapper.selectOne(any())).thenReturn(permission);
        when(documentMapper.selectById(30L)).thenReturn(document);
        when(passwordEncoder.encode("old-password")).thenReturn("$2a$10$upgraded");
        when(signedFileService.rewriteContent(document.getContent())).thenReturn(document.getContent());

        assertThat(permissionService.getByShareToken("share-token", "old-password")).isSameAs(document);
        assertThat(permission.getSharePassword()).startsWith("$2a$");
        verify(docPermissionMapper).updateById(permission);
    }

    private Document document(Long id, Long ownerId) {
        Document document = new Document();
        document.setId(id);
        document.setOwnerId(ownerId);
        document.setCollabMode("CRDT");
        return document;
    }
}
