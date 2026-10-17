package com.docflow.controller;

import com.docflow.service.CrdtCheckpointService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Full HTTP/security/MyBatis regression coverage, opt-in against an isolated audit MySQL only. */
@EnabledIfEnvironmentVariable(named = "DOCFLOW_TEST_MYSQL_ISOLATED", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "logging.level.org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration=ERROR")
@AutoConfigureMockMvc(printOnlyOnFailure = false, print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WorkspaceSecurityIntegrationTest {
    private static final Path TEMP_ROOT = Path.of("D:/Javaproject/.tmp").toAbsolutePath().normalize();
    private static final String PASSWORD = "audit-fixture-password";
    private static String serverUrl;
    private static String database;
    private static Path uploads;
    private static boolean created;
    private static int sequence;

    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate sql;
    @MockBean StringRedisTemplate redis;
    @MockBean TaskScheduler taskScheduler;
    @MockBean CrdtCheckpointService checkpoints;

    private long aliceId;
    private long bobId;
    private long aliceDoc;
    private long bobDoc;
    private Login alice;
    private Login bob;
    private byte[] png;

    @DynamicPropertySource
    static void isolatedProperties(DynamicPropertyRegistry properties) throws Exception {
        int port = Integer.parseInt(System.getenv("DOCFLOW_TEST_MYSQL_PORT"));
        assertThat(port).isBetween(20000, 60000);
        serverUrl = "jdbc:mysql://127.0.0.1:" + port
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        database = "docflow_audit_" + UUID.randomUUID().toString().replace("-", "");
        try (var admin = DriverManager.getConnection(serverUrl, "root", ""); var statement = admin.createStatement()) {
            try (var rows = statement.executeQuery("SELECT @@datadir")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1).replace('\\', '/').toLowerCase(Locale.ROOT))
                        .contains("/.tmp/audit-mysql-");
            }
            statement.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4");
            created = true;
        }
        // Reproduce a populated pre-upgrade installation, with no Flyway history.
        try (var legacy = DriverManager.getConnection(serverUrl.replace("/?", "/" + database + "?"), "root", "")) {
            ScriptUtils.executeSqlScript(legacy, new EncodedResource(
                    new ByteArrayResource(new ClassPathResource("db/migration/V1__baseline.sql")
                            .getContentAsString(StandardCharsets.UTF_8).replace("\uFEFF", "").getBytes(StandardCharsets.UTF_8)),
                    StandardCharsets.UTF_8));
            try (var account = legacy.prepareStatement("INSERT INTO `user`(id,username,password) VALUES(9,'legacy-owner',?)")) {
                account.setString(1, new BCryptPasswordEncoder().encode(PASSWORD));
                account.executeUpdate();
            }
            try (var seed = legacy.createStatement()) {
                seed.execute("INSERT INTO document(id,title,content,owner_id,content_format) VALUES(9,'Legacy document','<p>原有正文</p>',9,'HTML')");
            }
        }
        uploads = Files.createTempDirectory(TEMP_ROOT, "audit-spring-uploads-");
        properties.add("spring.datasource.url", () -> serverUrl.replace("/?", "/" + database + "?"));
        properties.add("spring.datasource.username", () -> "root");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.flyway.enabled", () -> "true");
        properties.add("spring.flyway.baseline-on-migrate", () -> "true");
        properties.add("file.upload-dir", uploads::toString);
        properties.add("app.environment", () -> "development");
        properties.add("app.mail.enabled", () -> "false");
        properties.add("app.auth.cookie.enabled", () -> "false");
        properties.add("app.auth.cookie.secure", () -> "false");
        properties.add("jwt.secret", () -> "audit-jwt-fixture-123456789012345678901234567890");
        properties.add("file.signing-secret", () -> "audit-file-fixture-123456789012345678901234567890");
        properties.add("app.crdt.admin-secret", () -> "audit-crdt-fixture-123456789012345678901234567890");
        properties.add("mybatis-plus.configuration.log-impl", () -> "org.apache.ibatis.logging.nologging.NoLoggingImpl");
        properties.add("logging.level.com.docflow", () -> "WARN");
    }

    @BeforeEach
    void seedIndependentUsersAndDocuments() throws Exception {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        SetOperations<String, String> sets = mock(SetOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(1L);
        when(redis.opsForHash()).thenReturn(hashes);
        when(hashes.entries(anyString())).thenReturn(Map.of());
        when(redis.opsForSet()).thenReturn(sets);
        int fixture = ++sequence;
        aliceId = 100L + fixture * 2L;
        bobId = aliceId + 1;
        aliceDoc = 1000L + fixture * 1000L;
        bobDoc = aliceDoc + 1;
        String password = new BCryptPasswordEncoder().encode(PASSWORD);
        sql.update("INSERT INTO `user`(id,username,password) VALUES(?,?,?),(?,?,?)",
                aliceId, "alice" + fixture, password, bobId, "bob" + fixture, password);
        sql.update("INSERT INTO document(id,title,owner_id,content,content_format) VALUES(?,?,?,'<p>fixture</p>','HTML'),(?,?,?,'<p>fixture</p>','HTML')",
                aliceDoc, "Alice fixture", aliceId, bobDoc, "Bob fixture", bobId);
        alice = login("alice" + fixture);
        bob = login("bob" + fixture);
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x1A78C2);
        try (var output = new ByteArrayOutputStream()) {
            assertThat(ImageIO.write(image, "png", output)).isTrue();
            png = output.toByteArray();
        }
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class))
                .isEqualTo(5);
    }

    @AfterAll
    static void removeOnlyCreatedFixtures() throws Exception {
        try {
            if (created) {
                assertThat(database).matches("docflow_audit_[0-9a-f]{32}");
                try (var admin = DriverManager.getConnection(serverUrl, "root", ""); var statement = admin.createStatement()) {
                    statement.execute("DROP DATABASE `" + database + "`");
                    try (var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='" + database + "'")) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getInt(1)).isZero();
                    }
                }
            }
        } finally {
            if (uploads != null) {
                assertThat(uploads.getParent()).isEqualTo(TEMP_ROOT);
                assertThat(uploads.getFileName().toString()).startsWith("audit-spring-uploads-");
                try (var paths = Files.walk(uploads)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                }
                assertThat(uploads).doesNotExist();
            }
        }
    }

    @Test
    void anUpgradedLegacyAccountCanLogInAndReadItsExistingListAndBody() throws Exception {
        Login legacy = login("legacy-owner");
        http.perform(get("/api/v1/docs").header(HttpHeaders.AUTHORIZATION, legacy.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("data[0].id").value(9))
                .andExpect(jsonPath("data[0].title").value("Legacy document"));
        http.perform(get("/api/v1/docs/9").header(HttpHeaders.AUTHORIZATION, legacy.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("data.content").value("<p>原有正文</p>"));
        assertThat(sql.queryForObject("SELECT content FROM document WHERE id=9", String.class)).isEqualTo("<p>原有正文</p>");
    }

    @Test
    void bearerLoginIssuesFileOnlyCookieAndReadPermissionRevocationTakesEffect() throws Exception {
        String image = upload(alice, aliceDoc);
        http.perform(get(image).cookie(alice.fileCookie)).andExpect(status().isOk())
                .andExpect(content().bytes(png)).andExpect(header().string("Cache-Control", "no-store"));
        http.perform(get(image)).andExpect(status().isNotFound());
        http.perform(get(image).cookie(bob.fileCookie)).andExpect(status().isNotFound());
        http.perform(get("/api/v1/auth/session").cookie(alice.fileCookie)).andExpect(status().isUnauthorized());
        http.perform(get("/api/v1/auth/session").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isOk());
        http.perform(multipart("/api/v1/files/upload").file(file()).param("docId", Long.toString(aliceDoc))
                        .header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isForbidden());
        grantBobRead();
        http.perform(get(image).cookie(bob.fileCookie)).andExpect(status().isOk()).andExpect(content().bytes(png));
        revokeBob();
        http.perform(get(image).cookie(bob.fileCookie)).andExpect(status().isNotFound());
    }

    @Test
    void sharedImagesWorkButForgedReferencesAndAsyncExportsCannotReadAnotherOwnersImage() throws Exception {
        String privateImage = upload(alice, aliceDoc);
        sql.update("UPDATE document SET content=? WHERE id=?", html(privateImage), aliceDoc);
        String shared = shareImage(alice, aliceDoc);
        assertThat(shared).startsWith("/api/v1/public-files?");
        http.perform(get(shared)).andExpect(status().isOk()).andExpect(content().bytes(png));
        http.perform(get(shared.replace("docId=" + aliceDoc, "docId=" + bobDoc)))
                .andExpect(status().isNotFound());
        http.perform(post("/api/v1/docs").header(HttpHeaders.AUTHORIZATION, bob.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                                "title", "forged", "contentFormat", "HTML", "content", html(privateImage)))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value(403));
        // Simulate an old malicious document that predates write-time checks.
        sql.update("UPDATE document SET content=? WHERE id=?", html(privateImage), bobDoc);
        assertThat(shareImage(bob, bobDoc)).isEqualTo(privateImage);
        http.perform(get(privateImage).cookie(bob.fileCookie)).andExpect(status().isNotFound());
        for (String format : new String[]{"docx", "pdf"}) {
            MvcResult export = http.perform(post("/api/v1/docs/{id}/export/{format}", bobDoc, format)
                            .header(HttpHeaders.AUTHORIZATION, bob.bearer()).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsBytes(Map.of("content", html(privateImage)))))
                    .andExpect(request().asyncStarted()).andReturn();
            http.perform(asyncDispatch(export)).andExpect(status().isForbidden()).andExpect(jsonPath("code").value(403));
        }
        String ownImage = upload(bob, bobDoc);
        assertExportContainsImage(bob, bobDoc, html(ownImage));
        sql.update("UPDATE doc_permission SET expire_time=DATE_SUB(NOW(),INTERVAL 1 DAY) WHERE doc_id=? AND share_token IS NOT NULL", aliceDoc);
        http.perform(get(shared)).andExpect(status().isNotFound());
    }

    @Test
    void authorizedCopyKeepsImageAfterSourcePermissionRevocationAndPermanentPurge() throws Exception {
        String image = upload(alice, aliceDoc);
        sql.update("UPDATE document SET content=? WHERE id=?", html(image), aliceDoc);
        grantBobRead();
        JsonNode copy = body(http.perform(post("/api/v1/docs/{id}/copy", aliceDoc)
                        .header(HttpHeaders.AUTHORIZATION, bob.bearer())).andExpect(status().isOk()).andReturn());
        long copyId = copy.path("data").path("id").asLong();
        assertThat(copyId).isPositive();
        revokeBob();
        http.perform(delete("/api/v1/docs/{id}", aliceDoc).header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isOk());
        http.perform(delete("/api/v1/docs/{id}/permanent", aliceDoc).header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isOk());
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM document WHERE id=?", Integer.class, aliceDoc)).isZero();
        http.perform(get(image).cookie(bob.fileCookie)).andExpect(status().isOk()).andExpect(content().bytes(png));
        http.perform(get("/api/v1/docs/{id}", copyId).header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("data.content").value(html(image)));
        assertExportContainsImage(bob, copyId, null);
    }

    private Login login(String username) throws Exception {
        MvcResult result = http.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("data.cookieAuth").value(false)).andReturn();
        String token = body(result).path("data").path("token").asText();
        assertThat(token).isNotBlank();
        String header = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith("DOCFLOW_FILE_ACCESS=")).findFirst().orElseThrow();
        assertThat(header).contains("HttpOnly", "Path=/files", "SameSite=Lax");
        Cookie cookie = new Cookie("DOCFLOW_FILE_ACCESS", header.substring(header.indexOf('=') + 1, header.indexOf(';')));
        assertThat(cookie.getValue()).isEqualTo(token);
        return new Login(token, cookie);
    }

    private String upload(Login login, long docId) throws Exception {
        MvcResult result = http.perform(multipart("/api/v1/files/upload").file(file())
                        .param("docId", Long.toString(docId)).header(HttpHeaders.AUTHORIZATION, login.bearer()))
                .andExpect(status().isOk()).andReturn();
        String url = body(result).path("data").path("url").asText();
        assertThat(url).startsWith("/files/");
        return url;
    }

    private MockMultipartFile file() { return new MockMultipartFile("file", "fixture.png", "image/png", png); }
    private String html(String url) { return "<p><img src=\"" + url + "\"></p>"; }
    private JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsByteArray()); }

    private void grantBobRead() throws Exception {
        String username = sql.queryForObject("SELECT username FROM `user` WHERE id=?", String.class, bobId);
        http.perform(post("/api/v1/docs/{id}/permissions", aliceDoc).header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("username", username, "permission", "READ"))))
                .andExpect(status().isOk());
    }

    private void revokeBob() throws Exception {
        http.perform(delete("/api/v1/docs/{id}/permissions/{user}", aliceDoc, bobId)
                        .header(HttpHeaders.AUTHORIZATION, alice.bearer())).andExpect(status().isOk());
    }

    private String shareImage(Login login, long docId) throws Exception {
        JsonNode share = body(http.perform(post("/api/v1/docs/{id}/share", docId).header(HttpHeaders.AUTHORIZATION, login.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"permission\":\"READ\"}"))
                .andExpect(status().isOk()).andReturn());
        String token = share.path("data").path("token").asText();
        JsonNode shared = body(http.perform(get("/api/v1/share/{token}", token)).andExpect(status().isOk()).andReturn());
        return Jsoup.parseBodyFragment(shared.path("data").path("content").asText()).selectFirst("img").attr("src");
    }

    private void assertExportContainsImage(Login login, long docId, String content) throws Exception {
        var request = post("/api/v1/docs/{id}/export/docx", docId).header(HttpHeaders.AUTHORIZATION, login.bearer());
        if (content != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("content", content)));
        MvcResult started = http.perform(request).andExpect(request().asyncStarted()).andReturn();
        byte[] bytes = http.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var word = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            assertThat(word.getAllPictures()).hasSize(1);
            assertThat(word.getAllPictures().get(0).getData()).isEqualTo(png);
        }
    }

    private record Login(String token, Cookie fileCookie) {
        String bearer() { return "Bearer " + token; }
    }
}
