package com.devtoolcopilot.user.profile;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;

public interface ProfileService {
    ProfileResponse getProfile(Long userId);

    void updateProfile(Long userId, String nickname, String signature, String status);

    String uploadAvatar(Long userId, MultipartFile file);

    AvatarFile loadAvatar(String key);

    record ProfileResponse(Long userId, String username, String email, String nickname,
                           String signature, String avatarUrl, String status,
                           LocalDateTime createTime) {
    }

    record AvatarFile(String contentType, Path path) {
    }
}
