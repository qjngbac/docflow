package com.docflow.controller;

import com.docflow.common.Result;
import com.docflow.security.UserContext;
import com.docflow.service.CrdtCheckpointService;
import com.docflow.service.PermissionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/docs/{docId}/crdt/checkpoint")
public class CrdtCheckpointController {

    private final PermissionService permissionService;
    private final CrdtCheckpointService checkpointService;

    public CrdtCheckpointController(PermissionService permissionService, CrdtCheckpointService checkpointService) {
        this.permissionService = permissionService;
        this.checkpointService = checkpointService;
    }

    @GetMapping
    public Result<Map<String, Object>> status(@PathVariable Long docId) {
        permissionService.requireAdmin(docId, UserContext.getRequiredUserId());
        return Result.success(checkpointService.status(docId));
    }

    @PostMapping
    public Result<Map<String, Object>> checkpoint(@PathVariable Long docId) {
        permissionService.requireAdmin(docId, UserContext.getRequiredUserId());
        return Result.success(checkpointService.checkpoint(docId));
    }

    @GetMapping("/history")
    public Result<Map<String, Object>> history(@PathVariable Long docId) {
        permissionService.requireAdmin(docId, UserContext.getRequiredUserId());
        return Result.success(checkpointService.history(docId));
    }

    @PostMapping("/history/{checkpointId}/restore")
    public Result<Map<String, Object>> restore(@PathVariable Long docId, @PathVariable Long checkpointId) {
        permissionService.requireAdmin(docId, UserContext.getRequiredUserId());
        return Result.success(checkpointService.restore(docId, checkpointId));
    }
}
