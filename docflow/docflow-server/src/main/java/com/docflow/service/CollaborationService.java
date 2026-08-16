package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.entity.Document;
import com.docflow.mapper.DocumentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CollaborationService {

    private static final String DIRTY_DOCUMENTS_KEY = "docflow:collab:dirty-documents";
    private static final String DRAFT_KEY_PREFIX = "docflow:collab:doc:";
    private static final DefaultRedisScript<Long> CLEANUP_DRAFT_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('HGET', KEYS[1], 'revision'); "
                    + "if not current then redis.call('SREM', KEYS[2], ARGV[1]); return 0; end; "
                    + "if current == ARGV[2] then "
                    + "redis.call('DEL', KEYS[1]); redis.call('SREM', KEYS[2], ARGV[1]); return 1; end; "
                    + "redis.call('HSET', KEYS[1], 'persistedRevision', ARGV[2]); return 2;",
            Long.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private VersionService versionService;

    @Autowired
    private HtmlSanitizer htmlSanitizer;

    @Value("${collaboration.draft-ttl-hours:168}")
    private long draftTtlHours;

    private final Map<Long, Object> documentLocks = new ConcurrentHashMap<>();

    public Document stageUpdate(Long docId, Long userId, String title,
                                String content, Long expectedRevision) {
        Document persisted = permissionService.requireWritable(docId, userId);
        if (!"LEGACY".equals(persisted.getCollabMode())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "CRDT documents cannot use legacy revision updates");
        }
        synchronized (lockFor(docId)) {
            Document latest = mergeDraft(persisted);
            long currentRevision = revisionOf(latest);
            long basePersistedRevision = persistedRevisionOf(latest);
            if (expectedRevision == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "revision is required when updating document content");
            }
            if (expectedRevision != currentRevision) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "Document revision conflict; current revision is " + currentRevision);
            }

            if (StringUtils.hasText(title)) {
                latest.setTitle(title);
            }
            if (content != null) {
                if ("HTML".equals(latest.getContentFormat())) {
                    content = htmlSanitizer.clean(content);
                }
                latest.setContent(content);
                latest.setSummary(DocumentContentUtils.summary(content));
            }

            latest.setCollabMode(defaultMode(latest.getCollabMode()));
            latest.setRevision(currentRevision + 1);
            latest.setPersistedRevision(basePersistedRevision);
            latest.setContentHash(DocumentContentUtils.sha256(latest.getContent()));
            latest.setLastEditBy(userId);
            saveDraft(latest);
            return latest;
        }
    }

    public Document mergeDraft(Document persisted) {
        if (persisted == null || persisted.getId() == null) {
            return persisted;
        }
        Map<Object, Object> draft = redisTemplate.opsForHash().entries(draftKey(persisted.getId()));
        if (draft.isEmpty()) {
            initializeDefaults(persisted);
            return persisted;
        }

        long databasePersistedRevision = persistedRevisionOf(persisted);
        persisted.setTitle(value(draft, "title", persisted.getTitle()));
        persisted.setContent(value(draft, "content", persisted.getContent()));
        persisted.setSummary(DocumentContentUtils.summary(persisted.getContent()));
        persisted.setCollabMode(value(draft, "collabMode", defaultMode(persisted.getCollabMode())));
        persisted.setRevision(longValue(draft, "revision", revisionOf(persisted)));
        persisted.setPersistedRevision(longValue(
                draft, "persistedRevision", databasePersistedRevision));
        persisted.setContentHash(value(draft, "contentHash", persisted.getContentHash()));
        persisted.setLastEditBy(longValue(draft, "lastEditBy", persisted.getLastEditBy()));
        return persisted;
    }

    public Set<String> dirtyDocumentIds() {
        Set<String> ids = redisTemplate.opsForSet().members(DIRTY_DOCUMENTS_KEY);
        return ids == null ? Set.of() : ids;
    }

    @Transactional
    public Document flushDocument(Long docId, boolean createVersion) {
        synchronized (lockFor(docId)) {
            String key = draftKey(docId);
            Map<Object, Object> draft = redisTemplate.opsForHash().entries(key);
            if (draft.isEmpty()) {
                redisTemplate.opsForSet().remove(DIRTY_DOCUMENTS_KEY, docId.toString());
                return null;
            }

            Document persisted = documentMapper.selectById(docId);
            if (persisted == null) {
                deleteDraft(docId);
                return null;
            }

            long expectedPersistedRevision = longValue(draft, "persistedRevision", 0L);
            long draftRevision = longValue(draft, "revision", expectedPersistedRevision);
            long databaseRevision = revisionOf(persisted);

            if (databaseRevision == draftRevision) {
                deleteDraft(docId);
                return persisted;
            }
            if (databaseRevision != expectedPersistedRevision) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "Cannot persist draft because database revision changed to " + databaseRevision);
            }

            String title = value(draft, "title", persisted.getTitle());
            String content = value(draft, "content", persisted.getContent());
            String hash = value(draft, "contentHash", DocumentContentUtils.sha256(content));
            Long lastEditBy = longValue(draft, "lastEditBy", persisted.getLastEditBy());
            LocalDateTime persistedAt = LocalDateTime.now();

            int rows = documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                    .eq(Document::getId, docId)
                    .eq(Document::getRevision, expectedPersistedRevision)
                    .set(Document::getTitle, title)
                    .set(Document::getContent, content)
                    .set(Document::getSummary, DocumentContentUtils.summary(content))
                    .set(Document::getLastEditBy, lastEditBy)
                    .set(Document::getCollabMode, value(draft, "collabMode", "LEGACY"))
                    .set(Document::getRevision, draftRevision)
                    .set(Document::getPersistedRevision, draftRevision)
                    .set(Document::getContentHash, hash)
                    .set(Document::getLastPersistedAt, persistedAt));

            if (rows != 1) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "Document changed while the draft was being persisted");
            }

            Document updated = documentMapper.selectById(docId);
            if (createVersion) {
                versionService.createSnapshot(updated, lastEditBy, "AUTO");
            }
            cleanDraftAfterCommit(docId, draftRevision);
            log.debug("Persisted document {} at revision {}", docId, draftRevision);
            return updated;
        }
    }

    @Transactional
    public Document flushDocument(Long docId) {
        return flushDocument(docId, true);
    }

    public void initializeDefaults(Document document) {
        if (document.getCollabMode() == null) {
            document.setCollabMode("LEGACY");
        }
        if (document.getRevision() == null) {
            document.setRevision(0L);
        }
        if (document.getPersistedRevision() == null) {
            document.setPersistedRevision(document.getRevision());
        }
        if (document.getContentHash() == null) {
            document.setContentHash(DocumentContentUtils.sha256(document.getContent()));
        }
    }

    private void saveDraft(Document document) {
        Map<String, String> values = new HashMap<>();
        values.put("title", document.getTitle() == null ? "" : document.getTitle());
        values.put("content", document.getContent() == null ? "" : document.getContent());
        values.put("collabMode", defaultMode(document.getCollabMode()));
        values.put("revision", revisionOf(document) + "");
        values.put("persistedRevision", persistedRevisionOf(document) + "");
        values.put("contentHash", document.getContentHash());
        values.put("lastEditBy", document.getLastEditBy() == null ? "" : document.getLastEditBy().toString());

        String key = draftKey(document.getId());
        redisTemplate.opsForHash().putAll(key, values);
        redisTemplate.expire(key, Duration.ofHours(draftTtlHours));
        redisTemplate.opsForSet().add(DIRTY_DOCUMENTS_KEY, document.getId().toString());
    }

    private void deleteDraft(Long docId) {
        redisTemplate.delete(draftKey(docId));
        redisTemplate.opsForSet().remove(DIRTY_DOCUMENTS_KEY, docId.toString());
    }

    private void cleanDraftAfterCommit(Long docId, long persistedRevision) {
        Runnable cleanup = () -> redisTemplate.execute(
                CLEANUP_DRAFT_SCRIPT,
                List.of(draftKey(docId), DIRTY_DOCUMENTS_KEY),
                docId.toString(),
                Long.toString(persistedRevision));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cleanup.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cleanup.run();
            }
        });
    }

    private Object lockFor(Long docId) {
        return documentLocks.computeIfAbsent(docId, ignored -> new Object());
    }

    private String draftKey(Long docId) {
        return DRAFT_KEY_PREFIX + docId + ":draft";
    }

    private String defaultMode(String mode) {
        return StringUtils.hasText(mode) ? mode : "LEGACY";
    }

    private long revisionOf(Document document) {
        return document.getRevision() == null ? 0L : document.getRevision();
    }

    private long persistedRevisionOf(Document document) {
        return document.getPersistedRevision() == null
                ? revisionOf(document)
                : document.getPersistedRevision();
    }

    private String value(Map<Object, Object> values, String key, String defaultValue) {
        Object value = values.get(key);
        return value == null ? defaultValue : value.toString();
    }

    private Long longValue(Map<Object, Object> values, String key, Long defaultValue) {
        String value = value(values, key, null);
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

}
