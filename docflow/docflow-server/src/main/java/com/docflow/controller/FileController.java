package com.docflow.controller;

import com.docflow.common.Result;
import com.docflow.service.FileService;
import com.docflow.service.FileAccessService;
import com.docflow.service.PermissionService;
import com.docflow.security.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    @Autowired
    private FileService fileService;
    @Autowired private PermissionService permissionService;
    @Autowired private FileAccessService fileAccessService;

    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                            @RequestParam(required = false) Long docId) {
        Long userId = UserContext.getRequiredUserId();
        if (docId != null) permissionService.requireWritable(docId, userId);
        String url = fileService.upload(file);
        if (docId != null) fileAccessService.associateDocumentReferences(docId, null, url, userId);
        return Result.success(Map.of("url", url));
    }
}
