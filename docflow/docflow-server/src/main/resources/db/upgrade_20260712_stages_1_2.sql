-- Run once after upgrade_20260711_user_profile.sql.
ALTER TABLE `document`
    ADD COLUMN `category` VARCHAR(100) DEFAULT NULL COMMENT 'document category' AFTER `is_pinned`,
    ADD COLUMN `content_format` ENUM('MARKDOWN','HTML') NOT NULL DEFAULT 'MARKDOWN' COMMENT 'content format' AFTER `category`;

ALTER TABLE `doc_version`
    ADD COLUMN `content_format` ENUM('MARKDOWN','HTML') NOT NULL DEFAULT 'MARKDOWN' COMMENT 'version content format' AFTER `content`;

CREATE TABLE `file_attachment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `doc_id` BIGINT NOT NULL, `uploader_id` BIGINT NOT NULL,
    `original_name` VARCHAR(500) NOT NULL, `file_url` VARCHAR(1000) NOT NULL,
    `file_size` BIGINT NOT NULL, `mime_type` VARCHAR(200) DEFAULT NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (`id`), KEY `idx_attachment_doc` (`doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='document attachment';

CREATE TABLE `tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `owner_id` BIGINT NOT NULL, `name` VARCHAR(50) NOT NULL,
    `color` VARCHAR(20) DEFAULT '#64748b', `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_tag_owner_name` (`owner_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='document tag';

CREATE TABLE `document_tag` (
    `doc_id` BIGINT NOT NULL, `tag_id` BIGINT NOT NULL, PRIMARY KEY (`doc_id`, `tag_id`),
    KEY `idx_document_tag_tag` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='document tag relation';

CREATE TABLE `document_favorite` (
    `user_id` BIGINT NOT NULL, `doc_id` BIGINT NOT NULL,
    PRIMARY KEY (`user_id`, `doc_id`), KEY `idx_favorite_doc` (`doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user document favorite';

CREATE TABLE `document_template` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `owner_id` BIGINT DEFAULT NULL, `name` VARCHAR(100) NOT NULL,
    `description` VARCHAR(500) DEFAULT NULL, `category` VARCHAR(100) DEFAULT NULL, `content` LONGTEXT NOT NULL,
    `content_format` ENUM('MARKDOWN','HTML') NOT NULL DEFAULT 'MARKDOWN',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), KEY `idx_template_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='document template';

CREATE TABLE `verification_code` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `user_id` BIGINT DEFAULT NULL, `target` VARCHAR(100) NOT NULL,
    `purpose` ENUM('EMAIL_CHANGE','PASSWORD_RESET') NOT NULL, `code_hash` VARCHAR(255) NOT NULL,
    `expires_at` DATETIME NOT NULL, `used_at` DATETIME DEFAULT NULL, `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), KEY `idx_verification_lookup` (`target`, `purpose`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='email verification code';

CREATE TABLE `user_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `user_id` BIGINT NOT NULL, `token_id` VARCHAR(64) NOT NULL,
    `device_name` VARCHAR(200) DEFAULT NULL, `ip_address` VARCHAR(50) DEFAULT NULL,
    `last_active_at` DATETIME NOT NULL, `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_session_token` (`token_id`), KEY `idx_session_user` (`user_id`, `last_active_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user login session';

INSERT INTO `document_template` (`owner_id`, `name`, `description`, `category`, `content`, `content_format`) VALUES
(NULL, '会议纪要', '记录会议议题、结论和待办', '工作', '# 会议纪要\n\n## 会议信息\n\n- 时间：\n- 参与人：\n\n## 讨论事项\n\n## 结论\n\n## 待办事项\n', 'MARKDOWN'),
(NULL, '项目计划', '整理目标、里程碑和风险', '项目', '# 项目计划\n\n## 项目目标\n\n## 里程碑\n\n## 任务分工\n\n## 风险与对策\n', 'MARKDOWN');
