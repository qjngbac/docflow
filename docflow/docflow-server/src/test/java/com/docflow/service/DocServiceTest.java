package com.docflow.service;

import com.docflow.dto.DocumentCreateRequest;
import com.docflow.entity.Document;
import com.docflow.mapper.DocumentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocServiceTest {

    @Mock private DocumentMapper documentMapper;
    @Mock private PermissionService permissionService;
    @Mock private VersionService versionService;
    @Mock private CollaborationService collaborationService;
    @Mock private TemplateService templateService;
    @Mock private HtmlSanitizer htmlSanitizer;

    @InjectMocks private DocService docService;

    @Test
    void creatorOwnsNewCrdtDocumentImmediately() {
        DocumentCreateRequest request = new DocumentCreateRequest();
        request.setTitle("Collaborative document");
        request.setContent("<p>Initial</p>");
        request.setContentFormat("HTML");
        when(htmlSanitizer.clean("<p>Initial</p>")).thenReturn("<p>Initial</p>");
        doAnswer(invocation -> {
            invocation.<Document>getArgument(0).setId(21L);
            return 1;
        }).when(documentMapper).insert(any(Document.class));

        Document created = docService.create(4L, request);

        assertThat(created.getId()).isEqualTo(21L);
        assertThat(created.getOwnerId()).isEqualTo(4L);
        assertThat(created.getLastEditBy()).isEqualTo(4L);
        assertThat(created.getCollabMode()).isEqualTo("CRDT");
        assertThat(created.getContentFormat()).isEqualTo("HTML");
        ArgumentCaptor<Document> document = ArgumentCaptor.forClass(Document.class);
        verify(documentMapper).insert(document.capture());
        assertThat(document.getValue().getOwnerId()).isEqualTo(4L);
        verify(versionService).createSnapshot(created, 4L, "AUTO");
    }
}
