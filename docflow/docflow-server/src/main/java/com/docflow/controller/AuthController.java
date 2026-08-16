package com.docflow.controller;

import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.common.Result;
import com.docflow.entity.User;
import com.docflow.security.JwtUtil;
import com.docflow.security.AuthCookieService;
import com.docflow.security.ClientIpResolver;
import com.docflow.security.LoginProtectionService;
import com.docflow.security.UserContext;
import com.docflow.service.UserService;
import com.docflow.service.AccountSecurityService;
import com.docflow.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired private AccountSecurityService accountSecurityService;
    @Autowired private ClientIpResolver clientIpResolver;
    @Autowired private LoginProtectionService loginProtectionService;
    @Autowired private AuthCookieService authCookieService;

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request,
                                                HttpServletRequest servletRequest,
                                                HttpServletResponse servletResponse) {
        User user = userService.register(request.getUsername(), request.getPassword(), request.getEmail());
        return Result.success(buildLoginResponse(user, servletRequest, servletResponse, false));
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request,
                                             HttpServletRequest servletRequest,
                                             HttpServletResponse servletResponse) {
        String clientIp = clientIpResolver.resolve(servletRequest);
        loginProtectionService.checkBeforeLogin(request.getUsername(), clientIp,
                request.getCaptchaId(), request.getCaptchaCode());
        User user = userService.getForLogin(request.getUsername());
        if (user == null || !userService.checkPassword(request.getPassword(), user.getPassword())) {
            long retryAfter = loginProtectionService.recordFailure(request.getUsername(), clientIp);
            if (retryAfter > 0) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                        "登录失败次数过多，请稍后再试");
            }
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        userService.requireLoginAllowed(user);
        loginProtectionService.recordSuccess(request.getUsername());
        userService.updateLoginTime(user.getId());
        return Result.success(buildLoginResponse(user, servletRequest, servletResponse, request.isRememberMe()));
    }

    @GetMapping("/login-security")
    public Result<LoginProtectionService.LoginSecurityStatus> loginSecurity(
            @RequestParam(defaultValue = "") String username, HttpServletRequest request) {
        return Result.success(loginProtectionService.status(username, clientIpResolver.resolve(request)));
    }

    @GetMapping("/captcha")
    public Result<LoginProtectionService.CaptchaChallenge> captcha(
            @RequestParam(defaultValue = "") String username, HttpServletRequest request) {
        return Result.success(loginProtectionService.createCaptcha(username, clientIpResolver.resolve(request)));
    }

    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody(required = false) RefreshRequest request,
                                               HttpServletRequest servletRequest,
                                               HttpServletResponse servletResponse) {
        String refreshToken = request == null ? null : request.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) refreshToken = authCookieService.readRefreshToken(servletRequest);
        AccountSecurityService.SessionIssue issue = accountSecurityService.refreshSession(
                refreshToken, servletRequest.getHeader("User-Agent"), clientIpResolver.resolve(servletRequest));
        User user = userService.getById(issue.session().getUserId());
        return Result.success(buildTokenResponse(user, issue, servletResponse));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody(required = false) RefreshRequest request,
                               HttpServletRequest servletRequest,
                               HttpServletResponse servletResponse) {
        String refreshToken = request == null ? null : request.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) refreshToken = authCookieService.readRefreshToken(servletRequest);
        accountSecurityService.revokeByRefreshToken(refreshToken);
        authCookieService.clear(servletResponse);
        return Result.success();
    }

    @GetMapping("/session")
    public Result<Map<String, Object>> session() {
        Long userId = UserContext.getRequiredUserId();
        return Result.success(Map.of("authenticated", true, "user", UserVO.from(userService.getById(userId))));
    }

    @PostMapping("/realtime-token")
    public Result<Map<String, Object>> realtimeToken() {
        Long userId = UserContext.getRequiredUserId();
        User user = userService.getById(userId);
        String tokenId = UserContext.getSessionTokenId();
        if (tokenId == null || tokenId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        String token = jwtUtil.generateRealtimeToken(userId, user.getUsername(), tokenId);
        return Result.success(Map.of("token", token, "expiresInSeconds", 120));
    }

    @PostMapping("/heartbeat")
    public Result<Map<String, Long>> heartbeat() {
        UserContext.getRequiredUserId();
        return Result.success(Map.of("idleTimeoutSeconds", accountSecurityService.getIdleTimeoutSeconds()));
    }

    @PostMapping("/password-reset/request")
    public Result<Map<String, String>> requestPasswordReset(@Valid @RequestBody ResetRequest request,
                                                            HttpServletRequest servletRequest) {
        User user = userService.getByEmail(request.getEmail());
        if (user == null) throw new BusinessException(ErrorCode.NOT_FOUND, "Email is not registered");
        return Result.success(accountSecurityService.issueCode(user.getId(), request.getEmail(),
                "PASSWORD_RESET", clientIpResolver.resolve(servletRequest)));
    }

    @PostMapping("/password-reset/confirm")
    public Result<Void> confirmPasswordReset(@Valid @RequestBody ResetConfirmRequest request) {
        User user = userService.getByEmail(request.getEmail());
        if (user == null) throw new BusinessException(ErrorCode.NOT_FOUND, "Email is not registered");
        accountSecurityService.verify(user.getId(), request.getEmail(), "PASSWORD_RESET", request.getCode());
        userService.resetPassword(request.getEmail(), request.getNewPassword());
        accountSecurityService.revokeAllSessions(user.getId(), null);
        return Result.success();
    }

    private Map<String, Object> buildLoginResponse(User user, HttpServletRequest request,
                                                   HttpServletResponse response, boolean rememberMe) {
        AccountSecurityService.SessionIssue issue = accountSecurityService.recordSession(user.getId(), rememberMe,
                request.getHeader("User-Agent"), clientIpResolver.resolve(request));
        return buildTokenResponse(user, issue, response);
    }

    private Map<String, Object> buildTokenResponse(User user, AccountSecurityService.SessionIssue issue,
                                                   HttpServletResponse response) {
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), issue.session().getTokenId());
        Map<String, Object> data = new HashMap<>();
        authCookieService.write(response, token, jwtUtil.getExpirationMillis() / 1000L,
                issue.refreshToken(), issue.session().getExpiresAt(),
                Integer.valueOf(1).equals(issue.session().getRememberMe()));
        if (!authCookieService.isEnabled()) {
            data.put("token", token);
            data.put("accessToken", token);
            data.put("refreshToken", issue.refreshToken());
        }
        data.put("cookieAuth", authCookieService.isEnabled());
        data.put("authenticated", true);
        data.put("accessExpiresInSeconds", jwtUtil.getExpirationMillis() / 1000L);
        data.put("sessionExpiresAt", issue.session().getExpiresAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        data.put("idleTimeoutSeconds", accountSecurityService.getIdleTimeoutSeconds());
        data.put("sessionId", issue.session().getId());
        data.put("rememberMe", Integer.valueOf(1).equals(issue.session().getRememberMe()));
        data.put("user", UserVO.from(user));
        return data;
    }

    @Data
    public static class RegisterRequest {
        @NotBlank
        private String username;

        @NotBlank
        private String password;

        private String email;
    }

    @Data
    public static class LoginRequest {
        @NotBlank
        private String username;

        @NotBlank
        private String password;

        private boolean rememberMe;
        private String captchaId;
        private String captchaCode;
    }

    @Data public static class RefreshRequest { private String refreshToken; }

    @Data public static class ResetRequest { @NotBlank @Email private String email; }
    @Data public static class ResetConfirmRequest {
        @NotBlank @Email private String email;
        @NotBlank private String code;
        @NotBlank @Size(min = 8, max = 72) private String newPassword;
    }
}
