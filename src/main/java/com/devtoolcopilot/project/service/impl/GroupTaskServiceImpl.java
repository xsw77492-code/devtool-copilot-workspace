package com.devtoolcopilot.project.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.project.dto.GroupTaskBoardResponse;
import com.devtoolcopilot.project.dto.GroupTaskCreateRequest;
import com.devtoolcopilot.project.dto.GroupTaskItem;
import com.devtoolcopilot.project.dto.GroupTaskUpdateRequest;
import com.devtoolcopilot.project.entity.GroupTask;
import com.devtoolcopilot.project.entity.TeamGroup;
import com.devtoolcopilot.project.entity.TeamGroupActivity;
import com.devtoolcopilot.project.entity.TeamGroupMember;
import com.devtoolcopilot.project.mapper.GroupTaskMapper;
import com.devtoolcopilot.project.mapper.TeamGroupActivityMapper;
import com.devtoolcopilot.project.mapper.TeamGroupMapper;
import com.devtoolcopilot.project.mapper.TeamGroupMemberMapper;
import com.devtoolcopilot.project.service.GroupTaskService;
import com.devtoolcopilot.realtime.service.RealtimeCollabService;
import com.devtoolcopilot.user.entity.User;
import com.devtoolcopilot.user.service.UserService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class GroupTaskServiceImpl implements GroupTaskService {

    private static final Set<String> PRIORITIES = Set.of("HIGH", "MEDIUM", "LOW");
    private static final Set<String> STATUSES = Set.of("TODO", "DOING", "DONE");

    private final GroupTaskMapper groupTaskMapper;
    private final TeamGroupMapper teamGroupMapper;
    private final TeamGroupMemberMapper teamGroupMemberMapper;
    private final TeamGroupActivityMapper teamGroupActivityMapper;
    private final UserService userService;
    private final RealtimeCollabService realtimeCollabService;

    public GroupTaskServiceImpl(GroupTaskMapper groupTaskMapper,
                                TeamGroupMapper teamGroupMapper,
                                TeamGroupMemberMapper teamGroupMemberMapper,
                                TeamGroupActivityMapper teamGroupActivityMapper,
                                UserService userService,
                                RealtimeCollabService realtimeCollabService) {
        this.groupTaskMapper = groupTaskMapper;
        this.teamGroupMapper = teamGroupMapper;
        this.teamGroupMemberMapper = teamGroupMemberMapper;
        this.teamGroupActivityMapper = teamGroupActivityMapper;
        this.userService = userService;
        this.realtimeCollabService = realtimeCollabService;
    }

    @Override
    public GroupTaskBoardResponse board(Long userId, Long groupId) {
        requireMember(userId, groupId);
        List<GroupTaskItem> items = toItems(selectAll(groupId));
        LocalDateTime now = LocalDateTime.now();
        List<GroupTaskItem> todo = new ArrayList<>();
        List<GroupTaskItem> doing = new ArrayList<>();
        List<GroupTaskItem> done = new ArrayList<>();
        int overdue = 0;
        for (GroupTaskItem it : items) {
            boolean isOverdue = it.getDoneTime() == null && it.getDueTime() != null && it.getDueTime().isBefore(now);
            it.setOverdue(isOverdue);
            if (isOverdue) overdue++;
            if ("DONE".equals(it.getStatus())) done.add(it);
            else if ("DOING".equals(it.getStatus())) doing.add(it);
            else todo.add(it);
        }
        int total = items.size();
        int rate = total == 0 ? 0 : (int) Math.round(done.size() * 100.0 / total);
        return new GroupTaskBoardResponse(todo, doing, done, total, doing.size(), done.size(), overdue, rate);
    }

    @Override
    public List<GroupTaskItem> listTasks(Long userId, Long groupId) {
        requireMember(userId, groupId);
        return toItems(selectAll(groupId));
    }

    @Override
    public GroupTaskItem create(Long userId, Long groupId, GroupTaskCreateRequest req) {
        requireMember(userId, groupId);
        String title = req == null ? "" : trimToNull(req.getTitle());
        if (title == null) throw new ApiException(400, "GROUP_TASK_TITLE_REQUIRED");
        if (title.length() > 200) throw new ApiException(400, "GROUP_TASK_TITLE_TOO_LONG");

        String priority = normalizePriority(req.getPriority());
        Long assigneeId = req.getAssigneeId();
        if (assigneeId != null) requireMember(assigneeId, groupId);

        GroupTask task = new GroupTask();
        task.setGroupId(groupId);
        task.setTitle(title);
        task.setDescription(trimToNull(req.getDescription()));
        task.setPriority(priority);
        task.setStatus("TODO");
        task.setAssigneeId(assigneeId);
        task.setCreatedBy(userId);
        task.setDueTime(req.getDueTime());
        task.setSort(0L);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        groupTaskMapper.insert(task);

        String target = assigneeUsername(assigneeId);
        recordActivity(groupId, userId, "GROUP_TASK_CREATED",
                "创建了任务「" + title + "」" + (target == null ? "" : "，指派给 " + target));
        return toItem(task);
    }

    @Override
    public void update(Long userId, Long groupId, Long taskId, GroupTaskUpdateRequest req) {
        requireMember(userId, groupId);
        GroupTask task = requireTask(groupId, taskId);
        requireEditable(userId, groupId, task);
        if (req == null) throw new ApiException(400, "GROUP_TASK_UPDATE_REQUIRED");

        if (req.getTitle() != null) {
            String title = trimToNull(req.getTitle());
            if (title == null) throw new ApiException(400, "GROUP_TASK_TITLE_REQUIRED");
            if (title.length() > 200) throw new ApiException(400, "GROUP_TASK_TITLE_TOO_LONG");
            task.setTitle(title);
        }
        if (req.getDescription() != null) task.setDescription(trimToNull(req.getDescription()));
        if (req.getPriority() != null) task.setPriority(normalizePriority(req.getPriority()));
        if (req.getDueTime() != null) task.setDueTime(req.getDueTime());
        task.setUpdateTime(LocalDateTime.now());
        groupTaskMapper.updateById(task);
        recordActivity(groupId, userId, "GROUP_TASK_UPDATED", "更新了任务「" + task.getTitle() + "」");
    }

    @Override
    public void assign(Long userId, Long groupId, Long taskId, Long assigneeId) {
        requireMember(userId, groupId);
        GroupTask task = requireTask(groupId, taskId);
        if (!isOwner(userId, groupId) && !Objects.equals(task.getCreatedBy(), userId)) {
            throw new ApiException(403, "GROUP_TASK_ASSIGN_FORBIDDEN");
        }
        if (assigneeId == null) throw new ApiException(400, "GROUP_TASK_ASSIGNEE_REQUIRED");
        requireMember(assigneeId, groupId);
        task.setAssigneeId(assigneeId);
        task.setUpdateTime(LocalDateTime.now());
        groupTaskMapper.updateById(task);
        recordActivity(groupId, userId, "GROUP_TASK_ASSIGNED",
                "将任务「" + task.getTitle() + "」指派给 " + assigneeUsername(assigneeId));
    }

    @Override
    public void claim(Long userId, Long groupId, Long taskId) {
        requireMember(userId, groupId);
        GroupTask task = requireTask(groupId, taskId);
        if (task.getAssigneeId() != null && !Objects.equals(task.getAssigneeId(), userId)) {
            throw new ApiException(409, "GROUP_TASK_ALREADY_ASSIGNED");
        }
        task.setAssigneeId(userId);
        task.setUpdateTime(LocalDateTime.now());
        groupTaskMapper.updateById(task);
        recordActivity(groupId, userId, "GROUP_TASK_CLAIMED", "认领了任务「" + task.getTitle() + "」");
    }

    @Override
    public void changeStatus(Long userId, Long groupId, Long taskId, String status) {
        requireMember(userId, groupId);
        GroupTask task = requireTask(groupId, taskId);
        String s = status == null ? "" : status.trim().toUpperCase();
        if (!STATUSES.contains(s)) throw new ApiException(400, "GROUP_TASK_STATUS_INVALID");
        requireEditable(userId, groupId, task);

        task.setStatus(s);
        if ("DONE".equals(s)) {
            task.setDoneTime(LocalDateTime.now());
        } else if (task.getDoneTime() != null) {
            task.setDoneTime(null);
        }
        task.setUpdateTime(LocalDateTime.now());
        groupTaskMapper.updateById(task);
        String action = "DONE".equals(s) ? "完成了任务「" + task.getTitle() + "」"
                : "将任务「" + task.getTitle() + "」标记为进行中";
        recordActivity(groupId, userId, "DONE".equals(s) ? "GROUP_TASK_DONE" : "GROUP_TASK_UPDATED", action);
    }

    @Override
    public void delete(Long userId, Long groupId, Long taskId) {
        requireMember(userId, groupId);
        GroupTask task = requireTask(groupId, taskId);
        if (!isOwner(userId, groupId) && !Objects.equals(task.getCreatedBy(), userId)) {
            throw new ApiException(403, "GROUP_TASK_DELETE_FORBIDDEN");
        }
        groupTaskMapper.deleteById(taskId);
        recordActivity(groupId, userId, "GROUP_TASK_DELETED", "删除了任务「" + task.getTitle() + "」");
    }

    // ---------- 内部工具 ----------

    private List<GroupTask> selectAll(Long groupId) {
        return groupTaskMapper.selectList(Wrappers.<GroupTask>lambdaQuery()
                .eq(GroupTask::getGroupId, groupId)
                .orderByAsc(GroupTask::getSort)
                .orderByDesc(GroupTask::getCreateTime));
    }

    private GroupTask requireTask(Long groupId, Long taskId) {
        if (taskId == null) throw new ApiException(400, "GROUP_TASK_ID_REQUIRED");
        GroupTask task = groupTaskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getGroupId(), groupId)) {
            throw new ApiException(404, "GROUP_TASK_NOT_FOUND");
        }
        return task;
    }

    /** 可编辑：群主 / 任务创建者 / 当前指派人 */
    private void requireEditable(Long userId, Long groupId, GroupTask task) {
        if (isOwner(userId, groupId)
                || Objects.equals(task.getCreatedBy(), userId)
                || Objects.equals(task.getAssigneeId(), userId)) {
            return;
        }
        throw new ApiException(403, "GROUP_TASK_EDIT_FORBIDDEN");
    }

    private boolean isOwner(Long userId, Long groupId) {
        TeamGroup group = teamGroupMapper.selectById(groupId);
        return group != null && Objects.equals(group.getOwnerUserId(), userId);
    }

    private TeamGroup requireMember(Long userId, Long groupId) {
        if (userId == null) throw new ApiException(401, "UNAUTHENTICATED");
        TeamGroup group = requireGroup(groupId);
        if (isOwner(userId, groupId)) return group;
        Long cnt = teamGroupMemberMapper.selectCount(Wrappers.<TeamGroupMember>lambdaQuery()
                .eq(TeamGroupMember::getGroupId, groupId)
                .eq(TeamGroupMember::getUserId, userId));
        if (cnt == null || cnt == 0) throw new ApiException(403, "TEAM_GROUP_MEMBER_REQUIRED");
        return group;
    }

    private TeamGroup requireGroup(Long groupId) {
        if (groupId == null) throw new ApiException(400, "TEAM_GROUP_REQUIRED");
        TeamGroup group = teamGroupMapper.selectById(groupId);
        if (group == null) throw new ApiException(404, "TEAM_GROUP_NOT_FOUND");
        if (Objects.equals(group.getArchived(), 1)) throw new ApiException(410, "TEAM_GROUP_ARCHIVED");
        return group;
    }

    private String normalizePriority(String priority) {
        String p = priority == null ? "" : priority.trim().toUpperCase();
        if (p.isEmpty()) return "MEDIUM";
        if (!PRIORITIES.contains(p)) throw new ApiException(400, "GROUP_TASK_PRIORITY_INVALID");
        return p;
    }

    private List<GroupTaskItem> toItems(List<GroupTask> tasks) {
        if (tasks == null || tasks.isEmpty()) return new ArrayList<>();
        List<Long> userIds = new ArrayList<>();
        for (GroupTask t : tasks) {
            if (t.getAssigneeId() != null) userIds.add(t.getAssigneeId());
            if (t.getCreatedBy() != null) userIds.add(t.getCreatedBy());
        }
        Map<Long, User> users = userIds.isEmpty() ? Map.of()
                : userService.listByIds(userIds).stream().collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        List<GroupTaskItem> items = new ArrayList<>(tasks.size());
        for (GroupTask t : tasks) {
            items.add(toItem(t, users));
        }
        return items;
    }

    private GroupTaskItem toItem(GroupTask t) {
        List<Long> ids = Stream.of(t.getAssigneeId(), t.getCreatedBy())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> users = ids.isEmpty() ? Map.of()
                : userService.listByIds(ids).stream().collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        return toItem(t, users);
    }

    private GroupTaskItem toItem(GroupTask t, Map<Long, User> users) {
        GroupTaskItem item = new GroupTaskItem();
        item.setId(t.getId());
        item.setGroupId(t.getGroupId());
        item.setTitle(t.getTitle());
        item.setDescription(t.getDescription());
        item.setPriority(t.getPriority());
        item.setStatus(t.getStatus());
        item.setAssigneeId(t.getAssigneeId());
        item.setAssigneeUsername(usernameOf(users.get(t.getAssigneeId())));
        item.setCreatedBy(t.getCreatedBy());
        item.setCreatorUsername(usernameOf(users.get(t.getCreatedBy())));
        item.setDueTime(t.getDueTime());
        item.setDoneTime(t.getDoneTime());
        item.setOverdue(false);
        item.setCreateTime(t.getCreateTime());
        item.setUpdateTime(t.getUpdateTime());
        return item;
    }

    private String usernameOf(User u) {
        return u == null ? null : blankTo(u.getNickname(), u.getUsername(), u.getEmail(), null);
    }

    private String assigneeUsername(Long assigneeId) {
        if (assigneeId == null) return null;
        User u = userService.getById(assigneeId);
        return u == null ? null : blankTo(u.getNickname(), u.getUsername(), u.getEmail(), null);
    }

    /** 记录群组活动并广播到团队频道（与团队协作现有模式保持一致） */
    private void recordActivity(Long groupId, Long actorUserId, String type, String detail) {
        if (groupId == null || type == null || type.isBlank()) return;
        if (detail != null && detail.length() > 250) {
            detail = detail.substring(0, 250) + "…";
        }
        TeamGroupActivity activity = new TeamGroupActivity();
        activity.setGroupId(groupId);
        activity.setActorUserId(actorUserId);
        activity.setType(type);
        activity.setDetail(detail);
        teamGroupActivityMapper.insert(activity);
        broadcastTeamEvent(groupId, actorUserId, type, detail);
    }

    private void broadcastTeamEvent(Long groupId, Long actorUserId, String type, String detail) {
        try {
            String username = null;
            if (actorUserId != null) {
                User actor = userService.getById(actorUserId);
                if (actor != null) username = blankTo(actor.getNickname(), actor.getUsername(), actor.getEmail(), null);
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("groupId", groupId);
            payload.put("type", type);
            payload.put("detail", detail == null ? "" : detail);
            payload.put("username", username == null ? "" : username);
            realtimeCollabService.broadcastTeam(actorUserId, type, payload);
        } catch (Exception ignored) {
        }
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String blankTo(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
