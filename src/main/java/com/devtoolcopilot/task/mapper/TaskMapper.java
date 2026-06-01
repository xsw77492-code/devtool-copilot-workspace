package com.devtoolcopilot.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devtoolcopilot.task.entity.Task;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskMapper extends BaseMapper<Task> {
    @Select("""
            SELECT DISTINCT task_id
            FROM (
              SELECT tl.task_id AS task_id
              FROM task_timeline tl
              WHERE tl.project_id = #{projectId}
                AND tl.user_id = #{userId}
              UNION ALL
              SELECT tc.task_id AS task_id
              FROM task_comment tc
              WHERE tc.project_id = #{projectId}
                AND tc.user_id = #{userId}
            ) x
            """)
    List<Long> participatedTaskIds(@Param("userId") Long userId, @Param("projectId") Long projectId);

    @Select("""
            SELECT *
            FROM `task`
            WHERE due_time IS NOT NULL
              AND assignee_id IS NOT NULL
              AND status <> 'DONE'
              AND due_time >= #{now}
              AND due_time < #{until}
              AND (due_reminded_time IS NULL OR due_reminded_time <> due_time)
            ORDER BY due_time ASC, id ASC
            """)
    List<Task> dueSoonTasks(@Param("now") LocalDateTime now, @Param("until") LocalDateTime until);

    @Update("""
            UPDATE `task`
            SET due_reminded_time = due_time
            WHERE id = #{taskId}
            """)
    int markDueReminded(@Param("taskId") Long taskId);
}
