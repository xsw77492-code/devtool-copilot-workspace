package com.devtoolcopilot.integration.gitee.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GiteePanelDTO {
    private String owner;
    private String repo;
    private List<TaskItem> tasks;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskItem {
        private Long taskId;
        private String title;
        private String status;
        private List<PullRequestItem> prs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PullRequestItem {
        private Integer number;
        private String title;
        private String state;
        private String url;
        private String ciState;
        private String ciUrl;
        private String source;
        private Long linkId;
    }
}

