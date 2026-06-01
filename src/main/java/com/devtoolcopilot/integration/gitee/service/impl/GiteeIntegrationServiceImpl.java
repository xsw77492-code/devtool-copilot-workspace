package com.devtoolcopilot.integration.gitee.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.integration.gitee.client.GiteeClient;
import com.devtoolcopilot.integration.gitee.dto.GiteePanelDTO;
import com.devtoolcopilot.integration.gitee.dto.GiteeRepoConfigDTO;
import com.devtoolcopilot.integration.gitee.entity.GiteeRepoConfig;
import com.devtoolcopilot.integration.gitee.entity.TaskPrLink;
import com.devtoolcopilot.integration.gitee.mapper.GiteeRepoConfigMapper;
import com.devtoolcopilot.integration.gitee.mapper.TaskPrLinkMapper;
import com.devtoolcopilot.integration.gitee.service.GiteeIntegrationService;
import com.devtoolcopilot.project.entity.Project;
import com.devtoolcopilot.project.entity.ProjectMemberRole;
import com.devtoolcopilot.project.mapper.ProjectMapper;
import com.devtoolcopilot.project.service.ProjectCollabService;
import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.service.TaskService;
import com.devtoolcopilot.task.timeline.entity.TaskTimelineType;
import com.devtoolcopilot.task.timeline.service.TaskTimelineService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GiteeIntegrationServiceImpl implements GiteeIntegrationService {
    private static final Pattern TASK_ID_PATTERN = Pattern.compile("#(\\d+)");
    private static final Pattern PR_NUMBER_PATTERN = Pattern.compile("(\\d+)");

    private final ProjectMapper projectMapper;
    private final TaskService taskService;
    private final GiteeRepoConfigMapper configMapper;
    private final TaskPrLinkMapper linkMapper;
    private final GiteeClient giteeClient;
    private final TaskTimelineService timelineService;
    private final ProjectCollabService projectCollabService;

    public GiteeIntegrationServiceImpl(ProjectMapper projectMapper,
                                       TaskService taskService,
                                       GiteeRepoConfigMapper configMapper,
                                       TaskPrLinkMapper linkMapper,
                                       GiteeClient giteeClient,
                                       TaskTimelineService timelineService,
                                       ProjectCollabService projectCollabService) {
        this.projectMapper = projectMapper;
        this.taskService = taskService;
        this.configMapper = configMapper;
        this.linkMapper = linkMapper;
        this.giteeClient = giteeClient;
        this.timelineService = timelineService;
        this.projectCollabService = projectCollabService;
    }

    @Override
    public GiteeRepoConfigDTO getConfig(Long userId, Long projectId) {
        requireProject(userId, projectId);
        GiteeRepoConfig cfg = configMapper.selectOne(Wrappers.<GiteeRepoConfig>lambdaQuery()
                .eq(GiteeRepoConfig::getUserId, userId)
                .eq(GiteeRepoConfig::getProjectId, projectId));
        if (cfg == null) {
            return new GiteeRepoConfigDTO(projectId, null, null, false);
        }
        return new GiteeRepoConfigDTO(projectId, cfg.getOwner(), cfg.getRepo(), cfg.getAccessToken() != null && !cfg.getAccessToken().isBlank());
    }

    @Override
    public GiteeRepoConfigDTO saveConfig(Long userId, Long projectId, String owner, String repo, String accessToken) {
        Project project = requireProject(userId, projectId);
        ensureProjectWritable(project);
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("OWNER_REQUIRED");
        if (repo == null || repo.isBlank()) throw new IllegalArgumentException("REPO_REQUIRED");
        if (accessToken == null || accessToken.isBlank()) throw new IllegalArgumentException("TOKEN_REQUIRED");

        GiteeRepoConfig cfg = configMapper.selectOne(Wrappers.<GiteeRepoConfig>lambdaQuery()
                .eq(GiteeRepoConfig::getUserId, userId)
                .eq(GiteeRepoConfig::getProjectId, projectId));
        if (cfg == null) {
            cfg = new GiteeRepoConfig();
            cfg.setUserId(userId);
            cfg.setProjectId(projectId);
            cfg.setOwner(owner.trim());
            cfg.setRepo(repo.trim());
            cfg.setAccessToken(accessToken.trim());
            configMapper.insert(cfg);
        } else {
            cfg.setOwner(owner.trim());
            cfg.setRepo(repo.trim());
            cfg.setAccessToken(accessToken.trim());
            configMapper.updateById(cfg);
        }
        return new GiteeRepoConfigDTO(projectId, cfg.getOwner(), cfg.getRepo(), true);
    }

    @Override
    public Long linkTaskToPr(Long userId, Long projectId, Long taskId, String prInput) {
        Project project = requireProject(userId, projectId);
        ensureProjectWritable(project);
        if (taskId == null) throw new IllegalArgumentException("TASK_ID_REQUIRED");
        Integer prNumber = parsePrNumber(prInput);
        if (prNumber == null) throw new IllegalArgumentException("PR_REQUIRED");
        Task t = taskService.getDetail(userId, taskId);
        if (t == null || !Objects.equals(t.getProjectId(), projectId)) throw new IllegalArgumentException("TASK_NOT_FOUND");

        TaskPrLink exist = linkMapper.selectOne(Wrappers.<TaskPrLink>lambdaQuery()
                .eq(TaskPrLink::getUserId, userId)
                .eq(TaskPrLink::getProjectId, projectId)
                .eq(TaskPrLink::getTaskId, taskId)
                .eq(TaskPrLink::getPrNumber, prNumber));
        if (exist != null) {
            exist.setPrUrl(prInput != null ? prInput.trim() : exist.getPrUrl());
            exist.setSource("MANUAL");
            linkMapper.updateById(exist);
            timelineService.addEvent(userId, projectId, taskId, TaskTimelineType.PR_LINKED, "绑定 PR", String.valueOf(prNumber));
            return exist.getId();
        }

        TaskPrLink link = new TaskPrLink();
        link.setUserId(userId);
        link.setProjectId(projectId);
        link.setTaskId(taskId);
        link.setPrNumber(prNumber);
        link.setPrUrl(prInput != null ? prInput.trim() : null);
        link.setSource("MANUAL");
        linkMapper.insert(link);
        timelineService.addEvent(userId, projectId, taskId, TaskTimelineType.PR_LINKED, "绑定 PR", String.valueOf(prNumber));
        return link.getId();
    }

    @Override
    public void unlink(Long userId, Long id) {
        if (id == null) throw new IllegalArgumentException("ID_REQUIRED");
        TaskPrLink link = linkMapper.selectById(id);
        if (link == null || !Objects.equals(link.getUserId(), userId)) {
            throw new IllegalArgumentException("NOT_FOUND");
        }
        Project project = projectMapper.selectById(link.getProjectId());
        ensureProjectWritable(project);
        linkMapper.deleteById(id);
        timelineService.addEvent(userId, link.getProjectId(), link.getTaskId(), TaskTimelineType.PR_UNLINKED, "解绑 PR", String.valueOf(link.getPrNumber()));
    }

    @Override
    public GiteePanelDTO panel(Long userId, Long projectId) {
        Project project = requireProject(userId, projectId);
        GiteeRepoConfig cfg = configMapper.selectOne(Wrappers.<GiteeRepoConfig>lambdaQuery()
                .eq(GiteeRepoConfig::getUserId, userId)
                .eq(GiteeRepoConfig::getProjectId, projectId));
        if (cfg == null || cfg.getAccessToken() == null || cfg.getAccessToken().isBlank()) {
            throw new IllegalArgumentException("GITEE_CONFIG_REQUIRED");
        }

        List<Task> tasks = taskService.listByProjectId(userId, projectId);
        Map<Long, Task> taskMap = new LinkedHashMap<>();
        for (Task t : tasks) taskMap.put(t.getId(), t);

        List<GiteeClient.PullRequest> prs = giteeClient.listPullRequests(cfg.getOwner(), cfg.getRepo(), cfg.getAccessToken(), 50);
        Map<Long, List<GiteePanelDTO.PullRequestItem>> prByTask = new HashMap<>();
        Map<Integer, GiteeClient.PullRequest> prByNumber = new HashMap<>();
        for (GiteeClient.PullRequest pr : prs) {
            prByNumber.put(pr.number(), pr);
            for (Long tid : extractTaskIds(pr.title())) {
                if (!taskMap.containsKey(tid)) continue;
                prByTask.computeIfAbsent(tid, k -> new ArrayList<>()).add(toItem(pr, "AUTO", null, null, null));
            }
        }

        List<TaskPrLink> links = linkMapper.selectList(Wrappers.<TaskPrLink>lambdaQuery()
                .eq(TaskPrLink::getUserId, userId)
                .eq(TaskPrLink::getProjectId, projectId));
        Map<Integer, GiteeClient.CommitStatus> statusCache = new HashMap<>();
        Map<String, GiteeClient.CommitStatus> shaCache = new HashMap<>();

        for (TaskPrLink link : links) {
            if (!taskMap.containsKey(link.getTaskId())) continue;
            Integer prNumber = link.getPrNumber();
            GiteeClient.PullRequest pr = prByNumber.get(prNumber);
            if (pr == null) {
                pr = giteeClient.getPullRequest(cfg.getOwner(), cfg.getRepo(), cfg.getAccessToken(), prNumber);
            }
            if (pr == null) continue;
            GiteePanelDTO.PullRequestItem item = toItem(pr, "MANUAL", link.getId(), link.getPrUrl(), null);
            prByTask.computeIfAbsent(link.getTaskId(), k -> new ArrayList<>()).add(item);
        }

        for (List<GiteePanelDTO.PullRequestItem> list : prByTask.values()) {
            if (list == null) continue;
            Map<Integer, GiteePanelDTO.PullRequestItem> dedup = new LinkedHashMap<>();
            for (GiteePanelDTO.PullRequestItem it : list) {
                if (it.getNumber() == null) continue;
                GiteePanelDTO.PullRequestItem prev = dedup.get(it.getNumber());
                if (prev == null) dedup.put(it.getNumber(), it);
                else if ("MANUAL".equals(it.getSource())) dedup.put(it.getNumber(), it);
            }
            list.clear();
            list.addAll(dedup.values());
        }

        for (List<GiteePanelDTO.PullRequestItem> list : prByTask.values()) {
            for (GiteePanelDTO.PullRequestItem it : list) {
                if (it.getNumber() == null) continue;
                if (statusCache.containsKey(it.getNumber())) {
                    applyCi(it, statusCache.get(it.getNumber()));
                    continue;
                }
                String sha = latestCommitSha(cfg.getOwner(), cfg.getRepo(), cfg.getAccessToken(), it.getNumber());
                if (sha == null) {
                    it.setCiState("UNKNOWN");
                    continue;
                }
                GiteeClient.CommitStatus st = shaCache.get(sha);
                if (st == null) {
                    st = giteeClient.getCommitStatus(cfg.getOwner(), cfg.getRepo(), cfg.getAccessToken(), sha);
                    shaCache.put(sha, st);
                }
                statusCache.put(it.getNumber(), st);
                applyCi(it, st);
            }
        }

        List<GiteePanelDTO.TaskItem> out = new ArrayList<>();
        for (Task t : tasks) {
            List<GiteePanelDTO.PullRequestItem> list = prByTask.getOrDefault(t.getId(), List.of());
            out.add(new GiteePanelDTO.TaskItem(t.getId(), t.getTitle(), String.valueOf(t.getStatus()), list));
        }
        return new GiteePanelDTO(cfg.getOwner(), cfg.getRepo(), out);
    }

    private void applyCi(GiteePanelDTO.PullRequestItem it, GiteeClient.CommitStatus st) {
        if (st == null) {
            it.setCiState("UNKNOWN");
            return;
        }
        String state = st.state() == null ? "" : st.state().trim().toLowerCase();
        if (state.contains("success")) it.setCiState("SUCCESS");
        else if (state.contains("fail") || state.contains("error")) it.setCiState("FAILED");
        else if (state.contains("pending") || state.contains("running")) it.setCiState("RUNNING");
        else if (state.isBlank()) it.setCiState("UNKNOWN");
        else it.setCiState(state.toUpperCase());
        it.setCiUrl(st.targetUrl());
    }

    private String latestCommitSha(String owner, String repo, String token, int prNumber) {
        List<GiteeClient.Commit> commits = giteeClient.listPullRequestCommits(owner, repo, token, prNumber, 20);
        if (commits == null || commits.isEmpty()) return null;
        return commits.get(commits.size() - 1).sha();
    }

    private static GiteePanelDTO.PullRequestItem toItem(GiteeClient.PullRequest pr,
                                                        String source,
                                                        Long linkId,
                                                        String manualUrl,
                                                        String ciState) {
        String url = pr.htmlUrl();
        if ((url == null || url.isBlank()) && manualUrl != null) url = manualUrl;

        String state = normalizePrState(pr.state(), pr.merged());
        return new GiteePanelDTO.PullRequestItem(pr.number(), pr.title(), state, url, ciState, null, source, linkId);
    }

    private static String normalizePrState(String state, Boolean merged) {
        String s = state == null ? "" : state.trim().toUpperCase();
        if (Boolean.TRUE.equals(merged)) return "MERGED";
        if (s.isBlank()) return "UNKNOWN";
        if ("OPEN".equals(s)) return "OPEN";
        if ("CLOSED".equals(s)) return "CLOSED";
        if ("MERGED".equals(s)) return "MERGED";
        return s;
    }

    private Project requireProject(Long userId, Long projectId) {
        if (userId == null) throw new IllegalArgumentException("USER_ID_REQUIRED");
        if (projectId == null) throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        projectCollabService.requireAtLeast(userId, projectId, ProjectMemberRole.DEVELOPER);
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw new IllegalArgumentException("PROJECT_NOT_FOUND_OR_FORBIDDEN");
        return project;
    }

    private void ensureProjectWritable(Project project) {
        if (project == null) return;
        if (project.getArchived() != null && project.getArchived() == 1) {
            throw new ApiException(400, "项目已归档");
        }
    }

    private static List<Long> extractTaskIds(String text) {
        if (text == null || text.isBlank()) return List.of();
        Matcher m = TASK_ID_PATTERN.matcher(text);
        List<Long> out = new ArrayList<>();
        while (m.find()) {
            try {
                out.add(Long.parseLong(m.group(1)));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private static Integer parsePrNumber(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isBlank()) return null;
        Matcher m = PR_NUMBER_PATTERN.matcher(s);
        Integer last = null;
        while (m.find()) {
            try {
                last = Integer.parseInt(m.group(1));
            } catch (Exception ignored) {
            }
        }
        return last;
    }
}
