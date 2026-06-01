package com.devtoolcopilot.docgen.service.impl;

import com.devtoolcopilot.ai.client.DeepSeekClient;
import com.devtoolcopilot.asset.config.AssetProperties;
import com.devtoolcopilot.asset.entity.ProjectAsset;
import com.devtoolcopilot.asset.mapper.ProjectAssetMapper;
import com.devtoolcopilot.audit.service.ProjectAuditService;
import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.docgen.dto.*;
import com.devtoolcopilot.docgen.service.DocGenService;
import com.devtoolcopilot.docgen.wenduoduo.WenDuoDuoPptService;
import com.devtoolcopilot.kb.service.KbExternalDocService;
import com.devtoolcopilot.project.service.ProjectCollabService;
import com.devtoolcopilot.task.deliverable.service.TaskDeliverableService;
import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.mapper.TaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DocGenServiceImpl implements DocGenService {
    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;
    private final ProjectCollabService projectCollabService;
    private final ProjectAuditService projectAuditService;
    private final ProjectAssetMapper assetMapper;
    private final AssetProperties assetProperties;
    private final TaskDeliverableService taskDeliverableService;
    private final KbExternalDocService kbExternalDocService;
    private final TaskMapper taskMapper;
    private final WenDuoDuoPptService wenDuoDuoPptService;

    public DocGenServiceImpl(DeepSeekClient deepSeekClient,
                             ObjectMapper objectMapper,
                             ProjectCollabService projectCollabService,
                             ProjectAuditService projectAuditService,
                             ProjectAssetMapper assetMapper,
                             AssetProperties assetProperties,
                             TaskDeliverableService taskDeliverableService,
                             KbExternalDocService kbExternalDocService,
                             TaskMapper taskMapper,
                             WenDuoDuoPptService wenDuoDuoPptService) {
        this.deepSeekClient = deepSeekClient;
        this.objectMapper = objectMapper;
        this.projectCollabService = projectCollabService;
        this.projectAuditService = projectAuditService;
        this.assetMapper = assetMapper;
        this.assetProperties = assetProperties;
        this.taskDeliverableService = taskDeliverableService;
        this.kbExternalDocService = kbExternalDocService;
        this.taskMapper = taskMapper;
        this.wenDuoDuoPptService = wenDuoDuoPptService;
    }

    @Override
    public DocGenResponse generatePptx(Long userId, DocGenRequest req) {
        if (userId == null) throw new ApiException(401, "未登录");
        Long projectId = req == null ? null : req.getProjectId();
        if (projectId == null) throw new ApiException(400, "projectId不能为空");
        String requirement = req == null ? null : req.getRequirement();
        if (requirement == null || requirement.isBlank()) throw new ApiException(400, "requirement不能为空");
        projectCollabService.requireMember(userId, projectId);

        DocGenSaveTo saveTo = req.getSaveTo() == null ? DocGenSaveTo.KB : req.getSaveTo();
        Long taskId = req.getTaskId();
        if ((saveTo == DocGenSaveTo.DELIVERABLE || saveTo == DocGenSaveTo.BOTH) && taskId == null) {
            throw new ApiException(400, "taskId不能为空");
        }

        PptxOutline outline = buildPptxOutline(projectId, requirement.trim(), req.getStyle());
        normalizePptx(outline);
        String filename = buildFilename(req.getTitle(), outline.getTitle(), "pptx");
        String markdown = outlineToMarkdown(outline, projectId);
        byte[] bytes = wenDuoDuoPptService.generatePptxByMarkdown(markdown, null);
        Long assetId = saveAsset(userId, projectId, "PPTX", filename, "application/vnd.openxmlformats-officedocument.presentationml.presentation", bytes);
        String downloadUrl = "/api/assets/" + assetId + "/download";
        if (projectAuditService != null) {
            projectAuditService.record(projectId, userId, "DOCGEN_PPTX", "ASSET", assetId, filename, "{\"assetId\":" + assetId + "}");
        }

        DocGenResponse resp = new DocGenResponse();
        resp.setProjectId(projectId);
        resp.setAssetId(assetId);
        resp.setFilename(filename);
        resp.setDownloadUrl(downloadUrl);

        if (saveTo == DocGenSaveTo.KB || saveTo == DocGenSaveTo.BOTH) {
            try {
                Long kbId = kbExternalDocService.create(userId, projectId, "[PPT] " + filename, downloadUrl, markdown);
                resp.setKbDocId(kbId);
                if (projectAuditService != null) {
                    projectAuditService.record(projectId, userId, "DOCGEN_SAVE_KB", "KB_DOC", kbId, filename, "{\"assetId\":" + assetId + ",\"kbDocId\":" + kbId + "}");
                }
            } catch (Exception ignored) {
            }
        }

        if (saveTo == DocGenSaveTo.DELIVERABLE || saveTo == DocGenSaveTo.BOTH) {
            try {
                Task t = taskMapper.selectById(taskId);
                if (t == null || !projectId.equals(t.getProjectId())) throw new ApiException(404, "任务不存在");
                Long did = taskDeliverableService.create(userId, taskId, "DOC", filename, downloadUrl, null);
                resp.setDeliverableId(did);
                if (projectAuditService != null) {
                    projectAuditService.record(projectId, userId, "DOCGEN_ATTACH_DELIVERABLE", "DELIVERABLE", did, filename, "{\"assetId\":" + assetId + ",\"deliverableId\":" + did + "}");
                }
            } catch (Exception e) {
                if (e instanceof ApiException ae) throw ae;
                throw new ApiException(500, "保存到交付物失败");
            }
        }

        return resp;
    }

    @Override
    public DocGenResponse generateDocx(Long userId, DocGenRequest req) {
        if (userId == null) throw new ApiException(401, "未登录");
        Long projectId = req == null ? null : req.getProjectId();
        if (projectId == null) throw new ApiException(400, "projectId不能为空");
        String requirement = req == null ? null : req.getRequirement();
        if (requirement == null || requirement.isBlank()) throw new ApiException(400, "requirement不能为空");
        projectCollabService.requireMember(userId, projectId);

        DocGenSaveTo saveTo = req.getSaveTo() == null ? DocGenSaveTo.KB : req.getSaveTo();
        Long taskId = req.getTaskId();
        if ((saveTo == DocGenSaveTo.DELIVERABLE || saveTo == DocGenSaveTo.BOTH) && taskId == null) {
            throw new ApiException(400, "taskId不能为空");
        }

        DocxOutline outline = buildDocxOutline(projectId, requirement.trim(), req.getStyle());
        normalizeDocx(outline);
        String filename = buildFilename(req.getTitle(), outline.getTitle(), "docx");
        byte[] bytes = renderDocx(outline);
        Long assetId = saveAsset(userId, projectId, "DOCX", filename, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes);
        String downloadUrl = "/api/assets/" + assetId + "/download";
        if (projectAuditService != null) {
            projectAuditService.record(projectId, userId, "DOCGEN_DOCX", "ASSET", assetId, filename, "{\"assetId\":" + assetId + "}");
        }

        DocGenResponse resp = new DocGenResponse();
        resp.setProjectId(projectId);
        resp.setAssetId(assetId);
        resp.setFilename(filename);
        resp.setDownloadUrl(downloadUrl);

        if (saveTo == DocGenSaveTo.KB || saveTo == DocGenSaveTo.BOTH) {
            try {
                Long kbId = kbExternalDocService.create(userId, projectId, "[DOC] " + filename, downloadUrl, outlineToMarkdown(outline, projectId));
                resp.setKbDocId(kbId);
                if (projectAuditService != null) {
                    projectAuditService.record(projectId, userId, "DOCGEN_SAVE_KB", "KB_DOC", kbId, filename, "{\"assetId\":" + assetId + ",\"kbDocId\":" + kbId + "}");
                }
            } catch (Exception ignored) {
            }
        }

        if (saveTo == DocGenSaveTo.DELIVERABLE || saveTo == DocGenSaveTo.BOTH) {
            try {
                Task t = taskMapper.selectById(taskId);
                if (t == null || !projectId.equals(t.getProjectId())) throw new ApiException(404, "任务不存在");
                Long did = taskDeliverableService.create(userId, taskId, "DOC", filename, downloadUrl, null);
                resp.setDeliverableId(did);
                if (projectAuditService != null) {
                    projectAuditService.record(projectId, userId, "DOCGEN_ATTACH_DELIVERABLE", "DELIVERABLE", did, filename, "{\"assetId\":" + assetId + ",\"deliverableId\":" + did + "}");
                }
            } catch (Exception e) {
                if (e instanceof ApiException ae) throw ae;
                throw new ApiException(500, "保存到交付物失败");
            }
        }

        return resp;
    }

    private PptxOutline buildPptxOutline(Long projectId, String requirement, String style) {
        String systemPrompt = """
                你是资深产品经理与咨询顾问，擅长把需求整理成“专业、简洁、可汇报”的 PPT 大纲。
                只输出 JSON，不要输出任何解释文字，不要 Markdown，不要代码块。
                JSON schema:
                {
                  "title": "PPT主标题",
                  "subtitle": "副标题(可空)",
                  "slides": [
                    { "title": "页标题", "bullets": ["要点1","要点2"] }
                  ]
                }
                约束：
                - slides 6~10 页
                - 每页 bullets 2~5 条，短句，强调结构和可执行
                - 语言：中文
                """;
        String userPrompt = "项目ID：" + projectId + "\n需求：" + requirement + "\n风格：" + (style == null ? "" : style.trim());
        String raw = deepSeekClient.chat(systemPrompt, userPrompt);
        String json = extractJson(raw);
        if (json == null || json.isBlank()) throw new ApiException(502, "AI输出解析失败");
        try {
            PptxOutline o = objectMapper.readValue(json, PptxOutline.class);
            if (o.getSlides() == null) o.setSlides(List.of());
            if (style != null && !style.isBlank()) o.setStyle(style.trim());
            return o;
        } catch (Exception e) {
            throw new ApiException(502, "AI输出解析失败");
        }
    }

    private DocxOutline buildDocxOutline(Long projectId, String requirement, String style) {
        String systemPrompt = """
                你是资深技术写作与项目管理专家，擅长输出“专业、清晰、可落地”的项目文档大纲（Word）。
                只输出 JSON，不要输出任何解释文字，不要 Markdown，不要代码块。
                JSON schema:
                {
                  "title": "文档标题",
                  "sections": [
                    { "heading": "章节标题", "paragraphs": ["段落1"], "bullets": ["要点1"] }
                  ]
                }
                约束：
                - sections 6~10 个
                - 段落短一些，bullets 2~6 条
                - 语言：中文
                """;
        String userPrompt = "项目ID：" + projectId + "\n需求：" + requirement + "\n风格：" + (style == null ? "" : style.trim());
        String raw = deepSeekClient.chat(systemPrompt, userPrompt);
        String json = extractJson(raw);
        if (json == null || json.isBlank()) throw new ApiException(502, "AI输出解析失败");
        try {
            DocxOutline o = objectMapper.readValue(json, DocxOutline.class);
            if (o.getSections() == null) o.setSections(List.of());
            return o;
        } catch (Exception e) {
            throw new ApiException(502, "AI输出解析失败");
        }
    }

    private byte[] renderDocx(DocxOutline outline) {
        try (XWPFDocument doc = new XWPFDocument()) {
            String title = safeText(outline.getTitle());
            XWPFParagraph pTitle = doc.createParagraph();
            pTitle.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun rTitle = pTitle.createRun();
            rTitle.setText(title);
            rTitle.setFontFamily("Microsoft YaHei");
            rTitle.setFontSize(22);
            rTitle.setBold(true);

            XWPFParagraph pMeta = doc.createParagraph();
            pMeta.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun rMeta = pMeta.createRun();
            rMeta.setText(LocalDate.now().toString());
            rMeta.setFontFamily("Microsoft YaHei");
            rMeta.setFontSize(11);
            rMeta.setColor("64748B");

            List<DocxOutline.DocxSection> sections = outline.getSections() == null ? List.of() : outline.getSections();
            int max = Math.min(12, sections.size());
            for (int i = 0; i < max; i++) {
                DocxOutline.DocxSection s = sections.get(i);
                if (s == null) continue;
                String heading = safeText(s.getHeading());
                XWPFParagraph h = doc.createParagraph();
                h.setSpacingBefore(200);
                XWPFRun hr = h.createRun();
                hr.setText(heading);
                hr.setFontFamily("Microsoft YaHei");
                hr.setFontSize(14);
                hr.setBold(true);
                hr.setColor("0EA5E9");

                List<String> paras = s.getParagraphs() == null ? List.of() : s.getParagraphs();
                for (String para : paras) {
                    String t = safeText(para);
                    if (t.isBlank()) continue;
                    XWPFParagraph p = doc.createParagraph();
                    p.setSpacingBefore(120);
                    XWPFRun pr = p.createRun();
                    pr.setText(t);
                    pr.setFontFamily("Microsoft YaHei");
                    pr.setFontSize(11);
                }

                List<String> bullets = s.getBullets() == null ? List.of() : s.getBullets();
                for (String b : bullets) {
                    String t = safeText(b);
                    if (t.isBlank()) continue;
                    XWPFParagraph p = doc.createParagraph();
                    p.setSpacingBefore(60);
                    XWPFRun br = p.createRun();
                    br.setText("• " + t);
                    br.setFontFamily("Microsoft YaHei");
                    br.setFontSize(11);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ApiException(500, "Word渲染失败");
        }
    }

    private Long saveAsset(Long userId, Long projectId, String kind, String filename, String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new ApiException(500, "生成失败");
        String baseDir = (assetProperties.getBaseDir() == null || assetProperties.getBaseDir().isBlank())
                ? "data/assets"
                : assetProperties.getBaseDir().trim();
        String ym = LocalDate.now().toString().replace("-", "").substring(0, 6);
        String key = UUID.randomUUID().toString().replace("-", "");
        String ext = safeExt(filename);
        String storedName = key + (ext.isEmpty() ? "" : ("." + ext));
        Path dir = Paths.get(baseDir, "docgen", ym).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new ApiException(500, "存储目录不可用");
        }
        Path path = dir.resolve(storedName).toAbsolutePath().normalize();
        try {
            Files.write(path, bytes);
        } catch (Exception e) {
            throw new ApiException(500, "保存失败");
        }

        ProjectAsset a = new ProjectAsset();
        a.setProjectId(projectId);
        a.setUserId(userId);
        a.setKind(kind);
        a.setName(filename);
        a.setExt(ext);
        a.setContentType(contentType);
        a.setSizeBytes((long) bytes.length);
        a.setStorageKey(key);
        a.setStoragePath(path.toString());
        assetMapper.insert(a);
        return a.getId();
    }

    private void normalizePptx(PptxOutline o) {
        if (o == null) return;
        if (o.getTitle() != null) o.setTitle(o.getTitle().trim());
        if (o.getSubtitle() != null) o.setSubtitle(o.getSubtitle().trim());
        if (o.getSlides() == null) o.setSlides(List.of());
        List<PptxOutline.PptxSlide> out = new ArrayList<>();
        for (PptxOutline.PptxSlide s : o.getSlides()) {
            if (s == null) continue;
            if (s.getTitle() != null) s.setTitle(s.getTitle().trim());
            if (s.getBullets() == null) s.setBullets(List.of());
            out.add(s);
        }
        o.setSlides(out);
        if (o.getTitle() == null || o.getTitle().isBlank()) o.setTitle("项目汇报");
    }

    private void normalizeDocx(DocxOutline o) {
        if (o == null) return;
        if (o.getTitle() != null) o.setTitle(o.getTitle().trim());
        if (o.getSections() == null) o.setSections(List.of());
        List<DocxOutline.DocxSection> out = new ArrayList<>();
        for (DocxOutline.DocxSection s : o.getSections()) {
            if (s == null) continue;
            if (s.getHeading() != null) s.setHeading(s.getHeading().trim());
            if (s.getParagraphs() == null) s.setParagraphs(List.of());
            if (s.getBullets() == null) s.setBullets(List.of());
            out.add(s);
        }
        o.setSections(out);
        if (o.getTitle() == null || o.getTitle().isBlank()) o.setTitle("项目文档");
    }

    private String buildFilename(String title, String fallback, String ext) {
        String t = title == null ? "" : title.trim();
        if (t.isBlank()) t = fallback == null ? "" : fallback.trim();
        if (t.isBlank()) t = "document";
        t = t.replaceAll("[\\\\/\\r\\n\\t]", " ").trim();
        if (t.length() > 48) t = t.substring(0, 48).trim();
        if (t.isBlank()) t = "document";
        return t + "." + ext;
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

    private String extractJson(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.startsWith("```")) {
            int idx = s.indexOf("{");
            int end = s.lastIndexOf("}");
            if (idx >= 0 && end > idx) return s.substring(idx, end + 1).trim();
        }
        int idx = s.indexOf("{");
        int end = s.lastIndexOf("}");
        if (idx >= 0 && end > idx) return s.substring(idx, end + 1).trim();
        return null;
    }

    private static String safeText(String v) {
        String s = v == null ? "" : v.trim();
        if (s.length() > 240) s = s.substring(0, 240).trim();
        return s;
    }

    private String outlineToMarkdown(PptxOutline o, Long projectId) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(o == null ? "" : safeText(o.getTitle())).append("\n\n");
        if (projectId != null) sb.append("ProjectId: ").append(projectId).append("\n\n");
        if (o != null && o.getSlides() != null) {
            int i = 1;
            for (PptxOutline.PptxSlide s : o.getSlides()) {
                if (s == null) continue;
                sb.append("## ").append(i++).append(". ").append(safeText(s.getTitle())).append("\n");
                if (s.getBullets() != null) {
                    for (String b : s.getBullets()) {
                        String t = safeText(b);
                        if (t.isBlank()) continue;
                        sb.append("- ").append(t).append("\n");
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String outlineToMarkdown(DocxOutline o, Long projectId) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(o == null ? "" : safeText(o.getTitle())).append("\n\n");
        if (projectId != null) sb.append("ProjectId: ").append(projectId).append("\n\n");
        if (o != null && o.getSections() != null) {
            for (DocxOutline.DocxSection s : o.getSections()) {
                if (s == null) continue;
                sb.append("## ").append(safeText(s.getHeading())).append("\n");
                if (s.getParagraphs() != null) {
                    for (String p : s.getParagraphs()) {
                        String t = safeText(p);
                        if (t.isBlank()) continue;
                        sb.append(t).append("\n\n");
                    }
                }
                if (s.getBullets() != null) {
                    for (String b : s.getBullets()) {
                        String t = safeText(b);
                        if (t.isBlank()) continue;
                        sb.append("- ").append(t).append("\n");
                    }
                    sb.append("\n");
                }
            }
        }
        return sb.toString().trim();
    }
}
