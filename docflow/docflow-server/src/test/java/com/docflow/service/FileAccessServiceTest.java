package com.docflow.service;

import com.docflow.common.BusinessException;
import com.docflow.entity.StoredFile;
import com.docflow.mapper.StoredFileMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileAccessServiceTest {
    private static final String URL = "/files/2026/10/16/image.png";
    private final StoredFileMapper mapper = mock(StoredFileMapper.class);
    private final FileAccessService service = new FileAccessService(mapper);

    private void stored(String kind, Long owner) {
        StoredFile file = new StoredFile();
        file.setFileUrl(URL); file.setFileKind(kind); file.setOwnerId(owner);
        when(mapper.find(URL)).thenReturn(file);
    }

    @Test
    void privateAvatarPolicyRequiresAuthenticationAndAnExplicitShareAssociation() {
        stored("AVATAR", 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "publicAvatarAccess", false);
        assertThat(service.canRead(URL, null)).isFalse();
        assertThat(service.canRead(URL, 2L)).isTrue();
        assertThat(service.canDocumentFile(URL, 3L)).isFalse();
        when(mapper.documentScope(URL, 3L)).thenReturn(1);
        assertThat(service.canDocumentFile(URL, 3L)).isTrue();
    }

    @Test
    void rejectsUnknownAndForeignFilesAndNeverGrantsOnReads() {
        assertThat(service.canRead(URL, 2L)).isFalse();
        stored("DOCUMENT", 1L);
        assertThat(service.canRead(URL, 2L)).isFalse();
        assertThat(service.canDocumentFile(URL, 2L)).isFalse();
        verify(mapper, never()).grant(any(), any(), any());
    }

    @Test
    void permitsUploadPreviewThenRequiresDocumentAclAfterBindingIncludingForUploader() {
        stored("DOCUMENT", 1L);
        assertThat(service.canRead(URL, 1L)).isTrue();
        when(mapper.references(URL)).thenReturn(1);
        assertThat(service.canRead(URL, 1L)).isFalse();
        when(mapper.readableDocuments(eq(URL), eq(1L), any())).thenReturn(1);
        assertThat(service.canRead(URL, 1L)).isTrue();
        when(mapper.readableDocuments(eq(URL), eq(2L), any())).thenReturn(1);
        assertThat(service.canRead(URL, 2L)).isTrue();
        assertThat(service.canRead(URL, null)).isFalse();
    }

    @Test
    void onlyRegisteredAvatarsArePublicAndFeedbackStaysOwnerOrAdminOnly() {
        assertThat(service.canRead(URL, null)).isFalse();
        stored("AVATAR", 1L);
        assertThat(service.canRead(URL, null)).isTrue();
        stored("FEEDBACK", 1L);
        when(mapper.readableDocuments(eq(URL), eq(2L), any())).thenReturn(1);
        assertThat(service.canRead(URL, 1L)).isTrue();
        assertThat(service.canRead(URL, 2L)).isFalse();
        when(mapper.activeAdmin(2L)).thenReturn(1);
        assertThat(service.canRead(URL, 2L)).isTrue();
        assertThat(service.canDocumentFile(URL, 3L)).isFalse();
        assertThatThrownBy(() -> service.associateDocumentReferences(3L, null, URL, 1L))
                .isInstanceOf(BusinessException.class);
        verify(mapper, never()).grant(any(), any(), any());
    }

    @Test
    void grantsOnlyAlreadyReadableReferencesAndCopyPropagatesEstablishedAssociations() {
        stored("DOCUMENT", 1L);
        assertThatThrownBy(() -> service.associateDocumentReferences(3L, null, URL, 2L))
                .isInstanceOf(BusinessException.class);
        service.associateDocumentReferences(3L, "![image](" + URL + ")", null, 1L);
        verify(mapper).grant(URL, "DOCUMENT", 3L);
        when(mapper.readableDocument(eq(3L), eq(2L), any())).thenReturn(1);
        when(mapper.scopeUrls("DOCUMENT", 3L)).thenReturn(List.of(URL));
        service.copyDocumentReferences(3L, 4L, 2L);
        verify(mapper).grant(URL, "DOCUMENT", 4L);
        assertThatThrownBy(() -> service.copyDocumentReferences(3L, 5L, 9L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void readableTemplateInstancesRetainTheirFiles() {
        stored("DOCUMENT", null);
        when(mapper.readableTemplate(6L, 2L)).thenReturn(1);
        when(mapper.scopeUrls("TEMPLATE", 6L)).thenReturn(List.of(URL));
        service.copyTemplateReferences(6L, 4L, 2L);
        verify(mapper).grant(URL, "DOCUMENT", 4L);
    }

    @Test
    void shareAccessRequiresLiveMatchingShareAndEstablishedDocumentScope() {
        stored("DOCUMENT", 1L);
        when(mapper.activeShare(eq(3L), eq("share"), any())).thenReturn(1);
        assertThat(service.canShareFile(URL, 3L, "share")).isFalse();
        when(mapper.documentScope(URL, 3L)).thenReturn(1);
        assertThat(service.canShareFile(URL, 3L, "share")).isTrue();
        assertThat(service.canShareFile(URL, 4L, "share")).isFalse();
        when(mapper.activeShare(eq(3L), eq("share"), any())).thenReturn(0);
        assertThat(service.canShareFile(URL, 3L, "share")).isFalse();
    }

    @Test
    void rejectsAliasesAndExtractsLocalReferencesWithoutClaimingRemoteUrls() {
        for (String invalid : List.of("/files/../secret.png", "/files/a/./b.png", "/files//a.png", "/files/a%2fb.png",
                "/files/a.png?x=1", "/files/a\\b.png", "/files/a/", "/files/a.png.", "/files/folder./a.png")) {
            assertThat(FileAccessService.isCanonical(invalid)).as(invalid).isFalse();
            assertThat(service.canRead(invalid, 1L)).isFalse();
        }
        assertThat(FileAccessService.references("<img src='" + URL + "'> ![img](/files/local.png) "
                + "https://remote.test/files/remote.png https://remote.test./files/remote2.png "
                + "https://remote.test/?path=/files/remote3.png", null)).containsExactly(URL, "/files/local.png");
        assertThat(FileAccessService.references("<img src=/files/entity&#46;png>"
                + "<img src='https://remote.test/files/remote.png'>", null)).containsExactly("/files/entity.png");
    }
}
