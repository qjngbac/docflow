package com.docflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalConfigurationTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void operationalYamlFilesAreValid() throws Exception {
        var local = loader.load("application", new ClassPathResource("application.yml"));
        var example = loader.load("application-example", new ClassPathResource("application-example.yml"));

        assertFalse(local.isEmpty());
        assertFalse(example.isEmpty());
        assertNotNull(local.get(0).getProperty("spring.flyway.enabled"));
        assertTrue(String.valueOf(local.get(0).getProperty("management.server.address"))
                .contains("127.0.0.1"));
        assertEquals("health,info,prometheus",
                local.get(0).getProperty("management.endpoints.web.exposure.include"));
    }

    @Test
    void flywayBaselineTargetsTheConfiguredDatabaseOnly() throws Exception {
        var resource = new ClassPathResource("db/migration/V1__baseline.sql");
        String sql = resource.getContentAsString(StandardCharsets.UTF_8);

        assertFalse(sql.toUpperCase().contains("CREATE DATABASE"));
        assertFalse(sql.toUpperCase().contains("USE `DOCFLOW`"));
        assertEquals(23, sql.split("CREATE TABLE IF NOT EXISTS", -1).length - 1);
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `crdt_checkpoint`"));
    }
}
