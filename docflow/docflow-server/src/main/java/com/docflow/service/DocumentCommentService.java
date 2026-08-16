package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.dto.CommentCreateRequest;
import com.docflow.entity.DocumentComment;
import com.docflow.entity.User;
import com.docflow.mapper.DocumentCommentMapper;
import com.docflow.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentCommentService {
    @Autowired private DocumentCommentMapper commentMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private PermissionService permissionService;
    @Autowired private NotificationService notificationService;
    private static final Pattern MENTION = Pattern.compile("@([\\p{L}\\p{N}_.-]{1,50})");

    public List<DocumentComment> list(Long docId, Long userId) {
        permissionService.requireReadable(docId, userId);
        return commentMapper.selectList(new LambdaQueryWrapper<DocumentComment>()
                        .eq(DocumentComment::getDocId, docId)
                        .orderByAsc(DocumentComment::getStatus)
                        .orderByDesc(DocumentComment::getCreatedAt))
                .stream().map(this::withAuthor).toList();
    }

    @Transactional
    public DocumentComment create(Long docId, Long userId, CommentCreateRequest request) {
        var document = permissionService.requireCommentable(docId, userId);
        DocumentComment comment = new DocumentComment();
        comment.setDocId(docId);
        comment.setUserId(userId);
        if (request.getParentId() != null) {
            DocumentComment parent = requireComment(docId, request.getParentId());
            comment.setParentId(parent.getParentId() == null ? parent.getId() : parent.getParentId());
        }
        comment.setContent(request.getContent().trim());
        comment.setSelectedText(request.getSelectedText() == null ? null : request.getSelectedText().trim());
        comment.setStatus("OPEN");
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(comment.getCreatedAt());
        commentMapper.insert(comment);
        if (comment.getParentId() != null) {
            DocumentComment root = requireComment(docId, comment.getParentId());
            notificationService.create(root.getUserId(), userId, docId, comment.getId(),
                    "COMMENT_REPLY", "回复了你在文档《" + document.getTitle() + "》中的批注");
        }
        Set<Long> notified = new LinkedHashSet<>();
        Matcher matcher = MENTION.matcher(comment.getContent());
        while (matcher.find()) {
            User mentioned = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, matcher.group(1)).last("LIMIT 1"));
            if (mentioned != null && permissionService.canRead(document, mentioned.getId())
                    && notified.add(mentioned.getId())) {
                notificationService.create(mentioned.getId(), userId, docId, comment.getId(),
                        "MENTION", "在文档《" + document.getTitle() + "》的批注中提到了你");
            }
        }
        return withAuthor(comment);
    }

    @Transactional
    public DocumentComment setResolved(Long docId, Long commentId, Long userId, boolean resolved) {
        permissionService.requireWritable(docId, userId);
        DocumentComment comment = requireComment(docId, commentId);
        comment.setStatus(resolved ? "RESOLVED" : "OPEN");
        comment.setResolvedBy(resolved ? userId : null);
        comment.setResolvedAt(resolved ? LocalDateTime.now() : null);
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.updateById(comment);
        return withAuthor(comment);
    }

    @Transactional
    public void delete(Long docId, Long commentId, Long userId) {
        var document = permissionService.requireReadable(docId, userId);
        DocumentComment comment = requireComment(docId, commentId);
        if (!userId.equals(comment.getUserId()) && !permissionService.canWrite(document, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the author or an editor can delete this comment");
        }
        if (comment.getParentId() == null) {
            commentMapper.delete(new LambdaQueryWrapper<DocumentComment>()
                    .eq(DocumentComment::getParentId, comment.getId()));
        }
        commentMapper.deleteById(comment.getId());
    }

    @Transactional
    public void resolveBatch(Long docId, List<Long> ids, Long userId, boolean resolved) {
        permissionService.requireWritable(docId, userId);
        if (ids == null || ids.isEmpty() || ids.size() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Batch must contain 1 to 100 comments");
        }
        for (Long id : ids.stream().distinct().toList()) setResolved(docId, id, userId, resolved);
    }

    public void deleteByDocument(Long docId) {
        commentMapper.delete(new LambdaQueryWrapper<DocumentComment>()
                .eq(DocumentComment::getDocId, docId));
    }

    private DocumentComment requireComment(Long docId, Long commentId) {
        DocumentComment comment = commentMapper.selectById(commentId);
        if (comment == null || !docId.equals(comment.getDocId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Comment not found");
        }
        return comment;
    }

    private DocumentComment withAuthor(DocumentComment comment) {
        User author = userMapper.selectById(comment.getUserId());
        if (author != null) {
            comment.setAuthorName(author.getNickname() == null || author.getNickname().isBlank()
                    ? author.getUsername() : author.getNickname());
            comment.setAuthorAvatar(author.getAvatar());
        }
        return comment;
    }
}
