package com.docflow.service;

import com.docflow.entity.DocVersion;
import com.docflow.entity.Document;
import com.docflow.mapper.DocVersionMapper;
import com.docflow.mapper.DocumentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VersionServiceTest {

    @Mock private DocVersionMapper docVersionMapper;
    @Mock private DocumentMapper documentMapper;
    @Mock private PermissionService permissionService;
    @Mock private HtmlSanitizer htmlSanitizer;
    @Mock private CrdtCheckpointService crdtCheckpointService;

    @InjectMocks private VersionService versionService;

    @Test
    void rollbackReplacesAuthoritativeCrdtDocumentContent() {
        Document document = new Document();
        document.setId(8L);
        document.setOwnerId(2L);
        document.setTitle("Current");
        document.setContent("<p>Current</p>");
        document.setContentFormat("HTML");
        document.setCollabMode("CRDT");
        document.setRevision(3L);
        DocVersion version = new DocVersion();
        version.setDocId(8L);
        version.setVersionNum(1);
        version.setTitle("Earlier");
        version.setContent("<p>Earlier</p>");
        version.setContentFormat("HTML");
        when(permissionService.requireWritable(8L, 2L)).thenReturn(document);
        when(docVersionMapper.selectOne(any())).thenReturn(version);
        when(docVersionMapper.selectList(any())).thenReturn(List.of());

        Document result = versionService.rollback(8L, 1, 2L);

        verify(crdtCheckpointService).replaceContent(8L, "<p>Earlier</p>");
        verify(documentMapper).updateById(document);
        assertThat(result.getContent()).isEqualTo("<p>Earlier</p>");
    }
}
