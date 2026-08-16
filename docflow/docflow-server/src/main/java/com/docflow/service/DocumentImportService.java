package com.docflow.service;

import com.docflow.dto.DocumentCreateRequest;
import com.docflow.entity.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentImportService {

    @Autowired private DocxImportService docxImportService;
    @Autowired private DocService docService;
    @Autowired private FileService fileService;

    public Document importDocx(Long userId, MultipartFile file, Long folderId, String category) {
        DocxImportService.ImportedDocx imported = docxImportService.convert(file);
        try {
            DocumentCreateRequest request = new DocumentCreateRequest();
            request.setTitle(imported.title());
            request.setContent(imported.html());
            request.setContentFormat("HTML");
            request.setFolderId(folderId == null ? 0L : folderId);
            request.setCategory(category);
            return docService.create(userId, request);
        } catch (RuntimeException e) {
            imported.imageUrls().forEach(fileService::deleteByUrl);
            throw e;
        }
    }
}
