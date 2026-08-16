package com.docflow.security;

import com.docflow.service.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SignedFileServiceTest {
    @Test
    void acceptsValidShortLivedSignatureAndRejectsTampering() {
        FileService files = mock(FileService.class);
        SignedFileService service = new SignedFileService(files, "test-secret-with-sufficient-length", 300);
        Path stored = Path.of("uploads", "image.png");
        when(files.resolveStoredFile("/files/image.png")).thenReturn(Optional.of(stored));

        String signed = service.sign("/files/image.png");
        var query = UriComponentsBuilder.fromUriString(signed).build().getQueryParams();
        long expires = Long.parseLong(query.getFirst("expires"));
        String signature = query.getFirst("signature");

        assertThat(service.resolve("/files/image.png", expires, signature)).contains(stored);
        assertThat(service.resolve("/files/other.png", expires, signature)).isEmpty();
        assertThat(service.resolve("/files/image.png", expires, signature + "x")).isEmpty();
    }
}
