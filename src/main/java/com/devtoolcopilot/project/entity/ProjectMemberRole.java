package com.devtoolcopilot.project.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ProjectMemberRole {
    OWNER,
    DEVELOPER,
    VIEWER;

    @JsonCreator
    public static ProjectMemberRole fromString(String v) {
        if (v == null) return VIEWER;
        try {
            return ProjectMemberRole.valueOf(v.trim().toUpperCase());
        } catch (Exception e) {
            return VIEWER;
        }
    }
}

