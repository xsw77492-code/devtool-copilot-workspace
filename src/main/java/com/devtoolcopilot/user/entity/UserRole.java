package com.devtoolcopilot.user.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum UserRole {
    USER,
    ADMIN;

    @JsonCreator
    public static UserRole fromString(String v) {
        if (v == null) return USER;
        try {
            return UserRole.valueOf(v.trim().toUpperCase());
        } catch (Exception e) {
            return USER;
        }
    }
}

