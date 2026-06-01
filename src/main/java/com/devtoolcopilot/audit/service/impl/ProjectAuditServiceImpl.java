package com.devtoolcopilot.audit.service.impl;

import com.devtoolcopilot.audit.dto.ProjectAuditExportResponse;
import com.devtoolcopilot.audit.dto.ProjectAuditItem;
import com.devtoolcopilot.audit.dto.ProjectAuditListResponse;
import com.devtoolcopilot.audit.entity.ProjectAuditLog;
import com.devtoolcopilot.audit.mapper.ProjectAuditLogMapper;
import com.devtoolcopilot.audit.service.ProjectAuditService;
import com.devtoolcopilot.audit.util.CurrentRequestUtils;
import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.common.web.ClientIpUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ProjectAuditServiceImpl implements ProjectAuditService {
    private final ProjectAuditLogMapper auditLogMapper;

    public ProjectAuditServiceImpl(ProjectAuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    public void record(Long projectId,
                       Long actorUserId,
                       String action,
                       String targetType,
                       Long targetId,
                       String summary,
                       String detail) {
        try {
            if (projectId == null || actorUserId == null) return;
            if (action == null || action.isBlank()) return;

            HttpServletRequest req = CurrentRequestUtils.get();
            String ip = ClientIpUtils.getClientIp(req);
            String ua = req == null ? null : req.getHeader("User-Agent");

            ProjectAuditLog l = new ProjectAuditLog();
            l.setProjectId(projectId);
            l.setActorUserId(actorUserId);
            l.setAction(action.trim());
            l.setTargetType(targetType == null ? null : targetType.trim());
            l.setTargetId(targetId);
            l.setSummary(trimTo(summary, 255));
            l.setDetail(detail);
            l.setIp(trimTo(ip, 64));
            l.setUserAgent(trimTo(ua, 512));
            auditLogMapper.insert(l);
        } catch (Exception ignored) {
        }
    }

    @Override
    public ProjectAuditListResponse list(Long userId,
                                        Long projectId,
                                        Long cursor,
                                        Integer limit,
                                        String action,
                                        Long actorUserId,
                                        String q,
                                        Long fromTime,
                                        Long toTime) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (projectId == null) throw new ApiException(400, "projectId不能为空");

        int finalLimit = limit == null ? 100 : Math.max(1, Math.min(limit, 200));
        LocalDateTime from = fromTime == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(fromTime), ZoneId.systemDefault());
        LocalDateTime to = toTime == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(toTime), ZoneId.systemDefault());

        List<ProjectAuditItem> list = auditLogMapper.listItems(
                projectId,
                cursor,
                finalLimit,
                normalize(action),
                actorUserId,
                normalize(q),
                from,
                to
        );

        ProjectAuditListResponse resp = new ProjectAuditListResponse();
        resp.setList(list);
        if (list == null || list.isEmpty()) {
            resp.setNextCursor(null);
        } else {
            resp.setNextCursor(list.get(list.size() - 1).getId());
        }
        return resp;
    }

    @Override
    public ProjectAuditExportResponse exportCsv(Long userId,
                                               Long projectId,
                                               String action,
                                               Long actorUserId,
                                               String q,
                                               Long fromTime,
                                               Long toTime) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (projectId == null) throw new ApiException(400, "projectId不能为空");

        LocalDateTime from = fromTime == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(fromTime), ZoneId.systemDefault());
        LocalDateTime to = toTime == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(toTime), ZoneId.systemDefault());

        List<ProjectAuditItem> list = auditLogMapper.listItemsForExport(
                projectId,
                normalize(action),
                actorUserId,
                normalize(q),
                from,
                to
        );

        StringBuilder sb = new StringBuilder();
        sb.append("time,id,actorUserId,actorUsername,actorEmail,action,targetType,targetId,summary,ip,userAgent,detail\n");
        if (list != null) {
            for (ProjectAuditItem i : list) {
                sb.append(csv(i.getCreateTime() == null ? "" : i.getCreateTime().toString())).append(',');
                sb.append(csv(String.valueOf(i.getId()))).append(',');
                sb.append(csv(String.valueOf(i.getActorUserId()))).append(',');
                sb.append(csv(nvl(i.getActorUsername()))).append(',');
                sb.append(csv(nvl(i.getActorEmail()))).append(',');
                sb.append(csv(nvl(i.getAction()))).append(',');
                sb.append(csv(nvl(i.getTargetType()))).append(',');
                sb.append(csv(i.getTargetId() == null ? "" : String.valueOf(i.getTargetId()))).append(',');
                sb.append(csv(nvl(i.getSummary()))).append(',');
                sb.append(csv(nvl(i.getIp()))).append(',');
                sb.append(csv(nvl(i.getUserAgent()))).append(',');
                sb.append(csv(nvl(i.getDetail()))).append('\n');
            }
        }

        ProjectAuditExportResponse resp = new ProjectAuditExportResponse();
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        resp.setFilename("project-audit-" + projectId + "-" + ts + ".csv");
        resp.setContent(sb.toString());
        return resp;
    }

    @Override
    public void deleteOne(Long userId, Long projectId, Long id) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (projectId == null) throw new ApiException(400, "projectId不能为空");
        if (id == null) throw new ApiException(400, "id不能为空");
        int deleted = auditLogMapper.deleteOne(projectId, id);
        if (deleted <= 0) throw new ApiException(404, "审计记录不存在");
    }

    @Override
    public int clear(Long userId, Long projectId, String action, Long actorUserId, String q, Long fromTime, Long toTime) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (projectId == null) throw new ApiException(400, "projectId不能为空");
        LocalDateTime from = fromTime == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(fromTime), ZoneId.systemDefault());
        LocalDateTime to = toTime == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(toTime), ZoneId.systemDefault());
        return auditLogMapper.clearByQuery(projectId, normalize(action), actorUserId, normalize(q), from, to);
    }

    private static String normalize(String v) {
        if (v == null) return null;
        String s = v.trim();
        return s.isBlank() ? null : s;
    }

    private static String trimTo(String v, int max) {
        if (v == null) return null;
        String s = v.trim();
        if (s.length() <= max) return s;
        return s.substring(0, max);
    }

    private static String nvl(String v) {
        return v == null ? "" : v;
    }

    private static String csv(String v) {
        String s = v == null ? "" : v;
        String escaped = s.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
