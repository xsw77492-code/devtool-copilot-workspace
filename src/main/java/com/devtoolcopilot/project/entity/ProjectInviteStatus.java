package com.devtoolcopilot.project.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ProjectInviteStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    CANCELED;

    @JsonCreator
    public static ProjectInviteStatus fromString(String v) {
        if (v == null) return PENDING;
        try {
            return ProjectInviteStatus.valueOf(v.trim().toUpperCase());
        } catch (Exception e) {
            return PENDING;
        }
    }
}

