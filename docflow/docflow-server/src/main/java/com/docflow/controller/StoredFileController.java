package com.docflow.controller;

import com.docflow.security.UserContext;
import com.docflow.service.FileAccessService;
import com.docflow.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
public class StoredFileController {
    private final FileService files;
    private final FileAccessService access;

    public StoredFileController(FileService files, FileAccessService access) {
        this.files = files;
        this.access = access;
    }

    @GetMapping("/files/**")
    public ResponseEntity<FileSystemResource> get(HttpServletRequest request) {
        String url = request.getRequestURI().substring(request.getContextPath().length());
        Long userId = UserContext.get() == null ? null : UserContext.get().getUserId();
        if (!access.canRead(url, userId)) return ResponseEntity.notFound().build();
        var path = files.resolveStoredFile(url).orElse(null);
        if (path == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaTypeFactory.getMediaType(path.getFileName().toString()).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(path.getFileName().toString(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(path));
    }
}
