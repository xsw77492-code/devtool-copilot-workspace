package com.devtoolcopilot.kb.service;

import com.devtoolcopilot.kb.entity.KbExternalDoc;

import java.util.List;

public interface KbExternalDocService {
    Long create(Long userId, Long projectId, String title, String url, String content);

    List<KbExternalDoc> list(Long userId, Long projectId, int limit);

    List<KbExternalDoc> search(Long userId, Long projectId, String query, int limit);
}

