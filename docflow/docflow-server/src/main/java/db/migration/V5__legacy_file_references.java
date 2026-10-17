package db.migration;

import com.docflow.service.FileAccessService;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * One-time compatibility snapshot, recorded by Flyway. Generic old uploads have no uploader
 * provenance; their existing document/version/template references must be trusted at this cutover.
 * This cannot distinguish a historically injected URL from a legitimate cross-owner copy.
 * It never runs on reads or later saves, never scans physical file contents, and never claims
 * unknown orphans. Known document files need target-owner ownership or current source access;
 * feedback and avatar records never acquire document/template grants here. Historical foreign
 * copies whose source access has since been revoked cannot be proved and are not inferred.
 */
public class V5__legacy_file_references extends BaseJavaMigration {
    static final String SOURCE_ACCESS_SQL = "SELECT COUNT(*) FROM stored_file_scope s WHERE s.file_url=? AND ("
            + "(s.scope_type='DOCUMENT' AND EXISTS(SELECT 1 FROM document d WHERE d.id=s.scope_id "
            + "AND d.is_deleted=0 AND (d.owner_id=? OR EXISTS(SELECT 1 FROM doc_permission p "
            + "WHERE p.doc_id=d.id AND p.user_id=? AND p.permission IN ('READ','COMMENT','WRITE','ADMIN') "
            + "AND (p.expire_time IS NULL OR p.expire_time>?))))) "
            + "OR (s.scope_type='TEMPLATE' AND EXISTS(SELECT 1 FROM document_template t WHERE t.id=s.scope_id "
            + "AND (t.owner_id IS NULL OR t.owner_id=?))))";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        LocalDateTime cutoff = LocalDateTime.now();
        backfill(connection, "SELECT id,id,content,cover_image,owner_id FROM document "
                + "WHERE id>? ORDER BY id LIMIT 1", "DOCUMENT", cutoff);
        backfill(connection, "SELECT v.id,v.doc_id,v.content,NULL,d.owner_id FROM doc_version v "
                + "JOIN document d ON d.id=v.doc_id WHERE v.id>? ORDER BY v.id LIMIT 1", "DOCUMENT", cutoff);
        backfill(connection, "SELECT id,id,content,NULL,owner_id FROM document_template "
                + "WHERE id>? ORDER BY id LIMIT 1", "TEMPLATE", cutoff);
    }

    private void backfill(Connection connection, String query, String type, LocalDateTime cutoff) throws SQLException {
        try (PreparedStatement source = connection.prepareStatement(query);
             PreparedStatement register = connection.prepareStatement(
                     "INSERT IGNORE INTO stored_file(file_url,owner_id,file_kind) VALUES(?,NULL,'DOCUMENT')");
             PreparedStatement metadata = connection.prepareStatement(
                     "SELECT owner_id,file_kind FROM stored_file WHERE file_url=?");
             PreparedStatement sourceAccess = connection.prepareStatement(SOURCE_ACCESS_SQL);
             PreparedStatement grant = connection.prepareStatement(
                     "INSERT IGNORE INTO stored_file_scope(file_url,scope_type,scope_id) "
                             + "SELECT file_url,?,? FROM stored_file WHERE file_url=? AND file_kind='DOCUMENT'")) {
            long cursor = 0L;
            while (true) {
                long scopeId;
                Long scopeOwner;
                String content;
                String cover;
                source.setLong(1, cursor);
                // MySQL buffers ordinary results; LIMIT bounds memory and closing here permits
                // the metadata/grant queries below on the same connection without streaming.
                try (ResultSet rows = source.executeQuery()) {
                    if (!rows.next()) break;
                    cursor = rows.getLong(1); // Version primary key is distinct from its document scope ID.
                    scopeId = rows.getLong(2);
                    content = rows.getString(3);
                    cover = rows.getString(4);
                    scopeOwner = rows.getObject(5, Long.class);
                }
                for (String url : FileAccessService.references(content, cover)) {
                    register.setString(1, url);
                    register.executeUpdate();
                    if (!canGrant(metadata, sourceAccess, url, scopeOwner, cutoff)) continue;
                    grant.setString(1, type);
                    grant.setLong(2, scopeId);
                    grant.setString(3, url);
                    grant.executeUpdate();
                }
            }
        }
    }

    private boolean canGrant(PreparedStatement metadata, PreparedStatement sourceAccess,
                             String url, Long scopeOwner, LocalDateTime cutoff) throws SQLException {
        metadata.setString(1, url);
        try (ResultSet file = metadata.executeQuery()) {
            if (!file.next() || !"DOCUMENT".equals(file.getString(2))) return false;
            Long fileOwner = file.getObject(1, Long.class);
            if (fileOwner == null) return true; // Explicit compatibility trust for generic snapshot references.
            if (scopeOwner == null) return false; // No inference of private files into system templates.
            if (fileOwner.equals(scopeOwner)) return true;
        }
        sourceAccess.setString(1, url);
        sourceAccess.setLong(2, scopeOwner);
        sourceAccess.setLong(3, scopeOwner);
        sourceAccess.setTimestamp(4, Timestamp.valueOf(cutoff));
        sourceAccess.setLong(5, scopeOwner);
        try (ResultSet access = sourceAccess.executeQuery()) {
            return access.next() && access.getInt(1) > 0;
        }
    }
}
