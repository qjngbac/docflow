package com.docflow.service;

import com.docflow.common.BusinessException;
import com.docflow.entity.User;
import com.docflow.mapper.UserMapper;
import com.docflow.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthorizationServiceTest {
    @Mock private UserMapper userMapper;
    @InjectMocks private AdminAuthorizationService service;

    @AfterEach void clearContext() { UserContext.clear(); }

    @Test
    void allowsEnabledSystemAdministrator() {
        UserContext.set(1L, "admin");
        User user = user(1, "ADMIN");
        when(userMapper.selectById(1L)).thenReturn(user);

        assertThat(service.requireAdmin()).isSameAs(user);
    }

    @Test
    void rejectsOrdinaryUser() {
        UserContext.set(2L, "user");
        when(userMapper.selectById(2L)).thenReturn(user(1, "USER"));

        assertThatThrownBy(service::requireAdmin).isInstanceOf(BusinessException.class)
                .hasMessageContaining("管理员");
    }

    private User user(int status, String role) {
        User user = new User(); user.setId(1L); user.setStatus(status); user.setSystemRole(role); return user;
    }
}
