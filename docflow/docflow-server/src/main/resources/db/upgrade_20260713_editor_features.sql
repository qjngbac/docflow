USE `docflow`;

CREATE TABLE IF NOT EXISTS `document_comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `doc_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `content` VARCHAR(2000) NOT NULL,
    `selected_text` VARCHAR(1000) DEFAULT NULL,
    `status` ENUM('OPEN','RESOLVED') NOT NULL DEFAULT 'OPEN',
    `resolved_by` BIGINT DEFAULT NULL,
    `resolved_at` DATETIME DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_comment_doc_status` (`doc_id`, `status`, `created_at`),
    KEY `idx_comment_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='document comments';
