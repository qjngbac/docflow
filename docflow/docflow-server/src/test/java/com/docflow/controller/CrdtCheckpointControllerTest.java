package com.docflow.controller;

import com.docflow.common.Result;
import com.docflow.security.UserContext;
import com.docflow.service.CrdtCheckpointService;
import com.docflow.service.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrdtCheckpointControllerTest {

    @Mock private PermissionService permissionService;
    @Mock private CrdtCheckpointService checkpointService;

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void adminCanInspectAndCompactCheckpoint() {
        CrdtCheckpointController controller = new CrdtCheckpointController(permissionService, checkpointService);
        UserContext.set(8L, "admin");
        when(checkpointService.status(21L)).thenReturn(Map.of("pendingUpdates", 4));
        when(checkpointService.checkpoint(21L)).thenReturn(Map.of("pendingUpdates", 0));

        Result<Map<String, Object>> before = controller.status(21L);
        Result<Map<String, Object>> after = controller.checkpoint(21L);

        verify(permissionService, times(2)).requireAdmin(21L, 8L);
        assertThat(before.getData().get("pendingUpdates")).isEqualTo(4);
        assertThat(after.getData().get("pendingUpdates")).isEqualTo(0);
    }
}
