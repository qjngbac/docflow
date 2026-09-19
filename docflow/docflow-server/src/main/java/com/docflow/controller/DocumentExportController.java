package com.docflow.controller;

import com.docflow.dto.DocumentExportRequest;
import com.docflow.security.UserContext;
import com.docflow.service.DocumentExportService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

@RestController
@RequestMapping("/api/v1/docs/{docId}/export")
public class DocumentExportController {
    @Autowired private DocumentExportService exportService;

    @PostMapping(value = "/docx", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public Callable<ResponseEntity<byte[]>> exportDocx(@PathVariable Long docId,
                                                        @Valid @RequestBody(required = false) DocumentExportRequest request) {
        // UserContext 基于请求线程，必须在切换到导出线程池前取出用户身份和请求内容。
        Long userId = UserContext.getRequiredUserId();
        String content = request == null ? null : request.getContent();
        return () -> docxResponse(exportService.exportDocx(docId, userId, content));
    }

    private ResponseEntity<byte[]> docxResponse(DocumentExportService.ExportedDocument exported) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(exported.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(exported.content());
    }

    @PostMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public Callable<ResponseEntity<byte[]>> exportPdf(@PathVariable Long docId,
                                                       @Valid @RequestBody(required = false) DocumentExportRequest request) {
        // 异步任务只接收普通值，避免在线程池中访问已经结束的 HTTP 请求上下文。
        Long userId = UserContext.getRequiredUserId();
        String content = request == null ? null : request.getContent();
        return () -> pdfResponse(exportService.exportPdf(docId, userId, content));
    }

    private ResponseEntity<byte[]> pdfResponse(DocumentExportService.ExportedDocument exported) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(exported.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(exported.content());
    }
}
