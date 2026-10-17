package com.docflow.security;

import com.docflow.common.BusinessException;
import com.docflow.service.FileAccessService;
import com.docflow.service.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SignedFileServiceTest {
    private final FileService files = mock(FileService.class);
    private final FileAccessService access = mock(FileAccessService.class);
    private final SignedFileService service = new SignedFileService(files, access, "test-secret-with-sufficient-length", 300);

    @Test
    void refusesToSignAPathWithoutAnAuthorizedDocumentShareContext() {
        assertThatThrownBy(() -> service.sign("/files/victim.png")).isInstanceOf(BusinessException.class);
        assertThat(service.resolve("/files/victim.png", Long.MAX_VALUE, "signature")).isEmpty();
    }

    @Test
    void acceptsAuthorizedSignatureAndRejectsPathDocumentTokenAndSignatureTampering() {
        String url = "/files/image.png";
        Path stored = Path.of("uploads", "image.png");
        when(files.resolveStoredFile(url)).thenReturn(Optional.of(stored));
        when(access.canShareFile(url, 3L, "valid-share")).thenReturn(true);
        var query = UriComponentsBuilder.fromUriString(service.sign(url, 3L, "valid-share")).build().getQueryParams();
        long expires = Long.parseLong(query.getFirst("expires"));
        String signature = query.getFirst("signature");

        assertThat(service.resolve(url, expires, signature, 3L, "valid-share")).contains(stored);
        assertThat(service.resolve("/files/other.png", expires, signature, 3L, "valid-share")).isEmpty();
        assertThat(service.resolve(url, expires, signature, 4L, "valid-share")).isEmpty();
        assertThat(service.resolve(url, expires, signature, 3L, "other-share")).isEmpty();
        assertThat(service.resolve(url, expires, signature + "x", 3L, "valid-share")).isEmpty();
        verify(access).requireShareFile(url, 3L, "valid-share");
    }

    @Test
    void rejectsRevokedShareAndExpiredCapability() {
        String url = "/files/image.png";
        when(access.canShareFile(url, 3L, "valid-share")).thenReturn(true);
        var query = UriComponentsBuilder.fromUriString(service.sign(url, 3L, "valid-share")).build().getQueryParams();
        long expires = Long.parseLong(query.getFirst("expires"));
        when(access.canShareFile(url, 3L, "valid-share")).thenReturn(false);

        assertThat(service.resolve(url, expires, query.getFirst("signature"), 3L, "valid-share")).isEmpty();
        assertThat(service.resolve(url, 1, query.getFirst("signature"), 3L, "valid-share")).isEmpty();
        verify(files, never()).resolveStoredFile(anyString());
    }

    @Test
    void rewritesOnlyAuthorizedDocumentFilesInHtmlAndMarkdown() {
        when(access.canShareFile("/files/own.png", 3L, "share")).thenReturn(true);
        String html = service.rewriteContent("<img src='/files/own.png'><img src='/files/victim.png'>", 3L, "share");
        assertThat(html).contains("/api/v1/public-files?").contains("src=\"/files/victim.png\"");
        String markdown = service.rewriteContent("![own](/files/own.png) ![other](/files/victim.png) https://example.org./files/own.png", 3L, "share");
        assertThat(markdown).contains("![own](/api/v1/public-files?")
                .contains("![other](/files/victim.png)").contains("https://example.org./files/own.png");
    }

    @Test
    void rejectsSigningAForeignFileEvenWhenItsUrlWasInsertedInAnOwnedDocument() {
        doThrow(new BusinessException(com.docflow.common.ErrorCode.FORBIDDEN, "No file access"))
                .when(access).requireShareFile("/files/victim.png", 3L, "share");
        assertThatThrownBy(() -> service.sign("/files/victim.png", 3L, "share")).isInstanceOf(BusinessException.class);
    }
}
