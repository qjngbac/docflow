-- Run once on an existing DocFlow database. Safe to run repeatedly.
DELIMITER $$

DROP PROCEDURE IF EXISTS docflow_add_column_if_missing$$
CREATE PROCEDURE docflow_add_column_if_missing(
    IN table_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64),
    IN column_definition_value VARCHAR(500)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = table_name_value
          AND COLUMN_NAME = column_name_value
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', table_name_value, '` ADD COLUMN `',
                          column_name_value, '` ', column_definition_value);
        PREPARE statement FROM @ddl;
        EXECUTE statement;
        DEALLOCATE PREPARE statement;
    END IF;
END$$

DELIMITER ;

CALL docflow_add_column_if_missing('user', 'system_role',
    'ENUM(''USER'',''ADMIN'') NOT NULL DEFAULT ''USER'' COMMENT ''system role'' AFTER `status`');
CALL docflow_add_column_if_missing('user', 'ban_reason',
    'VARCHAR(500) DEFAULT NULL COMMENT ''account ban reason'' AFTER `system_role`');
CALL docflow_add_column_if_missing('user', 'banned_at',
    'DATETIME DEFAULT NULL COMMENT ''account ban time'' AFTER `ban_reason`');
CALL docflow_add_column_if_missing('user', 'ban_expires_at',
    'DATETIME DEFAULT NULL COMMENT ''optional automatic unban time'' AFTER `banned_at`');

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

DROP PROCEDURE IF EXISTS docflow_add_column_if_missing;

-- Promote the first administrator manually after replacing the username:
-- UPDATE `user` SET `system_role` = 'ADMIN' WHERE `username` = 'your_username';
