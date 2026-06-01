package com.devtoolcopilot.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devtoolcopilot.project.entity.Project;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ProjectMapper extends BaseMapper<Project> {
    @Select("""
            SELECT p.*
            FROM project p
            WHERE p.user_id = #{userId}
               OR EXISTS (
                  SELECT 1 FROM project_member pm
                  WHERE pm.project_id = p.id AND pm.user_id = #{userId}
               )
              AND (#{includeArchived} = 1 OR p.archived = 0)
            ORDER BY p.id DESC
            """)
    List<Project> listAccessibleByUserId(@Param("userId") Long userId, @Param("includeArchived") Integer includeArchived);
}
