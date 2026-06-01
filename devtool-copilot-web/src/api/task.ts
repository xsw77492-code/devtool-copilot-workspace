import { apiDelete, apiGet, apiPost, apiPut } from './http'

export type TaskStatus = 'TODO' | 'DOING' | 'DONE'
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | string

export interface Task {
  id: number
  projectId: number
  title: string
  status: TaskStatus
  description?: string
  acceptanceCriteria?: string
  priority?: TaskPriority
  tags?: string
  assignee?: string
  assigneeId?: number | null
  dueTime?: string
  milestoneId?: number | null
  parentTaskId?: number | null
  type?: string | null
  boardSort?: number
  updatedAt?: string
  createdAt?: number
  createTime?: string
}

export interface TaskTimelineItem {
  id: number
  userId: number
  projectId: number
  taskId: number
  type: string
  title?: string
  detail?: string
  createTime?: string
  createdAt?: number
}

export interface TaskComment {
  id: number
  projectId: number
  taskId: number
  userId: number
  username: string
  content: string
  replyToId?: number | null
  createTime?: string
  createdAt?: number
}

export interface TaskBoardView {
  id: number
  projectId: number
  userId: number
  name: string
  color?: string | null
  filtersJson: string
  createTime?: string
  updateTime?: string
}

export interface TaskTemplate {
  id: number
  projectId?: number | null
  userId: number
  name: string
  payloadJson: string
  createTime?: string
  updateTime?: string
}

export type DeliverableType = 'LINK' | 'DOC' | 'PR'
export type DeliverableStatus = 'PENDING' | 'DONE'

export interface TaskDeliverable {
  id: number
  projectId: number
  taskId: number
  userId: number
  username?: string | null
  type: DeliverableType | string
  title: string
  url?: string | null
  content?: string | null
  status: DeliverableStatus | string
  sort?: number | null
  createTime?: string | null
  updateTime?: string | null
}

export interface TaskChecklistItem {
  id: number
  projectId: number
  taskId: number
  userId: number
  username?: string | null
  content: string
  isDone: number
  doneTime?: string | null
  createTime?: string | null
  updateTime?: string | null
}

export interface WorkspaceExportResponse {
  filename: string
  content: string
}

export interface WorkspaceWeeklyReportResponse {
  title: string
  content: string
}

export interface WorkspaceMyWorkItem {
  taskId: number
  projectId: number
  projectName: string
  title: string
  status: TaskStatus
  priority?: string | null
  dueTime?: string | null
  updatedAt?: string | null
}

