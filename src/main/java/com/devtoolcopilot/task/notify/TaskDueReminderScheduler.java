package com.devtoolcopilot.task.notify;

import com.devtoolcopilot.notification.service.NotificationService;
import com.devtoolcopilot.task.entity.Task;
import com.devtoolcopilot.task.mapper.TaskMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class TaskDueReminderScheduler {
    private final TaskMapper taskMapper;
    private final NotificationService notificationService;

    public TaskDueReminderScheduler(TaskMapper taskMapper, NotificationService notificationService) {
        this.taskMapper = taskMapper;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000L)
    public void scan() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = now.plusHours(24);
        List<Task> list = taskMapper.dueSoonTasks(now, until);
        if (list == null || list.isEmpty()) return;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm");
        for (Task t : list) {
            if (t.getAssigneeId() == null) continue;
            String due = t.getDueTime() == null ? "" : fmt.format(t.getDueTime());
            String title = "任务即将到期";
            String content = due.isBlank() ? t.getTitle() : (t.getTitle() + " · " + due);
            String payload = "{\"projectId\":" + t.getProjectId() + ",\"taskId\":" + t.getId() + "}";
            try {
                notificationService.create(t.getAssigneeId(), "TASK_DUE_SOON", title, content, payload, t.getProjectId(), t.getId(), null, "TASK_DUE_SOON:" + t.getId());
                taskMapper.markDueReminded(t.getId());
            } catch (Exception ignored) {
            }
        }
    }
}
