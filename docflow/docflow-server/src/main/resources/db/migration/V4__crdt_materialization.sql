CREATE TABLE IF NOT EXISTS crdt_materialization (
    doc_id BIGINT NOT NULL PRIMARY KEY,
    generation BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
    editor_user_id BIGINT NULL,
    payload LONGBLOB NOT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Durable pending CRDT HTML materialization';

-- schema.sql may already contain the column when an existing database is first baselined.
SET @docflow_generation_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema=DATABASE() AND table_name='document' AND column_name='crdt_materialized_generation'),
    'SELECT 1',
    'ALTER TABLE document ADD COLUMN crdt_materialized_generation BIGINT NOT NULL DEFAULT 0'
);
PREPARE docflow_generation_statement FROM @docflow_generation_ddl;
EXECUTE docflow_generation_statement;
DEALLOCATE PREPARE docflow_generation_statement;
