package com.docflow.scheduler;

import com.docflow.service.CollaborationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
/** 定期把旧版协作模式暂存在 Redis 的修订内容持久化到 MySQL。 */
@Component
public class CollaborationPersistenceScheduler {

    @Autowired
    private CollaborationService collaborationService;

    @Scheduled(fixedDelayString = "${collaboration.persist-interval-ms:30000}")
    public void persistDirtyDocuments() {
        Set<String> documentIds;
        try {
            documentIds = collaborationService.dirtyDocumentIds();
        } catch (Exception e) {
            log.error("Failed to read dirty collaborative documents", e);
            return;
        }
        for (String value : documentIds) {
            try {
                collaborationService.flushDocument(Long.valueOf(value));
            } catch (Exception e) {
                log.error("Failed to persist collaborative document {}", value, e);
            }
        }
    }
}
