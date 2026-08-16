package com.docflow.controller;

import com.docflow.common.Result;
import com.docflow.entity.Tag;
import com.docflow.security.UserContext;
import com.docflow.service.TagService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class TagController {
    @Autowired private TagService tagService;

    @GetMapping("/tags")
    public Result<List<Tag>> list() { return Result.success(tagService.list(UserContext.getRequiredUserId())); }

    @GetMapping("/docs/{docId}/tags")
    public Result<List<Tag>> documentTags(@PathVariable Long docId) {
        return Result.success(tagService.listForDocument(docId, UserContext.getRequiredUserId()));
    }

    @PutMapping("/docs/{docId}/tags")
    public Result<List<Tag>> setDocumentTags(@PathVariable Long docId, @RequestBody TagRequest request) {
        return Result.success(tagService.setForDocument(docId, UserContext.getRequiredUserId(), request.getNames()));
    }

    @Data public static class TagRequest { private List<String> names; }
}
