package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.dto.DocumentCreateRequest;
import com.docflow.dto.DocumentUpdateRequest;
import com.docflow.entity.Document;
import com.docflow.mapper.DocumentMapper;
import com.docflow.mapper.DocumentFavoriteMapper;
import com.docflow.entity.DocumentFavorite;
import com.docflow.mapper.DocPermissionMapper;
import com.docflow.mapper.DocVersionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Service
public class DocService {

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private VersionService versionService;

    @Autowired
    private CollaborationService collaborationService;

    @Autowired private TemplateService templateService;
    @Autowired private AttachmentService attachmentService;
    @Autowired private TagService tagService;
    @Autowired private DocVersionMapper docVersionMapper;
    @Autowired private DocPermissionMapper docPermissionMapper;
    @Autowired private DocumentFavoriteMapper documentFavoriteMapper;
    @Autowired private HtmlSanitizer htmlSanitizer;
    @Autowired private DocumentCommentService documentCommentService;
    @Value("${app.trash.retention-days:30}") private int trashRetentionDays;

    public List<Document> list(Long userId, Long folderId, boolean includeDeleted, String scope, String category) {
        Set<Long> favoriteIds = new HashSet<>(documentFavoriteMapper.selectList(
                new LambdaQueryWrapper<DocumentFavorite>().eq(DocumentFavorite::getUserId, userId))
                .stream().map(DocumentFavorite::getDocId).toList());
        if ("favorites".equalsIgnoreCase(scope) && favoriteIds.isEmpty()) return List.of();
        List<Long> permittedDocIds = permissionService.listAccessibleDocIds(userId);
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .and(w -> {
                    w.eq(Document::getOwnerId, userId);
                    if (!permittedDocIds.isEmpty()) {
                        w.or().in(Document::getId, permittedDocIds);
                    }
                })
                .orderByDesc(Document::getIsPinned)
                .orderByDesc(Document::getUpdatedAt);
        if (folderId != null) {
            wrapper.eq(Document::getFolderId, folderId);
        }
        if (!includeDeleted) {
            wrapper.eq(Document::getIsDeleted, 0);
        }
        if ("favorites".equalsIgnoreCase(scope)) wrapper.in(Document::getId, favoriteIds);
        if (StringUtils.hasText(category)) {
            wrapper.eq(Document::getCategory, category);
        }
        if ("recent".equalsIgnoreCase(scope)) {
            wrapper.last("LIMIT 30");
        }
        return documentMapper.selectList(wrapper).stream()
                .map(collaborationService::mergeDraft)
                .peek(document -> document.setIsFavorite(favoriteIds.contains(document.getId()) ? 1 : 0))
                .toList();
    }

