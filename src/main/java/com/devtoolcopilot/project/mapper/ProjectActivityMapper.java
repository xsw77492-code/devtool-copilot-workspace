package com.devtoolcopilot.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devtoolcopilot.project.dto.ProjectActivityItem;
import com.devtoolcopilot.project.entity.ProjectActivity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ProjectActivityMapper extends BaseMapper<ProjectActivity> {
    @Select("""
            SELECT a.id AS id,
                   a.actor_user_id AS actorUserId,
                   u.username AS actorUsername,
                   a.type AS type,
                   a.detail AS detail,
                   a.create_time AS createTime
            FROM project_activity a
            LEFT JOIN `user` u ON u.id = a.actor_user_id
            WHERE a.project_id = #{projectId}
            ORDER BY a.id DESC
            LIMIT #{limit}
            """)
    List<ProjectActivityItem> listItems(@Param("projectId") Long projectId, @Param("limit") int limit);
}
