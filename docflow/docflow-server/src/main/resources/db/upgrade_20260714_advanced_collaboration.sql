-- Run once after upgrade_20260714_review_publishing.sql.
-- Adds historical CRDT snapshots, per-document CSL reference records, and resumable attachment upload sessions.

CREATE TABLE IF NOT EXISTS `crdt_checkpoint_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `doc_id` BIGINT NOT NULL,
    `protocol_version` INT NOT NULL DEFAULT 1,
    `checkpoint_seq` BIGINT NOT NULL DEFAULT 0,
    `payload` LONGBLOB NOT NULL,
    `reason` ENUM('AUTO','MANUAL','PRE_RESTORE') NOT NULL DEFAULT 'AUTO',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_crdt_history_doc_time` (`doc_id`, `created_at`),
    KEY `idx_crdt_history_doc_seq` (`doc_id`, `checkpoint_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Historical Yjs CRDT checkpoints for restore';

CREATE TABLE IF NOT EXISTS `document_reference` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `doc_id` BIGINT NOT NULL,
    `cite_key` VARCHAR(100) NOT NULL,
    `doi` VARCHAR(255) DEFAULT NULL,
    `csl_json` LONGTEXT NOT NULL COMMENT 'CSL-JSON reference record',
    `created_by` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_document_reference_key` (`doc_id`, `cite_key`),
    UNIQUE KEY `uk_document_reference_doi` (`doc_id`, `doi`),
    KEY `idx_document_reference_doc` (`doc_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per-document CSL JSON bibliography library';

CREATE TABLE IF NOT EXISTS `attachment_upload_session` (
    `id` CHAR(36) NOT NULL,
    `doc_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `original_name` VARCHAR(500) NOT NULL,
    `mime_type` VARCHAR(200) DEFAULT NULL,
    `total_size` BIGINT NOT NULL,
    `total_chunks` INT NOT NULL,
    `received_chunks` INT NOT NULL DEFAULT 0,
    `sha256` CHAR(64) DEFAULT NULL,
    `status` ENUM('UPLOADING','COMPLETE','ABORTED') NOT NULL DEFAULT 'UPLOADING',
    `expires_at` DATETIME NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_attachment_upload_doc_user` (`doc_id`, `user_id`, `status`),
    KEY `idx_attachment_upload_expire` (`expires_at`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Resumable attachment upload session';
