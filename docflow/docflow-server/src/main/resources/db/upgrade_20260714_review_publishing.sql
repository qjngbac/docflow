USE `docflow`;

ALTER TABLE `user`
    ADD COLUMN `welcome_dismissed` TINYINT NOT NULL DEFAULT 0 COMMENT '1 means the built-in guide was dismissed' AFTER `last_login_at`;

ALTER TABLE `document`
    ADD COLUMN `cover_image` VARCHAR(1000) DEFAULT NULL COMMENT 'optional document cover image' AFTER `content_format`,
    ADD COLUMN `page_format` ENUM('A4','LETTER') NOT NULL DEFAULT 'A4' COMMENT 'paper format' AFTER `cover_image`,
    ADD COLUMN `margin_top` SMALLINT NOT NULL DEFAULT 20 COMMENT 'top margin in mm' AFTER `page_format`,
    ADD COLUMN `margin_right` SMALLINT NOT NULL DEFAULT 20 COMMENT 'right margin in mm' AFTER `margin_top`,
    ADD COLUMN `margin_bottom` SMALLINT NOT NULL DEFAULT 20 COMMENT 'bottom margin in mm' AFTER `margin_right`,
    ADD COLUMN `margin_left` SMALLINT NOT NULL DEFAULT 20 COMMENT 'left margin in mm' AFTER `margin_bottom`,
    ADD COLUMN `page_header` VARCHAR(500) DEFAULT NULL COMMENT 'print header text' AFTER `margin_left`,
    ADD COLUMN `page_footer` VARCHAR(500) DEFAULT NULL COMMENT 'print footer text' AFTER `page_header`;

ALTER TABLE `doc_permission`
    MODIFY COLUMN `permission` ENUM('READ','COMMENT','WRITE','ADMIN') NOT NULL DEFAULT 'READ' COMMENT 'permission level';

ALTER TABLE `document_comment`
    ADD COLUMN `parent_id` BIGINT DEFAULT NULL COMMENT 'root comment id for replies' AFTER `user_id`,
    ADD KEY `idx_comment_parent` (`parent_id`, `created_at`);

ALTER TABLE `doc_version`
    ADD COLUMN `version_name` VARCHAR(100) DEFAULT NULL COMMENT 'user supplied version name' AFTER `version_type`,
    ADD COLUMN `description` VARCHAR(500) DEFAULT NULL COMMENT 'version description' AFTER `version_name`;

CREATE TABLE IF NOT EXISTS `comment_mention` (
    `comment_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `read_at` DATETIME DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`comment_id`, `user_id`),
    KEY `idx_mention_user_unread` (`user_id`, `read_at`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='comment mention and unread state';

CREATE TABLE IF NOT EXISTS `user_notification` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `actor_id` BIGINT DEFAULT NULL,
    `doc_id` BIGINT DEFAULT NULL,
    `comment_id` BIGINT DEFAULT NULL,
    `type` ENUM('MENTION','COMMENT_REPLY') NOT NULL,
    `content` VARCHAR(500) NOT NULL,
    `is_read` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_notification_user_read` (`user_id`, `is_read`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='in-app notifications';
