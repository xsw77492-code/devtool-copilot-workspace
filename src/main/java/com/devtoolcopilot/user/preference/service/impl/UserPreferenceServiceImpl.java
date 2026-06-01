package com.devtoolcopilot.user.preference.service.impl;

import com.devtoolcopilot.user.preference.dto.UserPreferenceDTO;
import com.devtoolcopilot.user.preference.dto.UserPreferenceUpdateRequest;
import com.devtoolcopilot.user.preference.entity.UserPreference;
import com.devtoolcopilot.user.preference.mapper.UserPreferenceMapper;
import com.devtoolcopilot.user.preference.service.UserPreferenceService;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class UserPreferenceServiceImpl implements UserPreferenceService {
    private static final Set<String> ACCENTS = Set.of("teal", "sky", "emerald", "amber", "rose", "slate");

    private final UserPreferenceMapper mapper;

    public UserPreferenceServiceImpl(UserPreferenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UserPreferenceDTO get(Long userId) {
        if (userId == null) return defaultDto();
        UserPreference row = mapper.selectById(userId);
        if (row == null) {
            row = new UserPreference();
            row.setUserId(userId);
            row.setAccentKey("teal");
            row.setTimezone("Asia/Shanghai");
            row.setWeekStart(1);
            row.setReduceMotion(0);
            mapper.insert(row);
        }
        UserPreferenceDTO dto = new UserPreferenceDTO();
        dto.setAccentKey(row.getAccentKey() == null ? "teal" : row.getAccentKey());
        dto.setTimezone(row.getTimezone() == null ? "Asia/Shanghai" : row.getTimezone());
        dto.setWeekStart(row.getWeekStart() == null ? 1 : row.getWeekStart());
        dto.setReduceMotion(row.getReduceMotion() == null ? 0 : row.getReduceMotion());
        return dto;
    }

    @Override
    public void update(Long userId, UserPreferenceUpdateRequest req) {
        if (userId == null || req == null) return;
        UserPreference row = mapper.selectById(userId);
        if (row == null) {
            row = new UserPreference();
            row.setUserId(userId);
            row.setAccentKey("teal");
            row.setTimezone("Asia/Shanghai");
            row.setWeekStart(1);
            row.setReduceMotion(0);
            mapper.insert(row);
        }

        if (req.getAccentKey() != null && ACCENTS.contains(req.getAccentKey())) row.setAccentKey(req.getAccentKey());
        if (req.getTimezone() != null && !req.getTimezone().isBlank()) row.setTimezone(req.getTimezone());
        if (req.getWeekStart() != null && (req.getWeekStart() == 0 || req.getWeekStart() == 1)) row.setWeekStart(req.getWeekStart());
        if (req.getReduceMotion() != null) row.setReduceMotion(req.getReduceMotion() == 1 ? 1 : 0);

        mapper.updateById(row);
    }

    private static UserPreferenceDTO defaultDto() {
        UserPreferenceDTO dto = new UserPreferenceDTO();
        dto.setAccentKey("teal");
        dto.setTimezone("Asia/Shanghai");
        dto.setWeekStart(1);
        dto.setReduceMotion(0);
        return dto;
    }
}
