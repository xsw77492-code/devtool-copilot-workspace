package com.devtoolcopilot.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devtoolcopilot.audit.dto.ProjectAuditItem;
import com.devtoolcopilot.audit.entity.ProjectAuditLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface ProjectAuditLogMapper extends BaseMapper<ProjectAuditLog> {
    @Select("""
            <script>
            SELECT a.id AS id,
                   a.project_id AS projectId,
                   a.actor_user_id AS actorUserId,
                   u.username AS actorUsername,
                   u.email AS actorEmail,
                   a.action AS action,
                   a.target_type AS targetType,
                   a.target_id AS targetId,
                   a.summary AS summary,
                   a.detail AS detail,
                   a.ip AS ip,
                   a.user_agent AS userAgent,
                   a.create_time AS createTime
            FROM project_audit_log a
            LEFT JOIN `user` u ON u.id = a.actor_user_id
            WHERE a.project_id = #{projectId}
              <if test="cursor != null">AND a.id &lt; #{cursor}</if>
              <if test="action != null and action != ''">AND a.action = #{action}</if>
              <if test="actorUserId != null">AND a.actor_user_id = #{actorUserId}</if>
              <if test="fromTime != null">AND a.create_time &gt;= #{fromTime}</if>
              <if test="toTime != null">AND a.create_time &lt;= #{toTime}</if>
              <if test="q != null and q != ''">
                AND (
                  a.summary LIKE CONCAT('%', #{q}, '%')
                  OR a.detail LIKE CONCAT('%', #{q}, '%')
                )
              </if>
            ORDER BY a.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ProjectAuditItem> listItems(@Param("projectId") Long projectId,
                                    @Param("cursor") Long cursor,
                                    @Param("limit") int limit,
                                    @Param("action") String action,
                                    @Param("actorUserId") Long actorUserId,
                                    @Param("q") String q,
                                    @Param("fromTime") LocalDateTime fromTime,
                                    @Param("toTime") LocalDateTime toTime);

    @Select("""
            <script>
            SELECT a.id AS id,
                   a.project_id AS projectId,
                   a.actor_user_id AS actorUserId,
                   u.username AS actorUsername,
                   u.email AS actorEmail,
                   a.action AS action,
                   a.target_type AS targetType,
                   a.target_id AS targetId,
                   a.summary AS summary,
                   a.detail AS detail,
                   a.ip AS ip,
                   a.user_agent AS userAgent,
                   a.create_time AS createTime
            FROM project_audit_log a
            LEFT JOIN `user` u ON u.id = a.actor_user_id
            WHERE a.project_id = #{projectId}
              <if test="action != null and action != ''">AND a.action = #{action}</if>
              <if test="actorUserId != null">AND a.actor_user_id = #{actorUserId}</if>
              <if test="fromTime != null">AND a.create_time &gt;= #{fromTime}</if>
              <if test="toTime != null">AND a.create_time &lt;= #{toTime}</if>
              <if test="q != null and q != ''">
                AND (
                  a.summary LIKE CONCAT('%', #{q}, '%')
                  OR a.detail LIKE CONCAT('%', #{q}, '%')
                )
              </if>
            ORDER BY a.id DESC
            LIMIT 2000
            </script>
            """)
    List<ProjectAuditItem> listItemsForExport(@Param("projectId") Long projectId,
                                             @Param("action") String action,
                                             @Param("actorUserId") Long actorUserId,
                                             @Param("q") String q,
                                             @Param("fromTime") LocalDateTime fromTime,
                                             @Param("toTime") LocalDateTime toTime);

    @Delete("""
            DELETE FROM project_audit_log
            WHERE project_id = #{projectId} AND id = #{id}
            """)
    int deleteOne(@Param("projectId") Long projectId, @Param("id") Long id);

    @Delete("""
            <script>
            DELETE FROM project_audit_log
            WHERE project_id = #{projectId}
              <if test="action != null and action != ''">AND action = #{action}</if>
              <if test="actorUserId != null">AND actor_user_id = #{actorUserId}</if>
              <if test="fromTime != null">AND create_time &gt;= #{fromTime}</if>
              <if test="toTime != null">AND create_time &lt;= #{toTime}</if>
              <if test="q != null and q != ''">
                AND (
                  summary LIKE CONCAT('%', #{q}, '%')
                  OR detail LIKE CONCAT('%', #{q}, '%')
                )
              </if>
            </script>
            """)
    int clearByQuery(@Param("projectId") Long projectId,
                     @Param("action") String action,
                     @Param("actorUserId") Long actorUserId,
                     @Param("q") String q,
                     @Param("fromTime") LocalDateTime fromTime,
                     @Param("toTime") LocalDateTime toTime);
}
