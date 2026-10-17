package com.docflow.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.docflow.entity.Document;
import com.docflow.mapper.DocumentMapper;
import com.docflow.mapper.StoredFileMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Exercises the actual upgrade boundary: populated databases without Flyway history. */
@EnabledIfEnvironmentVariable(named = "DOCFLOW_TEST_MYSQL_ISOLATED", matches = "true")
class LegacySchemaUpgradeIntegrationTest {
    @Test void existingDocumentsAndBinaryHistorySurviveTheLegacyUpgrade() throws Exception {
        exerciseUpgrade("db/migration/V1__baseline.sql", false);
    }

    @Test void aDatabaseInitializedFromTheCurrentBootstrapCanAlsoBeUpgraded() throws Exception {
        exerciseUpgrade("db/schema.sql", true);
    }

    private void exerciseUpgrade(String resource, boolean alreadyCurrent) throws Exception {
        int port = Integer.parseInt(System.getenv("DOCFLOW_TEST_MYSQL_PORT"));
        assertThat(port).isBetween(20000, 60000);
        String server = "jdbc:mysql://127.0.0.1:" + port
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        String database = "docflow_audit_" + UUID.randomUUID().toString().replace("-", "");
        boolean created = false;
        try (Connection admin = DriverManager.getConnection(server, "root", ""); var command = admin.createStatement()) {
            try (var row = command.executeQuery("SELECT @@datadir")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1).replace('\\', '/').toLowerCase(Locale.ROOT))
                        .contains("/.tmp/audit-mysql-");
            }
            command.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4");
            created = true;
            String url = server.replace("/?", "/" + database + "?");
            try (Connection connection = DriverManager.getConnection(url, "root", "")) {
                String script;
                try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                    assertThat(input).isNotNull();
                    script = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\uFEFF", "");
                }
                // Never let the bootstrap's fixed database name escape this random test database.
                script = script.replaceFirst("(?m)^CREATE DATABASE[^\r\n]*[\r\n]+", "")
                        .replaceFirst("(?m)^USE `docflow`;[\r\n]+", "");
                ScriptUtils.executeSqlScript(connection, new EncodedResource(
                        new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
                try (var sql = connection.createStatement()) {
                    sql.execute("INSERT INTO `user`(id,username,password,avatar) VALUES(1,'legacy-owner','fixture','/files/avatar.png')");
                    sql.execute("INSERT INTO folder(id,name,owner_id) VALUES(7,'Existing folder',1)");
                    sql.execute("INSERT INTO document(id,title,owner_id,folder_id,content,content_format,is_deleted) VALUES"
                            + "(11,'Existing document',1,7,'<p>原有正文<img src=\"/files/inline.png\"></p>','HTML',0),"
                            + "(12,'Trashed document',1,7,'保留回收站正文','HTML',1)");
                    sql.execute("INSERT INTO doc_version(doc_id,version_num,title,content,created_by) VALUES(11,1,'Historical title','历史版本',1)");
                    sql.execute("INSERT INTO crdt_checkpoint(doc_id,payload) VALUES(11,X'000102FF')");
                    sql.execute("INSERT INTO crdt_update(doc_id,update_id,payload) VALUES(11,'first',X'00FF01'),(11,'second',X'020304')");
                    sql.execute("INSERT INTO crdt_checkpoint_history(doc_id,checkpoint_seq,payload) VALUES(11,2,X'FF0001')");
                    // Node may already have created its outbox before Java is upgraded.
                    sql.execute("CREATE TABLE IF NOT EXISTS crdt_materialization (doc_id BIGINT PRIMARY KEY,"
                            + "generation BIGINT NOT NULL AUTO_INCREMENT UNIQUE,editor_user_id BIGINT NULL,"
                            + "payload LONGBLOB NOT NULL,updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3))");
                }
                List<String> before = snapshot(connection);
                var configuration = new MybatisConfiguration();
                configuration.setMapUnderscoreToCamelCase(true);
                configuration.setEnvironment(new Environment("legacy", new JdbcTransactionFactory(),
                        new DriverManagerDataSource(url, "root", "")));
                configuration.addMapper(DocumentMapper.class);
                configuration.addMapper(StoredFileMapper.class);
                var sessions = new MybatisSqlSessionFactoryBuilder().build(configuration);
                if (!alreadyCurrent) {
                    try (var session = sessions.openSession()) {
                        assertThatThrownBy(() -> session.getMapper(DocumentMapper.class).selectById(11L))
                                .hasStackTraceContaining("crdt_materialized_generation");
                    }
                }
                Flyway flyway = Flyway.configure().dataSource(url, "root", "")
                        .locations("classpath:db/migration").baselineOnMigrate(true).baselineVersion("1").load();
                flyway.migrate();
                flyway.validate();
                assertThat(flyway.migrate().migrationsExecuted).isZero();
                assertThat(snapshot(connection)).containsExactlyElementsOf(before);
                try (var session = sessions.openSession()) {
                    Document document = session.getMapper(DocumentMapper.class).selectById(11L);
                    assertThat(document.getTitle()).isEqualTo("Existing document");
                    assertThat(document.getContent()).contains("原有正文");
                    var files = session.getMapper(StoredFileMapper.class);
                    assertThat(files.find("/files/avatar.png").getFileKind()).isEqualTo("AVATAR");
                    assertThat(files.documentScope("/files/inline.png", 11L)).isEqualTo(1);
                }
            }
        } finally {
            if (created) {
                assertThat(database).matches("docflow_audit_[0-9a-f]{32}");
                try (Connection admin = DriverManager.getConnection(server, "root", ""); var sql = admin.createStatement()) {
                    sql.execute("DROP DATABASE `" + database + "`");
                }
            }
        }
    }

    private List<String> snapshot(Connection connection) throws Exception {
        List<String> result = new ArrayList<>();
        for (String query : List.of("SELECT id,username,password,avatar FROM `user` ORDER BY id",
                "SELECT * FROM folder ORDER BY id",
                "SELECT id,title,content,summary,owner_id,folder_id,is_deleted,revision,content_hash,updated_at FROM document ORDER BY id",
                "SELECT * FROM doc_version ORDER BY id", "SELECT * FROM crdt_checkpoint ORDER BY doc_id",
                "SELECT * FROM crdt_update ORDER BY id", "SELECT * FROM crdt_checkpoint_history ORDER BY doc_id,checkpoint_seq")) {
            try (var sql = connection.createStatement(); var rows = sql.executeQuery(query)) {
                result.add(query);
                while (rows.next()) {
                    for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                        Object value = rows.getObject(i);
                        result.add(value instanceof byte[] bytes ? HexFormat.of().formatHex(bytes) : String.valueOf(value));
                    }
                }
            }
        }
        return result;
    }
}
