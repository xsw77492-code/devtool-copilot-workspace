package com.devtoolcopilot.project.service;

import com.devtoolcopilot.project.dto.GroupTaskBoardResponse;
import com.devtoolcopilot.project.dto.GroupTaskCreateRequest;
import com.devtoolcopilot.project.dto.GroupTaskItem;
import com.devtoolcopilot.project.dto.GroupTaskUpdateRequest;

import java.util.List;

public interface GroupTaskService {

    /** 群组任务看板聚合（成员可看） */
    GroupTaskBoardResponse board(Long userId, Long groupId);

    /** 群组任务列表（成员可看） */
    List<GroupTaskItem> listTasks(Long userId, Long groupId);

    /** 创建任务（群成员可建，可指定指派人） */
    GroupTaskItem create(Long userId, Long groupId, GroupTaskCreateRequest req);

    /** 更新标题/描述/优先级/截止时间（群主/创建者/指派人） */
    void update(Long userId, Long groupId, Long taskId, GroupTaskUpdateRequest req);

    /** 指派（群主/创建者，目标必须是群成员） */
    void assign(Long userId, Long groupId, Long taskId, Long assigneeId);

    /** 认领（群成员，仅可认领未指派的任务） */
    void claim(Long userId, Long groupId, Long taskId);

    /** 状态流转（群主/创建者/指派人） */
    void changeStatus(Long userId, Long groupId, Long taskId, String status);

    /** 删除（群主/创建者） */
    void delete(Long userId, Long groupId, Long taskId);
}
