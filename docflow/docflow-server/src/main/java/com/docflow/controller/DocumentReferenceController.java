package com.docflow.controller;

import com.docflow.common.Result;
import com.docflow.dto.ReferenceDoiRequest;
import com.docflow.dto.ReferenceRequest;
import com.docflow.entity.DocumentReference;
import com.docflow.security.UserContext;
import com.docflow.service.DocumentReferenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/docs/{docId}/references")
public class DocumentReferenceController {
    private final DocumentReferenceService referenceService;
    public DocumentReferenceController(DocumentReferenceService referenceService) { this.referenceService = referenceService; }

    @GetMapping public Result<List<DocumentReference>> list(@PathVariable Long docId) { return Result.success(referenceService.list(docId, UserContext.getRequiredUserId())); }
    @PostMapping public Result<DocumentReference> create(@PathVariable Long docId, @Valid @RequestBody ReferenceRequest request) { return Result.success(referenceService.create(docId, UserContext.getRequiredUserId(), request)); }
    @PostMapping("/doi") public Result<DocumentReference> importDoi(@PathVariable Long docId, @Valid @RequestBody ReferenceDoiRequest request) { return Result.success(referenceService.importDoi(docId, UserContext.getRequiredUserId(), request)); }
    @DeleteMapping("/{referenceId}") public Result<Void> delete(@PathVariable Long docId, @PathVariable Long referenceId) { referenceService.delete(docId, referenceId, UserContext.getRequiredUserId()); return Result.success(); }
}
