package com.devtoolcopilot.user.preference.service;

import com.devtoolcopilot.user.preference.dto.UserPreferenceDTO;
import com.devtoolcopilot.user.preference.dto.UserPreferenceUpdateRequest;

public interface UserPreferenceService {
    UserPreferenceDTO get(Long userId);

    void update(Long userId, UserPreferenceUpdateRequest req);
}

