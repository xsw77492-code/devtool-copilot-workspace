import { apiDelete, apiGet, apiPost } from './http'

export interface NotificationItem {
  id: number
  projectId?: number | null
  taskId?: number | null
  commentId?: number | null
  type: string
  title: string
  content?: string | null
  dataJson?: string | null
  isRead: number
  aggCount?: number | null
  updateTime?: string
  createTime?: string
}

export interface NotificationListResponse {
  unreadCount: number
  list: NotificationItem[]
}

export interface NotificationExportResponse {
  filename: string
  content: string
}

export interface NotificationTypePrefItem {
  type: string
  enabled: number
}

export interface NotificationSettingsResponse {
  dndEnabled: number
  dndStartMinute: number
  dndEndMinute: number
  typePrefs: NotificationTypePrefItem[]
}

export interface NotificationSettingsRequest {
  dndEnabled?: number
  dndStartMinute?: number
  dndEndMinute?: number
  typePrefs?: NotificationTypePrefItem[]
}

function getAuthHeaders() {
  const token = localStorage.getItem('dtc_token')
  const headers: Record<string, string> = {}
  if (token) headers.Authorization = `Bearer ${token}`
  return headers
}

export const notificationApi = {
  async list(params?: {
    cursor?: number
    limit?: number
    unreadOnly?: boolean
    type?: string
    types?: string[]
    projectId?: number
    q?: string
  }) {
    const query: any = { ...params }
    if (params?.types?.length) query.types = params.types.join(',')
    else delete query.types
    return apiGet<NotificationListResponse>('/api/notification/list', query)
  },

  async unreadCount() {
    return apiGet<number>('/api/notification/unread-count')
  },

  async read(id: number) {
    return apiPost<void>(`/api/notification/read/${id}`)
  },

  async readBatch(ids: number[]) {
    return apiPost<number>(`/api/notification/read-batch`, { ids })
  },

  async readAll() {
    return apiPost<number>(`/api/notification/read-all`)
  },

  async readByFilter(params?: { unreadOnly?: boolean; type?: string; types?: string[]; projectId?: number; q?: string }) {
    const usp = new URLSearchParams()
    if (params?.unreadOnly !== undefined) usp.set('unreadOnly', String(params.unreadOnly))
    if (params?.type) usp.set('type', params.type)
    if (params?.types?.length) usp.set('types', params.types.join(','))
    if (params?.projectId) usp.set('projectId', String(params.projectId))
    if (params?.q) usp.set('q', params.q)
    const qs = usp.toString()
    return apiPost<number>(`/api/notification/read-by-filter${qs ? `?${qs}` : ''}`)
  },

  async clearRead() {
    return apiPost<number>(`/api/notification/clear-read`)
  },

  async remove(id: number) {
    return apiDelete<void>(`/api/notification/${id}`)
  },

  async exportCsv(params?: { unreadOnly?: boolean; type?: string; types?: string[]; projectId?: number; q?: string }) {
    const query: any = { ...params }
    if (params?.types?.length) query.types = params.types.join(',')
    else delete query.types
    return apiGet<NotificationExportResponse>(`/api/notification/export`, query)
  },

  async settings() {
    return apiGet<NotificationSettingsResponse>(`/api/notification/settings`)
  },

  async updateSettings(payload: NotificationSettingsRequest) {
    return apiPost<void>(`/api/notification/settings`, payload as any)
  },

  async stream(onEvent: (ev: { type: string; data: string }) => void, signal?: AbortSignal) {
    const resp = await fetch('/api/notification/stream', { method: 'GET', headers: getAuthHeaders(), signal })
    if (!resp.ok || !resp.body) throw new Error(`请求失败(${resp.status})`)

    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buf = ''

    function handleEventBlock(block: string) {
      const lines = block.split('\n')
      let event = ''
      const dataLines: string[] = []
      for (const line of lines) {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        else if (line.startsWith('data:')) dataLines.push(line.slice(5))
      }
      const data = dataLines.join('\n')
      if (!event) return
      onEvent({ type: event, data })
    }

    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      while (true) {
        const idx = buf.indexOf('\n\n')
        if (idx < 0) break
        const block = buf.slice(0, idx).trim()
        buf = buf.slice(idx + 2)
        if (!block) continue
        handleEventBlock(block)
      }
    }
  }
}
