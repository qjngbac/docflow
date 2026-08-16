package com.docflow.service;

import com.docflow.dto.CommentCreateRequest;
import com.docflow.entity.Document;
import com.docflow.entity.DocumentComment;
import com.docflow.entity.User;
import com.docflow.mapper.DocumentCommentMapper;
import com.docflow.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentCommentServiceTest {
    @Mock private DocumentCommentMapper commentMapper;
    @Mock private UserMapper userMapper;
    @Mock private PermissionService permissionService;
    private DocumentCommentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentCommentService();
        ReflectionTestUtils.setField(service, "commentMapper", commentMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "permissionService", permissionService);
    }

    @Test
    void creatingCommentRequiresCommentPermissionAndReturnsAuthor() {
        when(permissionService.requireCommentable(12L, 7L)).thenReturn(new Document());
        when(commentMapper.insert(any(DocumentComment.class))).thenAnswer(invocation -> {
            invocation.<DocumentComment>getArgument(0).setId(33L);
            return 1;
        });
        User author = new User();
        author.setId(7L);
        author.setUsername("writer");
        author.setNickname("Editor");
        when(userMapper.selectById(7L)).thenReturn(author);
        CommentCreateRequest request = new CommentCreateRequest();
        request.setContent("  Please clarify this.  ");
        request.setSelectedText("selected text");

        DocumentComment created = service.create(12L, 7L, request);

        verify(permissionService).requireCommentable(12L, 7L);
        assertThat(created.getId()).isEqualTo(33L);
        assertThat(created.getContent()).isEqualTo("Please clarify this.");
        assertThat(created.getStatus()).isEqualTo("OPEN");
        assertThat(created.getAuthorName()).isEqualTo("Editor");
    }
}
