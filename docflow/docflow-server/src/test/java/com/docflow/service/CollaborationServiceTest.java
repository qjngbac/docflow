package com.docflow.service;

import com.docflow.common.BusinessException;
import com.docflow.entity.Document;
import com.docflow.mapper.DocumentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaborationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private PermissionService permissionService;

    @Mock
    private VersionService versionService;

    @InjectMocks
    private CollaborationService collaborationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(collaborationService, "draftTtlHours", 168L);
        when(redisTemplate.<Object, Object>opsForHash()).thenReturn(hashOperations);
    }

    @Test
    void rejectsStaleRevision() {
        Document document = document(5L);
        when(permissionService.requireWritable(10L, 2L)).thenReturn(document);
        when(hashOperations.entries(anyString())).thenReturn(Map.of());

        assertThatThrownBy(() -> collaborationService.stageUpdate(
                10L, 2L, null, "new content", 4L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(409);
                    assertThat(exception.getMessage()).contains("current revision is 5");
                });
    }

    @Test
    void storesDraftAndIncrementsRevision() {
        Document document = document(5L);
        when(permissionService.requireWritable(10L, 2L)).thenReturn(document);
        when(hashOperations.entries(anyString())).thenReturn(Map.of());
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        Document updated = collaborationService.stageUpdate(
                10L, 2L, "Updated", "new content", 5L);

        assertThat(updated.getRevision()).isEqualTo(6L);
        assertThat(updated.getPersistedRevision()).isEqualTo(5L);
        assertThat(updated.getContent()).isEqualTo("new content");
        verify(hashOperations).putAll(anyString(), anyMap());
        verify(setOperations).add("docflow:collab:dirty-documents", "10");
    }

    private Document document(Long revision) {
        Document document = new Document();
        document.setId(10L);
        document.setTitle("Original");
        document.setContent("old content");
        document.setRevision(revision);
        document.setPersistedRevision(revision);
        document.setCollabMode("LEGACY");
        return document;
    }
}
