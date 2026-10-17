package com.docflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import static org.assertj.core.api.Assertions.assertThat;

class FileResourceConfigurationTest {
    @Test
    void neverExposesTheUploadDirectoryAsAStaticResource() {
        WebMvcConfig config = new WebMvcConfig();
        ResourceHandlerRegistry registry = new ResourceHandlerRegistry(
                new StaticWebApplicationContext(), new MockServletContext());

        config.addResourceHandlers(registry);

        assertThat(registry.hasMappingForPattern("/files/**")).isFalse();
    }
}
