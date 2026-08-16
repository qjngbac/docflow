package com.docflow.service;

import com.docflow.common.BusinessException;
import com.docflow.entity.User;
import com.docflow.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminNotificationServiceTest {
    @Mock private UserMapper userMapper;
    @Mock private NotificationService notificationService;
    @Mock private AdminService adminService;
    @InjectMocks private AdminNotificationService service;

    @Test
    void sendsMessageToEverySelectedUserIncludingAdministrator() {
        when(userMapper.selectList(any())).thenReturn(List.of(user(1L, "admin"), user(2L, "member")));

        AdminNotificationService.SendResult result = service.send(
                1L, "ALL", null, null, null, "系统将在今晚维护");

        assertThat(result.sentCount()).isEqualTo(2);
        verify(notificationService).createAdminMessage(1L, 1L, "系统将在今晚维护");
        verify(notificationService).createAdminMessage(2L, 1L, "系统将在今晚维护");
        verify(adminService).audit(1L, "ADMIN_NOTIFICATION_SEND", "全部用户；接收人数：2");
    }

    @Test
    void sendsMessageToOneExactUsername() {
        when(userMapper.selectOne(any())).thenReturn(user(7L, "target"));

        AdminNotificationService.SendResult result = service.send(
                1L, "USER", " target ", null, null, "请检查账号资料");

        assertThat(result.sentCount()).isEqualTo(1);
        verify(notificationService).createAdminMessage(7L, 1L, "请检查账号资料");
        verify(adminService).audit(1L, "ADMIN_NOTIFICATION_SEND", "用户 target；接收人数：1");
    }

    @Test
    void sendsMessageToUsersRegisteredInSelectedPeriod() {
        LocalDateTime from = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 8, 8, 23, 59);
        when(userMapper.selectList(any())).thenReturn(List.of(user(3L, "new-user")));

        AdminNotificationService.SendResult result = service.send(
                1L, "REGISTERED_AT", null, from, to, "欢迎使用云笺");

        assertThat(result.sentCount()).isEqualTo(1);
        verify(notificationService).createAdminMessage(3L, 1L, "欢迎使用云笺");
        verify(adminService).audit(1L, "ADMIN_NOTIFICATION_SEND", "注册时间 2026-08-01T00:00 至 2026-08-08T23:59；接收人数：1");
    }

    @Test
    void rejectsReversedRegistrationPeriodBeforeWritingNotifications() {
        LocalDateTime from = LocalDateTime.of(2026, 8, 9, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 8, 1, 0, 0);

        assertThatThrownBy(() -> service.send(1L, "REGISTERED_AT", null, from, to, "测试提示"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("开始时间");
        verify(notificationService, never()).createAdminMessage(any(), any(), any());
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setIsDeleted(0);
        return user;
    }
}
