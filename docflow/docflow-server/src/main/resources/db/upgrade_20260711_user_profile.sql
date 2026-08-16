-- Run this migration once on an existing DocFlow database.
ALTER TABLE `user`
    ADD COLUMN `nickname` VARCHAR(50) DEFAULT NULL COMMENT 'display name' AFTER `username`;

UPDATE `user`
SET `nickname` = `username`
WHERE `nickname` IS NULL OR TRIM(`nickname`) = '';
ALTER TABLE `user`
    ADD COLUMN `nickname` VARCHAR(50) DEFAULT NULL COMMENT 'display name' AFTER `username`;