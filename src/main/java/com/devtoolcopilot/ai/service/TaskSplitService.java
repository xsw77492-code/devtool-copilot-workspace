package com.devtoolcopilot.ai.service;

import com.devtoolcopilot.ai.dto.TaskSplitResponseDTO;

import java.util.function.Consumer;

public interface TaskSplitService {
    TaskSplitResponseDTO split(Long userId, String requirement);

    TaskSplitResponseDTO splitStream(Long userId, String requirement, Consumer<String> onDelta);
}
