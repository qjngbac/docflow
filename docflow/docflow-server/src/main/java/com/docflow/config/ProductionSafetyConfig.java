package com.docflow.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Fail startup rather than expose development credentials or verification codes in production. */
@Component
public class ProductionSafetyConfig implements InitializingBean {
    private final Environment environment;

    public ProductionSafetyConfig(Environment environment) { this.environment = environment; }

    @Override public void afterPropertiesSet() {
        String mode = environment.getProperty("app.environment", "development");
        if (!"production".equalsIgnoreCase(mode) && !"prod".equalsIgnoreCase(mode)
                && !environment.acceptsProfiles(Profiles.of("production", "prod"))) return;
        requireSecret("jwt.secret");
        requireSecret("file.signing-secret");
        requireSecret("app.crdt.admin-secret");
        if (environment.getProperty("app.verification.debug-code", Boolean.class, false)) {
            throw new IllegalStateException("Production must disable app.verification.debug-code");
        }
        if (!environment.getProperty("app.auth.cookie.secure", Boolean.class, false)) {
            throw new IllegalStateException("Production must enable app.auth.cookie.secure");
        }
    }

    private void requireSecret(String name) {
        String secret = environment.getProperty(name, "");
        if (secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32
                || secret.contains("docflow-local") || secret.startsWith("replace-")) {
            throw new IllegalStateException("Production requires an independent random secret for " + name);
        }
    }
}
