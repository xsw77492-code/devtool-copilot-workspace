package com.devtoolcopilot.project.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devtoolcopilot.audit.service.ProjectAuditService;
import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.project.config.DevtoolWebProperties;
import com.devtoolcopilot.project.config.ProjectInviteProperties;
import com.devtoolcopilot.project.dto.ProjectActivityItem;
import com.devtoolcopilot.project.dto.ProjectInviteCreateResponse;
import com.devtoolcopilot.project.dto.ProjectInviteItem;
import com.devtoolcopilot.project.dto.ProjectMembersResponse;
import com.devtoolcopilot.project.entity.ProjectActivity;
import com.devtoolcopilot.project.entity.ProjectInvite;
import com.devtoolcopilot.project.entity.ProjectInviteStatus;
import com.devtoolcopilot.project.entity.ProjectMember;
import com.devtoolcopilot.project.entity.ProjectMemberRole;
import com.devtoolcopilot.project.mapper.ProjectActivityMapper;
import com.devtoolcopilot.project.mapper.ProjectInviteMapper;
import com.devtoolcopilot.project.mapper.ProjectMemberMapper;
import com.devtoolcopilot.project.mapper.ProjectMapper;
import com.devtoolcopilot.notification.service.NotificationService;
import com.devtoolcopilot.realtime.service.RealtimeCollabService;
import com.devtoolcopilot.user.config.DevtoolMailProperties;
import com.devtoolcopilot.user.entity.User;
import com.devtoolcopilot.user.service.UserService;
import com.devtoolcopilot.user.util.TokenUtils;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Service
public class ProjectCollabServiceImpl implements com.devtoolcopilot.project.service.ProjectCollabService {
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectInviteMapper projectInviteMapper;
    private final ProjectActivityMapper projectActivityMapper;
    private final ProjectMapper projectMapper;
    private final DevtoolWebProperties webProperties;
    private final ProjectInviteProperties inviteProperties;
    private final UserService userService;
    private final NotificationService notificationService;
    private final RealtimeCollabService realtimeCollabService;
    private final JavaMailSender mailSender;
    private final DevtoolMailProperties mailProperties;
    private final ProjectAuditService projectAuditService;

    public ProjectCollabServiceImpl(ProjectMemberMapper projectMemberMapper,
                                   ProjectInviteMapper projectInviteMapper,
                                   ProjectActivityMapper projectActivityMapper,
                                   ProjectMapper projectMapper,
                                   DevtoolWebProperties webProperties,
                                   ProjectInviteProperties inviteProperties,
                                   UserService userService,
                                   NotificationService notificationService,
                                   RealtimeCollabService realtimeCollabService,
                                   JavaMailSender mailSender,
                                   DevtoolMailProperties mailProperties,
                                   ProjectAuditService projectAuditService) {
        this.projectMemberMapper = projectMemberMapper;
        this.projectInviteMapper = projectInviteMapper;
        this.projectActivityMapper = projectActivityMapper;
        this.projectMapper = projectMapper;
        this.webProperties = webProperties;
        this.inviteProperties = inviteProperties;
        this.userService = userService;
        this.notificationService = notificationService;
        this.realtimeCollabService = realtimeCollabService;
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.projectAuditService = projectAuditService;
    }

    @Override
    public ProjectMemberRole getMyRole(Long userId, Long projectId) {
        if (userId == null || projectId == null) return null;
        return projectMemberMapper.findRole(projectId, userId);
    }

    @Override
    public void requireMember(Long userId, Long projectId) {
        ProjectMemberRole r = getMyRole(userId, projectId);
        if (r == null) {
            ProjectMember pm = projectMemberMapper.selectOne(Wrappers.<ProjectMember>lambdaQuery()
                    .eq(ProjectMember::getProjectId, projectId)
                    .eq(ProjectMember::getUserId, userId)
                    .last("LIMIT 1")
            );
            if (pm != null && Objects.equals(pm.getDisabled(), 1)) throw new ApiException(403, "成员已被禁用");
            throw new ApiException(403, "非项目成员");
        }
    }

