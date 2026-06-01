package com.devtoolcopilot.dashboard.mapper;

import com.devtoolcopilot.dashboard.dto.DashboardDayCountItem;
import com.devtoolcopilot.dashboard.dto.DashboardDoneTaskItem;
import com.devtoolcopilot.dashboard.dto.DashboardMemberActivityItem;
import com.devtoolcopilot.dashboard.dto.DashboardStatusCountItem;
import com.devtoolcopilot.dashboard.dto.DashboardTaskActionItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface DashboardMapper {
    @Select("""
            SELECT COUNT(*)
            FROM project p
            WHERE p.user_id = #{userId}
               OR EXISTS (
                 SELECT 1 FROM project_member pm
                 WHERE pm.project_id = p.id AND pm.user_id = #{userId}
               )
              AND (#{projectId} IS NULL OR p.id = #{projectId})
            """)
    Long projectTotal(@Param("userId") Long userId,
                      @Param("projectId") Long projectId);

    @Select("""
            SELECT COUNT(*)
            FROM `task` t
            JOIN project p ON p.id = t.project_id
            WHERE (#{projectId} IS NULL OR p.id = #{projectId})
              AND (#{startAt} IS NULL OR t.create_time >= #{startAt})
              AND (#{endExclusive} IS NULL OR t.create_time < #{endExclusive})
              AND (
                p.user_id = #{userId}
                OR EXISTS (
                  SELECT 1 FROM project_member pm
                  WHERE pm.project_id = p.id AND pm.user_id = #{userId}
                )
              )
            """)
    Long taskTotal(@Param("userId") Long userId,
                   @Param("projectId") Long projectId,
                   @Param("startAt") LocalDateTime startAt,
                   @Param("endExclusive") LocalDateTime endExclusive);

    @Select("""
            SELECT COUNT(*)
            FROM `task` t
            JOIN project p ON p.id = t.project_id
            WHERE (#{projectId} IS NULL OR p.id = #{projectId})
              AND (#{startAt} IS NULL OR t.create_time >= #{startAt})
              AND (#{endExclusive} IS NULL OR t.create_time < #{endExclusive})
              AND t.status = 'DONE'
              AND (
                p.user_id = #{userId}
                OR EXISTS (
                  SELECT 1 FROM project_member pm
                  WHERE pm.project_id = p.id AND pm.user_id = #{userId}
                )
              )
            """)
    Long doneTaskTotal(@Param("userId") Long userId,
                       @Param("projectId") Long projectId,
                       @Param("startAt") LocalDateTime startAt,
                       @Param("endExclusive") LocalDateTime endExclusive);

    @Select("""
            SELECT COUNT(*)
            FROM ai_chat_history h
            JOIN project p ON p.id = h.project_id
            WHERE (#{projectId} IS NULL OR p.id = #{projectId})
              AND (#{startAt} IS NULL OR h.create_time >= #{startAt})
              AND (#{endExclusive} IS NULL OR h.create_time < #{endExclusive})
              AND (
                p.user_id = #{userId}
                OR EXISTS (
                  SELECT 1 FROM project_member pm
                  WHERE pm.project_id = p.id AND pm.user_id = #{userId}
                )
              )
            """)
    Long aiCallTotal(@Param("userId") Long userId,
                     @Param("projectId") Long projectId,
                     @Param("startAt") LocalDateTime startAt,
                     @Param("endExclusive") LocalDateTime endExclusive);

    @Select("""
            SELECT COUNT(*)
            FROM `task` t
            JOIN project p ON p.id = t.project_id
            WHERE (#{projectId} IS NULL OR p.id = #{projectId})
              AND (#{startAt} IS NULL OR t.create_time >= #{startAt})
              AND (#{endExclusive} IS NULL OR t.create_time < #{endExclusive})
              AND (
                p.user_id = #{userId}
                OR EXISTS (
                  SELECT 1 FROM project_member pm
                  WHERE pm.project_id = p.id AND pm.user_id = #{userId}
                )
              )
            """)
    Long tasksCreatedThisWeek(@Param("userId") Long userId,
                              @Param("projectId") Long projectId,
                              @Param("startAt") LocalDateTime startAt,
                              @Param("endExclusive") LocalDateTime endExclusive);

    @Select("""
            SELECT DATE(t.create_time) AS day, COUNT(*) AS count
            FROM `task` t
            JOIN project p ON p.id = t.project_id
            WHERE (#{projectId} IS NULL OR p.id = #{projectId})
              AND (#{startAt} IS NULL OR t.create_time >= #{startAt})
              AND (#{endExclusive} IS NULL OR t.create_time < #{endExclusive})
              AND (
                p.user_id = #{userId}
                OR EXISTS (
                  SELECT 1 FROM project_member pm
                  WHERE pm.project_id = p.id AND pm.user_id = #{userId}
                )
              )
            GROUP BY DATE(t.create_time)
            ORDER BY day ASC
            """)
    List<DashboardDayCountItem> taskTrend7d(@Param("userId") Long userId,
                                           @Param("projectId") Long projectId,
                                           @Param("startAt") LocalDateTime startAt,
                                           @Param("endExclusive") LocalDateTime endExclusive);

    @Select("""
            SELECT DATE(h.create_time) AS day, COUNT(*) AS count
            FROM ai_chat_history h
            JOIN project p ON p.id = h.project_id
            WHERE (#{projectId} IS NULL OR p.id = #{projectId})
              AND (#{startAt} IS NULL OR h.create_time >= #{startAt})
              AND (#{endExclusive} IS NULL OR h.create_time < #{endExclusive})
              AND (
                p.user_id = #{userId}
                OR EXISTS (
                  SELECT 1 FROM project_member pm
                  WHERE pm.project_id = p.id AND pm.user_id = #{userId}
                )
              )
            GROUP BY DATE(h.create_time)
            ORDER BY day ASC
            """)
    List<DashboardDayCountItem> aiTrend7d(@Param("userId") Long userId,
                                         @Param("projectId") Long projectId,
                                         @Param("startAt") LocalDateTime startAt,
                                         @Param("endExclusive") LocalDateTime endExclusive);

    @Select("""
            SELECT t.status AS status, COUNT(*) AS count
            FROM `task` t
            JOIN project p ON p.id = t.project_id
            WHERE (#{projectId} IS NULL OR p.id = #{projectId})
              AND (#{startAt} IS NULL OR t.create_time >= #{startAt})
              AND (#{endExclusive} IS NULL OR t.create_time < #{endExclusive})
              AND (
                p.user_id = #{userId}
                OR EXISTS (
                  SELECT 1 FROM project_member pm
                  WHERE pm.project_id = p.id AND pm.user_id = #{userId}
                )
              )
            GROUP BY t.status
            """)
    List<DashboardStatusCountItem> taskStatusDist(@Param("userId") Long userId,
                                                  @Param("projectId") Long projectId,
                                                  @Param("startAt") LocalDateTime startAt,
                                                  @Param("endExclusive") LocalDateTime endExclusive);

    @Select("""
            SELECT x.userId AS userId, u.username AS username, SUM(x.cnt) AS actionCount
            FROM (
              SELECT tl.user_id AS userId, COUNT(*) AS cnt
              FROM task_timeline tl
              JOIN project p ON p.id = tl.project_id
              WHERE (#{projectId} IS NULL OR p.id = #{projectId})
                AND (#{startAt} IS NULL OR tl.create_time >= #{startAt})
                AND (#{endExclusive} IS NULL OR tl.create_time < #{endExclusive})
                AND (
                  p.user_id = #{userId}
                  OR EXISTS (
                    SELECT 1 FROM project_member pm
                    WHERE pm.project_id = p.id AND pm.user_id = #{userId}
                  )
                )
              GROUP BY tl.user_id

              UNION ALL

              SELECT pa.actor_user_id AS userId, COUNT(*) AS cnt
              FROM project_activity pa
              JOIN project p ON p.id = pa.project_id
              WHERE pa.actor_user_id IS NOT NULL
                AND (#{projectId} IS NULL OR p.id = #{projectId})
                AND (#{startAt} IS NULL OR pa.create_time >= #{startAt})
                AND (#{endExclusive} IS NULL OR pa.create_time < #{endExclusive})
                AND (
                  p.user_id = #{userId}
                  OR EXISTS (
                    SELECT 1 FROM project_member pm
                    WHERE pm.project_id = p.id AND pm.user_id = #{userId}
                  )
                )
              GROUP BY pa.actor_user_id
            ) x
            LEFT JOIN `user` u ON u.id = x.userId
            GROUP BY x.userId, u.username
            ORDER BY actionCount DESC
            LIMIT 8
            """)
    List<DashboardMemberActivityItem> memberActivity7d(@Param("userId") Long userId,
                                                       @Param("projectId") Long projectId,
                                                       @Param("startAt") LocalDateTime startAt,
                                                       @Param("endExclusive") LocalDateTime endExclusive);

    @Select("""
            SELECT
              t.id AS taskId,
              p.id AS projectId,
              p.name AS projectName,
              t.title AS title,
              t.status AS status,
              t.priority AS priority,
              u.username AS assigneeName,
              t.due_time AS dueTime,
              NULL AS commentCount
            FROM `task` t
            JOIN project p ON p.id = t.project_id
            LEFT JOIN `user` u ON u.id = t.assignee_id
            WHERE (#{projectId} IS NULL OR p.id = #{projectId})
              AND t.assignee_id = #{assigneeId}
              AND t.status <> 'DONE'
              AND (
                (t.due_time IS NOT NULL AND t.due_time >= #{startAt} AND t.due_time < #{endExclusive})
                OR (t.due_time IS NULL AND t.create_time >= #{startAt} AND t.create_time < #{endExclusive})
              )
              AND (
                p.user_id = #{userId}
                OR EXISTS (
                  SELECT 1 FROM project_member pm
                  WHERE pm.project_id = p.id AND pm.user_id = #{userId}
                )
              )
            ORDER BY (t.due_time IS NULL) ASC, t.due_time ASC, t.update_time DESC
            LIMIT 6
            """)
    List<DashboardTaskActionItem> myActions(@Param("userId") Long userId,
                                           @Param("projectId") Long projectId,
                                           @Param("startAt") LocalDateTime startAt,
                                           @Param("endExclusive") LocalDateTime endExclusive,
                                           @Param("assigneeId") Long assigneeId);

    @Select("""
            SELECT
              t.id AS taskId,
              p.id AS projectId,
              p.name AS projectName,
              t.title AS title,
              t.status AS status,
              t.priority AS priority,
              u.username AS assigneeName,
              t.due_time AS dueTime,
              NULL AS commentCount
            FROM `task` t
            JOIN project p ON p.id = t.project_id
            LEFT JOIN `user` u ON u.id = t.assignee_id
            WHERE (#{projectId} IS NULL OR p.id = #{projectId})
              AND t.status <> 'DONE'
              AND t.due_time IS NOT NULL
              AND t.due_time < #{nowAt}
              AND t.due_time >= #{startAt} AND t.due_time < #{endExclusive}
              AND (
                p.user_id = #{userId}
                OR EXISTS (
                  SELECT 1 FROM project_member pm
                  WHERE pm.project_id = p.id AND pm.user_id = #{userId}
                )
              )
            ORDER BY t.due_time ASC
            LIMIT 6
            """)
    List<DashboardTaskActionItem> riskTasks(@Param("userId") Long userId,
                                           @Param("projectId") Long projectId,
                                           @Param("startAt") LocalDateTime startAt,
                                           @Param("endExclusive") LocalDateTime endExclusive,
                                           @Param("nowAt") LocalDateTime nowAt);

    @Select("""
            SELECT
              t.id AS taskId,
              p.id AS projectId,
              p.name AS projectName,
              t.title AS title,
              t.status AS status,
              t.priority AS priority,
              u.username AS assigneeName,
              t.due_time AS dueTime,
              COUNT(c.id) AS commentCount
            FROM task_comment c
            JOIN `task` t ON t.id = c.task_id
            JOIN project p ON p.id = t.project_id
            LEFT JOIN `user` u ON u.id = t.assignee_id
            WHERE (#{projectId} IS NULL OR p.id = #{projectId})
              AND c.create_time >= #{startAt} AND c.create_time < #{endExclusive}
              AND (
                p.user_id = #{userId}
                OR EXISTS (
                  SELECT 1 FROM project_member pm
                  WHERE pm.project_id = p.id AND pm.user_id = #{userId}
                )
              )
            GROUP BY t.id, p.id, p.name, t.title, t.status, t.priority, u.username, t.due_time
            ORDER BY commentCount DESC
            LIMIT 6
            """)
    List<DashboardTaskActionItem> topDiscussedTasks(@Param("userId") Long userId,
                                                    @Param("projectId") Long projectId,
                                                    @Param("startAt") LocalDateTime startAt,
                                                    @Param("endExclusive") LocalDateTime endExclusive);

    @Select("""
            SELECT COUNT(*)
            FROM `task` t
            JOIN project p ON p.id = t.project_id
            WHERE (#{projectId} IS NULL OR p.id = #{projectId})
              AND t.status = 'DOING'
              AND (
                p.user_id = #{userId}
                OR EXISTS (
                  SELECT 1 FROM project_member pm
                  WHERE pm.project_id = p.id AND pm.user_id = #{userId}
                )
              )
            """)
    Long wipTotal(@Param("userId") Long userId,
                  @Param("projectId") Long projectId);

    @Select("""
            SELECT DATE(t.done_time) AS day, COUNT(*) AS count
            FROM `task` t
            JOIN project p ON p.id = t.project_id
            WHERE (#{projectId} IS NULL OR p.id = #{projectId})
              AND t.done_time IS NOT NULL
              AND t.done_time >= #{startAt} AND t.done_time < #{endExclusive}
              AND (
                p.user_id = #{userId}
                OR EXISTS (
                  SELECT 1 FROM project_member pm
                  WHERE pm.project_id = p.id AND pm.user_id = #{userId}
                )
              )
            GROUP BY DATE(t.done_time)
            ORDER BY day ASC
            """)
    List<DashboardDayCountItem> throughputTrend(@Param("userId") Long userId,
                                               @Param("projectId") Long projectId,
                                               @Param("startAt") LocalDateTime startAt,
                                               @Param("endExclusive") LocalDateTime endExclusive);

    @Select("""
            SELECT TIMESTAMPDIFF(SECOND, t.started_time, t.done_time) AS seconds
            FROM `task` t
            JOIN project p ON p.id = t.project_id
            WHERE (#{projectId} IS NULL OR p.id = #{projectId})
              AND t.done_time IS NOT NULL
              AND t.started_time IS NOT NULL
              AND t.done_time >= #{startAt} AND t.done_time < #{endExclusive}
              AND (
                p.user_id = #{userId}
                OR EXISTS (
                  SELECT 1 FROM project_member pm
                  WHERE pm.project_id = p.id AND pm.user_id = #{userId}
                )
              )
            ORDER BY seconds ASC
            LIMIT 600
            """)
    List<Long> cycleTimeSecondsSamples(@Param("userId") Long userId,
                                       @Param("projectId") Long projectId,
                                       @Param("startAt") LocalDateTime startAt,
                                       @Param("endExclusive") LocalDateTime endExclusive);

    @Select("""
            SELECT
              t.id AS taskId,
              p.id AS projectId,
              p.name AS projectName,
              t.title AS title,
              t.status AS status,
              u.username AS assigneeName,
              t.done_time AS doneTime
            FROM `task` t
            JOIN project p ON p.id = t.project_id
            LEFT JOIN `user` u ON u.id = t.assignee_id
            WHERE (#{projectId} IS NULL OR p.id = #{projectId})
              AND t.done_time IS NOT NULL
              AND t.done_time >= #{startAt} AND t.done_time < #{endExclusive}
              AND (
                p.user_id = #{userId}
                OR EXISTS (
                  SELECT 1 FROM project_member pm
                  WHERE pm.project_id = p.id AND pm.user_id = #{userId}
                )
              )
            ORDER BY t.done_time DESC
            LIMIT 50
            """)
    List<DashboardDoneTaskItem> doneTasksByDay(@Param("userId") Long userId,
                                              @Param("projectId") Long projectId,
                                              @Param("startAt") LocalDateTime startAt,
                                              @Param("endExclusive") LocalDateTime endExclusive);
}
