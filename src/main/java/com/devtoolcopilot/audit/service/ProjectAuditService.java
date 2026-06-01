package com.devtoolcopilot.audit.service;

import com.devtoolcopilot.audit.dto.ProjectAuditExportResponse;
import com.devtoolcopilot.audit.dto.ProjectAuditListResponse;

public interface ProjectAuditService {
    void record(Long projectId,
                Long actorUserId,
                String action,
                String targetType,
                Long targetId,
                String summary,
                String detail);

    ProjectAuditListResponse list(Long userId,
                                 Long projectId,
                                 Long cursor,
                                 Integer limit,
                                 String action,
                                 Long actorUserId,
                                 String q,
                                 Long fromTime,
                                 Long toTime);

    ProjectAuditExportResponse exportCsv(Long userId,
                                        Long projectId,
                                        String action,
                                        Long actorUserId,
                                        String q,
                                        Long fromTime,
                                        Long toTime);

    void deleteOne(Long userId, Long projectId, Long id);

    int clear(Long userId, Long projectId, String action, Long actorUserId, String q, Long fromTime, Long toTime);
}
