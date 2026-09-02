package com.devtoolcopilot.project.controller;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.project.dto.GroupTaskAssignRequest;
import com.devtoolcopilot.project.dto.GroupTaskBoardResponse;
import com.devtoolcopilot.project.dto.GroupTaskCreateRequest;
import com.devtoolcopilot.project.dto.GroupTaskItem;
import com.devtoolcopilot.project.dto.GroupTaskStatusRequest;
import com.devtoolcopilot.project.dto.GroupTaskUpdateRequest;
import com.devtoolcopilot.project.service.GroupTaskService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project/team/groups/{groupId}/tasks")
public class GroupTaskController {

    private final GroupTaskService groupTaskService;

    public GroupTaskController(GroupTaskService groupTaskService) {
        this.groupTaskService = groupTaskService;
    }

    /** 群组任务看板聚合（成员可看） */
    @GetMapping
    public R<GroupTaskBoardResponse> board(@PathVariable Long groupId) {
        return R.ok(groupTaskService.board(UserContext.getUserId(), groupId));
    }

    /** 创建群组任务（群成员可建，可指派） */
    @PostMapping
    public R<GroupTaskItem> create(@PathVariable Long groupId, @RequestBody GroupTaskCreateRequest req) {
        return R.ok(groupTaskService.create(UserContext.getUserId(), groupId, req));
    }

    /** 更新标题/描述/优先级/截止时间 */
    @PutMapping("/{taskId}")
    public R<Void> update(@PathVariable Long groupId, @PathVariable Long taskId,
                          @RequestBody GroupTaskUpdateRequest req) {
        groupTaskService.update(UserContext.getUserId(), groupId, taskId, req);
        return R.ok();
    }

    /** 指派给群成员（群主/创建者） */
    @PostMapping("/{taskId}/assign")
    public R<Void> assign(@PathVariable Long groupId, @PathVariable Long taskId,
                          @RequestBody GroupTaskAssignRequest req) {
        groupTaskService.assign(UserContext.getUserId(), groupId, taskId, req.getAssigneeId());
        return R.ok();
    }

    /** 认领任务（无指派时可认领） */
    @PostMapping("/{taskId}/claim")
    public R<Void> claim(@PathVariable Long groupId, @PathVariable Long taskId) {
        groupTaskService.claim(UserContext.getUserId(), groupId, taskId);
        return R.ok();
    }

    /** 状态流转 TODO/DOING/DONE */
    @PostMapping("/{taskId}/status")
    public R<Void> changeStatus(@PathVariable Long groupId, @PathVariable Long taskId,
                                @RequestBody GroupTaskStatusRequest req) {
        groupTaskService.changeStatus(UserContext.getUserId(), groupId, taskId, req.getStatus());
        return R.ok();
    }

    /** 删除任务（群主/创建者） */
    @DeleteMapping("/{taskId}")
    public R<Void> delete(@PathVariable Long groupId, @PathVariable Long taskId) {
        groupTaskService.delete(UserContext.getUserId(), groupId, taskId);
        return R.ok();
    }
}
