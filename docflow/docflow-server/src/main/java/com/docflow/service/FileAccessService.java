package com.docflow.service;

import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.entity.StoredFile;
import com.docflow.mapper.StoredFileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.jsoup.Jsoup;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.time.LocalDateTime;

/** File associations are granted by authorized operations, never by a download or a share request. */
@Service
public class FileAccessService {
    private static final Pattern LOCAL_REFERENCE = Pattern.compile("(?<![A-Za-z0-9:/._?=&%\\-])(/files/[A-Za-z0-9_./-]+)");
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]*");
    private final StoredFileMapper files;
    @org.springframework.beans.factory.annotation.Value("${file.public-access-enabled:true}")
    private boolean publicAvatarAccess = true;

    public FileAccessService(StoredFileMapper files) { this.files = files; }

    public static boolean isCanonical(String url) {
        if (url == null || !url.startsWith("/files/") || url.length() > 512) return false;
        String[] segments = url.substring(7).split("/", -1);
        for (String segment : segments) {
            if (!SEGMENT.matcher(segment).matches() || segment.endsWith(".")) return false;
        }
        return segments.length > 0;
    }

    public static Set<String> references(String content, String cover) {
        Set<String> urls = new LinkedHashSet<>();
        if (content != null) {
            for (var element : Jsoup.parseBodyFragment(content).select("img[src],a[href]")) {
                String url = element.attr(element.normalName().equals("img") ? "src" : "href");
                if (isCanonical(url)) urls.add(url);
            }
            // Parse HTML attribute values with the same entity decoding as export/signing;
            // scan only the remaining text for Markdown and bare relative URLs.
            var matcher = LOCAL_REFERENCE.matcher(content.replaceAll("<[^>]*>", " "));
            while (matcher.find()) if (isCanonical(matcher.group())) urls.add(matcher.group());
        }
        if (isCanonical(cover)) urls.add(cover);
        return urls;
    }

    public void register(String url, Long ownerId, String kind) {
        if (!isCanonical(url) || ownerId == null || !Set.of("DOCUMENT", "AVATAR", "FEEDBACK").contains(kind)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid stored file registration");
        }
        files.insert(url, ownerId, kind);
    }

    public boolean canRead(String url, Long userId) {
        if (!isCanonical(url)) return false;
        StoredFile file = files.find(url);
        if (file == null) return false;
        if ("AVATAR".equals(file.getFileKind())) return publicAvatarAccess || userId != null;
        if (userId == null) return false;
        if ("FEEDBACK".equals(file.getFileKind())) {
            return userId.equals(file.getOwnerId()) || files.activeAdmin(userId) > 0;
        }
        if (userId.equals(file.getOwnerId()) && files.references(url) == 0) return true;
        return files.readableDocuments(url, userId, LocalDateTime.now()) > 0 || files.readableTemplates(url, userId) > 0;
    }

    public void requireReadable(String url, Long userId) {
        if (!canRead(url, userId)) deny();
    }

    public boolean canDocumentFile(String url, Long docId) {
        if (!isCanonical(url) || docId == null) return false;
        StoredFile file = files.find(url);
        return file != null && (("AVATAR".equals(file.getFileKind())
                && (publicAvatarAccess || files.documentScope(url, docId) > 0))
                || ("DOCUMENT".equals(file.getFileKind()) && files.documentScope(url, docId) > 0));
    }

    public void requireDocumentFile(String url, Long docId) {
        if (!canDocumentFile(url, docId)) deny();
    }

    public boolean canShareFile(String url, Long docId, String token) {
        return token != null && !token.isBlank() && token.length() <= 128 && docId != null
                && files.activeShare(docId, token, LocalDateTime.now()) > 0 && canDocumentFile(url, docId);
    }

    public void requireShareFile(String url, Long docId, String token) {
        if (!canShareFile(url, docId, token)) deny();
    }

    @Transactional
    public void associateDocumentReferences(Long docId, String content, String cover, Long userId) {
        associate("DOCUMENT", docId, references(content, cover), userId);
    }

    @Transactional
    public void associateTemplateReferences(Long templateId, String content, Long userId) {
        associate("TEMPLATE", templateId, references(content, null), userId);
    }

    private void associate(String type, Long id, Set<String> urls, Long userId) {
        if (id == null || userId == null) deny();
        for (String url : urls) {
            requireReadable(url, userId);
            StoredFile file = files.find(url);
            if ("FEEDBACK".equals(file.getFileKind())) deny();
        }
        for (String url : urls) files.grant(url, type, id);
    }

    @Transactional
    public void copyDocumentReferences(Long sourceId, Long targetId, Long userId) {
        if (userId == null || files.readableDocument(sourceId, userId, LocalDateTime.now()) == 0) deny();
        copyScope("DOCUMENT", sourceId, targetId);
    }

    @Transactional
    public void copyTemplateReferences(Long templateId, Long docId, Long userId) {
        if (userId == null || files.readableTemplate(templateId, userId) == 0) deny();
        copyScope("TEMPLATE", templateId, docId);
    }

    private void copyScope(String sourceType, Long sourceId, Long targetId) {
        if (targetId == null) deny();
        for (String url : files.scopeUrls(sourceType, sourceId)) {
            StoredFile file = files.find(url);
            if (file != null && !"FEEDBACK".equals(file.getFileKind())) files.grant(url, "DOCUMENT", targetId);
        }
    }

    public void removeDocumentReferences(Long docId) { files.removeScope("DOCUMENT", docId); }
    public void removeTemplateReferences(Long templateId) { files.removeScope("TEMPLATE", templateId); }
    public java.util.List<String> documentUrls(Long docId) { return files.scopeUrls("DOCUMENT", docId); }
    public java.util.List<String> templateUrls(Long templateId) { return files.scopeUrls("TEMPLATE", templateId); }
    public boolean isReferenced(String url) { return isCanonical(url) && files.references(url) > 0; }
    public boolean claimDeletion(String url) { return isCanonical(url) && files.removeUnreferencedFile(url) > 0; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimDeletionAfterCommit(String url) {
        return isCanonical(url) && files.removeUnreferencedFile(url) > 0;
    }

    private static void deny() { throw new BusinessException(ErrorCode.FORBIDDEN, "No file access permission"); }
}
