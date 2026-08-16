package com.docflow.controller;

import com.docflow.security.SignedFileService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/public-files")
public class PublicFileController {
    private final SignedFileService signedFiles;

    public PublicFileController(SignedFileService signedFiles) {
        this.signedFiles = signedFiles;
    }

    @GetMapping
    public ResponseEntity<FileSystemResource> get(@RequestParam("path") String fileUrl,
                                                   @RequestParam long expires,
                                                   @RequestParam String signature) {
        Path path = signedFiles.resolve(fileUrl, expires, signature).orElse(null);
        if (path == null) return ResponseEntity.notFound().build();
        MediaType type = MediaTypeFactory.getMediaType(path.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(path.getFileName().toString(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(type)
                .body(new FileSystemResource(path));
    }
}
