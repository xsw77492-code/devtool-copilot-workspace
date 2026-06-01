package com.devtoolcopilot.asset.controller;

import com.devtoolcopilot.asset.dto.AssetItem;
import com.devtoolcopilot.asset.service.AssetService;
import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.common.exception.ApiException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/assets")
public class AssetController {
    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping("/list")
    public R<List<AssetItem>> list(@RequestParam Long projectId, @RequestParam(required = false) Integer limit) {
        Long userId = UserContext.getUserId();
        int lim = limit == null ? 20 : limit;
        return R.ok(assetService.listByProject(userId, projectId, lim));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new ApiException(401, "未登录");
        var f = assetService.loadDownload(userId, id);
        return buildFileResponse(f, false);
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new ApiException(401, "未登录");
        var f = assetService.loadDownload(userId, id);
        if (isPptx(f)) {
            AssetService.DownloadFile pdf = ensurePptxPreviewPdf(f);
            return buildFileResponse(pdf, true);
        }
        return buildFileResponse(f, true);
    }

    private static boolean isPptx(AssetService.DownloadFile f) {
        if (f == null) return false;
        String ct = f.contentType() == null ? "" : f.contentType().toLowerCase();
        String name = f.filename() == null ? "" : f.filename().toLowerCase();
        return ct.contains("presentationml") || name.endsWith(".pptx");
    }

    private static AssetService.DownloadFile ensurePptxPreviewPdf(AssetService.DownloadFile src) {
        Path pptx = src.path();
        String baseName = src.filename() == null ? "preview" : src.filename();
        String pdfName = baseName.toLowerCase().endsWith(".pptx") ? baseName.substring(0, baseName.length() - 5) + ".pdf" : (baseName + ".pdf");
        Path pdf = pptx.resolveSibling(pptx.getFileName().toString() + ".preview.pdf");
        try {
            if (Files.exists(pdf)) {
                if (Files.getLastModifiedTime(pdf).toMillis() >= Files.getLastModifiedTime(pptx).toMillis()) {
                    return new AssetService.DownloadFile(pdfName, MediaType.APPLICATION_PDF_VALUE, pdf);
                }
            }
        } catch (Exception ignored) {
        }

        synchronized (AssetController.class) {
            try {
                if (Files.exists(pdf)) {
                    if (Files.getLastModifiedTime(pdf).toMillis() >= Files.getLastModifiedTime(pptx).toMillis()) {
                        return new AssetService.DownloadFile(pdfName, MediaType.APPLICATION_PDF_VALUE, pdf);
                    }
                }
            } catch (Exception ignored) {
            }
            try {
                renderPptxToPdf(pptx, pdf);
            } catch (Exception e) {
                throw new ApiException(500, "预览生成失败");
            }
        }
        return new AssetService.DownloadFile(pdfName, MediaType.APPLICATION_PDF_VALUE, pdf);
    }

    private static void renderPptxToPdf(Path pptx, Path pdf) throws Exception {
        try (InputStream in = Files.newInputStream(pptx);
             org.apache.poi.xslf.usermodel.XMLSlideShow ppt = new org.apache.poi.xslf.usermodel.XMLSlideShow(in);
             org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            Dimension pg = ppt.getPageSize();
            for (org.apache.poi.xslf.usermodel.XSLFSlide slide : ppt.getSlides()) {
                BufferedImage img = new BufferedImage(pg.width, pg.height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setPaint(Color.WHITE);
                g.fillRect(0, 0, pg.width, pg.height);
                slide.draw(g);
                g.dispose();

                org.apache.pdfbox.pdmodel.common.PDRectangle rect =
                        new org.apache.pdfbox.pdmodel.common.PDRectangle(pg.width, pg.height);
                org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage(rect);
                doc.addPage(page);
                var pdImg = org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, img);
                try (var cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                    cs.drawImage(pdImg, 0, 0, pg.width, pg.height);
                }
            }
            doc.save(pdf.toFile());
        }
    }

    private static ResponseEntity<Resource> buildFileResponse(AssetService.DownloadFile f, boolean inline) {
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
