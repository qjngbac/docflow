package com.docflow.controller;

import com.docflow.common.Result;
import com.docflow.entity.UserNotification;
import com.docflow.security.UserContext;
import com.docflow.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    @Autowired private NotificationService notificationService;

    @GetMapping
    public Result<List<UserNotification>> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                               @RequestParam(required = false) String type) {
        return Result.success(notificationService.list(UserContext.getRequiredUserId(), unreadOnly, type));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        return Result.success(Map.of("count", notificationService.unreadCount(UserContext.getRequiredUserId())));
    }

    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id, UserContext.getRequiredUserId());
        return Result.success();
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllRead(UserContext.getRequiredUserId());
        return Result.success();
    }
}
