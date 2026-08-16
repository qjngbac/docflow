package com.docflow.controller;

import com.docflow.common.Result;
import com.docflow.dto.DocumentCreateRequest;
import com.docflow.dto.DocumentUpdateRequest;
import com.docflow.dto.ShareRequest;
import com.docflow.entity.Document;
import com.docflow.security.UserContext;
import com.docflow.service.DocService;
import com.docflow.service.DocumentImportService;
import com.docflow.service.PermissionService;
import com.docflow.vo.ShareVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import lombok.Data;

@RestController
@RequestMapping("/api/v1/docs")
public class DocController {

    @Autowired
    private DocService docService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private DocumentImportService documentImportService;

    @GetMapping
    public Result<List<Document>> list(@RequestParam(required = false) Long folderId,
                                       @RequestParam(defaultValue = "false") boolean includeDeleted,
                                       @RequestParam(required = false) String scope,
                                       @RequestParam(required = false) String category) {
        return Result.success(docService.list(UserContext.getRequiredUserId(), folderId, includeDeleted, scope, category));
    }

    @GetMapping("/search")
    public Result<List<Document>> search(@RequestParam String q) {
        return Result.success(docService.search(UserContext.getRequiredUserId(), q));
    }

    @GetMapping("/trash/settings")
    public Result<Map<String, Integer>> trashSettings() {
        return Result.success(Map.of("retentionDays", docService.getTrashRetentionDays()));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<Document> create(@Valid @RequestBody DocumentCreateRequest request) {
        return Result.success(docService.create(UserContext.getRequiredUserId(), request));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Document> importDocx(@RequestParam("file") MultipartFile file,
                                       @RequestParam(required = false) Long folderId,
                                       @RequestParam(required = false) String category) {
        return Result.success(documentImportService.importDocx(
                UserContext.getRequiredUserId(), file, folderId, category));
    }

    @PostMapping("/{id}/copy")
    public Result<Document> copy(@PathVariable Long id,
                                 @RequestParam(required = false) Long folderId) {
        return Result.success(docService.copy(id, UserContext.getRequiredUserId(), folderId));
    }

    @GetMapping("/{id}")
    public Result<Document> get(@PathVariable Long id) {
        return Result.success(docService.get(id, UserContext.getRequiredUserId()));
    }

    @PutMapping("/{id}")
    public Result<Document> update(@PathVariable Long id, @Valid @RequestBody DocumentUpdateRequest request) {
        return Result.success(docService.update(id, UserContext.getRequiredUserId(), request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        docService.moveToTrash(id, UserContext.getRequiredUserId());
        return Result.success();
    }

    @PostMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable Long id) {
        docService.restore(id, UserContext.getRequiredUserId());
        return Result.success();
    }

    @DeleteMapping("/{id}/permanent")
    public Result<Void> purge(@PathVariable Long id) {
        docService.purge(id, UserContext.getRequiredUserId());
        return Result.success();
    }

    @PostMapping("/batch/move")
    public Result<Void> batchMove(@RequestBody BatchRequest request) {
        docService.batchMove(request.getIds(), request.getFolderId() == null ? 0L : request.getFolderId(), UserContext.getRequiredUserId());
        return Result.success();
    }

    @PostMapping("/batch/trash")
    public Result<Void> batchTrash(@RequestBody BatchRequest request) {
        docService.batchTrash(request.getIds(), UserContext.getRequiredUserId()); return Result.success();
    }

    @PostMapping("/batch/restore")
    public Result<Void> batchRestore(@RequestBody BatchRequest request) {
        docService.batchRestore(request.getIds(), UserContext.getRequiredUserId()); return Result.success();
    }

    @PostMapping("/batch/permanent")
    public Result<Void> batchPurge(@RequestBody BatchRequest request) {
        docService.batchPurge(request.getIds(), UserContext.getRequiredUserId()); return Result.success();
    }

    @PostMapping("/{id}/share")
    public Result<ShareVO> share(@PathVariable Long id, @Valid @RequestBody ShareRequest request) {
        return Result.success(permissionService.createShare(id, UserContext.getRequiredUserId(), request));
    }

    @Data public static class BatchRequest { private List<Long> ids; private Long folderId; }

}
