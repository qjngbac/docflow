package com.docflow.controller;

import com.docflow.common.Result;
import com.docflow.dto.AttachmentUploadInitRequest;
import com.docflow.entity.AttachmentUploadSession;
import com.docflow.entity.FileAttachment;
import com.docflow.security.UserContext;
import com.docflow.service.AttachmentUploadService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/docs/{docId}/attachments/uploads")
public class AttachmentUploadController {
    private final AttachmentUploadService uploadService;
    public AttachmentUploadController(AttachmentUploadService uploadService) { this.uploadService = uploadService; }

    @PostMapping public Result<AttachmentUploadSession> start(@PathVariable Long docId, @Valid @RequestBody AttachmentUploadInitRequest request) { return Result.success(uploadService.start(docId, UserContext.getRequiredUserId(), request)); }
    @PutMapping("/{uploadId}/chunks/{chunkIndex}") public Result<AttachmentUploadSession> chunk(@PathVariable Long docId, @PathVariable String uploadId, @PathVariable int chunkIndex, @RequestParam("file") MultipartFile file) { return Result.success(uploadService.uploadChunk(docId, uploadId, chunkIndex, UserContext.getRequiredUserId(), file)); }
    @PostMapping("/{uploadId}/complete") public Result<FileAttachment> complete(@PathVariable Long docId, @PathVariable String uploadId) { return Result.success(uploadService.complete(docId, uploadId, UserContext.getRequiredUserId())); }
}