export const taskApi = {
  async listByProject(projectId: number): Promise<Task[]> {
    const list = await apiGet<Task[]>('/api/task/list', { projectId })
    return list
      .map((t) => ({
        ...t,
        createdAt: t.createTime ? Date.parse(t.createTime) : undefined
      }))
      .slice()
      .sort((a, b) => b.id - a.id)
  },

  async kanban(projectId: number): Promise<Task[]> {
    const list = await apiGet<Task[]>('/api/task/kanban/list', { projectId })
    return list.map((t) => ({
      ...t,
      createdAt: t.createTime ? Date.parse(t.createTime) : undefined
    }))
  },

  async kanbanMove(payload: {
    projectId: number
    taskId: number
    toStatus: TaskStatus
    beforeId?: number | null
    afterId?: number | null
    baseUpdatedAt?: string | null
    forceDone?: boolean | null
  }): Promise<boolean> {
    return apiPost<boolean>('/api/task/kanban/move', {
      projectId: payload.projectId,
      taskId: payload.taskId,
      toStatus: payload.toStatus,
      beforeId: payload.beforeId ?? undefined,
      afterId: payload.afterId ?? undefined,
      baseUpdatedAt: payload.baseUpdatedAt ?? undefined,
      forceDone: payload.forceDone ?? undefined
    })
  },

  async create(
    projectId: number,
    title: string,
    opts?: {
      assigneeId?: number | null
      dueAt?: number | null
      priority?: string | null
      tags?: string | null
      description?: string | null
      acceptanceCriteria?: string | null
      milestoneId?: number | null
      parentTaskId?: number | null
      type?: string | null
      source?: string
    }
  ): Promise<number> {
    return apiPost<number>('/api/task/create', {
      projectId,
      title,
      assigneeId: opts?.assigneeId ?? undefined,
      dueTime: opts?.dueAt ?? undefined,
      priority: opts?.priority ?? undefined,
      tags: opts?.tags ?? undefined,
      description: opts?.description ?? undefined,
      acceptanceCriteria: opts?.acceptanceCriteria ?? undefined,
      milestoneId: opts?.milestoneId ?? undefined,
      parentTaskId: opts?.parentTaskId ?? undefined,
      type: opts?.type ?? undefined,
      source: opts?.source
    })
  },

  async updateStatus(taskId: number, status: TaskStatus, opts?: { forceDone?: boolean | null }): Promise<boolean> {
    if (!taskId) return false
    await apiPut<void>(`/api/task/${taskId}/status`, { status, forceDone: opts?.forceDone ?? undefined })
    return true
  },

  async updateStatusSafe(
    taskId: number,
    status: TaskStatus,
    baseUpdatedAt?: string | null,
    opts?: { forceDone?: boolean | null }
  ): Promise<boolean> {
    if (!taskId) return false
    await apiPut<void>(`/api/task/${taskId}/status`, {
      status,
      baseUpdatedAt: baseUpdatedAt ?? undefined,
      forceDone: opts?.forceDone ?? undefined
    })
    return true
  },

  async get(taskId: number): Promise<Task> {
    const t = await apiGet<Task>(`/api/task/${taskId}`)
    return {
      ...t,
      createdAt: t.createTime ? Date.parse(t.createTime) : undefined
    }
  },

  async delete(taskId: number): Promise<boolean> {
    if (!taskId) return false
    await apiDelete<void>(`/api/task/${taskId}`)
    return true
  },

  isFollowed(projectId: number, taskId: number): Promise<boolean> {
    return apiGet<boolean>(`/api/task/${taskId}/followed`, { projectId })
  },

  follow(projectId: number, taskId: number): Promise<void> {
    return apiPost<void>(`/api/task/${taskId}/follow?projectId=${encodeURIComponent(String(projectId))}`)
  },

  unfollow(projectId: number, taskId: number): Promise<void> {
    return apiPost<void>(`/api/task/${taskId}/unfollow?projectId=${encodeURIComponent(String(projectId))}`)
  },

  async updateDetail(taskId: number, payload: Partial<Task> & { dueAt?: number | null; baseUpdatedAt?: string | null }): Promise<boolean> {
    const body: any = {
      title: payload.title,
      description: payload.description,
      acceptanceCriteria: payload.acceptanceCriteria,
      priority: payload.priority,
      tags: payload.tags,
      assignee: payload.assignee,
      assigneeId: payload.assigneeId,
      dueTime: payload.dueAt ?? undefined,
      milestoneId: payload.milestoneId ?? undefined,
      parentTaskId: payload.parentTaskId ?? undefined,
      type: payload.type ?? undefined,
      baseUpdatedAt: payload.baseUpdatedAt ?? undefined
    }
    await apiPut<void>(`/api/task/${taskId}`, body)
    return true
  },

  subtasks(taskId: number): Promise<Task[]> {
    return apiGet<Task[]>(`/api/task/${taskId}/subtasks`)
  },

  myWork(limit = 6): Promise<WorkspaceMyWorkItem[]> {
    return apiGet<WorkspaceMyWorkItem[]>('/api/task/workspace/my-work', { limit })
  },

  async timeline(taskId: number): Promise<TaskTimelineItem[]> {
    const list = await apiGet<TaskTimelineItem[]>(`/api/task/${taskId}/timeline`)
    return list.map((e) => ({ ...e, createdAt: e.createTime ? Date.parse(e.createTime) : undefined }))
  },

  async addNote(taskId: number, content: string): Promise<number> {
    return apiPost<number>(`/api/task/${taskId}/note`, { content })
  },

  async comments(taskId: number): Promise<TaskComment[]> {
    const list = await apiGet<TaskComment[]>(`/api/task/${taskId}/comments`)
    return list.map((c) => ({ ...c, createdAt: c.createTime ? Date.parse(c.createTime) : undefined }))
  },

  async addComment(taskId: number, payload: { content: string; replyToId?: number | null }): Promise<number> {
    return apiPost<number>(`/api/task/${taskId}/comment`, {
      content: payload.content,
      replyToId: payload.replyToId ?? null
    })
  },

  deliverables(taskId: number): Promise<TaskDeliverable[]> {
    return apiGet<TaskDeliverable[]>(`/api/task/${taskId}/deliverables`)
  },

  createDeliverable(taskId: number, payload: { type: DeliverableType | string; title: string; url?: string | null; content?: string | null }) {
    return apiPost<number>(`/api/task/${taskId}/deliverable`, payload as any)
  },

  updateDeliverable(id: number, payload: { title?: string; url?: string | null; content?: string | null; status?: DeliverableStatus | string }) {
    return apiPut<void>(`/api/task/deliverable/${id}`, payload as any)
  },

  moveDeliverable(id: number, payload: { beforeId?: number | null; afterId?: number | null }) {
    return apiPost<void>(`/api/task/deliverable/${id}/move`, {
      beforeId: payload.beforeId ?? undefined,
      afterId: payload.afterId ?? undefined
    })
  },

  deleteDeliverable(id: number) {
    return apiDelete<void>(`/api/task/deliverable/${id}`)
  },

  checklist(taskId: number): Promise<TaskChecklistItem[]> {
    return apiGet<TaskChecklistItem[]>(`/api/task/${taskId}/checklist`)
  },

  createChecklistItem(taskId: number, content: string) {
    return apiPost<number>(`/api/task/${taskId}/checklist`, { content })
  },

  updateChecklistItem(id: number, payload: { content?: string; done?: boolean }) {
    return apiPut<void>(`/api/task/checklist/${id}`, payload as any)
  },

  deleteChecklistItem(id: number) {
    return apiDelete<void>(`/api/task/checklist/${id}`)
  },

  participatedIds(projectId: number): Promise<number[]> {
    return apiGet<number[]>('/api/task/participated-ids', { projectId })
  },

  batchUpdateStatus(taskIds: number[], status: TaskStatus): Promise<number> {
    return apiPut<number>('/api/task/batch/status', { taskIds, status })
  },

  batchUpdateFields(payload: {
    taskIds: number[]
    priority?: string
    assigneeId?: number | null
    dueTime?: number | null
    clearAssignee?: boolean
    clearDueTime?: boolean
  }): Promise<number> {
    return apiPut<number>('/api/task/batch/update', payload as any)
  },

  listViews(projectId: number): Promise<TaskBoardView[]> {
    return apiGet<TaskBoardView[]>('/api/task/views', { projectId })
  },

  createView(payload: { projectId: number; name: string; color?: string | null; filtersJson: string }): Promise<TaskBoardView> {
    return apiPost<TaskBoardView>('/api/task/view', payload as any)
  },

  updateView(id: number, payload: { name?: string; color?: string | null; filtersJson?: string }): Promise<TaskBoardView> {
    return apiPut<TaskBoardView>(`/api/task/view/${id}`, payload as any)
  },

  deleteView(id: number): Promise<void> {
    return apiDelete<void>(`/api/task/view/${id}`)
  },

  listTemplates(projectId?: number | null): Promise<TaskTemplate[]> {
    return apiGet<TaskTemplate[]>('/api/task/templates', projectId ? { projectId } : undefined)
  },

  createTemplate(payload: { projectId?: number | null; name: string; payloadJson: string }): Promise<TaskTemplate> {
    return apiPost<TaskTemplate>('/api/task/template', payload as any)
  },

  updateTemplate(id: number, payload: { name?: string; payloadJson?: string }): Promise<TaskTemplate> {
    return apiPut<TaskTemplate>(`/api/task/template/${id}`, payload as any)
  },

  deleteTemplate(id: number): Promise<void> {
    return apiDelete<void>(`/api/task/template/${id}`)
  },

  workspaceExport(payload: {
    projectId: number
    mode?: string
    q?: string
    overdueOnly?: boolean
    sort?: string
  }): Promise<WorkspaceExportResponse> {
    return apiPost<WorkspaceExportResponse>('/api/task/workspace/export', payload as any)
  },

  weeklyReport(params: { projectId: number; startDate?: string; endDate?: string }): Promise<WorkspaceWeeklyReportResponse> {
    return apiGet<WorkspaceWeeklyReportResponse>('/api/task/workspace/weekly-report', params as any)
  }
}
