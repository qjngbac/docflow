package com.docflow.service;

import com.docflow.entity.*;
import com.docflow.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class NotificationServiceTest {
    @Test void systemNotificationsWithoutAnActorOrDocumentStillLoad() {
        var mapper = mock(UserNotificationMapper.class);
        var service = new NotificationService();
        ReflectionTestUtils.setField(service, "notificationMapper", mapper);
        ReflectionTestUtils.setField(service, "userMapper", mock(UserMapper.class));
        ReflectionTestUtils.setField(service, "documentMapper", mock(DocumentMapper.class));
        var message = new UserNotification(); message.setContent("System message");
        when(mapper.selectList(any())).thenReturn(List.of(message));
        assertThat(service.list(1L, false)).containsExactly(message);
    }

    @Test @SuppressWarnings({"unchecked", "rawtypes"})
    void sendsLargeRecipientListsInBoundedBatches() {
        var mapper = mock(UserNotificationMapper.class);
        var service = new NotificationService();
        ReflectionTestUtils.setField(service, "notificationMapper", mapper);
        var recipients = java.util.stream.LongStream.rangeClosed(1, 1001).boxed().toList();
        service.createAdminMessages(recipients, 1L, "Notice");
        org.mockito.ArgumentCaptor<List<Long>> batches = org.mockito.ArgumentCaptor.forClass((Class) List.class);
        verify(mapper, times(3)).insertAdminMessages(batches.capture(), eq(1L), eq("Notice"), any());
        assertThat(batches.getAllValues().stream().flatMap(List::stream).toList()).containsExactlyElementsOf(recipients);
        assertThat(batches.getAllValues()).allMatch(batch -> batch.size() <= 500);
    }

    @Test void enrichesAListUsingBatchesInsteadOfPerNotificationQueries() {
        var notifications = mock(UserNotificationMapper.class);
        var users = mock(UserMapper.class); var documents = mock(DocumentMapper.class);
        var service = new NotificationService();
        ReflectionTestUtils.setField(service, "notificationMapper", notifications);
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "documentMapper", documents);
        var first = new UserNotification(); first.setActorId(2L); first.setDocId(3L);
        var second = new UserNotification(); second.setActorId(2L); second.setDocId(3L);
        var user = new User(); user.setId(2L); user.setUsername("alice");
        var doc = new Document(); doc.setId(3L); doc.setTitle("Notes");
        when(notifications.selectList(any())).thenReturn(List.of(first, second));
        lenient().when(users.selectBatchIds(any())).thenReturn(List.of(user));
        lenient().when(documents.selectBatchIds(any())).thenReturn(List.of(doc));
        var result = service.list(1L, false);
        assertThat(result).allMatch(item -> "alice".equals(item.getActorName()) && "Notes".equals(item.getDocumentTitle()));
        verify(users).selectBatchIds(any()); verify(documents).selectBatchIds(any());
        verify(users, never()).selectById(any(java.io.Serializable.class));
        verify(documents, never()).selectById(any(java.io.Serializable.class));
    }
}
