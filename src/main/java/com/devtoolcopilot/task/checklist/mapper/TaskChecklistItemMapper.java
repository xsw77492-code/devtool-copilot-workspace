package com.devtoolcopilot.task.checklist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devtoolcopilot.task.checklist.dto.TaskChecklistItemDTO;
import com.devtoolcopilot.task.checklist.entity.TaskChecklistItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface TaskChecklistItemMapper extends BaseMapper<TaskChecklistItem> {
    @Select("""
            SELECT COUNT(1)
            FROM task_checklist_item c
            WHERE c.task_id = #{taskId} AND c.is_done = 0
            """)
    Long countUndone(@Param("taskId") Long taskId);

    @Select("""
            SELECT c.id AS id,
                   c.project_id AS projectId,
                   c.task_id AS taskId,
                   c.user_id AS userId,
                   u.username AS username,
                   c.content AS content,
                   c.is_done AS isDone,
                   c.done_time AS doneTime,
                   c.create_time AS createTime,
                   c.update_time AS updateTime
            FROM task_checklist_item c
            LEFT JOIN `user` u ON u.id = c.user_id
            WHERE c.task_id = #{taskId}
            ORDER BY c.is_done ASC, c.id ASC
            """)
    List<TaskChecklistItemDTO> listByTask(@Param("taskId") Long taskId);
}
