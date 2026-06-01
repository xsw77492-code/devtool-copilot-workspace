package com.devtoolcopilot.kb.controller;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.kb.dto.KbExternalDocCreateRequest;
import com.devtoolcopilot.kb.dto.KbExternalDocItem;
import com.devtoolcopilot.kb.entity.KbExternalDoc;
import com.devtoolcopilot.kb.service.KbExternalDocService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kb/external")
public class KbExternalDocController {
    private final KbExternalDocService kbExternalDocService;

    public KbExternalDocController(KbExternalDocService kbExternalDocService) {
        this.kbExternalDocService = kbExternalDocService;
    }

    @PostMapping("/create")
    public R<Long> create(@RequestBody KbExternalDocCreateRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        try {
            Long id = kbExternalDocService.create(userId,
                    req == null ? null : req.getProjectId(),
                    req == null ? null : req.getTitle(),
                    req == null ? null : req.getUrl(),
                    req == null ? null : req.getContent());
            return R.ok(id);
        } catch (IllegalArgumentException e) {
            String m = e.getMessage();
            if ("TITLE_REQUIRED".equals(m)) return R.fail(400, "title不能为空");
            if ("CONTENT_REQUIRED".equals(m)) return R.fail(400, "content不能为空");
            return R.fail(400, "请求参数错误");
        }
    }

    @GetMapping("/list")
    public R<List<KbExternalDocItem>> list(@RequestParam(required = false) Long projectId,
                                          @RequestParam(required = false) Integer limit,
                                          @RequestParam(required = false) String q) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");
        int lim = limit == null ? 50 : limit;
        List<KbExternalDoc> list = (q == null || q.isBlank())
                ? kbExternalDocService.list(userId, projectId, lim)
                : kbExternalDocService.search(userId, projectId, q, lim);
        List<KbExternalDocItem> out = list.stream().map(this::toItem).toList();
        return R.ok(out);
    }

    private KbExternalDocItem toItem(KbExternalDoc d) {
        KbExternalDocItem it = new KbExternalDocItem();
        it.setId(d.getId());
        it.setProjectId(d.getProjectId());
        it.setTitle(d.getTitle());
        it.setUrl(d.getUrl());
        it.setUpdateTime(d.getUpdateTime());
        return it;
    }
}

