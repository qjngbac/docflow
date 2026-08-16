package com.docflow.controller;

import com.docflow.common.Result;
import com.docflow.entity.DocumentTemplate;
import com.docflow.entity.UserFeedback;
import com.docflow.security.UserContext;
import com.docflow.service.AdminService;
import com.docflow.service.AdminNotificationService;
import com.docflow.service.FeedbackService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    @Autowired private AdminService adminService;
    @Autowired private AdminNotificationService adminNotificationService;
    @Autowired private FeedbackService feedbackService;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.success(adminService.overview());
    }

    @GetMapping("/users")
    public Result<List<AdminService.AdminUserView>> users(@RequestParam(required = false) String q,
                                                          @RequestParam(required = false) Integer status,
                                                          @RequestParam(required = false) String role) {
        return Result.success(adminService.users(q, status, role));
    }

    @PutMapping("/users/{userId}/status")
    public Result<Void> setUserStatus(@PathVariable Long userId, @RequestBody UserStatusRequest request) {
        adminService.setUserStatus(UserContext.getRequiredUserId(), userId,
                request.isEnabled(), request.getReason(), request.getExpiresAt());
        return Result.success();
    }

    @PutMapping("/users/{userId}/role")
    public Result<Void> setUserRole(@PathVariable Long userId, @Valid @RequestBody RoleRequest request) {
        adminService.setUserRole(UserContext.getRequiredUserId(), userId, request.getRole());
        return Result.success();
    }

    @PostMapping("/users/{userId}/revoke-sessions")
    public Result<Map<String, Integer>> revokeSessions(@PathVariable Long userId) {
        return Result.success(Map.of("count", adminService.revokeSessions(UserContext.getRequiredUserId(), userId)));
    }

    @GetMapping("/feedback")
    public Result<List<UserFeedback>> feedback(@RequestParam(required = false) String status,
                                               @RequestParam(required = false) String type) {
        return Result.success(feedbackService.listForAdmin(status, type));
    }

    @PutMapping("/feedback/{feedbackId}")
    public Result<UserFeedback> handleFeedback(@PathVariable Long feedbackId,
                                               @Valid @RequestBody FeedbackHandleRequest request) {
        Long adminId = UserContext.getRequiredUserId();
        UserFeedback feedback = feedbackService.handle(
                feedbackId, adminId, request.getStatus(), request.getAdminReply());
        adminService.audit(adminId, "ADMIN_FEEDBACK_HANDLE",
                "反馈 #" + feedbackId + " -> " + feedback.getStatus());
        return Result.success(feedback);
    }

    @GetMapping("/logs")
    public Result<List<AdminService.AdminLogView>> logs(@RequestParam(required = false) String action) {
        return Result.success(adminService.logs(action));
    }

    @GetMapping("/templates")
    public Result<List<DocumentTemplate>> templates() {
        return Result.success(adminService.systemTemplates());
    }

    @PostMapping("/templates")
    public Result<DocumentTemplate> createTemplate(@RequestBody DocumentTemplate request) {
        return Result.success(adminService.saveSystemTemplate(UserContext.getRequiredUserId(), null, request));
    }

    @PutMapping("/templates/{id}")
    public Result<DocumentTemplate> updateTemplate(@PathVariable Long id, @RequestBody DocumentTemplate request) {
        return Result.success(adminService.saveSystemTemplate(UserContext.getRequiredUserId(), id, request));
    }

    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        adminService.deleteSystemTemplate(UserContext.getRequiredUserId(), id);
        return Result.success();
    }

    @GetMapping("/system-status")
    public Result<Map<String, Object>> systemStatus() {
        return Result.success(adminService.systemStatus());
    }

    @PostMapping("/notifications")
    public Result<AdminNotificationService.SendResult> sendNotification(
            @Valid @RequestBody AdminNotificationRequest request) {
        return Result.success(adminNotificationService.send(
                UserContext.getRequiredUserId(), request.getTargetType(), request.getUsername(),
                request.getRegisteredFrom(), request.getRegisteredTo(), request.getContent()));
    }

    @Data
    public static class UserStatusRequest {
        private boolean enabled;
        private String reason;
        private LocalDateTime expiresAt;
    }

    @Data
    public static class RoleRequest {
        @NotBlank private String role;
    }

    @Data
    public static class FeedbackHandleRequest {
        @NotBlank private String status;
        private String adminReply;
    }

    @Data
    public static class AdminNotificationRequest {
        @NotBlank private String targetType;
        private String username;
        private LocalDateTime registeredFrom;
        private LocalDateTime registeredTo;
        @NotBlank @Size(max = 500) private String content;
    }
}
