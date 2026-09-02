package com.devtoolcopilot.task.search.service;

import com.devtoolcopilot.task.search.dto.TaskSearchResponse;

import java.util.List;

public interface TaskSearchService {
    TaskSearchResponse search(Long userId,
                              Long projectId,
                              Boolean includeArchived,
                              String q,
                              List<String> statuses,
                              Long assigneeId,
                              Long milestoneId,
                              List<String> tags,
                              Long updatedFromMs,
                              Long updatedToMs,
                              Integer page,
                              Integer pageSize);
}

