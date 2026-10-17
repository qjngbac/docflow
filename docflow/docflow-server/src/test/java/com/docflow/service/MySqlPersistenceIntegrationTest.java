package com.docflow.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.docflow.entity.Document;
import com.docflow.mapper.DocVersionMapper;
import com.docflow.mapper.DocumentMapper;
import com.docflow.mapper.StoredFileMapper;
import com.docflow.mapper.UserNotificationMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt-in only: refuses to run against any instance not rooted in an audit temp directory. */
@EnabledIfEnvironmentVariable(named = "DOCFLOW_TEST_MYSQL_ISOLATED", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MySqlPersistenceIntegrationTest {
    private String serverUrl;
    private String database;
    private String url;
    private boolean created;
    private SqlSessionFactory sessions;
    private static final String IMAGE = "/files/legacy-attachment.png";

    @BeforeAll void createIsolatedDatabaseAndMigrateExistingData() throws Exception {
        int port = Integer.parseInt(System.getenv("DOCFLOW_TEST_MYSQL_PORT"));
        assertThat(port).isBetween(20000, 60000);
        serverUrl = "jdbc:mysql://127.0.0.1:" + port + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        database = "docflow_audit_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(serverUrl, "root", ""); var statement = admin.createStatement()) {
            try (ResultSet rows = statement.executeQuery("SELECT @@datadir")) {
                rows.next();
                assertThat(rows.getString(1).replace('\\', '/').toLowerCase(Locale.ROOT)).contains("/.tmp/audit-mysql-");
            }
            statement.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4");
            created = true;
        }
        url = serverUrl.replace("/?", "/" + database + "?");
        Flyway.configure().dataSource(url, "root", "").locations("classpath:db/migration").target("2").load().migrate();
        try (Connection connection = connection(); var sql = connection.createStatement()) {
            sql.execute("INSERT INTO `user`(id,username,password) VALUES(1,'owner','fixture'),(2,'other','fixture'),(3,'reader','fixture')");
            sql.execute("INSERT INTO document(id,title,owner_id,content) VALUES"
                    + "(11,'source',1,'<img src=\"" + IMAGE + "\">'),"
                    + "(12,'copy',1,'<img src=\"" + IMAGE + "\">'),"
                    + "(13,'unrelated',2,'<img src=\"" + IMAGE + "\">'),"
                    + "(14,'reader copy',3,'<img src=\"" + IMAGE + "\">'),"
                    + "(101,'versions',1,'version'),(102,'materialization',1,'base')");
            sql.execute("INSERT INTO doc_permission(doc_id,user_id,permission,created_by) VALUES(11,3,'READ',1)");
            sql.execute("INSERT INTO file_attachment(doc_id,uploader_id,original_name,file_url,file_size) VALUES"
                    + "(11,1,'image.png','" + IMAGE + "',1)");
            sql.execute("INSERT INTO document_template(id,owner_id,name,content) VALUES"
                    + "(21,1,'owner template','<img src=\"" + IMAGE + "\">')");
        }
        Flyway flyway = Flyway.configure().dataSource(url, "root", "").locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);
        flyway.validate();
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("audit", new JdbcTransactionFactory(), new DriverManagerDataSource(url, "root", "")));
        configuration.addMapper(DocumentMapper.class);
        configuration.addMapper(DocVersionMapper.class);
        configuration.addMapper(StoredFileMapper.class);
        configuration.addMapper(UserNotificationMapper.class);
        sessions = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @AfterAll void removeOnlyTheDatabaseCreatedByThisTest() throws Exception {
        if (!created) return;
        assertThat(database).matches("docflow_audit_[0-9a-f]{32}");
        try (Connection admin = DriverManager.getConnection(serverUrl, "root", ""); var statement = admin.createStatement()) {
            statement.execute("DROP DATABASE `" + database + "`");
        }
    }

    private Connection connection() throws Exception { return DriverManager.getConnection(url, "root", ""); }

    @Test void legacyCopiesTemplatesAndAuthorizedReadersKeepTheirEstablishedFiles() {
        try (SqlSession session = sessions.openSession(true)) {
            StoredFileMapper files = session.getMapper(StoredFileMapper.class);
            assertThat(files.documentScope(IMAGE, 11L)).isEqualTo(1);
            assertThat(files.documentScope(IMAGE, 12L)).isEqualTo(1);
            assertThat(files.documentScope(IMAGE, 13L)).isZero();
            assertThat(files.documentScope(IMAGE, 14L)).isEqualTo(1);
            assertThat(files.scopeUrls("TEMPLATE", 21L)).containsExactly(IMAGE);
            files.removeScope("DOCUMENT", 11L);
            assertThat(files.removeUnreferencedFile(IMAGE)).isZero();
            assertThat(files.documentScope(IMAGE, 12L)).isEqualTo(1);
        }
    }

    @Test void versionAllocationUsesCurrentReadsEvenWhenBothTransactionsHaveOldSnapshots() throws Exception {
        try (Connection first = connection(); Connection second = connection()) {
            first.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            second.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            first.setAutoCommit(false); second.setAutoCommit(false);
            try (SqlSession one = sessions.openSession(first); SqlSession two = sessions.openSession(second)) {
                // Both transactions establish their consistent read before the first version is inserted.
                one.getMapper(DocumentMapper.class).selectById(101L);
                two.getMapper(DocumentMapper.class).selectById(101L);
                Document document = new Document(); document.setId(101L); document.setTitle("Version");
                versions(one).createSnapshot(document, 1L, "MANUAL"); first.commit();
                versions(two).createSnapshot(document, 1L, "MANUAL"); second.commit();
            }
        }
        try (Connection connection = connection(); var sql = connection.createStatement();
             ResultSet rows = sql.executeQuery("SELECT GROUP_CONCAT(version_num ORDER BY version_num) FROM doc_version WHERE doc_id=101")) {
            rows.next(); assertThat(rows.getString(1)).isEqualTo("1,2");
        }
    }

    private VersionService versions(SqlSession session) {
        VersionService service = new VersionService();
        ReflectionTestUtils.setField(service, "documentMapper", session.getMapper(DocumentMapper.class));
        ReflectionTestUtils.setField(service, "docVersionMapper", session.getMapper(DocVersionMapper.class));
        return service;
    }

    @Test void batchNotificationInsertBindsAllRecipientsAndText() throws Exception {
        try (SqlSession session = sessions.openSession(true)) {
            NotificationService service = new NotificationService();
            ReflectionTestUtils.setField(service, "notificationMapper", session.getMapper(UserNotificationMapper.class));
            service.createAdminMessages(java.util.List.of(1L, 2L, 3L), 1L, "Batch's 系统通知");
        }
        try (Connection connection = connection(); var query = connection.prepareStatement(
                "SELECT COUNT(*) FROM user_notification WHERE content=? AND type='ADMIN_MESSAGE'")) {
            query.setString(1, "Batch's 系统通知");
            try (ResultSet rows = query.executeQuery()) { rows.next(); assertThat(rows.getInt(1)).isEqualTo(3); }
        }
    }

    @Test void databasePredicateRejectsLateMaterializationEvenAfterAStaleRead() {
        try (SqlSession session = sessions.openSession(true)) {
            var permission = mock(PermissionService.class);
            Document stale = session.getMapper(DocumentMapper.class).selectById(102L);
            when(permission.requireDocument(102L)).thenReturn(stale);
            DocService service = new DocService();
            ReflectionTestUtils.setField(service, "permissionService", permission);
            ReflectionTestUtils.setField(service, "documentMapper", session.getMapper(DocumentMapper.class));
            ReflectionTestUtils.setField(service, "htmlSanitizer", new HtmlSanitizer());
            ReflectionTestUtils.setField(service, "versionService", mock(VersionService.class));
            service.updateCrdtSnapshotFromCollaboration(102L, 1L, "<p>newest</p>", 20L);
            service.updateCrdtSnapshotFromCollaboration(102L, 1L, "<p>late old response</p>", 19L);
            service.updateCrdtSnapshotFromCollaboration(102L, 1L, "<p>old protocol</p>");
            Document saved = session.getMapper(DocumentMapper.class).selectById(102L);
            assertThat(saved.getContent()).isEqualTo("<p>newest</p>");
            assertThat(saved.getCrdtMaterializedGeneration()).isEqualTo(20L);
        }
    }
}
