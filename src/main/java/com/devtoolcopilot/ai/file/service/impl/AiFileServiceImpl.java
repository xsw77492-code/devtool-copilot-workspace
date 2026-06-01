package com.devtoolcopilot.ai.file.service.impl;

import com.devtoolcopilot.ai.file.dto.AiFileUploadResponse;
import com.devtoolcopilot.ai.file.service.AiFileService;
import com.devtoolcopilot.asset.config.AssetProperties;
import com.devtoolcopilot.asset.entity.ProjectAsset;
import com.devtoolcopilot.asset.mapper.ProjectAssetMapper;
import com.devtoolcopilot.audit.service.ProjectAuditService;
import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.project.service.ProjectCollabService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class AiFileServiceImpl implements AiFileService {
    private static final long MAX_BYTES = 20L * 1024L * 1024L;
    private static final int MAX_EXTRACT_CHARS = 12000;

    private final ProjectCollabService projectCollabService;
    private final ProjectAssetMapper assetMapper;
    private final AssetProperties assetProperties;
    private final ProjectAuditService projectAuditService;

    public AiFileServiceImpl(ProjectCollabService projectCollabService,
                             ProjectAssetMapper assetMapper,
                             AssetProperties assetProperties,
                             ProjectAuditService projectAuditService) {
        this.projectCollabService = projectCollabService;
        this.assetMapper = assetMapper;
        this.assetProperties = assetProperties;
        this.projectAuditService = projectAuditService;
    }

    @Override
    public AiFileUploadResponse uploadAndExtract(Long userId, Long projectId, MultipartFile file) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (projectId == null) throw new ApiException(400, "projectId不能为空");
        projectCollabService.requireMember(userId, projectId);
        if (file == null || file.isEmpty()) throw new ApiException(400, "请选择文件");
        long size = file.getSize();
        if (size <= 0) throw new ApiException(400, "文件为空");
        if (size > MAX_BYTES) throw new ApiException(413, "文件过大，最大支持 20MB");

        String filename = normalizeName(file.getOriginalFilename());
        String ext = safeExt(filename);
        String contentType = file.getContentType();

        String baseDir = (assetProperties.getBaseDir() == null || assetProperties.getBaseDir().isBlank())
                ? "data/assets"
                : assetProperties.getBaseDir().trim();
        String ym = LocalDate.now().toString().replace("-", "").substring(0, 6);
        String key = UUID.randomUUID().toString().replace("-", "");
        String storedName = key + (ext.isEmpty() ? "" : ("." + ext));
        Path dir = Paths.get(baseDir, "ai-upload", ym).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new ApiException(500, "存储目录不可用");
        }
        Path path = dir.resolve(storedName).toAbsolutePath().normalize();
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, path);
        } catch (Exception e) {
            throw new ApiException(500, "保存失败");
        }

        ProjectAsset a = new ProjectAsset();
        a.setProjectId(projectId);
        a.setUserId(userId);
        a.setKind("AI_UPLOAD");
        a.setName(filename);
        a.setExt(ext);
        a.setContentType(contentType);
        a.setSizeBytes(size);
        a.setStorageKey(key);
        a.setStoragePath(path.toString());
        assetMapper.insert(a);

        if (projectAuditService != null) {
            projectAuditService.record(projectId, userId, "AI_FILE_UPLOAD", "ASSET", a.getId(), filename, "{\"assetId\":" + a.getId() + "}");
        }

        String extracted = extractText(path, ext);
        String clipped = clip(extracted, MAX_EXTRACT_CHARS);
        return new AiFileUploadResponse(projectId, a.getId(), filename, contentType, clipped.length(), clipped);
    }

    private String extractText(Path path, String ext) {
        String e = ext == null ? "" : ext.trim().toLowerCase();
        try {
            if ("pdf".equals(e)) return extractPdf(path);
            if ("docx".equals(e)) return extractDocx(path);
            if ("pptx".equals(e)) return extractPptx(path);
        } catch (Exception ignored) {
        }
        return "";
    }

    private String extractPdf(Path path) throws Exception {
        try (PDDocument doc = PDDocument.load(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    private String extractDocx(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path); XWPFDocument doc = new XWPFDocument(in)) {
            return doc.getParagraphs().stream().map(p -> p.getText() == null ? "" : p.getText()).reduce("", (a, b) -> a + "\n" + b);
        }
    }

    private String extractPptx(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path); XMLSlideShow ppt = new XMLSlideShow(in)) {
            StringBuilder sb = new StringBuilder();
            int idx = 1;
            for (XSLFSlide slide : ppt.getSlides()) {
                sb.append("Slide ").append(idx++).append(":\n");
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape ts) {
                        String t = ts.getText();
                        if (t != null && !t.isBlank()) sb.append(t.trim()).append("\n");
                    }
                }
                sb.append("\n");
                if (sb.length() > MAX_EXTRACT_CHARS * 2L) break;
            }
            return sb.toString();
        }
    }

    private static String normalizeName(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isBlank()) return "file";
        v = v.replace("\\", "/");
        int idx = v.lastIndexOf('/');
        if (idx >= 0) v = v.substring(idx + 1);
        if (v.isBlank()) return "file";
        if (v.length() > 255) v = v.substring(0, 255);
        return v;
    }

    private static String safeExt(String filename) {
        if (filename == null) return "";
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) return "";
        String ext = filename.substring(idx + 1).trim().toLowerCase();
        if (ext.length() > 12) return "";
        for (int i = 0; i < ext.length(); i++) {
            char c = ext.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9')) return "";
        }
        return ext;
    }

    private static String clip(String text, int max) {
        String s = text == null ? "" : text.trim();
        if (s.length() <= max) return s;
        return s.substring(0, max);
    }
}
