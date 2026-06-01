package com.devtoolcopilot.ai.service;

import java.util.function.Consumer;

public interface ProjectSummaryService {
    String summarizeProject(Long userId, Long projectId);

    String summarizeProjectStream(Long userId, Long projectId, Consumer<String> onDelta);
}
