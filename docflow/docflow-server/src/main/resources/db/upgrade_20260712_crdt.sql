-- Run once. Existing documents remain on the legacy revision workflow.
ALTER TABLE `document`
    MODIFY COLUMN `collab_mode` ENUM('REVISION','OT','CRDT','LEGACY') NOT NULL DEFAULT 'REVISION';

UPDATE `document`
SET `collab_mode` = 'LEGACY'
WHERE `collab_mode` IN ('REVISION', 'OT');

ALTER TABLE `document`
    MODIFY COLUMN `collab_mode` ENUM('LEGACY','CRDT') NOT NULL DEFAULT 'CRDT';

CREATE TABLE `crdt_checkpoint` (
    `doc_id` BIGINT NOT NULL,
    `protocol_version` INT NOT NULL DEFAULT 1,
    `checkpoint_seq` BIGINT NOT NULL DEFAULT 0,
    `payload` LONGBLOB NOT NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Yjs CRDT compacted checkpoint';

CREATE TABLE `crdt_update` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `doc_id` BIGINT NOT NULL,
    `protocol_version` INT NOT NULL DEFAULT 1,
    `client_id` VARCHAR(64) DEFAULT NULL,
    `update_id` CHAR(64) NOT NULL,
    `payload` MEDIUMBLOB NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_crdt_doc_update` (`doc_id`, `update_id`),
    KEY `idx_crdt_doc_seq` (`doc_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Yjs CRDT incremental update';
