package com.docflow.controller;

import com.docflow.dto.DocumentExportRequest;
import com.docflow.security.UserContext;
import com.docflow.service.DocumentExportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentExportControllerTest {

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void exportDocxCapturesCurrentUserBeforeAsyncExecution() throws Exception {
        DocumentExportService service = mock(DocumentExportService.class);
        DocumentExportController controller = new DocumentExportController();
        ReflectionTestUtils.setField(controller, "exportService", service);
        DocumentExportRequest request = new DocumentExportRequest();
        request.setContent("<p>content</p>");
        byte[] bytes = new byte[]{1, 2, 3};
        when(service.exportDocx(9L, 7L, "<p>content</p>"))
                .thenReturn(new DocumentExportService.ExportedDocument("test.docx", bytes));
        UserContext.set(7L, "owner");

        Callable<ResponseEntity<byte[]>> task = controller.exportDocx(9L, request);
        UserContext.clear();
        ResponseEntity<byte[]> response = task.call();

        verify(service).exportDocx(9L, 7L, "<p>content</p>");
        assertEquals(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                response.getHeaders().getContentType());
        assertArrayEquals(bytes, response.getBody());
    }
}
