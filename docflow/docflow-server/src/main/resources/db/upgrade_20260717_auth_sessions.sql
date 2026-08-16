-- Run once on an existing DocFlow database before starting the updated server.
-- The helpers make the migration safe to re-run after a partial execution.

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

DROP PROCEDURE IF EXISTS docflow_add_index_if_missing$$
CREATE PROCEDURE docflow_add_index_if_missing(
    IN table_name_value VARCHAR(64),
    IN index_name_value VARCHAR(64),
    IN index_definition_value VARCHAR(500)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = table_name_value
          AND INDEX_NAME = index_name_value
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', table_name_value, '` ADD ', index_definition_value);
        PREPARE statement FROM @ddl;
        EXECUTE statement;
        DEALLOCATE PREPARE statement;
    END IF;
END$$

DELIMITER ;

CALL docflow_add_column_if_missing('user_session', 'refresh_token_hash',
    'CHAR(64) DEFAULT NULL COMMENT ''SHA-256 of the rotating refresh token'' AFTER `last_active_at`');
CALL docflow_add_column_if_missing('user_session', 'expires_at',
    'DATETIME DEFAULT NULL COMMENT ''absolute session expiry'' AFTER `refresh_token_hash`');
CALL docflow_add_column_if_missing('user_session', 'revoked_at',
    'DATETIME DEFAULT NULL COMMENT ''session revocation time'' AFTER `expires_at`');
CALL docflow_add_column_if_missing('user_session', 'remember_me',
    'TINYINT NOT NULL DEFAULT 0 AFTER `revoked_at`');
CALL docflow_add_column_if_missing('user_session', 'updated_at',
    'DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `created_at`');

CALL docflow_add_index_if_missing('user_session', 'uk_session_refresh_hash',
    'UNIQUE KEY `uk_session_refresh_hash` (`refresh_token_hash`)');
CALL docflow_add_index_if_missing('user_session', 'idx_session_active',
    'KEY `idx_session_active` (`user_id`, `revoked_at`, `expires_at`)');

DROP PROCEDURE IF EXISTS docflow_add_column_if_missing;
DROP PROCEDURE IF EXISTS docflow_add_index_if_missing;
