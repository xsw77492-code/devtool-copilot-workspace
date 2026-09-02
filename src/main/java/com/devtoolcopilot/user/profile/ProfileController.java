package com.devtoolcopilot.user.profile;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user")
public class ProfileController {
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/profile")
    public R<ProfileService.ProfileResponse> profile() {
        return R.ok(profileService.getProfile(UserContext.getUserId()));
    }

    @PutMapping("/profile")
    public R<Void> update(@RequestBody UpdateProfileReq req) {
        profileService.updateProfile(UserContext.getUserId(), req.nickname(), req.signature(), req.status());
        return R.ok();
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<String> avatar(@RequestPart("file") MultipartFile file) {
        return R.ok(profileService.uploadAvatar(UserContext.getUserId(), file));
    }

    @GetMapping("/avatar/{key}")
    public ResponseEntity<Resource> avatarFile(@PathVariable String key) {
        ProfileService.AvatarFile f = profileService.loadAvatar(key);
        FileSystemResource res = new FileSystemResource(f.path());
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline");
        headers.add(HttpHeaders.CACHE_CONTROL, "public, max-age=86400");
        long len = -1L;
        try {
            len = res.contentLength();
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(f.contentType()))
                .contentLength(len)
                .body(res);
    }

    public record UpdateProfileReq(String nickname, String signature, String status) {
    }
}
