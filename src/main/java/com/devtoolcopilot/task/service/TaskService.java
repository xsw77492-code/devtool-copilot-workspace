package com.devtoolcopilot.task.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.entity.TaskStatus;

import java.util.List;

public interface TaskService extends IService<Task> {
    Long createTask(Long userId,
                    Long projectId,
                    String title,
                    String description,
                    String acceptanceCriteria,
                    String priority,
                    String tags,
                    String assignee,
                    Long assigneeId,
                    Long dueTime,
                    Long milestoneId,
                    Long parentTaskId,
                    String type,
                    String source);

    boolean updateStatus(Long userId, Long taskId, TaskStatus status, String baseUpdatedAt, Boolean forceDone);

    int batchUpdateStatus(Long userId, List<Long> taskIds, TaskStatus status, Boolean forceDone);

    int batchUpdateFields(Long userId,
                          List<Long> taskIds,
                          String priority,
                          Long assigneeId,
                          Long dueTime,
                          Boolean clearAssignee,
                          Boolean clearDueTime);

    List<Task> listByProjectId(Long userId, Long projectId);

    List<Long> participatedTaskIds(Long userId, Long projectId);

    Task getDetail(Long userId, Long taskId);

    List<Task> kanbanList(Long userId, Long projectId);

    boolean kanbanMove(Long userId,
                       Long projectId,
                       Long taskId,
                       TaskStatus toStatus,
                       Long beforeId,
                       Long afterId,
                       String baseUpdatedAt,
                       Boolean forceDone);

    boolean updateDetail(Long userId,
                         Long taskId,
                         String title,
                         String description,
                         String acceptanceCriteria,
                         String priority,
                         String tags,
                         String assignee,
                         Long assigneeId,
                         Long dueTime,
                         Long milestoneId,
                         Long parentTaskId,
                         String type,
                         String baseUpdatedAt);

    List<Task> myWork(Long userId, List<Long> projectIds, int limit);

    boolean deleteTask(Long userId, Long taskId);

    List<Task> listSubtasks(Long userId, Long taskId);
}
