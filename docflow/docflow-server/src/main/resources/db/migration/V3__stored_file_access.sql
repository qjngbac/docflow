-- Existing physical paths and URLs remain unchanged. Only registry-backed files are served.
CREATE TABLE IF NOT EXISTS stored_file (
    file_url VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id BIGINT DEFAULT NULL,
    file_kind ENUM('DOCUMENT','AVATAR','FEEDBACK') NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (file_url)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS stored_file_scope (
    file_url VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    scope_type ENUM('DOCUMENT','TEMPLATE') NOT NULL,
    scope_id BIGINT NOT NULL,
    PRIMARY KEY (file_url, scope_type, scope_id),
    KEY idx_file_scope (scope_type, scope_id),
    CONSTRAINT fk_file_scope_file FOREIGN KEY (file_url) REFERENCES stored_file(file_url)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Feedback has the strongest privacy category if inconsistent old records overlap.
INSERT IGNORE INTO stored_file(file_url, owner_id, file_kind)
SELECT file_url, user_id, 'FEEDBACK' FROM feedback_image
WHERE LENGTH(file_url)<=512 AND file_url REGEXP '^/files/[A-Za-z0-9_][A-Za-z0-9_./-]*$'
  AND file_url NOT REGEXP '/[.][.]?(/|$)' AND file_url NOT LIKE '%//%' AND file_url NOT LIKE '%/';

INSERT IGNORE INTO stored_file(file_url, owner_id, file_kind)
SELECT file_url, uploader_id, 'DOCUMENT' FROM file_attachment
WHERE LENGTH(file_url)<=512 AND file_url REGEXP '^/files/[A-Za-z0-9_][A-Za-z0-9_./-]*$'
  AND file_url NOT REGEXP '/[.][.]?(/|$)' AND file_url NOT LIKE '%//%' AND file_url NOT LIKE '%/';

INSERT IGNORE INTO stored_file_scope(file_url, scope_type, scope_id)
SELECT a.file_url, 'DOCUMENT', a.doc_id FROM file_attachment a
JOIN stored_file f ON f.file_url=a.file_url AND f.file_kind='DOCUMENT';

INSERT IGNORE INTO stored_file(file_url, owner_id, file_kind)
SELECT avatar, id, 'AVATAR' FROM `user`
WHERE LENGTH(avatar)<=512 AND avatar REGEXP '^/files/[A-Za-z0-9_][A-Za-z0-9_./-]*$'
  AND avatar NOT REGEXP '/[.][.]?(/|$)' AND avatar NOT LIKE '%//%' AND avatar NOT LIKE '%/';

-- Generic historical image provenance was never recorded. V5 performs a single snapshot
-- backfill of document/version/template references; see its explicit trust limitation.
