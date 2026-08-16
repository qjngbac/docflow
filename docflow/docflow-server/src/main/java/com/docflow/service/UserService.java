package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.entity.User;
import com.docflow.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public User register(String username, String password, String email) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Username and password are required");
        }

        LambdaQueryWrapper<User> usernameWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username);
        if (userMapper.selectCount(usernameWrapper) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Username already exists");
        }

        if (StringUtils.hasText(email)) {
            LambdaQueryWrapper<User> emailWrapper = new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, email);
            if (userMapper.selectCount(emailWrapper) > 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Email already exists");
            }
        }

        User user = new User();
        user.setUsername(username);
        user.setNickname(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setStatus(1);
        user.setSystemRole("USER");
        user.setIsDeleted(0);
        userMapper.insert(user);
        return user;
    }

    public User getByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .eq(User::getStatus, 1));
    }

    public User getForLogin(String username) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("LIMIT 1"));
        if (user != null) refreshExpiredBan(user);
        return user;
    }

    public void requireLoginAllowed(User user) {
        refreshExpiredBan(user);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            String reason = user == null || !StringUtils.hasText(user.getBanReason())
                    ? "账号已被管理员停用，请联系管理员"
                    : "账号已被封禁：" + user.getBanReason();
            if (user != null && user.getBanExpiresAt() != null) {
                reason += "（预计解除时间：" + user.getBanExpiresAt() + "）";
            }
            throw new BusinessException(ErrorCode.FORBIDDEN, reason);
        }
    }

    private void refreshExpiredBan(User user) {
        if (user == null || Integer.valueOf(1).equals(user.getStatus())
                || user.getBanExpiresAt() == null || user.getBanExpiresAt().isAfter(LocalDateTime.now())) return;
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getStatus, 1)
                .set(User::getBanReason, null)
                .set(User::getBannedAt, null)
                .set(User::getBanExpiresAt, null));
        user.setStatus(1);
        user.setBanReason(null);
        user.setBannedAt(null);
        user.setBanExpiresAt(null);
    }

    public boolean checkPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public void updateLoginTime(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public User getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "User not found");
        }
        return user;
    }

    public User updateProfile(Long userId, String nickname) {
        String normalizedNickname = nickname == null ? "" : nickname.trim();
        if (!StringUtils.hasText(normalizedNickname) || normalizedNickname.length() > 50) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Nickname must be between 1 and 50 characters");
        }

        User user = getById(userId);
        user.setNickname(normalizedNickname);
        userMapper.updateById(user);
        return getById(userId);
    }

    public User updateAvatar(Long userId, String avatarUrl) {
        getById(userId);
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId).set(User::getAvatar, avatarUrl));
        return getById(userId);
    }

    public User dismissWelcome(Long userId) {
        getById(userId);
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId).set(User::getWelcomeDismissed, 1));
        return getById(userId);
    }

    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = getById(userId);
        if (!checkPassword(currentPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Current password is incorrect");
        }
        if (newPassword == null || newPassword.length() < 8
                || newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "New password must be 8 to 72 bytes");
        }
        if (checkPassword(newPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "New password must be different from current password");
        }

        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);
    }

    public User updateEmail(Long userId, String email) {
        if (!StringUtils.hasText(email) || email.length() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid email");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)
                .ne(User::getId, userId)) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Email already exists");
        }
        User update = new User(); update.setId(userId); update.setEmail(email.toLowerCase());
        userMapper.updateById(update);
        return getById(userId);
    }

    public void resetPassword(String email, String newPassword) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email.toLowerCase()));
        if (user == null) throw new BusinessException(ErrorCode.NOT_FOUND, "Email is not registered");
        validateNewPassword(newPassword);
        User update = new User(); update.setId(user.getId()); update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);
    }

    public User getByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email.toLowerCase()));
    }

    private void validateNewPassword(String password) {
        if (password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "New password must be 8 to 72 bytes");
        }
    }
}
