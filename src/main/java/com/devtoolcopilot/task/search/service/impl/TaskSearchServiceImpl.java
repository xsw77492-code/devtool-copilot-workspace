package com.devtoolcopilot.task.search.service.impl;

import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.project.service.ProjectCollabService;
import com.devtoolcopilot.task.search.dto.TaskSearchItem;
import com.devtoolcopilot.task.search.dto.TaskSearchResponse;
import com.devtoolcopilot.task.search.mapper.TaskSearchMapper;
import com.devtoolcopilot.task.search.service.TaskSearchService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaskSearchServiceImpl implements TaskSearchService {
    private final TaskSearchMapper taskSearchMapper;
    private final ProjectCollabService projectCollabService;

    public TaskSearchServiceImpl(TaskSearchMapper taskSearchMapper, ProjectCollabService projectCollabService) {
        this.taskSearchMapper = taskSearchMapper;
        this.projectCollabService = projectCollabService;
    }

    @Override
    public TaskSearchResponse search(Long userId,
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
                                     Integer pageSize) {
        if (userId == null) throw new ApiException(401, "未登录");

        Long pid = projectId != null && projectId > 0 ? projectId : null;
        if (pid != null) projectCollabService.requireMember(userId, pid);

        int p = page == null ? 1 : Math.max(1, page);
        int size = pageSize == null ? 30 : Math.max(1, Math.min(100, pageSize));
        int offset = (p - 1) * size;

        String text = q == null ? "" : q.trim();
        if (text.length() > 200) text = text.substring(0, 200).trim();
        if (text.isBlank()) text = null;

        List<String> ss = statuses == null ? null : statuses.stream().map(s -> s == null ? "" : s.trim().toUpperCase()).filter(s -> !s.isBlank()).distinct().toList();
        if (ss != null && ss.isEmpty()) ss = null;

        List<String> ts = tags == null ? null : tags.stream().map(s -> s == null ? "" : s.trim()).filter(s -> !s.isBlank()).distinct().toList();
        if (ts != null && ts.isEmpty()) ts = null;

        LocalDateTime from = updatedFromMs == null || updatedFromMs <= 0 ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(updatedFromMs), ZoneId.systemDefault());
        LocalDateTime to = updatedToMs == null || updatedToMs <= 0 ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(updatedToMs), ZoneId.systemDefault());

        Integer inc = includeArchived != null && includeArchived ? 1 : 0;
        Long total = taskSearchMapper.count(userId, pid, inc, text, ss, assigneeId, milestoneId, ts, from, to);
        if (total == null) total = 0L;
        List<TaskSearchItem> items = total > 0 ? taskSearchMapper.search(userId, pid, inc, text, ss, assigneeId, milestoneId, ts, from, to, offset, size) : new ArrayList<>();
        return new TaskSearchResponse(total, items);
    }
}

