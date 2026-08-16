package com.docflow.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSanitizerTest {
    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    @Test
    void keepsEditorMetadataAndRemovesExecutableMarkup() {
        String cleaned = sanitizer.clean("<span data-type='inline-math' data-latex='E=mc^2' "
                + "data-comment-id='9' onclick='bad()'></span><script>alert(1)</script>");

        assertThat(cleaned).contains("data-type=\"inline-math\"")
                .contains("data-latex=\"E=mc^2\"")
                .contains("data-comment-id=\"9\"")
                .doesNotContain("onclick", "script", "alert");
    }
}
