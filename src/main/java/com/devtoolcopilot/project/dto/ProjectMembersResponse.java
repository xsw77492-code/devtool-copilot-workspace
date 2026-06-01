package com.devtoolcopilot.project.dto;

import com.devtoolcopilot.project.entity.ProjectMemberRole;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ProjectMembersResponse {
    private ProjectMemberRole myRole;
    private List<ProjectMemberItem> members;
}

