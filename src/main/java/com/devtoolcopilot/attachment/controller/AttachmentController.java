package com.devtoolcopilot.attachment.controller;

import com.devtoolcopilot.attachment.dto.AttachmentItem;
import com.devtoolcopilot.attachment.service.AttachmentService;
import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.common.exception.ApiException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping({"/api/attachment", "/api/attachments"})
public class AttachmentController {
    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping("/task/{taskId}")
    public R<List<AttachmentItem>> listByTask(@PathVariable Long taskId) {
        Long userId = UserContext.getUserId();
        return R.ok(attachmentService.listByTask(userId, taskId));
    }

    @PostMapping(value = "/task/{taskId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<AttachmentItem> uploadToTask(@PathVariable Long taskId, @RequestPart("file") MultipartFile file) {
        Long userId = UserContext.getUserId();
        return R.ok(attachmentService.uploadToTask(userId, taskId, file));
    }

    @PostMapping(value = "/comment/{commentId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<AttachmentItem> uploadToComment(@PathVariable Long commentId, @RequestPart("file") MultipartFile file) {
        Long userId = UserContext.getUserId();
        return R.ok(attachmentService.uploadToComment(userId, commentId, file));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        attachmentService.delete(userId, id);
        return R.ok();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new ApiException(401, "未登录");
        var f = attachmentService.loadDownload(userId, id);
        return buildFileResponse(f, false);
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new ApiException(401, "未登录");
        var f = attachmentService.loadPreview(userId, id);
        return buildFileResponse(f, true);
    }

    private static ResponseEntity<Resource> buildFileResponse(AttachmentService.DownloadFile f, boolean inline) {
        FileSystemResource res = new FileSystemResource(f.path());
        String name = f.filename() == null ? "file" : f.filename();
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, (inline ? "inline" : "attachment") + "; filename*=UTF-8''" + encoded);
        headers.add(HttpHeaders.CACHE_CONTROL, "private, max-age=0, no-store");
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
}
