package com.docflow.controller;

import com.docflow.common.Result;
import com.docflow.entity.Document;
import com.docflow.entity.User;
import com.docflow.security.UserContext;
import com.docflow.service.DocService;
import com.docflow.service.PermissionService;
import com.docflow.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class CrdtAccessControllerTest {

    @Mock private PermissionService permissionService;
    @Mock private UserService userService;
    @Mock private DocService docService;

    @InjectMocks private CrdtAccessController controller;

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void ownerReceivesWritableCrdtAccessImmediatelyAfterCreation() {
        Document document = new Document();
        document.setId(41L);
        document.setOwnerId(4L);
        document.setCollabMode("CRDT");
        document.setContent("<p>Initial</p>");
        document.setContentFormat("HTML");
        User user = new User();
        user.setId(4L);
        user.setUsername("owner");
        user.setNickname("Owner");
        when(permissionService.requireReadable(41L, 4L)).thenReturn(document);
        when(permissionService.canWrite(document, 4L)).thenReturn(true);
        when(userService.getById(4L)).thenReturn(user);
        UserContext.set(4L, "owner");

        Result<CrdtAccessController.CrdtAccess> result = controller.access(41L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getDocumentId()).isEqualTo(41L);
        assertThat(result.getData().getUserId()).isEqualTo(4L);
        assertThat(result.getData().isWritable()).isTrue();
        assertThat(result.getData().getInitialContent()).isEqualTo("<p>Initial</p>");
    }

    @Test
    void collaborationServiceSecretIsRequiredForMaterializedSnapshot() {
        ReflectionTestUtils.setField(controller, "crdtAdminSecret", "internal-secret");
        CrdtAccessController.CrdtSnapshot snapshot = new CrdtAccessController.CrdtSnapshot();
        snapshot.setHtml("<p>saved</p>");
        snapshot.setEditorUserId(4L);

        assertThatThrownBy(() -> controller.updateSnapshot(41L, "wrong-secret", snapshot))
                .isInstanceOfSatisfying(com.docflow.common.BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(403));

        controller.updateSnapshot(41L, "internal-secret", snapshot);
        verify(docService).updateCrdtSnapshotFromCollaboration(41L, 4L, "<p>saved</p>");
    }
}
