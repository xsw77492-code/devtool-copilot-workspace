import { apiDelete, apiGet, apiPost, apiPut } from './http'

export type GroupTaskStatus = 'TODO' | 'DOING' | 'DONE'
export type GroupTaskPriority = 'HIGH' | 'MEDIUM' | 'LOW'

export interface GroupTaskItem {
  id: number
  groupId: number
  title: string
  description?: string | null
  priority: GroupTaskPriority
  status: GroupTaskStatus
  assigneeId?: number | null
  assigneeUsername?: string | null
  createdBy?: number | null
  creatorUsername?: string | null
  dueTime?: string | null
  doneTime?: string | null
  overdue: boolean
  createTime?: string | null
  updateTime?: string | null
}

export interface GroupTaskBoardResponse {
  todo: GroupTaskItem[]
  doing: GroupTaskItem[]
  done: GroupTaskItem[]
  total: number
  doingCount: number
  doneCount: number
  overdueCount: number
  completionRate: number
}

export interface GroupTaskCreatePayload {
  title: string
  description?: string
  priority?: GroupTaskPriority
  assigneeId?: number | null
  dueTime?: string | null
}

export interface GroupTaskUpdatePayload {
  title?: string
  description?: string
  priority?: GroupTaskPriority
  dueTime?: string | null
}

export const groupTaskApi = {
  board(groupId: number) {
    return apiGet<GroupTaskBoardResponse>(`/api/project/team/groups/${groupId}/tasks`)
  },
  create(groupId: number, payload: GroupTaskCreatePayload) {
    return apiPost<GroupTaskItem>(`/api/project/team/groups/${groupId}/tasks`, payload)
  },
  update(groupId: number, taskId: number, payload: GroupTaskUpdatePayload) {
    return apiPut<void>(`/api/project/team/groups/${groupId}/tasks/${taskId}`, payload)
  },
  assign(groupId: number, taskId: number, assigneeId: number) {
    return apiPost<void>(`/api/project/team/groups/${groupId}/tasks/${taskId}/assign`, { assigneeId })
  },
  claim(groupId: number, taskId: number) {
    return apiPost<void>(`/api/project/team/groups/${groupId}/tasks/${taskId}/claim`)
  },
  changeStatus(groupId: number, taskId: number, status: GroupTaskStatus) {
    return apiPost<void>(`/api/project/team/groups/${groupId}/tasks/${taskId}/status`, { status })
  },
  delete(groupId: number, taskId: number) {
    return apiDelete<void>(`/api/project/team/groups/${groupId}/tasks/${taskId}`)
  }
}
