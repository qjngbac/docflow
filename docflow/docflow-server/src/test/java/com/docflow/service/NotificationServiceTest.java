package com.docflow.service;

import com.docflow.entity.UserNotification;
import com.docflow.mapper.DocumentMapper;
import com.docflow.mapper.UserMapper;
import com.docflow.mapper.UserNotificationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock private UserNotificationMapper notificationMapper;
    @Mock private UserMapper userMapper;
    @Mock private DocumentMapper documentMapper;
    @InjectMocks private NotificationService service;

    @Test
    void adminMessageIsPersistedWhenAdministratorIsAlsoTheRecipient() {
        service.createAdminMessage(1L, 1L, "系统维护提示");

        ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
        verify(notificationMapper).insert(captor.capture());
        UserNotification notification = captor.getValue();
        assertThat(notification.getUserId()).isEqualTo(1L);
        assertThat(notification.getActorId()).isEqualTo(1L);
        assertThat(notification.getType()).isEqualTo("ADMIN_MESSAGE");
        assertThat(notification.getContent()).isEqualTo("系统维护提示");
        assertThat(notification.getIsRead()).isZero();
        assertThat(notification.getCreatedAt()).isNotNull();
    }
}
