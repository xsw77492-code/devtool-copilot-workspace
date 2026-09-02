package com.devtoolcopilot.release.service;

import com.devtoolcopilot.release.dto.ReleaseGenerateNotesResponse;
import com.devtoolcopilot.release.dto.ReleaseCreateRequest;
import com.devtoolcopilot.release.dto.ReleaseUpdateRequest;
import com.devtoolcopilot.release.entity.ProjectRelease;

import java.util.List;

public interface ReleaseService {
    List<ProjectRelease> listByProject(Long userId, Long projectId);

    ProjectRelease get(Long userId, Long releaseId);

    Long create(Long userId, ReleaseCreateRequest req);

    boolean updateDraft(Long userId, Long releaseId, ReleaseUpdateRequest req);

    ReleaseGenerateNotesResponse generateNotes(Long userId, Long releaseId);

    boolean publish(Long userId, Long releaseId);

    boolean deleteDraft(Long userId, Long releaseId);
}
