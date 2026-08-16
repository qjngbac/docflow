-- Run this once when upgrading an existing DocFlow database without Flyway.
ALTER TABLE `user_notification`
    MODIFY COLUMN `type` ENUM('MENTION','COMMENT_REPLY','FEEDBACK_UPDATED','ADMIN_MESSAGE') NOT NULL;
