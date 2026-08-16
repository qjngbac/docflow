-- Flyway baseline for a new DocFlow database.
-- The target database must be created before the application starts.

CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL COMMENT 'username',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT 'display name',
    `password` VARCHAR(255) NOT NULL COMMENT 'bcrypt password',
    `email` VARCHAR(100) DEFAULT NULL COMMENT 'email',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT 'avatar url',
    `status` TINYINT DEFAULT 1 COMMENT '1 enabled, 0 disabled',
    `system_role` ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER' COMMENT 'system role',
    `ban_reason` VARCHAR(500) DEFAULT NULL COMMENT 'account ban reason',
    `banned_at` DATETIME DEFAULT NULL COMMENT 'account ban time',
    `ban_expires_at` DATETIME DEFAULT NULL COMMENT 'optional automatic unban time',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '1 deleted, 0 active',
    `last_login_at` DATETIME DEFAULT NULL COMMENT 'last login time',
    `welcome_dismissed` TINYINT NOT NULL DEFAULT 0 COMMENT '1 means the built-in guide was dismissed',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user table';

CREATE TABLE IF NOT EXISTS `folder` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(200) NOT NULL COMMENT 'folder name',
    `parent_id` BIGINT DEFAULT 0 COMMENT 'parent folder id, 0 means root',
    `owner_id` BIGINT NOT NULL COMMENT 'owner user id',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '1 deleted, 0 active',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_owner` (`owner_id`),
    KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='folder table';

CREATE TABLE IF NOT EXISTS `document` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(500) NOT NULL DEFAULT 'Untitled Document' COMMENT 'document title',
    `content` LONGTEXT COMMENT 'markdown content',
    `summary` VARCHAR(1000) DEFAULT NULL COMMENT 'content summary',
    `owner_id` BIGINT NOT NULL COMMENT 'owner user id',
    `folder_id` BIGINT DEFAULT 0 COMMENT 'folder id, 0 means root',
    `is_pinned` TINYINT DEFAULT 0 COMMENT '1 pinned, 0 normal',
    `category` VARCHAR(100) DEFAULT NULL COMMENT 'document category',
    `content_format` ENUM('MARKDOWN','HTML') NOT NULL DEFAULT 'MARKDOWN' COMMENT 'content format',
    `cover_image` VARCHAR(1000) DEFAULT NULL COMMENT 'optional document cover image',
    `page_format` ENUM('A4','LETTER') NOT NULL DEFAULT 'A4' COMMENT 'paper format',
    `margin_top` SMALLINT NOT NULL DEFAULT 20 COMMENT 'top margin in mm',
    `margin_right` SMALLINT NOT NULL DEFAULT 20 COMMENT 'right margin in mm',
    `margin_bottom` SMALLINT NOT NULL DEFAULT 20 COMMENT 'bottom margin in mm',
    `margin_left` SMALLINT NOT NULL DEFAULT 20 COMMENT 'left margin in mm',
    `page_header` VARCHAR(500) DEFAULT NULL COMMENT 'print header text',
    `page_footer` VARCHAR(500) DEFAULT NULL COMMENT 'print footer text',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '1 in trash, 0 active',
    `deleted_at` DATETIME DEFAULT NULL COMMENT 'deleted time',
    `last_edit_by` BIGINT DEFAULT NULL COMMENT 'last editor user id',
    `collab_mode` ENUM('LEGACY','CRDT') NOT NULL DEFAULT 'CRDT' COMMENT 'collaboration mode',
    `revision` BIGINT NOT NULL DEFAULT 0 COMMENT 'current content revision',
    `persisted_revision` BIGINT NOT NULL DEFAULT 0 COMMENT 'persisted content revision',
    `content_hash` VARCHAR(64) DEFAULT NULL COMMENT 'SHA-256 content hash',
    `last_persisted_at` DATETIME(3) DEFAULT NULL COMMENT 'last persistence time',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_owner` (`owner_id`),
    KEY `idx_folder` (`folder_id`),
    KEY `idx_deleted` (`is_deleted`),
    KEY `idx_updated` (`updated_at`),
    KEY `idx_doc_revision` (`id`, `revision`),
    KEY `idx_persisted_revision` (`persisted_revision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='document table';

CREATE TABLE IF NOT EXISTS `doc_permission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `doc_id` BIGINT NOT NULL COMMENT 'document id',
    `user_id` BIGINT DEFAULT NULL COMMENT 'collaborator user id, null means share link',
    `permission` ENUM('READ','COMMENT','WRITE','ADMIN') DEFAULT 'READ' COMMENT 'permission level',
    `share_token` VARCHAR(64) DEFAULT NULL COMMENT 'share token',
    `share_password` VARCHAR(255) DEFAULT NULL COMMENT 'share password',
    `expire_time` DATETIME DEFAULT NULL COMMENT 'expiration time',
    `created_by` BIGINT NOT NULL COMMENT 'creator user id',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_doc` (`doc_id`),
    KEY `idx_user` (`user_id`),
    UNIQUE KEY `uk_share_token` (`share_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='document permission table';

CREATE TABLE IF NOT EXISTS `doc_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `doc_id` BIGINT NOT NULL COMMENT 'document id',
    `title` VARCHAR(500) NOT NULL COMMENT 'version title',
    `content` LONGTEXT NOT NULL COMMENT 'version content',
    `content_format` ENUM('MARKDOWN','HTML') NOT NULL DEFAULT 'MARKDOWN' COMMENT 'version content format',
    `version_num` INT NOT NULL COMMENT 'version number',
    `source_revision` BIGINT DEFAULT NULL COMMENT 'source document revision',
    `version_type` ENUM('AUTO','MANUAL','ROLLBACK') NOT NULL DEFAULT 'AUTO' COMMENT 'version type',
    `version_name` VARCHAR(100) DEFAULT NULL COMMENT 'user supplied version name',
    `description` VARCHAR(500) DEFAULT NULL COMMENT 'version description',
    `created_by` BIGINT NOT NULL COMMENT 'creator user id',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_doc_version` (`doc_id`, `version_num`),
    KEY `idx_doc_version_created` (`doc_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='document version table';

CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT 'operator user id',
    `doc_id` BIGINT DEFAULT NULL COMMENT 'document id',
    `action` VARCHAR(50) NOT NULL COMMENT 'action type',
    `detail` VARCHAR(500) DEFAULT NULL COMMENT 'action detail',
    `ip` VARCHAR(50) DEFAULT NULL COMMENT 'ip address',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_doc` (`doc_id`),
    KEY `idx_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='operation log table';

CREATE TABLE IF NOT EXISTS `file_attachment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `doc_id` BIGINT NOT NULL,
    `uploader_id` BIGINT NOT NULL,
    `original_name` VARCHAR(500) NOT NULL,
    `file_url` VARCHAR(1000) NOT NULL,
    `file_size` BIGINT NOT NULL,
    `mime_type` VARCHAR(200) DEFAULT NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_attachment_doc` (`doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='document attachment';

CREATE TABLE IF NOT EXISTS `tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `owner_id` BIGINT NOT NULL,
    `name` VARCHAR(50) NOT NULL,
    `color` VARCHAR(20) DEFAULT '#64748b',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag_owner_name` (`owner_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='document tag';

CREATE TABLE IF NOT EXISTS `document_tag` (
    `doc_id` BIGINT NOT NULL,
    `tag_id` BIGINT NOT NULL,
    PRIMARY KEY (`doc_id`, `tag_id`),
    KEY `idx_document_tag_tag` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='document tag relation';

CREATE TABLE IF NOT EXISTS `document_favorite` (
    `user_id` BIGINT NOT NULL, `doc_id` BIGINT NOT NULL,
    PRIMARY KEY (`user_id`, `doc_id`), KEY `idx_favorite_doc` (`doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user document favorite';

CREATE TABLE IF NOT EXISTS `document_template` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `owner_id` BIGINT DEFAULT NULL COMMENT 'null means system template',
    `name` VARCHAR(100) NOT NULL,
    `description` VARCHAR(500) DEFAULT NULL,
    `category` VARCHAR(100) DEFAULT NULL,
    `content` LONGTEXT NOT NULL,
    `content_format` ENUM('MARKDOWN','HTML') NOT NULL DEFAULT 'MARKDOWN',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_template_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='document template';

CREATE TABLE IF NOT EXISTS `verification_code` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT DEFAULT NULL,
    `target` VARCHAR(100) NOT NULL,
    `purpose` ENUM('EMAIL_CHANGE','PASSWORD_RESET') NOT NULL,
    `request_ip` VARCHAR(50) DEFAULT NULL,
    `code_hash` VARCHAR(255) NOT NULL,
    `expires_at` DATETIME NOT NULL,
    `used_at` DATETIME DEFAULT NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_verification_lookup` (`target`, `purpose`, `created_at`),
    KEY `idx_verification_ip_time` (`request_ip`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='email verification code';

CREATE TABLE IF NOT EXISTS `user_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `token_id` VARCHAR(64) NOT NULL,
    `device_name` VARCHAR(200) DEFAULT NULL,
    `ip_address` VARCHAR(50) DEFAULT NULL,
    `last_active_at` DATETIME NOT NULL,
    `refresh_token_hash` CHAR(64) DEFAULT NULL COMMENT 'SHA-256 of the rotating refresh token',
    `expires_at` DATETIME DEFAULT NULL COMMENT 'absolute session expiry',
    `revoked_at` DATETIME DEFAULT NULL COMMENT 'session revocation time',
    `remember_me` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_token` (`token_id`),
    UNIQUE KEY `uk_session_refresh_hash` (`refresh_token_hash`),
    KEY `idx_session_user` (`user_id`, `last_active_at`),
    KEY `idx_session_active` (`user_id`, `revoked_at`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user login session';

CREATE TABLE IF NOT EXISTS `user_feedback` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `doc_id` BIGINT DEFAULT NULL COMMENT 'optional related document',
    `type` ENUM('BUG','UI','SUGGESTION','OTHER') NOT NULL DEFAULT 'BUG',
    `description` TEXT NOT NULL,
    `status` ENUM('OPEN','PROCESSING','RESOLVED','CLOSED') NOT NULL DEFAULT 'OPEN',
    `admin_reply` VARCHAR(2000) DEFAULT NULL,
    `handler_id` BIGINT DEFAULT NULL,
    `handled_at` DATETIME DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_feedback_user_time` (`user_id`, `created_at`),
    KEY `idx_feedback_status_time` (`status`, `created_at`),
    KEY `idx_feedback_doc` (`doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user feedback';

CREATE TABLE IF NOT EXISTS `feedback_image` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `feedback_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `file_url` VARCHAR(1000) NOT NULL,
    `original_name` VARCHAR(500) DEFAULT NULL,
    `file_size` BIGINT NOT NULL,
    `mime_type` VARCHAR(200) DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_feedback_image_feedback` (`feedback_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='feedback screenshots';

CREATE TABLE IF NOT EXISTS `crdt_checkpoint` (
    `doc_id` BIGINT NOT NULL,
    `protocol_version` INT NOT NULL DEFAULT 1,
    `checkpoint_seq` BIGINT NOT NULL DEFAULT 0,
    `payload` LONGBLOB NOT NULL,
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Yjs CRDT compacted checkpoint';

CREATE TABLE IF NOT EXISTS `document_comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `doc_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `parent_id` BIGINT DEFAULT NULL COMMENT 'root comment id for replies',
    `content` VARCHAR(2000) NOT NULL,
    `selected_text` VARCHAR(1000) DEFAULT NULL,
    `status` ENUM('OPEN','RESOLVED') NOT NULL DEFAULT 'OPEN',
    `resolved_by` BIGINT DEFAULT NULL,
    `resolved_at` DATETIME DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_comment_doc_status` (`doc_id`, `status`, `created_at`),
    KEY `idx_comment_user` (`user_id`),
    KEY `idx_comment_parent` (`parent_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='document comments';

CREATE TABLE IF NOT EXISTS `comment_mention` (
    `comment_id` BIGINT NOT NULL, `user_id` BIGINT NOT NULL,
    `read_at` DATETIME DEFAULT NULL, `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`comment_id`, `user_id`),
    KEY `idx_mention_user_unread` (`user_id`, `read_at`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='comment mention and unread state';

CREATE TABLE IF NOT EXISTS `user_notification` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `user_id` BIGINT NOT NULL, `actor_id` BIGINT DEFAULT NULL,
    `doc_id` BIGINT DEFAULT NULL, `comment_id` BIGINT DEFAULT NULL,
    `type` ENUM('MENTION','COMMENT_REPLY') NOT NULL, `content` VARCHAR(500) NOT NULL,
    `is_read` TINYINT NOT NULL DEFAULT 0, `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), KEY `idx_notification_user_read` (`user_id`, `is_read`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='in-app notifications';

CREATE TABLE IF NOT EXISTS `crdt_update` (
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

CREATE TABLE IF NOT EXISTS `crdt_checkpoint_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `doc_id` BIGINT NOT NULL, `protocol_version` INT NOT NULL DEFAULT 1,
    `checkpoint_seq` BIGINT NOT NULL DEFAULT 0, `payload` LONGBLOB NOT NULL,
    `reason` ENUM('AUTO','MANUAL','PRE_RESTORE') NOT NULL DEFAULT 'AUTO',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`), KEY `idx_crdt_history_doc_time` (`doc_id`, `created_at`), KEY `idx_crdt_history_doc_seq` (`doc_id`, `checkpoint_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Historical Yjs CRDT checkpoints for restore';

CREATE TABLE IF NOT EXISTS `document_reference` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `doc_id` BIGINT NOT NULL, `cite_key` VARCHAR(100) NOT NULL,
    `doi` VARCHAR(255) DEFAULT NULL, `csl_json` LONGTEXT NOT NULL, `created_by` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_document_reference_key` (`doc_id`, `cite_key`),
    UNIQUE KEY `uk_document_reference_doi` (`doc_id`, `doi`), KEY `idx_document_reference_doc` (`doc_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per-document CSL JSON bibliography library';

CREATE TABLE IF NOT EXISTS `attachment_upload_session` (
    `id` CHAR(36) NOT NULL, `doc_id` BIGINT NOT NULL, `user_id` BIGINT NOT NULL, `original_name` VARCHAR(500) NOT NULL,
    `mime_type` VARCHAR(200) DEFAULT NULL, `total_size` BIGINT NOT NULL, `total_chunks` INT NOT NULL, `received_chunks` INT NOT NULL DEFAULT 0,
    `sha256` CHAR(64) DEFAULT NULL, `status` ENUM('UPLOADING','COMPLETE','ABORTED') NOT NULL DEFAULT 'UPLOADING',
    `expires_at` DATETIME NOT NULL, `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), KEY `idx_attachment_upload_doc_user` (`doc_id`, `user_id`, `status`), KEY `idx_attachment_upload_expire` (`expires_at`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Resumable attachment upload session';

INSERT INTO `document_template` (`owner_id`, `name`, `description`, `category`, `content`, `content_format`)
SELECT NULL, '会议纪要', '记录会议议题、结论和待办', '工作', '# 会议纪要\n\n## 会议信息\n\n- 时间：\n- 参与人：\n\n## 讨论事项\n\n## 结论\n\n## 待办事项\n', 'MARKDOWN'
WHERE NOT EXISTS (SELECT 1 FROM `document_template` WHERE `owner_id` IS NULL AND `name` = '会议纪要');

INSERT INTO `document_template` (`owner_id`, `name`, `description`, `category`, `content`, `content_format`)
SELECT NULL, '项目计划', '整理目标、里程碑和风险', '项目', '# 项目计划\n\n## 项目目标\n\n## 里程碑\n\n## 任务分工\n\n## 风险与对策\n', 'MARKDOWN'
WHERE NOT EXISTS (SELECT 1 FROM `document_template` WHERE `owner_id` IS NULL AND `name` = '项目计划');
