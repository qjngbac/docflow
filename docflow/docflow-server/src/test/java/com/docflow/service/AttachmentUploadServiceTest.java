package com.docflow.service;

import com.docflow.common.BusinessException;
import com.docflow.dto.AttachmentUploadInitRequest;
import com.docflow.entity.AttachmentUploadSession;
import com.docflow.mapper.AttachmentUploadSessionMapper;
import com.docflow.mapper.FileAttachmentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AttachmentUploadServiceTest {
    @TempDir Path directory;

    @Test
    void rejectsChunkExceedingDeclaredFileSizeBeforeWriting() {
        var sessions = mock(AttachmentUploadSessionMapper.class);
        var service = new AttachmentUploadService(sessions, mock(FileAttachmentMapper.class),
                mock(PermissionService.class), mock(FileService.class));
        ReflectionTestUtils.setField(service, "uploadDir", directory.toString());
        var session = new AttachmentUploadSession();
        session.setId("test-upload"); session.setDocId(1L); session.setUserId(2L);
        session.setStatus("UPLOADING"); session.setTotalSize(1L); session.setTotalChunks(1);
        session.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(sessions.lockById("test-upload")).thenReturn(session);

        assertThatThrownBy(() -> service.uploadChunk(1L, "test-upload", 0, 2L,
                new MockMultipartFile("file", "part", "application/octet-stream", new byte[]{1, 2})))
                .isInstanceOf(BusinessException.class);
        assertThat(Files.exists(directory.resolve(".chunks/test-upload/0.part"))).isFalse();
    }

    @Test
    void rejectsImpossibleChunkLayout() {
        var service = new AttachmentUploadService(mock(AttachmentUploadSessionMapper.class),
                mock(FileAttachmentMapper.class), mock(PermissionService.class), mock(FileService.class));
        var request = new AttachmentUploadInitRequest();
        request.setFileName("data.txt"); request.setSize(1L); request.setTotalChunks(4096);
        assertThatThrownBy(() -> service.start(1L, 2L, request)).isInstanceOf(BusinessException.class);
    }
}
