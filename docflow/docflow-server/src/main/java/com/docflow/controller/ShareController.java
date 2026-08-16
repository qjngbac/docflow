package com.docflow.controller;

import com.docflow.common.Result;
import com.docflow.entity.Document;
import com.docflow.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import lombok.Data;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/share")
public class ShareController {

    @Autowired
    private PermissionService permissionService;

    @GetMapping("/{token}")
    public Result<Document> getByToken(@PathVariable String token) {
        return Result.success(permissionService.getByShareToken(token, null));
    }

    @PostMapping("/{token}/access")
    public Result<Document> access(@PathVariable String token,
                                   @RequestBody(required = false) ShareAccessRequest request) {
        return Result.success(permissionService.getByShareToken(token,
                request == null ? null : request.getPassword()));
    }

    @Data
    public static class ShareAccessRequest {
        private String password;
    }
}
