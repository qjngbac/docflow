package com.docflow.scheduler;

import com.docflow.service.DocService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class TrashCleanScheduler {

    @Autowired
    private DocService docService;

    @Scheduled(cron = "${app.trash.cleanup-cron:0 0 2 * * ?}")
    public void cleanTrash() {
        int retentionDays = docService.getTrashRetentionDays();
        int count = docService.cleanDeletedBefore(LocalDateTime.now().minusDays(retentionDays));
        log.info("Cleaned {} documents older than {} days from trash", count, retentionDays);
    }
}
