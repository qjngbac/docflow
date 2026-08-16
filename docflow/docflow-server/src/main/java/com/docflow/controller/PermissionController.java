package com.docflow.controller;

import com.docflow.common.Result;
import com.docflow.entity.DocPermission;
import com.docflow.security.UserContext;
import com.docflow.service.PermissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/docs/{docId}/permissions")
public class PermissionController {

    @Autowired
    private PermissionService permissionService;

    @PostMapping
    public Result<DocPermission> addCollaborator(
            @PathVariable Long docId,
            @Valid @RequestBody AddCollaboratorRequest request) {
        return Result.success(permissionService.addCollaborator(
                docId,
                UserContext.getRequiredUserId(),
                request.getUsername(),
                request.getPermission()));
    }

    @GetMapping
    public Result<List<PermissionService.CollaboratorInfo>> list(@PathVariable Long docId) {
        return Result.success(permissionService.getCollaborators(
                docId, UserContext.getRequiredUserId()));
    }

    @PutMapping("/{collaboratorUserId}")
    public Result<Void> updatePermission(
            @PathVariable Long docId,
            @PathVariable Long collaboratorUserId,
            @Valid @RequestBody UpdatePermissionRequest request) {
        permissionService.updatePermission(
                docId,
                UserContext.getRequiredUserId(),
                collaboratorUserId,
                request.getPermission());
        return Result.success();
    }

    @DeleteMapping("/{collaboratorUserId}")
    public Result<Void> removeCollaborator(
            @PathVariable Long docId,
            @PathVariable Long collaboratorUserId) {
        permissionService.removeCollaborator(
                docId, UserContext.getRequiredUserId(), collaboratorUserId);
        return Result.success();
    }

    @Data
    public static class AddCollaboratorRequest {
        @NotBlank
        private String username;

        @Pattern(regexp = "READ|COMMENT|WRITE|ADMIN")
        private String permission = "READ";
    }

    @Data
    public static class UpdatePermissionRequest {
        @NotBlank
        @Pattern(regexp = "READ|COMMENT|WRITE|ADMIN")
        private String permission;
    }
}
