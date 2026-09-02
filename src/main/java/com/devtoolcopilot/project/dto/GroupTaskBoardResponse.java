package com.devtoolcopilot.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupTaskBoardResponse {
    private List<GroupTaskItem> todo;
    private List<GroupTaskItem> doing;
    private List<GroupTaskItem> done;
    private int total;
    private int doingCount;
    private int doneCount;
    private int overdueCount;
    /** 已完成占比（百分比 0-100） */
    private int completionRate;
}
