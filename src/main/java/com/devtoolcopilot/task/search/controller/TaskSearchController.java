package com.devtoolcopilot.task.search.controller;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.task.search.dto.TaskSearchResponse;
import com.devtoolcopilot.task.search.service.TaskSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/task")
public class TaskSearchController {
    private final TaskSearchService taskSearchService;

    public TaskSearchController(TaskSearchService taskSearchService) {
        this.taskSearchService = taskSearchService;
    }

    @GetMapping("/search")
    public R<TaskSearchResponse> search(@RequestParam(required = false) Long projectId,
                                        @RequestParam(required = false) Boolean includeArchived,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) Long assigneeId,
                                        @RequestParam(required = false) Long milestoneId,
                                        @RequestParam(required = false) String tags,
                                        @RequestParam(required = false) Long updatedFrom,
                                        @RequestParam(required = false) Long updatedTo,
                                        @RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer pageSize) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.fail(401, "未登录");

        List<String> statuses = split(status);
        List<String> tagList = split(tags);

        TaskSearchResponse res = taskSearchService.search(
                userId,
                projectId,
                includeArchived,
                q,
                statuses,
                assigneeId,
                milestoneId,
                tagList,
                updatedFrom,
                updatedTo,
                page,
                pageSize
        );
        return R.ok(res);
    }

    private static List<String> split(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isBlank()) return null;
        return Arrays.stream(s.split(","))
                .map(x -> x == null ? "" : x.trim())
                .filter(x -> !x.isBlank())
                .toList();
    }
}