    public List<Document> search(Long userId, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return list(userId, null, false, null, null);
        }
        List<Long> permittedDocIds = permissionService.listAccessibleDocIds(userId);
        Set<Long> favoriteIds = new HashSet<>(documentFavoriteMapper.selectList(
                new LambdaQueryWrapper<DocumentFavorite>().eq(DocumentFavorite::getUserId, userId))
                .stream().map(DocumentFavorite::getDocId).toList());
        return documentMapper.selectList(new LambdaQueryWrapper<Document>()
                .and(w -> {
                    w.eq(Document::getOwnerId, userId);
                    if (!permittedDocIds.isEmpty()) {
                        w.or().in(Document::getId, permittedDocIds);
                    }
                })
                .eq(Document::getIsDeleted, 0)
                .and(w -> w.like(Document::getTitle, keyword).or().like(Document::getContent, keyword))
                .orderByDesc(Document::getUpdatedAt)).stream()
                .map(collaborationService::mergeDraft)
                .peek(document -> document.setIsFavorite(favoriteIds.contains(document.getId()) ? 1 : 0))
                .toList();
    }

    @Transactional
    public Document create(Long userId, DocumentCreateRequest request) {
        String initialContent = request.getContent();
        String initialFormat = request.getContentFormat();
        String initialCategory = request.getCategory();
        if (request.getTemplateId() != null) {
            var template = templateService.getReadable(request.getTemplateId(), userId);
            initialContent = template.getContent();
            initialFormat = template.getContentFormat();
            if (!StringUtils.hasText(initialCategory)) initialCategory = template.getCategory();
        }
        Document document = new Document();
        document.setTitle(StringUtils.hasText(request.getTitle()) ? request.getTitle() : "Untitled Document");
        document.setContent("HTML".equals(initialFormat)
                ? htmlSanitizer.clean(initialContent) : initialContent == null ? "" : initialContent);
        document.setSummary(DocumentContentUtils.summary(document.getContent()));
        document.setOwnerId(userId);
        document.setFolderId(request.getFolderId() == null ? 0L : request.getFolderId());
        document.setIsPinned(0);
        document.setIsFavorite(0);
        document.setCategory(initialCategory);
        document.setContentFormat("HTML".equals(initialFormat) ? "HTML" : "MARKDOWN");
        document.setCoverImage(StringUtils.hasText(request.getCoverImage()) ? request.getCoverImage().trim() : null);
        document.setPageFormat(normalizePageFormat(request.getPageFormat()));
        document.setMarginTop(20);
        document.setMarginRight(20);
        document.setMarginBottom(20);
        document.setMarginLeft(20);
        document.setCollabMode("CRDT");
        document.setIsDeleted(0);
        document.setLastEditBy(userId);
        collaborationService.initializeDefaults(document);
        document.setLastPersistedAt(LocalDateTime.now());
        documentMapper.insert(document);
        versionService.createSnapshot(document, userId, "AUTO");
        return document;
    }

    @Transactional
    public Document copy(Long docId, Long userId, Long folderId) {
        Document source = collaborationService.mergeDraft(permissionService.requireReadable(docId, userId));

        Document copy = new Document();
        copy.setTitle(source.getTitle() + " Copy");
        copy.setContent(source.getContent() == null ? "" : source.getContent());
        copy.setSummary(source.getSummary());
        copy.setOwnerId(userId);
        copy.setFolderId(folderId == null ? source.getFolderId() : folderId);
        copy.setIsPinned(0);
        copy.setIsFavorite(0);
        copy.setCategory(source.getCategory());
        copy.setContentFormat(source.getContentFormat());
        copy.setCoverImage(source.getCoverImage());
        copy.setPageFormat(source.getPageFormat());
        copy.setMarginTop(source.getMarginTop());
        copy.setMarginRight(source.getMarginRight());
        copy.setMarginBottom(source.getMarginBottom());
        copy.setMarginLeft(source.getMarginLeft());
        copy.setPageHeader(source.getPageHeader());
        copy.setPageFooter(source.getPageFooter());
        copy.setCollabMode("CRDT");
        copy.setIsDeleted(0);
        copy.setLastEditBy(userId);
        collaborationService.initializeDefaults(copy);
        copy.setLastPersistedAt(LocalDateTime.now());
        documentMapper.insert(copy);
        versionService.createSnapshot(copy, userId, "AUTO");
        return copy;
    }

    public Document get(Long docId, Long userId) {
        return collaborationService.mergeDraft(permissionService.requireReadable(docId, userId));
    }

    @Transactional
    public void updateCrdtSnapshot(Long docId, Long userId, String html) {
        Document document = permissionService.requireWritable(docId, userId);
        persistCrdtSnapshot(docId, userId, html, document);
    }

    @Transactional
    public void updateCrdtSnapshotFromCollaboration(Long docId, Long editorUserId, String html) {
        Document document = permissionService.requireDocument(docId);
        Long effectiveEditorId = editorUserId == null ? document.getOwnerId() : editorUserId;
        persistCrdtSnapshot(docId, effectiveEditorId, html, document);
    }

    private void persistCrdtSnapshot(Long docId, Long userId, String html, Document document) {
        if (!"CRDT".equals(document.getCollabMode())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Document is not in CRDT mode");
        }
        String safeHtml = htmlSanitizer.clean(html);
        boolean contentChanged = !safeHtml.equals(document.getContent() == null ? "" : document.getContent());
        LocalDateTime persistedAt = LocalDateTime.now();
        long nextRevision = contentChanged
                ? (document.getRevision() == null ? 1L : document.getRevision() + 1L)
                : (document.getRevision() == null ? 0L : document.getRevision());
        documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                .eq(Document::getId, docId)
                .set(Document::getContent, safeHtml)
                .set(Document::getContentFormat, "HTML")
                .set(Document::getSummary, DocumentContentUtils.summary(safeHtml))
                .set(Document::getLastEditBy, userId)
                .set(Document::getRevision, nextRevision)
                .set(Document::getPersistedRevision, nextRevision)
                .set(Document::getContentHash, DocumentContentUtils.sha256(safeHtml))
                .set(Document::getLastPersistedAt, persistedAt)
                .set(Document::getUpdatedAt, persistedAt));
        if (contentChanged) {
            document.setContent(safeHtml);
            document.setContentFormat("HTML");
            document.setRevision(nextRevision);
            document.setPersistedRevision(nextRevision);
            document.setLastPersistedAt(persistedAt);
            versionService.createAutoSnapshotIfDue(document, userId);
        }
    }

    @Transactional
    public Document update(Long docId, Long userId, DocumentUpdateRequest request) {
        Document document = permissionService.requireWritable(docId, userId);
        boolean crdtDocument = "CRDT".equals(document.getCollabMode());
        if (crdtDocument && request.getContent() != null) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "CRDT document content must be updated through the collaboration service");
        }
        boolean contentChanged = !crdtDocument
                && (StringUtils.hasText(request.getTitle()) || request.getContent() != null);
        LambdaUpdateWrapper<Document> metadataUpdate = new LambdaUpdateWrapper<Document>()
                .eq(Document::getId, docId);
        boolean metadataChanged = false;
        if (crdtDocument && StringUtils.hasText(request.getTitle())) {
            document.setTitle(request.getTitle().trim());
            metadataUpdate.set(Document::getTitle, document.getTitle());
            metadataChanged = true;
        }
        if (request.getFolderId() != null) {
            document.setFolderId(request.getFolderId());
            metadataUpdate.set(Document::getFolderId, request.getFolderId());
            metadataChanged = true;
        }
        if (request.getPinned() != null) {
            document.setIsPinned(request.getPinned() ? 1 : 0);
            metadataUpdate.set(Document::getIsPinned, document.getIsPinned());
            metadataChanged = true;
        }
        if (request.getFavorite() != null) {
            documentFavoriteMapper.delete(new LambdaQueryWrapper<DocumentFavorite>()
                    .eq(DocumentFavorite::getUserId, userId).eq(DocumentFavorite::getDocId, docId));
            if (request.getFavorite()) {
                DocumentFavorite favorite = new DocumentFavorite(); favorite.setUserId(userId); favorite.setDocId(docId);
                documentFavoriteMapper.insert(favorite);
                document.setIsFavorite(1);
            } else document.setIsFavorite(0);
        }
        if (request.getCategory() != null) {
            document.setCategory(StringUtils.hasText(request.getCategory()) ? request.getCategory().trim() : null);
            metadataUpdate.set(Document::getCategory, document.getCategory());
            metadataChanged = true;
        }
        if (request.getContentFormat() != null) {
            document.setContentFormat("HTML".equals(request.getContentFormat()) ? "HTML" : "MARKDOWN");
            metadataUpdate.set(Document::getContentFormat, document.getContentFormat());
            metadataChanged = true;
        }
        if (request.getCoverImage() != null) {
            document.setCoverImage(StringUtils.hasText(request.getCoverImage()) ? request.getCoverImage().trim() : null);
            metadataUpdate.set(Document::getCoverImage, document.getCoverImage());
            metadataChanged = true;
        }
        if (request.getPageFormat() != null) {
            document.setPageFormat(normalizePageFormat(request.getPageFormat()));
            metadataUpdate.set(Document::getPageFormat, document.getPageFormat());
            metadataChanged = true;
        }
        metadataChanged |= setMargin(request.getMarginTop(), Document::getMarginTop, document::setMarginTop, metadataUpdate);
        metadataChanged |= setMargin(request.getMarginRight(), Document::getMarginRight, document::setMarginRight, metadataUpdate);
        metadataChanged |= setMargin(request.getMarginBottom(), Document::getMarginBottom, document::setMarginBottom, metadataUpdate);
        metadataChanged |= setMargin(request.getMarginLeft(), Document::getMarginLeft, document::setMarginLeft, metadataUpdate);
        if (request.getPageHeader() != null) {
            document.setPageHeader(StringUtils.hasText(request.getPageHeader()) ? request.getPageHeader().trim() : null);
            metadataUpdate.set(Document::getPageHeader, document.getPageHeader());
            metadataChanged = true;
        }
        if (request.getPageFooter() != null) {
            document.setPageFooter(StringUtils.hasText(request.getPageFooter()) ? request.getPageFooter().trim() : null);
            metadataUpdate.set(Document::getPageFooter, document.getPageFooter());
            metadataChanged = true;
        }
        if (metadataChanged) {
            metadataUpdate.set(Document::getLastEditBy, userId);
            documentMapper.update(null, metadataUpdate);
            document.setLastEditBy(userId);
        }
        if (contentChanged) {
            return collaborationService.stageUpdate(
                    docId, userId, request.getTitle(), request.getContent(), request.getRevision());
        }
        return collaborationService.mergeDraft(document);
    }

    public void moveToTrash(Long docId, Long userId) {
        Document document = permissionService.requireAdmin(docId, userId);
        collaborationService.flushDocument(docId);
        document = permissionService.requireAdmin(docId, userId);
        document.setIsDeleted(1);
        document.setDeletedAt(LocalDateTime.now());
        documentMapper.updateById(document);
    }

    public void restore(Long docId, Long userId) {
        Document document = permissionService.requireAdmin(docId, userId);
        document.setIsDeleted(0);
        document.setDeletedAt(null);
        documentMapper.updateById(document);
    }

    @Transactional
    public void purge(Long docId, Long userId) {
        Document document = documentMapper.selectById(docId);
        if (document == null || !userId.equals(document.getOwnerId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the owner can permanently delete this document");
        }
        if (!Integer.valueOf(1).equals(document.getIsDeleted())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Document must be in trash before permanent deletion");
        }
        collaborationService.flushDocument(docId);
        attachmentService.deleteByDocument(docId);
        tagService.deleteRelations(docId);
        documentCommentService.deleteByDocument(docId);
        docVersionMapper.delete(new LambdaQueryWrapper<com.docflow.entity.DocVersion>()
                .eq(com.docflow.entity.DocVersion::getDocId, docId));
        docPermissionMapper.delete(new LambdaQueryWrapper<com.docflow.entity.DocPermission>()
                .eq(com.docflow.entity.DocPermission::getDocId, docId));
        documentFavoriteMapper.delete(new LambdaQueryWrapper<DocumentFavorite>()
                .eq(DocumentFavorite::getDocId, docId));
        documentMapper.deleteById(docId);
    }

    @Transactional
    public void batchMove(List<Long> ids, Long folderId, Long userId) {
        for (Long id : requireIds(ids)) update(id, userId, metadataRequest(folderId, null));
    }

    @Transactional
    public void batchTrash(List<Long> ids, Long userId) {
        for (Long id : requireIds(ids)) moveToTrash(id, userId);
    }

    @Transactional
    public void batchRestore(List<Long> ids, Long userId) {
        for (Long id : requireIds(ids)) restore(id, userId);
    }

    @Transactional
    public void batchPurge(List<Long> ids, Long userId) {
        for (Long id : requireIds(ids)) purge(id, userId);
    }

    private DocumentUpdateRequest metadataRequest(Long folderId, Boolean favorite) {
        DocumentUpdateRequest request = new DocumentUpdateRequest();
        request.setFolderId(folderId);
        request.setFavorite(favorite);
        return request;
    }

    private List<Long> requireIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Batch must contain 1 to 100 document IDs");
        }
        return ids.stream().distinct().toList();
    }

    private String normalizePageFormat(String value) {
        return "LETTER".equalsIgnoreCase(value) ? "LETTER" : "A4";
    }

    private boolean setMargin(Integer value,
                              com.baomidou.mybatisplus.core.toolkit.support.SFunction<Document, Integer> column,
                              java.util.function.Consumer<Integer> setter,
                              LambdaUpdateWrapper<Document> update) {
        if (value == null) return false;
        if (value < 5 || value > 60) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Page margins must be between 5 and 60 mm");
        }
        setter.accept(value);
        update.set(column, value);
        return true;
    }

    public int cleanDeletedBefore(LocalDateTime time) {
        List<Document> documents = documentMapper.selectList(new LambdaQueryWrapper<Document>()
                .eq(Document::getIsDeleted, 1)
                .lt(Document::getDeletedAt, time));
        documents.forEach(document -> purge(document.getId(), document.getOwnerId()));
        return documents.size();
    }

    public int getTrashRetentionDays() {
        return Math.max(1, trashRetentionDays);
    }

}
