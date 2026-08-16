package com.docflow.controller;

import com.docflow.common.Result;
import com.docflow.entity.FileAttachment;
import com.docflow.security.UserContext;
import com.docflow.service.AttachmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/docs/{docId}/attachments")
public class AttachmentController {
    @Autowired private AttachmentService attachmentService;

    @GetMapping
    public Result<List<FileAttachment>> list(@PathVariable Long docId) {
        return Result.success(attachmentService.list(docId, UserContext.getRequiredUserId()));
    }

    @PostMapping
    public Result<FileAttachment> upload(@PathVariable Long docId, @RequestParam("file") MultipartFile file) {
        return Result.success(attachmentService.upload(docId, UserContext.getRequiredUserId(), file));
    }

    @DeleteMapping("/{attachmentId}")
    public Result<Void> delete(@PathVariable Long docId, @PathVariable Long attachmentId) {
        attachmentService.delete(docId, attachmentId, UserContext.getRequiredUserId());
        return Result.success();
    }
}
