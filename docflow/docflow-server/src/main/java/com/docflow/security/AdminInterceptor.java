package com.docflow.security;

import com.docflow.service.AdminAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 在进入管理接口前统一验证当前账号的管理员身份。 */
@Component
public class AdminInterceptor implements HandlerInterceptor {
    @Autowired private AdminAuthorizationService authorizationService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        authorizationService.requireAdmin();
        return true;
    }
}
