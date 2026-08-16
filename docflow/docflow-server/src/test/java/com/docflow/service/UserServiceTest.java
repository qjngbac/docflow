package com.docflow.service;

import com.docflow.common.BusinessException;
import com.docflow.entity.User;
import com.docflow.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void updatesTrimmedNickname() {
        User before = user("old");
        User after = user("云笺用户");
        when(userMapper.selectById(2L)).thenReturn(before, after);

        User result = userService.updateProfile(2L, "  云笺用户  ");

        assertThat(result.getNickname()).isEqualTo("云笺用户");
        verify(userMapper).updateById(any(User.class));
    }

    @Test
    void rejectsIncorrectCurrentPassword() {
        User user = user("tester");
        user.setPassword("encoded");
        when(userMapper.selectById(2L)).thenReturn(user);
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(2L, "wrong", "new-password"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getMessage()).isEqualTo("Current password is incorrect"));
    }

    @Test
    void changesPasswordAfterVerification() {
        User user = user("tester");
        user.setPassword("encoded-old");
        when(userMapper.selectById(2L)).thenReturn(user);
        when(passwordEncoder.matches("old-password", "encoded-old")).thenReturn(true);
        when(passwordEncoder.matches("new-password", "encoded-old")).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new");

        userService.changePassword(2L, "old-password", "new-password");

        verify(userMapper).updateById(any(User.class));
    }

    private User user(String nickname) {
        User user = new User();
        user.setId(2L);
        user.setUsername("tester");
        user.setNickname(nickname);
        return user;
    }
}
