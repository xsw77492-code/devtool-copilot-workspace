package com.devtoolcopilot.user.profile;

import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.user.entity.User;
import com.devtoolcopilot.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class ProfileServiceImpl implements ProfileService {
    private static final Set<String> ALLOWED_IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final String AVATAR_PREFIX = "/api/user/avatar/";

    private final UserMapper userMapper;
    private final String avatarDir;

    public ProfileServiceImpl(UserMapper userMapper,
                              @Value("${devtool.avatar.dir:data/avatars}") String avatarDir) {
        this.userMapper = userMapper;
        this.avatarDir = avatarDir;
    }

    @Override
    public ProfileResponse getProfile(Long userId) {
        if (userId == null) throw new ApiException(401, "未登录");
        User u = userMapper.selectById(userId);
        if (u == null) throw new ApiException(404, "用户不存在");
        return toResponse(u);
    }

    @Override
    public void updateProfile(Long userId, String nickname, String signature, String status) {
        if (userId == null) throw new ApiException(401, "未登录");
        User u = userMapper.selectById(userId);
        if (u == null) throw new ApiException(404, "用户不存在");
        User update = new User();
        update.setId(userId);
        if (nickname != null) {
            String n = nickname.trim();
            if (n.length() > 32) throw new ApiException(400, "昵称最长 32 字符");
            update.setNickname(n.isBlank() ? null : n);
        }
        if (signature != null) {
            String s = signature.trim();
            if (s.length() > 120) throw new ApiException(400, "签名最长 120 字符");
            update.setSignature(s.isBlank() ? null : s);
        }
        if (status != null) {
            String st = status.trim().toUpperCase();
            if (!Set.of("ONLINE", "AWAY", "BUSY", "OFFLINE").contains(st)) throw new ApiException(400, "状态不合法");
            update.setStatus(st);
        }
        userMapper.updateById(update);
    }

    @Override
    public String uploadAvatar(Long userId, MultipartFile file) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (file == null || file.isEmpty()) throw new ApiException(400, "请选择图片");
        if (file.getSize() > 5 * 1024 * 1024) throw new ApiException(413, "头像图片最大 5MB");
        String original = file.getOriginalFilename();
        String ext = extOf(original);
        if (!ALLOWED_IMAGE_EXT.contains(ext)) throw new ApiException(400, "仅支持 jpg/png/gif/webp 图片");
        String ct = file.getContentType();
        if (ct == null || !ct.toLowerCase().startsWith("image/")) throw new ApiException(400, "仅支持图片文件");

        String key = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = Paths.get(avatarDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new ApiException(500, "头像目录不可用");
        }
        Path path = dir.resolve(key).toAbsolutePath().normalize();
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new ApiException(500, "保存失败");
        }

        User old = userMapper.selectById(userId);
        if (old != null && old.getAvatarUrl() != null && old.getAvatarUrl().startsWith(AVATAR_PREFIX)) {
            String oldKey = old.getAvatarUrl().substring(AVATAR_PREFIX.length());
            try {
                Files.deleteIfExists(dir.resolve(oldKey));
            } catch (Exception ignored) {
            }
        }
        String url = AVATAR_PREFIX + key;
        User update = new User();
        update.setId(userId);
        update.setAvatarUrl(url);
        userMapper.updateById(update);
        return url;
    }

    @Override
    public AvatarFile loadAvatar(String key) {
        if (key == null || key.isBlank()) throw new ApiException(404, "头像不存在");
        Path dir = Paths.get(avatarDir).toAbsolutePath().normalize();
        Path path = dir.resolve(key).toAbsolutePath().normalize();
        if (!path.startsWith(dir) || !Files.exists(path)) throw new ApiException(404, "头像不存在");
        String ext = extOf(key);
        String ct = switch (ext) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
        return new AvatarFile(ct, path);
    }

    private ProfileResponse toResponse(User u) {
        return new ProfileResponse(u.getId(), u.getUsername(), u.getEmail(),
                u.getNickname(), u.getSignature(), u.getAvatarUrl(),
                u.getStatus() == null ? "ONLINE" : u.getStatus(), u.getCreateTime());
    }

    private static String extOf(String name) {
        if (name == null) return "";
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) return "";
        return name.substring(idx + 1).trim().toLowerCase();
    }
}
