package com.devtoolcopilot.task.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;

public enum TaskStatus implements IEnum<String> {
    TODO("TODO"),
    DOING("DOING"),
    DONE("DONE");

    @EnumValue
    private final String value;

    TaskStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}
