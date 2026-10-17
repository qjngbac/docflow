package com.docflow.service;

import com.docflow.dto.DocumentCreateRequest;
import com.docflow.entity.Document;
import com.docflow.entity.DocumentFavorite;
import com.docflow.mapper.DocumentMapper;
import com.docflow.mapper.CrdtDataMapper;
import com.docflow.mapper.DocPermissionMapper;
import com.docflow.mapper.DocVersionMapper;
import com.docflow.mapper.DocumentFavoriteMapper;
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
    @Mock private AttachmentService attachmentService;
    @Mock private TagService tagService;
    @Mock private DocumentCommentService documentCommentService;
    @Mock private CrdtDataMapper crdtDataMapper;
    @Mock private CrdtCheckpointService crdtCheckpointService;
    @Mock private DocPermissionMapper docPermissionMapper;
    @Mock private DocVersionMapper docVersionMapper;
    @Mock private DocumentFavoriteMapper documentFavoriteMapper;
    @Mock private FolderService folderService;
    @Mock private FileAccessService fileAccessService;
    @Mock private FileService fileService;
    @Mock private DocumentReferenceService documentReferenceService;
    @Mock private NotificationService notificationService;
    @Mock private AttachmentUploadService attachmentUploadService;

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

    @Test
    void readOnlyCollaboratorCanChangePersonalFavorite() {
        Document document = new Document();
        document.setId(21L); document.setOwnerId(4L); document.setCollabMode("CRDT");
        org.mockito.Mockito.lenient().when(permissionService.requireReadable(21L, 5L)).thenReturn(document);
        org.mockito.Mockito.lenient().when(permissionService.requireWritable(21L, 5L))
                .thenThrow(new com.docflow.common.BusinessException(com.docflow.common.ErrorCode.FORBIDDEN));
        var request = new com.docflow.dto.DocumentUpdateRequest(); request.setFavorite(true);
        docService.update(21L, 5L, request);
        verify(documentFavoriteMapper).insert(any(DocumentFavorite.class));
    }

    @Test
    void lateMaterializationCannotOverwriteANewerGeneration() {
        Document current = new Document();
        current.setId(21L); current.setOwnerId(4L); current.setCollabMode("CRDT");
        current.setCrdtMaterializedGeneration(20L);
        when(permissionService.requireDocument(21L)).thenReturn(current);
        docService.updateCrdtSnapshotFromCollaboration(21L, 4L, "<p>stale</p>", 19L);
        docService.updateCrdtSnapshotFromCollaboration(21L, 4L, "<p>legacy stale</p>");
        org.mockito.Mockito.verifyNoInteractions(documentMapper, versionService, htmlSanitizer);
    }

    @Test
    void movingCrdtDocumentToTrashDisconnectsActiveEditors() {
        Document document = new Document();
        document.setId(21L);
        document.setOwnerId(4L);
        document.setCollabMode("CRDT");
        when(permissionService.requireAdmin(21L, 4L)).thenReturn(document);

        docService.moveToTrash(21L, 4L);

        assertThat(document.getIsDeleted()).isEqualTo(1);
        verify(crdtCheckpointService).disconnect(21L);
        verify(documentMapper).updateById(document);
    }

    @Test
    void restoreUsesDeletedDocumentAwareAdminCheck() {
        Document document = new Document();
        document.setId(21L);
        document.setOwnerId(4L);
        document.setIsDeleted(1);
        when(permissionService.requireAdminIncludingDeleted(21L, 4L)).thenReturn(document);

        docService.restore(21L, 4L);

        assertThat(document.getIsDeleted()).isZero();
        verify(documentMapper).updateById(document);
    }

    @Test
    void permanentDeletionRemovesDurableCrdtState() {
        Document document = new Document();
        document.setId(21L);
        document.setOwnerId(4L);
        document.setIsDeleted(1);
        document.setCollabMode("CRDT");
        when(documentMapper.selectById(21L)).thenReturn(document);

        docService.purge(21L, 4L);

        verify(crdtCheckpointService).disconnect(21L);
        verify(crdtDataMapper).deleteUpdates(21L);
        verify(crdtDataMapper).deleteCheckpointHistory(21L);
        verify(crdtDataMapper).deleteCheckpoint(21L);
        verify(crdtDataMapper).deleteMaterialization(21L);
        verify(documentReferenceService).deleteByDocument(21L);
        verify(notificationService).deleteByDocument(21L);
        verify(attachmentUploadService).deleteByDocument(21L);
        verify(documentMapper).deleteById(21L);
    }
}
