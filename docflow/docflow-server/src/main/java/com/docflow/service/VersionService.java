package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.entity.DocVersion;
import com.docflow.entity.Document;
import com.docflow.mapper.DocVersionMapper;
import com.docflow.mapper.DocumentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class VersionService {

    @Autowired
    private DocVersionMapper docVersionMapper;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private HtmlSanitizer htmlSanitizer;

    public void createSnapshot(Document document, Long userId) {
        createSnapshot(document, userId, "AUTO");
    }

    public void createSnapshot(Document document, Long userId, String versionType) {
        createSnapshot(document, userId, versionType, null, null);
    }

    public void createSnapshot(Document document, Long userId, String versionType,
                               String versionName, String description) {
        Integer maxVersion = docVersionMapper.selectList(new LambdaQueryWrapper<DocVersion>()
                        .eq(DocVersion::getDocId, document.getId())
                        .orderByDesc(DocVersion::getVersionNum)
                        .last("LIMIT 1"))
                .stream()
                .findFirst()
                .map(DocVersion::getVersionNum)
                .orElse(0);

        DocVersion version = new DocVersion();
        version.setDocId(document.getId());
        version.setTitle(document.getTitle());
        version.setContent(document.getContent() == null ? "" : document.getContent());
        version.setContentFormat(document.getContentFormat() == null ? "MARKDOWN" : document.getContentFormat());
        version.setVersionNum(maxVersion + 1);
        version.setSourceRevision(document.getRevision());
        version.setVersionType(versionType);
        version.setVersionName(versionName == null || versionName.isBlank() ? null : versionName.trim());
        version.setDescription(description == null || description.isBlank() ? null : description.trim());
        version.setCreatedBy(userId);
        version.setCreatedAt(LocalDateTime.now());
        docVersionMapper.insert(version);
    }

    public void createAutoSnapshotIfDue(Document document, Long userId) {
        DocVersion latest = docVersionMapper.selectOne(new LambdaQueryWrapper<DocVersion>()
                .eq(DocVersion::getDocId, document.getId())
                .orderByDesc(DocVersion::getVersionNum)
                .last("LIMIT 1"));
        if (latest != null && document.getContent().equals(latest.getContent())) {
            return;
        }
        if (latest != null && latest.getCreatedAt() != null
                && latest.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(5))) {
            return;
        }
        createSnapshot(document, userId, "AUTO");
    }

    public List<DocVersion> list(Long docId) {
        return docVersionMapper.selectList(new LambdaQueryWrapper<DocVersion>()
                .eq(DocVersion::getDocId, docId)
                .orderByDesc(DocVersion::getVersionNum));
    }

    @Transactional
    public void saveVersion(Long docId, Long userId) {
        saveVersion(docId, userId, null);
    }

    @Transactional
    public void saveVersion(Long docId, Long userId, String currentCrdtHtml) {
        saveVersion(docId, userId, currentCrdtHtml, null, null);
    }

    @Transactional
    public void saveVersion(Long docId, Long userId, String currentCrdtHtml,
                            String versionName, String description) {
        Document document = permissionService.requireWritable(docId, userId);
        if ("CRDT".equals(document.getCollabMode()) && currentCrdtHtml != null) {
            document.setContent(htmlSanitizer.clean(currentCrdtHtml));
            document.setContentFormat("HTML");
        }
        createSnapshot(document, userId, "MANUAL", versionName, description);
    }

    public List<DocVersion> getVersionList(Long docId, Long userId) {
        permissionService.requireReadable(docId, userId);
        return list(docId);
    }

    public DocVersion getVersion(Long docId, Integer versionNum, Long userId) {
        permissionService.requireReadable(docId, userId);
        return requireVersionNumber(docId, versionNum);
    }

    public DocVersion requireVersionNumber(Long docId, Integer versionNum) {
        DocVersion version = docVersionMapper.selectOne(new LambdaQueryWrapper<DocVersion>()
                .eq(DocVersion::getDocId, docId)
                .eq(DocVersion::getVersionNum, versionNum)
                .last("LIMIT 1"));
        if (version == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Version not found");
        }
        return version;
    }

    @Transactional
    public Document rollback(Long docId, Integer versionNum, Long userId) {
        Document document = permissionService.requireWritable(docId, userId);
        DocVersion version = requireVersionNumber(docId, versionNum);

        document.setTitle(version.getTitle());
        document.setContent(version.getContent());
        document.setContentFormat(version.getContentFormat() == null ? "MARKDOWN" : version.getContentFormat());
        document.setSummary(DocumentContentUtils.summary(version.getContent()));
        document.setLastEditBy(userId);
        long nextRevision = document.getRevision() == null ? 1L : document.getRevision() + 1;
        document.setRevision(nextRevision);
        document.setPersistedRevision(nextRevision);
        document.setContentHash(DocumentContentUtils.sha256(version.getContent()));
        document.setLastPersistedAt(LocalDateTime.now());
        documentMapper.updateById(document);

        createSnapshot(document, userId, "ROLLBACK");
        return document;
    }

    @Transactional
    public void deleteVersions(Long docId, Long userId) {
        permissionService.requireAdmin(docId, userId);
        docVersionMapper.delete(new LambdaQueryWrapper<DocVersion>()
                .eq(DocVersion::getDocId, docId));
    }

}
