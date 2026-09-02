package com.devtoolcopilot.task.search.mapper;

import com.devtoolcopilot.task.search.dto.TaskSearchItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskSearchMapper {
    @Select("""
            <script>
            SELECT
              t.id AS id,
              t.project_id AS projectId,
              p.archived AS projectArchived,
              p.name AS projectName,
              t.title AS title,
              t.status AS status,
              t.priority AS priority,
              t.tags AS tags,
              t.assignee AS assignee,
              t.assignee_id AS assigneeId,
              t.due_time AS dueTime,
              t.milestone_id AS milestoneId,
              m.name AS milestoneName,
              t.update_time AS updatedAt,
              t.create_time AS createTime
            FROM `task` t
              JOIN project p ON p.id = t.project_id
              LEFT JOIN project_milestone m ON m.id = t.milestone_id
            WHERE (p.user_id = #{userId}
               OR EXISTS (
                  SELECT 1
                  FROM project_member pm
                  WHERE pm.project_id = p.id
                    AND pm.user_id = #{userId}
                    AND (pm.disabled IS NULL OR pm.disabled &lt;&gt; 1)
               ))
            <if test="projectId != null and projectId &gt; 0">
              AND t.project_id = #{projectId}
            </if>
            <if test="includeArchived == null or includeArchived == 0">
              AND (p.archived IS NULL OR p.archived = 0)
            </if>
            <if test="q != null and q != ''">
              AND (
                t.title LIKE CONCAT('%', #{q}, '%')
                OR t.description LIKE CONCAT('%', #{q}, '%')
                OR EXISTS (
                  SELECT 1
                  FROM task_comment c
                  WHERE c.project_id = t.project_id
                    AND c.task_id = t.id
                    AND c.content LIKE CONCAT('%', #{q}, '%')
                )
                OR EXISTS (
                  SELECT 1
                  FROM task_deliverable d
                  WHERE d.project_id = t.project_id
                    AND d.task_id = t.id
                    AND (
                      d.title LIKE CONCAT('%', #{q}, '%')
                      OR d.url LIKE CONCAT('%', #{q}, '%')
                      OR d.content LIKE CONCAT('%', #{q}, '%')
                    )
                )
              )
            </if>
            <if test="statuses != null and statuses.size() &gt; 0">
              AND t.status IN
              <foreach collection="statuses" item="s" open="(" separator="," close=")">
                #{s}
              </foreach>
            </if>
            <if test="assigneeId != null and assigneeId &gt; 0">
              AND t.assignee_id = #{assigneeId}
            </if>
            <if test="assigneeId != null and assigneeId == -1">
              AND t.assignee_id IS NULL
            </if>
            <if test="milestoneId != null and milestoneId &gt; 0">
              AND t.milestone_id = #{milestoneId}
            </if>
            <if test="tags != null and tags.size() &gt; 0">
              <foreach collection="tags" item="tag">
                AND t.tags LIKE CONCAT('%', #{tag}, '%')
              </foreach>
            </if>
            <if test="updatedFrom != null">
              AND t.update_time &gt;= #{updatedFrom}
            </if>
            <if test="updatedTo != null">
              AND t.update_time &lt;= #{updatedTo}
            </if>
            ORDER BY t.update_time DESC, t.id DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<TaskSearchItem> search(@Param("userId") Long userId,
                               @Param("projectId") Long projectId,
                               @Param("includeArchived") Integer includeArchived,
                               @Param("q") String q,
                               @Param("statuses") List<String> statuses,
                               @Param("assigneeId") Long assigneeId,
                               @Param("milestoneId") Long milestoneId,
                               @Param("tags") List<String> tags,
                               @Param("updatedFrom") LocalDateTime updatedFrom,
                               @Param("updatedTo") LocalDateTime updatedTo,
                               @Param("offset") Integer offset,
                               @Param("limit") Integer limit);

    @Select("""
            <script>
            SELECT COUNT(DISTINCT t.id)
            FROM `task` t
              JOIN project p ON p.id = t.project_id
            WHERE (p.user_id = #{userId}
               OR EXISTS (
                  SELECT 1
                  FROM project_member pm
                  WHERE pm.project_id = p.id
                    AND pm.user_id = #{userId}
                    AND (pm.disabled IS NULL OR pm.disabled &lt;&gt; 1)
               ))
            <if test="projectId != null and projectId &gt; 0">
              AND t.project_id = #{projectId}
            </if>
            <if test="includeArchived == null or includeArchived == 0">
              AND (p.archived IS NULL OR p.archived = 0)
            </if>
            <if test="q != null and q != ''">
              AND (
                t.title LIKE CONCAT('%', #{q}, '%')
                OR t.description LIKE CONCAT('%', #{q}, '%')
                OR EXISTS (
                  SELECT 1
                  FROM task_comment c
                  WHERE c.project_id = t.project_id
                    AND c.task_id = t.id
                    AND c.content LIKE CONCAT('%', #{q}, '%')
                )
                OR EXISTS (
                  SELECT 1
                  FROM task_deliverable d
                  WHERE d.project_id = t.project_id
                    AND d.task_id = t.id
                    AND (
                      d.title LIKE CONCAT('%', #{q}, '%')
                      OR d.url LIKE CONCAT('%', #{q}, '%')
                      OR d.content LIKE CONCAT('%', #{q}, '%')
                    )
                )
              )
            </if>
            <if test="statuses != null and statuses.size() &gt; 0">
              AND t.status IN
              <foreach collection="statuses" item="s" open="(" separator="," close=")">
                #{s}
              </foreach>
            </if>
            <if test="assigneeId != null and assigneeId &gt; 0">
              AND t.assignee_id = #{assigneeId}
            </if>
            <if test="assigneeId != null and assigneeId == -1">
              AND t.assignee_id IS NULL
            </if>
            <if test="milestoneId != null and milestoneId &gt; 0">
              AND t.milestone_id = #{milestoneId}
            </if>
            <if test="tags != null and tags.size() &gt; 0">
              <foreach collection="tags" item="tag">
                AND t.tags LIKE CONCAT('%', #{tag}, '%')
              </foreach>
            </if>
            <if test="updatedFrom != null">
              AND t.update_time &gt;= #{updatedFrom}
            </if>
            <if test="updatedTo != null">
              AND t.update_time &lt;= #{updatedTo}
            </if>
            </script>
            """)
    Long count(@Param("userId") Long userId,
               @Param("projectId") Long projectId,
               @Param("includeArchived") Integer includeArchived,
               @Param("q") String q,
               @Param("statuses") List<String> statuses,
               @Param("assigneeId") Long assigneeId,
               @Param("milestoneId") Long milestoneId,
               @Param("tags") List<String> tags,
               @Param("updatedFrom") LocalDateTime updatedFrom,
               @Param("updatedTo") LocalDateTime updatedTo);
}

