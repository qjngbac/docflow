package com.docflow.service;

import com.docflow.common.BusinessException;
import com.docflow.entity.UserFeedback;
import com.docflow.mapper.DocumentMapper;
import com.docflow.mapper.FeedbackImageMapper;
import com.docflow.mapper.UserFeedbackMapper;
import com.docflow.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {
    @Mock private UserFeedbackMapper feedbackMapper;
    @Mock private FeedbackImageMapper imageMapper;
    @Mock private UserMapper userMapper;
    @Mock private DocumentMapper documentMapper;
    @Mock private FileService fileService;
    @Mock private PermissionService permissionService;
    @Mock private NotificationService notificationService;
    @InjectMocks private FeedbackService service;

    @Test
    void createsFeedbackLinkedToReadableDocument() {
        doAnswer(invocation -> { ((UserFeedback) invocation.getArgument(0)).setId(9L); return 1; })
                .when(feedbackMapper).insert(any(UserFeedback.class));

        UserFeedback result = service.create(2L, 7L, "bug", "保存按钮没有反应", null);

        assertThat(result.getId()).isEqualTo(9L);
        assertThat(result.getType()).isEqualTo("BUG");
        assertThat(result.getStatus()).isEqualTo("OPEN");
        verify(permissionService).requireReadable(7L, 2L);
        verify(feedbackMapper).insert(any(UserFeedback.class));
    }

    @Test
    void rejectsBlankDescription() {
        assertThatThrownBy(() -> service.create(2L, null, "BUG", "  ", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("问题描述");
    }
}
