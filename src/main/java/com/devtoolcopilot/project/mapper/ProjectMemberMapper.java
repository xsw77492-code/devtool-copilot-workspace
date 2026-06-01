package com.devtoolcopilot.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devtoolcopilot.project.dto.ProjectMemberItem;
import com.devtoolcopilot.project.entity.ProjectMember;
import com.devtoolcopilot.project.entity.ProjectMemberRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ProjectMemberMapper extends BaseMapper<ProjectMember> {
    @Select("""
            SELECT pm.user_id AS userId,
                   u.username AS username,
                   u.email AS email,
                   pm.role AS role,
                   pm.disabled AS disabled,
                   pm.disabled_time AS disabledTime,
                   ps.lastSeenAt AS lastSeenAt,
                   ps.online AS online,
                   pm.create_time AS joinedAt
            FROM project_member pm
            JOIN `user` u ON u.id = pm.user_id
            LEFT JOIN (
              SELECT project_id,
                     user_id,
                     MAX(last_seen_time) AS lastSeenAt,
                     MAX(CASE WHEN disconnect_time IS NULL THEN 1 ELSE 0 END) AS online
              FROM project_presence_session
              WHERE project_id = #{projectId}
              GROUP BY project_id, user_id
            ) ps ON ps.project_id = pm.project_id AND ps.user_id = pm.user_id
            WHERE pm.project_id = #{projectId}
            ORDER BY pm.disabled ASC, pm.role = 'OWNER' DESC, pm.id ASC
            """)
    List<ProjectMemberItem> listMemberItems(@Param("projectId") Long projectId);

    @Select("""
            SELECT role
            FROM project_member
            WHERE project_id = #{projectId}
              AND user_id = #{userId}
              AND (disabled IS NULL OR disabled = 0)
            LIMIT 1
            """)
    ProjectMemberRole findRole(@Param("projectId") Long projectId, @Param("userId") Long userId);
}
