package com.docflow.controller;

import com.docflow.common.Result;
import com.docflow.dto.PasswordChangeRequest;
import com.docflow.dto.UserProfileUpdateRequest;
import com.docflow.security.UserContext;
import com.docflow.service.FileService;
import com.docflow.service.UserService;
import com.docflow.service.AccountSecurityService;
import com.docflow.entity.User;
import com.docflow.entity.UserSession;
import com.docflow.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import com.docflow.security.ClientIpResolver;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private FileService fileService;

    @Autowired private AccountSecurityService accountSecurityService;
    @Autowired private ClientIpResolver clientIpResolver;

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(UserVO.from(userService.getById(UserContext.getRequiredUserId())));
    }

    @PutMapping("/me")
    public Result<UserVO> updateProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
        return Result.success(UserVO.from(userService.updateProfile(
                UserContext.getRequiredUserId(), request.getNickname())));
    }

    @PostMapping("/me/avatar")
    public Result<UserVO> updateAvatar(@RequestParam("file") MultipartFile file) {
        String avatarUrl = fileService.uploadAvatar(file);
        return Result.success(UserVO.from(userService.updateAvatar(
                UserContext.getRequiredUserId(), avatarUrl)));
    }

    @DeleteMapping("/me/avatar")
    public Result<UserVO> deleteAvatar() {
        Long userId = UserContext.getRequiredUserId();
        User user = userService.getById(userId);
        if (user.getAvatar() != null) fileService.deleteByUrl(user.getAvatar());
        return Result.success(UserVO.from(userService.updateAvatar(userId, null)));
    }

    @PutMapping("/me/welcome-dismissed")
    public Result<UserVO> dismissWelcome() {
        return Result.success(UserVO.from(userService.dismissWelcome(UserContext.getRequiredUserId())));
    }

    @PostMapping("/me/email/request")
    public Result<Map<String, String>> requestEmailChange(@Valid @RequestBody EmailRequest request,
                                                          HttpServletRequest servletRequest) {
        return Result.success(accountSecurityService.issueCode(UserContext.getRequiredUserId(), request.getEmail(),
                "EMAIL_CHANGE", clientIpResolver.resolve(servletRequest)));
    }

    @PutMapping("/me/email")
    public Result<UserVO> confirmEmailChange(@Valid @RequestBody EmailConfirmRequest request) {
        Long userId = UserContext.getRequiredUserId();
        accountSecurityService.verify(userId, request.getEmail(), "EMAIL_CHANGE", request.getCode());
        return Result.success(UserVO.from(userService.updateEmail(userId, request.getEmail())));
    }

    @GetMapping("/me/sessions")
    public Result<List<UserSession>> sessions() {
        return Result.success(accountSecurityService.listSessions(
                UserContext.getRequiredUserId(), UserContext.getSessionTokenId()));
    }

    @DeleteMapping("/me/sessions/{sessionId}")
    public Result<Map<String, Boolean>> revokeSession(@PathVariable Long sessionId) {
        boolean current = accountSecurityService.revokeSession(
                UserContext.getRequiredUserId(), sessionId, UserContext.getSessionTokenId());
        return Result.success(Map.of("current", current));
    }

    @DeleteMapping("/me/sessions/others")
    public Result<Map<String, Integer>> revokeOtherSessions() {
        int count = accountSecurityService.revokeOtherSessions(
                UserContext.getRequiredUserId(), UserContext.getSessionTokenId());
        return Result.success(Map.of("count", count));
    }

    @PutMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(UserContext.getRequiredUserId(),
                request.getCurrentPassword(), request.getNewPassword());
        accountSecurityService.revokeOtherSessions(
                UserContext.getRequiredUserId(), UserContext.getSessionTokenId());
        return Result.success();
    }

    @Data public static class EmailRequest { @NotBlank @Email private String email; }
    @Data public static class EmailConfirmRequest { @NotBlank @Email private String email; @NotBlank private String code; }
}
