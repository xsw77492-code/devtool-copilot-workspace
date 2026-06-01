package com.devtoolcopilot.project.pin.service;

import java.util.List;

public interface UserProjectPinService {
    List<Long> listPinnedProjectIds(Long userId);

    void setPinned(Long userId, Long projectId, boolean pinned);
}

