package com.devtoolcopilot.kb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.devtoolcopilot.kb.entity.KbExternalDoc;
import com.devtoolcopilot.kb.mapper.KbExternalDocMapper;
import com.devtoolcopilot.kb.service.KbExternalDocService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class KbExternalDocServiceImpl extends ServiceImpl<KbExternalDocMapper, KbExternalDoc> implements KbExternalDocService {
    @Override
    public Long create(Long userId, Long projectId, String title, String url, String content) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("TITLE_REQUIRED");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("CONTENT_REQUIRED");
        KbExternalDoc row = new KbExternalDoc();
        row.setUserId(userId);
        row.setProjectId(projectId);
        row.setTitle(title.trim());
        row.setUrl(url == null || url.isBlank() ? null : url.trim());
        row.setContent(content.trim());
        this.save(row);
        return row.getId();
    }

    @Override
    public List<KbExternalDoc> list(Long userId, Long projectId, int limit) {
        if (userId == null) return List.of();
        int lim = Math.max(1, Math.min(limit, 50));
        LambdaQueryWrapper<KbExternalDoc> qw = Wrappers.<KbExternalDoc>lambdaQuery()
                .eq(KbExternalDoc::getUserId, userId)
                .orderByDesc(KbExternalDoc::getUpdateTime)
                .orderByDesc(KbExternalDoc::getId)
                .last("LIMIT " + lim);
        if (projectId != null) {
            qw.eq(KbExternalDoc::getProjectId, projectId);
        }
        return this.list(qw);
    }

    @Override
    public List<KbExternalDoc> search(Long userId, Long projectId, String query, int limit) {
        if (userId == null) return List.of();
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return list(userId, projectId, limit);
        int lim = Math.max(1, Math.min(limit, 20));
        List<String> tokenList = tokenize(q);
        final List<String> tokens = tokenList.isEmpty() ? List.of(q) : tokenList;

        LambdaQueryWrapper<KbExternalDoc> qw = Wrappers.<KbExternalDoc>lambdaQuery()
                .eq(KbExternalDoc::getUserId, userId)
                .orderByDesc(KbExternalDoc::getUpdateTime)
                .orderByDesc(KbExternalDoc::getId)
                .last("LIMIT " + lim);
        if (projectId != null) {
            qw.eq(KbExternalDoc::getProjectId, projectId);
        }
        qw.and(w -> {
            boolean first = true;
            for (String t : tokens) {
                if (t == null || t.isBlank()) continue;
                if (first) {
                    w.like(KbExternalDoc::getTitle, t).or().like(KbExternalDoc::getContent, t);
                    first = false;
                } else {
                    w.or().like(KbExternalDoc::getTitle, t).or().like(KbExternalDoc::getContent, t);
                }
            }
        });
        return this.list(qw);
    }

    private List<String> tokenize(String text) {
        String s = text == null ? "" : text.trim();
        if (s.isEmpty()) return List.of();
        String[] parts = s.split("[\\s\\p{Punct}，。！？、；：\\|]+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (p == null) continue;
            String t = p.trim();
            if (t.length() < 2) continue;
            out.add(t);
            if (out.size() >= 3) break;
        }
        return out;
    }
}
