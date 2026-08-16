-- Run once after upgrade_20260712_stages_1_2.sql.
ALTER TABLE `verification_code`
    ADD COLUMN `request_ip` VARCHAR(50) DEFAULT NULL AFTER `purpose`;

CREATE INDEX `idx_verification_ip_time`
    ON `verification_code` (`request_ip`, `created_at`);
