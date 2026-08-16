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

@RestController
@RequestMapping("/api/v1/docs/{docId}/export")
public class DocumentExportController {
    @Autowired private DocumentExportService exportService;

    @PostMapping(value = "/docx", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> exportDocx(@PathVariable Long docId,
                                              @Valid @RequestBody(required = false) DocumentExportRequest request) {
        var exported = exportService.exportDocx(docId, UserContext.getRequiredUserId(),
                request == null ? null : request.getContent());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(exported.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(exported.content());
    }

    @PostMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long docId,
                                             @Valid @RequestBody(required = false) DocumentExportRequest request) {
        var exported = exportService.exportPdf(docId, UserContext.getRequiredUserId(),
                request == null ? null : request.getContent());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(exported.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(exported.content());
    }
}
