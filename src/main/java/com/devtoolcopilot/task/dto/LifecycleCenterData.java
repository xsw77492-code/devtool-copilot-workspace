package com.devtoolcopilot.task.dto;

import com.devtoolcopilot.project.entity.Project;

import java.util.List;

/**
 * 生命周期中心聚合数据：一次返回全部项目的脉冲数据，替代前端逐项目轮询。
 */
public class LifecycleCenterData {
    private List<Project> projects;
    private List<ProjectPulse> pulses;

    public List<Project> getProjects() {
        return projects;
    }

    public void setProjects(List<Project> projects) {
        this.projects = projects;
    }

    public List<ProjectPulse> getPulses() {
        return pulses;
    }

    public void setPulses(List<ProjectPulse> pulses) {
        this.pulses = pulses;
    }
}