    @Override
    public void requireAtLeast(Long userId, Long projectId, ProjectMemberRole minRole) {
        ProjectMemberRole r = getMyRole(userId, projectId);
        if (r == null) {
            ProjectMember pm = projectMemberMapper.selectOne(Wrappers.<ProjectMember>lambdaQuery()
                    .eq(ProjectMember::getProjectId, projectId)
                    .eq(ProjectMember::getUserId, userId)
                    .last("LIMIT 1")
            );
            if (pm != null && Objects.equals(pm.getDisabled(), 1)) throw new ApiException(403, "成员已被禁用");
            throw new ApiException(403, "非项目成员");
        }
        if (rank(r) < rank(minRole)) throw new ApiException(403, "权限不足");
    }

    @Override
    public ProjectMembersResponse members(Long userId, Long projectId) {
        requireMember(userId, projectId);
        ProjectMemberRole myRole = getMyRole(userId, projectId);
        return new ProjectMembersResponse(myRole, projectMemberMapper.listMemberItems(projectId));
    }

    @Override
    @Transactional
    public ProjectInviteCreateResponse invite(Long inviterUserId, Long projectId, String email, ProjectMemberRole role) {
        requireAtLeast(inviterUserId, projectId, ProjectMemberRole.OWNER);
        if (email == null || email.isBlank()) throw new ApiException(400, "邮箱不能为空");

        String normalizedEmail = email.trim().toLowerCase();
        ProjectMemberRole finalRole = role == null ? ProjectMemberRole.VIEWER : role;

        User invitee = userService.getOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, normalizedEmail));
        if (invitee != null) {
            ProjectMemberRole existsRole = projectMemberMapper.findRole(projectId, invitee.getId());
            if (existsRole != null) throw new ApiException(400, "该用户已是项目成员");
        }

        String token = TokenUtils.newOpaqueToken();
        String tokenHash = TokenUtils.sha256Hex(token);
        int expireDays = inviteProperties == null || inviteProperties.getExpireDays() == null ? 7 : Math.max(1, inviteProperties.getExpireDays());
        int maxUses = inviteProperties == null || inviteProperties.getMaxUses() == null ? 1 : Math.max(1, inviteProperties.getMaxUses());
        LocalDateTime expireTime = LocalDateTime.now().plusDays(expireDays);

        projectInviteMapper.update(null, Wrappers.<ProjectInvite>lambdaUpdate()
                .eq(ProjectInvite::getProjectId, projectId)
                .eq(ProjectInvite::getEmail, normalizedEmail)
                .eq(ProjectInvite::getStatus, ProjectInviteStatus.PENDING)
                .set(ProjectInvite::getStatus, ProjectInviteStatus.CANCELED)
                .set(ProjectInvite::getHandledTime, LocalDateTime.now())
        );

        ProjectInvite inv = new ProjectInvite();
        inv.setProjectId(projectId);
        inv.setInviterUserId(inviterUserId);
        inv.setEmail(normalizedEmail);
        inv.setRole(finalRole);
        inv.setTokenHash(tokenHash);
        inv.setStatus(ProjectInviteStatus.PENDING);
        inv.setExpireTime(expireTime);
        inv.setMaxUses(maxUses);
        inv.setUsedCount(0);
        projectInviteMapper.insert(inv);

        String baseUrl = webProperties == null ? null : webProperties.getBaseUrl();
        String origin = (baseUrl == null || baseUrl.isBlank()) ? "http://localhost:5173" : baseUrl.trim();
        if (origin.endsWith("/")) origin = origin.substring(0, origin.length() - 1);
        String link = origin + "/project-invite?token=" + token;
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(mailProperties.getFrom());
            msg.setTo(normalizedEmail);
            msg.setSubject("DevTool Copilot 项目邀请");
            msg.setText("你收到一个项目邀请，点击链接加入（有效期 " + expireDays + " 天）：\n" + link);
            mailSender.send(msg);
        } catch (Exception ignored) {
        }

        notificationService.create(inviterUserId,
                "PROJECT_INVITE_SENT",
                "邀请已发送",
                "已邀请 " + normalizedEmail + " 加入项目（" + finalRole + "）",
                "{\"projectId\":" + projectId + ",\"email\":\"" + normalizedEmail + "\",\"role\":\"" + finalRole + "\"}",
                projectId,
                null,
                null,
                "PROJECT_INVITE_SENT:" + projectId + ":" + normalizedEmail
        );
        if (invitee != null) {
            notificationService.create(invitee.getId(),
                    "PROJECT_INVITE_RECEIVED",
                    "收到项目邀请",
                    "你被邀请加入项目（" + finalRole + "）",
                    "{\"projectId\":" + projectId + ",\"inviteId\":" + inv.getId() + ",\"inviteLink\":\"" + link + "\"}",
                    projectId,
                    null,
                    null,
                    "PROJECT_INVITE_RECEIVED:" + projectId + ":" + normalizedEmail
            );
        }

        String auditDetail = "{\"email\":\"" + normalizedEmail + "\",\"role\":\"" + finalRole + "\"}";
        addActivity(projectId, inviterUserId, "MEMBER_INVITED", auditDetail);
        if (projectAuditService != null) {
            projectAuditService.record(projectId, inviterUserId, "MEMBER_INVITED", "PROJECT_MEMBER", null, "邀请成员", auditDetail);
        }
        return new ProjectInviteCreateResponse(inv.getId(), token, link);
    }

    @Override
    public List<ProjectInviteItem> invites(Long userId, Long projectId) {
        requireAtLeast(userId, projectId, ProjectMemberRole.OWNER);
        List<ProjectInvite> list = projectInviteMapper.selectList(
                Wrappers.<ProjectInvite>lambdaQuery()
                        .eq(ProjectInvite::getProjectId, projectId)
                        .orderByDesc(ProjectInvite::getId)
        );
        return list.stream()
                .map(i -> new ProjectInviteItem(i.getId(), i.getEmail(), i.getRole(), i.getStatus(), i.getExpireTime(), i.getCreateTime()))
                .toList();
    }

    @Override
    @Transactional
    public Long acceptInvite(Long userId, String token) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (token == null || token.isBlank()) throw new ApiException(400, "token不能为空");

        User me = userService.getById(userId);
        if (me == null) throw new ApiException(401, "未登录");
        String myEmail = (me.getEmail() == null ? "" : me.getEmail().trim().toLowerCase());

        String tokenHash = TokenUtils.sha256Hex(token);
        ProjectInvite inv = projectInviteMapper.selectOne(Wrappers.<ProjectInvite>lambdaQuery().eq(ProjectInvite::getTokenHash, tokenHash));
        if (inv == null) throw new ApiException(400, "邀请无效");
        if (inv.getStatus() != ProjectInviteStatus.PENDING) throw new ApiException(400, "邀请已处理");
        Integer maxUses = inv.getMaxUses();
        Integer usedCount = inv.getUsedCount();
        if (maxUses != null && usedCount != null && usedCount >= maxUses) throw new ApiException(400, "邀请次数已用完");

        if (inv.getExpireTime() != null && inv.getExpireTime().isBefore(LocalDateTime.now())) {
            inv.setStatus(ProjectInviteStatus.EXPIRED);
            inv.setHandledTime(LocalDateTime.now());
            projectInviteMapper.updateById(inv);
            throw new ApiException(400, "邀请已过期");
        }
        if (!myEmail.equals(inv.getEmail())) throw new ApiException(403, "该邀请不属于当前账号");

        ProjectMember exists = projectMemberMapper.selectOne(
                Wrappers.<ProjectMember>lambdaQuery().eq(ProjectMember::getProjectId, inv.getProjectId()).eq(ProjectMember::getUserId, userId)
        );
        if (exists == null) {
            ProjectMember m = new ProjectMember();
            m.setProjectId(inv.getProjectId());
            m.setUserId(userId);
            m.setRole(inv.getRole() == null ? ProjectMemberRole.VIEWER : inv.getRole());
            projectMemberMapper.insert(m);
        }

        inv.setStatus(ProjectInviteStatus.ACCEPTED);
        inv.setAcceptedUserId(userId);
        inv.setHandledTime(LocalDateTime.now());
        inv.setUsedCount((usedCount == null ? 0 : usedCount) + 1);
        projectInviteMapper.updateById(inv);

        notificationService.create(inv.getInviterUserId(),
                "PROJECT_INVITE_ACCEPTED",
                "邀请已被接受",
                myEmail + " 已加入项目",
                "{\"projectId\":" + inv.getProjectId() + ",\"email\":\"" + myEmail + "\"}",
                inv.getProjectId(),
                null,
                null,
                "PROJECT_INVITE_ACCEPTED:" + inv.getProjectId() + ":" + myEmail
        );
        addActivity(inv.getProjectId(), userId, "MEMBER_JOINED", "{\"email\":\"" + myEmail + "\"}");
        return inv.getProjectId();
    }

    @Override
    @Transactional
    public Long rejectInvite(Long userId, String token) {
        if (userId == null) throw new ApiException(401, "未登录");
        if (token == null || token.isBlank()) throw new ApiException(400, "token不能为空");

        User me = userService.getById(userId);
        if (me == null) throw new ApiException(401, "未登录");
        String myEmail = (me.getEmail() == null ? "" : me.getEmail().trim().toLowerCase());

        String tokenHash = TokenUtils.sha256Hex(token);
        ProjectInvite inv = projectInviteMapper.selectOne(Wrappers.<ProjectInvite>lambdaQuery().eq(ProjectInvite::getTokenHash, tokenHash));
        if (inv == null) throw new ApiException(400, "邀请无效");
        if (inv.getStatus() != ProjectInviteStatus.PENDING) throw new ApiException(400, "邀请已处理");
        if (!myEmail.equals(inv.getEmail())) throw new ApiException(403, "该邀请不属于当前账号");

        inv.setStatus(ProjectInviteStatus.REJECTED);
        inv.setAcceptedUserId(userId);
        inv.setHandledTime(LocalDateTime.now());
        projectInviteMapper.updateById(inv);
        notificationService.create(inv.getInviterUserId(),
                "PROJECT_INVITE_REJECTED",
                "邀请被拒绝",
                myEmail + " 拒绝了项目邀请",
                "{\"projectId\":" + inv.getProjectId() + ",\"email\":\"" + myEmail + "\"}",
                inv.getProjectId(),
                null,
                null,
                "PROJECT_INVITE_REJECTED:" + inv.getProjectId() + ":" + myEmail
        );
        return inv.getProjectId();
    }

    @Override
    @Transactional
    public ProjectInviteCreateResponse reissueInvite(Long ownerUserId, Long projectId, Long inviteId) {
        requireAtLeast(ownerUserId, projectId, ProjectMemberRole.OWNER);
        if (inviteId == null) throw new ApiException(400, "inviteId不能为空");
        ProjectInvite inv = projectInviteMapper.selectOne(Wrappers.<ProjectInvite>lambdaQuery()
                .eq(ProjectInvite::getId, inviteId)
                .eq(ProjectInvite::getProjectId, projectId)
                .last("LIMIT 1")
        );
        if (inv == null) throw new ApiException(404, "邀请不存在");
        if (inv.getStatus() != ProjectInviteStatus.PENDING) throw new ApiException(400, "邀请已处理");

        String token = TokenUtils.newOpaqueToken();
        String tokenHash = TokenUtils.sha256Hex(token);
        int expireDays = inviteProperties == null || inviteProperties.getExpireDays() == null ? 7 : Math.max(1, inviteProperties.getExpireDays());
        LocalDateTime expireTime = LocalDateTime.now().plusDays(expireDays);

        inv.setTokenHash(tokenHash);
        inv.setExpireTime(expireTime);
        inv.setUsedCount(0);
        inv.setHandledTime(null);
        projectInviteMapper.updateById(inv);
        projectInviteMapper.update(null, Wrappers.<ProjectInvite>lambdaUpdate()
                .eq(ProjectInvite::getId, inviteId)
                .set(ProjectInvite::getCreateTime, LocalDateTime.now())
        );

        String baseUrl = webProperties == null ? null : webProperties.getBaseUrl();
        String origin = (baseUrl == null || baseUrl.isBlank()) ? "http://localhost:5173" : baseUrl.trim();
        if (origin.endsWith("/")) origin = origin.substring(0, origin.length() - 1);
        String link = origin + "/project-invite?token=" + token;

        User invitee = userService.getOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, inv.getEmail()));
        if (invitee != null) {
            notificationService.create(invitee.getId(),
                    "PROJECT_INVITE_REISSUED",
                    "邀请链接已更新",
                    "项目邀请链接已更新，请使用新链接加入",
                    "{\"projectId\":" + projectId + ",\"inviteId\":" + inviteId + ",\"inviteLink\":\"" + link + "\"}",
                    projectId,
                    null,
                    null,
                    "PROJECT_INVITE_RECEIVED:" + projectId + ":" + inv.getEmail()
            );
        }
        String reissueDetail = "{\"inviteId\":" + inviteId + ",\"email\":\"" + inv.getEmail() + "\"}";
        addActivity(projectId, ownerUserId, "MEMBER_INVITE_REISSUED", reissueDetail);
        if (projectAuditService != null) {
            projectAuditService.record(projectId, ownerUserId, "MEMBER_INVITE_REISSUED", "PROJECT_INVITE", inviteId, "更新邀请链接", reissueDetail);
        }
        return new ProjectInviteCreateResponse(inviteId, token, link);
    }

    @Override
    @Transactional
    public void cancelInvite(Long ownerUserId, Long projectId, Long inviteId) {
        requireAtLeast(ownerUserId, projectId, ProjectMemberRole.OWNER);
        if (inviteId == null) throw new ApiException(400, "inviteId不能为空");
        ProjectInvite inv = projectInviteMapper.selectOne(Wrappers.<ProjectInvite>lambdaQuery()
                .eq(ProjectInvite::getId, inviteId)
                .eq(ProjectInvite::getProjectId, projectId)
                .last("LIMIT 1")
        );
        if (inv == null) throw new ApiException(404, "邀请不存在");
        if (inv.getStatus() != ProjectInviteStatus.PENDING) throw new ApiException(400, "邀请已处理");

        inv.setStatus(ProjectInviteStatus.CANCELED);
        inv.setHandledTime(LocalDateTime.now());
        projectInviteMapper.updateById(inv);

        User invitee = userService.getOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, inv.getEmail()));
        if (invitee != null) {
            notificationService.create(invitee.getId(),
                    "PROJECT_INVITE_CANCELED",
                    "邀请已取消",
                    "项目邀请已取消（projectId=" + projectId + "）",
                    "{\"projectId\":" + projectId + ",\"inviteId\":" + inviteId + "}",
                    projectId,
                    null,
                    null,
                    "PROJECT_INVITE_RECEIVED:" + projectId + ":" + inv.getEmail()
            );
        }
        String cancelDetail = "{\"inviteId\":" + inviteId + ",\"email\":\"" + inv.getEmail() + "\"}";
        addActivity(projectId, ownerUserId, "MEMBER_INVITE_CANCELED", cancelDetail);
        if (projectAuditService != null) {
            projectAuditService.record(projectId, ownerUserId, "MEMBER_INVITE_CANCELED", "PROJECT_INVITE", inviteId, "取消邀请", cancelDetail);
        }
    }

    @Override
    @Transactional
    public void removeMember(Long ownerUserId, Long projectId, Long memberUserId) {
        requireAtLeast(ownerUserId, projectId, ProjectMemberRole.OWNER);
        if (memberUserId == null) throw new ApiException(400, "成员不能为空");
        if (memberUserId.equals(ownerUserId)) throw new ApiException(400, "不能移除自己");

        int n = projectMemberMapper.delete(
                Wrappers.<ProjectMember>lambdaQuery().eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getUserId, memberUserId)
        );
        if (n <= 0) throw new ApiException(404, "成员不存在");
        notificationService.create(memberUserId,
                "PROJECT_MEMBER_REMOVED",
                "已移出项目",
                "你已被移出项目（projectId=" + projectId + "）",
                "{\"projectId\":" + projectId + "}",
                projectId,
                null,
                null,
                "PROJECT_MEMBER_REMOVED:" + projectId + ":" + memberUserId
        );
        String removeDetail = "{\"userId\":" + memberUserId + "}";
        addActivity(projectId, ownerUserId, "MEMBER_REMOVED", removeDetail);
        if (projectAuditService != null) {
            projectAuditService.record(projectId, ownerUserId, "MEMBER_REMOVED", "PROJECT_MEMBER", memberUserId, "移除成员", removeDetail);
        }
    }

    @Override
    @Transactional
    public void updateMemberRole(Long ownerUserId, Long projectId, Long memberUserId, ProjectMemberRole role) {
        requireAtLeast(ownerUserId, projectId, ProjectMemberRole.OWNER);
        if (memberUserId == null) throw new ApiException(400, "成员不能为空");
        if (memberUserId.equals(ownerUserId)) throw new ApiException(400, "不能修改自己角色");
        if (role == null) throw new ApiException(400, "role不能为空");
        if (role == ProjectMemberRole.OWNER) throw new ApiException(400, "请使用转让所有权");

        ProjectMember m = projectMemberMapper.selectOne(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, memberUserId)
                .last("LIMIT 1")
        );
        if (m == null) throw new ApiException(404, "成员不存在");
        if (m.getRole() == ProjectMemberRole.OWNER) throw new ApiException(400, "不能修改 OWNER 角色");
        if (Objects.equals(m.getRole(), role)) return;

        m.setRole(role);
        projectMemberMapper.updateById(m);
        notificationService.create(memberUserId,
                "PROJECT_MEMBER_ROLE_CHANGED",
                "项目角色已变更",
                "你的项目角色已更新为 " + role,
                "{\"projectId\":" + projectId + ",\"role\":\"" + role + "\"}",
                projectId,
                null,
                null,
                "PROJECT_MEMBER_ROLE_CHANGED:" + projectId + ":" + memberUserId
        );
        String roleDetail = "{\"userId\":" + memberUserId + ",\"role\":\"" + role + "\"}";
        addActivity(projectId, ownerUserId, "MEMBER_ROLE_CHANGED", roleDetail);
        if (projectAuditService != null) {
            projectAuditService.record(projectId, ownerUserId, "MEMBER_ROLE_CHANGED", "PROJECT_MEMBER", memberUserId, "调整成员角色", roleDetail);
        }
    }

    @Override
    @Transactional
    public void setMemberDisabled(Long ownerUserId, Long projectId, Long memberUserId, boolean disabled) {
        requireAtLeast(ownerUserId, projectId, ProjectMemberRole.OWNER);
        if (memberUserId == null) throw new ApiException(400, "成员不能为空");
        if (memberUserId.equals(ownerUserId)) throw new ApiException(400, "不能操作自己");

        ProjectMember m = projectMemberMapper.selectOne(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, memberUserId)
                .last("LIMIT 1")
        );
        if (m == null) throw new ApiException(404, "成员不存在");
        if (m.getRole() == ProjectMemberRole.OWNER) throw new ApiException(400, "不能禁用 OWNER，请先转让所有权");

        int target = disabled ? 1 : 0;
        if (Objects.equals(m.getDisabled(), target)) return;
        m.setDisabled(target);
        m.setDisabledTime(disabled ? LocalDateTime.now() : null);
        projectMemberMapper.updateById(m);

        notificationService.create(memberUserId,
                disabled ? "PROJECT_MEMBER_DISABLED" : "PROJECT_MEMBER_ENABLED",
                disabled ? "已被禁用" : "已被启用",
                disabled ? "你已被禁用（projectId=" + projectId + "）" : "你已被启用（projectId=" + projectId + "）",
                "{\"projectId\":" + projectId + "}",
                projectId,
                null,
                null,
                (disabled ? "PROJECT_MEMBER_DISABLED:" : "PROJECT_MEMBER_ENABLED:") + projectId + ":" + memberUserId
        );
        String disDetail = "{\"userId\":" + memberUserId + ",\"disabled\":" + (disabled ? 1 : 0) + "}";
        addActivity(projectId, ownerUserId, disabled ? "MEMBER_DISABLED" : "MEMBER_ENABLED", disDetail);
        if (projectAuditService != null) {
            projectAuditService.record(projectId, ownerUserId, disabled ? "MEMBER_DISABLED" : "MEMBER_ENABLED", "PROJECT_MEMBER", memberUserId,
                    disabled ? "禁用成员" : "启用成员", disDetail);
        }
    }

    @Override
    @Transactional
    public void transferOwnership(Long ownerUserId, Long projectId, Long newOwnerUserId) {
        requireAtLeast(ownerUserId, projectId, ProjectMemberRole.OWNER);
        if (newOwnerUserId == null) throw new ApiException(400, "成员不能为空");
        if (newOwnerUserId.equals(ownerUserId)) throw new ApiException(400, "不能转让给自己");

        ProjectMember target = projectMemberMapper.selectOne(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, newOwnerUserId)
                .last("LIMIT 1")
        );
        if (target == null) throw new ApiException(404, "成员不存在");
        if (Objects.equals(target.getDisabled(), 1)) throw new ApiException(400, "该成员已被禁用");

        int n = projectMapper.update(null, Wrappers.<com.devtoolcopilot.project.entity.Project>lambdaUpdate()
                .eq(com.devtoolcopilot.project.entity.Project::getId, projectId)
                .eq(com.devtoolcopilot.project.entity.Project::getUserId, ownerUserId)
                .set(com.devtoolcopilot.project.entity.Project::getUserId, newOwnerUserId)
        );
        if (n <= 0) throw new ApiException(400, "转让失败");

        projectMemberMapper.update(null, Wrappers.<ProjectMember>lambdaUpdate()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, ownerUserId)
                .set(ProjectMember::getRole, ProjectMemberRole.DEVELOPER)
        );
        projectMemberMapper.update(null, Wrappers.<ProjectMember>lambdaUpdate()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, newOwnerUserId)
                .set(ProjectMember::getRole, ProjectMemberRole.OWNER)
        );

        notificationService.create(newOwnerUserId,
                "PROJECT_OWNER_TRANSFERRED_IN",
                "你已成为 OWNER",
                "你已成为项目 OWNER（projectId=" + projectId + "）",
                "{\"projectId\":" + projectId + "}",
                projectId,
                null,
                null,
                "PROJECT_OWNER_TRANSFERRED_IN:" + projectId + ":" + newOwnerUserId
        );
        notificationService.create(ownerUserId,
                "PROJECT_OWNER_TRANSFERRED_OUT",
                "已转让所有权",
                "你已转让项目所有权（projectId=" + projectId + "）",
                "{\"projectId\":" + projectId + ",\"newOwnerUserId\":" + newOwnerUserId + "}",
                projectId,
                null,
                null,
                "PROJECT_OWNER_TRANSFERRED_OUT:" + projectId + ":" + ownerUserId
        );
        String transferDetail = "{\"newOwnerUserId\":" + newOwnerUserId + "}";
        addActivity(projectId, ownerUserId, "MEMBER_OWNER_TRANSFERRED", transferDetail);
        if (projectAuditService != null) {
            projectAuditService.record(projectId, ownerUserId, "MEMBER_OWNER_TRANSFERRED", "PROJECT_MEMBER", newOwnerUserId, "转让所有权", transferDetail);
        }
    }

    @Override
    @Transactional
    public void leaveProject(Long userId, Long projectId) {
        requireMember(userId, projectId);
        ProjectMemberRole myRole = getMyRole(userId, projectId);
        if (myRole == ProjectMemberRole.OWNER) throw new ApiException(400, "OWNER 需先转让所有权");

        int n = projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId)
        );
        if (n <= 0) throw new ApiException(404, "成员不存在");
        String leftDetail = "{\"userId\":" + userId + "}";
        addActivity(projectId, userId, "MEMBER_LEFT", leftDetail);
        if (projectAuditService != null) {
            projectAuditService.record(projectId, userId, "MEMBER_LEFT", "PROJECT_MEMBER", userId, "成员退出", leftDetail);
        }
    }

    @Override
    public com.devtoolcopilot.project.dto.ProjectMembersExportResponse exportMembers(Long userId, Long projectId) {
        requireMember(userId, projectId);
        List<com.devtoolcopilot.project.dto.ProjectMemberItem> list = projectMemberMapper.listMemberItems(projectId);
        DateTimeFormatter dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder sb = new StringBuilder();
        sb.append(csv("userId")).append(',')
                .append(csv("username")).append(',')
                .append(csv("email")).append(',')
                .append(csv("role")).append(',')
                .append(csv("disabled")).append(',')
                .append(csv("disabledTime")).append(',')
                .append(csv("online")).append(',')
                .append(csv("lastSeenAt")).append(',')
                .append(csv("joinedAt")).append('\n');
        for (var m : list) {
            String dis = Objects.equals(m.getDisabled(), 1) ? "1" : "0";
            String disAt = m.getDisabledTime() == null ? "" : dt.format(m.getDisabledTime());
            String online = Objects.equals(m.getOnline(), 1) ? "1" : "0";
            String lastSeen = m.getLastSeenAt() == null ? "" : dt.format(m.getLastSeenAt());
            String joined = m.getJoinedAt() == null ? "" : dt.format(m.getJoinedAt());
            sb.append(csv(m.getUserId())).append(',')
                    .append(csv(m.getUsername())).append(',')
                    .append(csv(m.getEmail())).append(',')
                    .append(csv(m.getRole() == null ? "" : m.getRole().name())).append(',')
                    .append(csv(dis)).append(',')
                    .append(csv(disAt)).append(',')
                    .append(csv(online)).append(',')
                    .append(csv(lastSeen)).append(',')
                    .append(csv(joined)).append('\n');
        }

        var resp = new com.devtoolcopilot.project.dto.ProjectMembersExportResponse();
        resp.setFilename("members_project_" + projectId + ".csv");
        resp.setContent(sb.toString());
        addActivity(projectId, userId, "MEMBERS_EXPORT_CSV", "{}");
        if (projectAuditService != null) {
            projectAuditService.record(projectId, userId, "MEMBERS_EXPORT_CSV", "PROJECT", projectId, "导出成员列表", "{}");
        }
        return resp;
    }

    @Override
    public List<ProjectActivityItem> activities(Long userId, Long projectId, Integer limit) {
        requireMember(userId, projectId);
        int l = limit == null ? 100 : Math.max(1, Math.min(limit, 300));
        return projectActivityMapper.listItems(projectId, l);
    }

    @Override
    public void deleteActivity(Long ownerUserId, Long projectId, Long activityId) {
        requireAtLeast(ownerUserId, projectId, ProjectMemberRole.OWNER);
        if (activityId == null) throw new ApiException(400, "activityId不能为空");
        int deleted = projectActivityMapper.delete(Wrappers.<ProjectActivity>lambdaQuery()
                .eq(ProjectActivity::getProjectId, projectId)
                .eq(ProjectActivity::getId, activityId)
        );
        if (deleted <= 0) throw new ApiException(404, "动态不存在");
    }

    @Override
    public int clearActivities(Long ownerUserId, Long projectId) {
        requireAtLeast(ownerUserId, projectId, ProjectMemberRole.OWNER);
        return projectActivityMapper.delete(Wrappers.<ProjectActivity>lambdaQuery().eq(ProjectActivity::getProjectId, projectId));
    }

    @Override
    public void addActivity(Long projectId, Long actorUserId, String type, String detail) {
        if (projectId == null || type == null || type.isBlank()) return;
        try {
            ProjectActivity a = new ProjectActivity();
            a.setProjectId(projectId);
            a.setActorUserId(actorUserId);
            a.setType(type);
            a.setDetail(detail);
            projectActivityMapper.insert(a);
            realtimeCollabService.broadcast(projectId, actorUserId, type, detail);
        } catch (Exception ignored) {
        }
    }

    private static int rank(ProjectMemberRole r) {
        if (r == null) return 0;
        if (r == ProjectMemberRole.OWNER) return 3;
        if (r == ProjectMemberRole.DEVELOPER) return 2;
        return 1;
    }

    private String csv(Object raw) {
        String s = raw == null ? "" : String.valueOf(raw);
        s = s.replace("\"", "\"\"");
        return "\"" + s + "\"";
    }
}
