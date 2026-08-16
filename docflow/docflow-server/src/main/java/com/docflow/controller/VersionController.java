package com.docflow.controller;

import com.docflow.common.Result;
import com.docflow.entity.DocVersion;
import com.docflow.entity.Document;
import com.docflow.dto.VersionSaveRequest;
import com.docflow.security.UserContext;
import com.docflow.service.CollaborationService;
import com.docflow.service.VersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/v1/docs/{docId}/versions")
public class VersionController {

    @Autowired
    private VersionService versionService;

    @Autowired
    private CollaborationService collaborationService;

    @PostMapping
    public Result<Void> saveVersion(@PathVariable Long docId,
                                    @Valid @RequestBody(required = false) VersionSaveRequest request) {
        collaborationService.flushDocument(docId, false);
        versionService.saveVersion(docId, UserContext.getRequiredUserId(),
                request == null ? null : request.getContent(),
                request == null ? null : request.getName(),
                request == null ? null : request.getDescription());
        return Result.success();
    }

    @GetMapping
    public Result<List<DocVersion>> list(@PathVariable Long docId) {
        return Result.success(versionService.getVersionList(
                docId, UserContext.getRequiredUserId()));
    }

    @GetMapping("/{versionNum}")
    public Result<DocVersion> detail(@PathVariable Long docId,
                                     @PathVariable Integer versionNum) {
        return Result.success(versionService.getVersion(
                docId, versionNum, UserContext.getRequiredUserId()));
    }

    @PostMapping("/{versionNum}/rollback")
    public Result<Document> rollback(@PathVariable Long docId,
                                     @PathVariable Integer versionNum) {
        collaborationService.flushDocument(docId);
        return Result.success(versionService.rollback(
                docId, versionNum, UserContext.getRequiredUserId()));
    }

    @DeleteMapping
    public Result<Void> deleteAll(@PathVariable Long docId) {
        collaborationService.flushDocument(docId, false);
        versionService.deleteVersions(docId, UserContext.getRequiredUserId());
        return Result.success();
    }
}
