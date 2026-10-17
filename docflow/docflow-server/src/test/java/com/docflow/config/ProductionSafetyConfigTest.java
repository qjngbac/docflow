package com.docflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.*;

class ProductionSafetyConfigTest {
    @Test void productionProfilesCannotStartWithDevelopmentCredentialsOrDebugCodes() {
        var environment = new MockEnvironment(); environment.setActiveProfiles("production");
        assertThatThrownBy(() -> new ProductionSafetyConfig(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);
        environment.withProperty("jwt.secret", "j".repeat(40))
                .withProperty("file.signing-secret", "f".repeat(40))
                .withProperty("app.crdt.admin-secret", "c".repeat(40))
                .withProperty("app.auth.cookie.secure", "true")
                .withProperty("app.verification.debug-code", "false");
        assertThatCode(() -> new ProductionSafetyConfig(environment).afterPropertiesSet()).doesNotThrowAnyException();
        environment.withProperty("app.verification.debug-code", "true");
        assertThatThrownBy(() -> new ProductionSafetyConfig(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void localDevelopmentRemainsAvailable() {
        assertThatCode(() -> new ProductionSafetyConfig(new MockEnvironment()).afterPropertiesSet())
                .doesNotThrowAnyException();
    }
}
