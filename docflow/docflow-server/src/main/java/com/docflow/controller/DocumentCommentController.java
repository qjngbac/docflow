package com.docflow.controller;

import com.docflow.common.Result;
import com.docflow.dto.CommentCreateRequest;
import com.docflow.dto.CommentStatusRequest;
import com.docflow.entity.DocumentComment;
import com.docflow.security.UserContext;
import com.docflow.service.DocumentCommentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/docs/{docId}/comments")
public class DocumentCommentController {
    @Autowired private DocumentCommentService commentService;

    @GetMapping
    public Result<List<DocumentComment>> list(@PathVariable Long docId) {
        return Result.success(commentService.list(docId, UserContext.getRequiredUserId()));
    }

    @PostMapping
    public Result<DocumentComment> create(@PathVariable Long docId,
                                          @Valid @RequestBody CommentCreateRequest request) {
        return Result.success(commentService.create(docId, UserContext.getRequiredUserId(), request));
    }

    @PutMapping("/{commentId}/status")
    public Result<DocumentComment> setStatus(@PathVariable Long docId,
                                             @PathVariable Long commentId,
                                             @Valid @RequestBody CommentStatusRequest request) {
        return Result.success(commentService.setResolved(docId, commentId,
                UserContext.getRequiredUserId(), request.getResolved()));
    }

    @DeleteMapping("/{commentId}")
    public Result<Void> delete(@PathVariable Long docId, @PathVariable Long commentId) {
        commentService.delete(docId, commentId, UserContext.getRequiredUserId());
        return Result.success();
    }

    @PutMapping("/batch/status")
    public Result<Void> setBatchStatus(@PathVariable Long docId, @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Long> ids = ((List<Number>) request.getOrDefault("ids", List.of())).stream()
                .map(Number::longValue).toList();
        commentService.resolveBatch(docId, ids, UserContext.getRequiredUserId(),
                Boolean.TRUE.equals(request.get("resolved")));
        return Result.success();
    }
}
