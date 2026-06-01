package com.devtoolcopilot.attachment.service;

import com.devtoolcopilot.attachment.dto.AttachmentItem;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AttachmentService {
    List<AttachmentItem> listByTask(Long userId, Long taskId);

    AttachmentItem uploadToTask(Long userId, Long taskId, MultipartFile file);

    AttachmentItem uploadToComment(Long userId, Long commentId, MultipartFile file);

    boolean delete(Long userId, Long attachmentId);

    DownloadFile loadDownload(Long userId, Long attachmentId);

    DownloadFile loadPreview(Long userId, Long attachmentId);

    record DownloadFile(String filename, String contentType, java.nio.file.Path path) {
    }
}
