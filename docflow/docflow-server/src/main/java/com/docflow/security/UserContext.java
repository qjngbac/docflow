package com.docflow.security;

import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;

public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId, String username) {
        set(userId, username, null);
    }

    public static void set(Long userId, String username, String sessionTokenId) {
        HOLDER.set(new CurrentUser(userId, username, sessionTokenId));
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static Long getRequiredUserId() {
        CurrentUser user = HOLDER.get();
        if (user == null || user.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Please login first");
        }
        return user.getUserId();
    }

    public static String getUsername() {
        CurrentUser user = HOLDER.get();
        return user == null ? null : user.getUsername();
    }

    public static String getSessionTokenId() {
        CurrentUser user = HOLDER.get();
        return user == null ? null : user.getSessionTokenId();
    }

    public static void clear() {
        HOLDER.remove();
    }

    @Data
    @AllArgsConstructor
    public static class CurrentUser {
        private Long userId;
        private String username;
        private String sessionTokenId;
    }
}
