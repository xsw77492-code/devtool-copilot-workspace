package com.devtoolcopilot.task.deliverable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devtoolcopilot.task.deliverable.dto.TaskDeliverableItem;
import com.devtoolcopilot.task.deliverable.entity.TaskDeliverable;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface TaskDeliverableMapper extends BaseMapper<TaskDeliverable> {
    @Select("""
            SELECT MAX(COALESCE(d.sort, d.id))
            FROM task_deliverable d
            WHERE d.task_id = #{taskId}
            """)
    Long maxSortKey(@Param("taskId") Long taskId);

    @Select("""
            SELECT d.id AS id,
                   d.project_id AS projectId,
                   d.task_id AS taskId,
                   d.user_id AS userId,
                   u.username AS username,
                   d.type AS type,
                   d.title AS title,
                   d.url AS url,
                   d.content AS content,
                   d.status AS status,
                   d.sort AS sort,
                   d.create_time AS createTime,
                   d.update_time AS updateTime
            FROM task_deliverable d
            LEFT JOIN `user` u ON u.id = d.user_id
            WHERE d.task_id = #{taskId}
            ORDER BY COALESCE(d.sort, d.id) DESC, d.id DESC
            """)
    List<TaskDeliverableItem> listByTask(@Param("taskId") Long taskId);
}
