package com.devtoolcopilot.task.timeline.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;

public enum TaskTimelineType implements IEnum<String> {
    CREATED("CREATED"),
    STATUS_CHANGED("STATUS_CHANGED"),
    UPDATED("UPDATED"),
    NOTE("NOTE"),
    COMMENT("COMMENT"),
    PR_LINKED("PR_LINKED"),
    PR_UNLINKED("PR_UNLINKED");

    @EnumValue
    private final String value;

    TaskTimelineType(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}
