import { apiGet, apiPost } from './http'

export interface InboxItem {
  id: number
  category: string
  title: string
  content?: string | null
  projectId?: number | null
  taskId?: number | null
  commentId?: number | null
  notificationId?: number | null
  isRead: number
  isHandled: number
  updateTime?: string
  createTime?: string
}

export interface InboxListResponse {
  unreadCount: number
  unhandledCount: number
  list: InboxItem[]
}

export const inboxApi = {
  async list(params?: {
    cursor?: number
    limit?: number
    unreadOnly?: boolean
    handled?: boolean
    category?: string
    projectId?: number
    q?: string
  }) {
    return apiGet<InboxListResponse>('/api/inbox/list', params as any)
  },

  async readBatch(ids: number[]) {
    return apiPost<number>('/api/inbox/read-batch', { ids })
  },

  async handleBatch(ids: number[]) {
    return apiPost<number>('/api/inbox/handle-batch', { ids })
  }
}

