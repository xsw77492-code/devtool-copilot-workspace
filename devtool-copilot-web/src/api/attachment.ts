import axios from 'axios'
import { apiDelete, apiGet, type R } from './http'

export interface AttachmentItem {
  id: number
  projectId: number
  taskId: number
  commentId?: number | null
  userId: number
  originalName: string
  contentType?: string | null
  sizeBytes: number
  createTime?: string
}

function getAuthHeaders() {
  const token = localStorage.getItem('dtc_token')
  const headers: Record<string, string> = {}
  if (token) headers.Authorization = `Bearer ${token}`
  return headers
}

function parsePayload<T>(resp: { status: number; data: unknown }): T {
  const payload = resp.data as R<T> | undefined
  if (!payload || typeof payload.code !== 'number') throw new Error(`请求失败(${resp.status})`)
  if (payload.code !== 0) throw new Error(payload.message || '请求失败')
  return payload.data
}

function parseFilenameFromDisposition(v: string | null): string | null {
  if (!v) return null
  const m = v.match(/filename\*\=UTF-8''([^;]+)/i)
  if (m && m[1]) {
    try {
      return decodeURIComponent(m[1])
    } catch {
      return m[1]
    }
  }
  const m2 = v.match(/filename\=\"?([^\";]+)\"?/i)
  return m2 && m2[1] ? m2[1] : null
}

async function fetchBlob(url: string, actionName: string): Promise<{ filename: string; blob: Blob; contentType: string }> {
  const resp = await fetch(url, { method: 'GET', headers: getAuthHeaders() })
  if (!resp.ok) throw new Error(`${actionName}失败(${resp.status})`)
  const filename = parseFilenameFromDisposition(resp.headers.get('content-disposition')) || 'file'
  const blob = await resp.blob()
  const contentType = resp.headers.get('content-type') || blob.type || 'application/octet-stream'
  return { filename, blob, contentType }
}

export const attachmentApi = {
  async listByTask(taskId: number) {
    return apiGet<AttachmentItem[]>(`/api/attachment/task/${taskId}`)
  },

  async uploadToTask(taskId: number, file: File) {
    const form = new FormData()
    form.append('file', file)
    const resp = await axios.request({
      url: `/api/attachment/task/${taskId}/upload`,
      method: 'POST',
      headers: { ...getAuthHeaders() },
      data: form,
      validateStatus: () => true
    })
    return parsePayload<AttachmentItem>(resp)
  },

  async uploadToComment(commentId: number, file: File) {
    const form = new FormData()
    form.append('file', file)
    const resp = await axios.request({
      url: `/api/attachment/comment/${commentId}/upload`,
      method: 'POST',
      headers: { ...getAuthHeaders() },
      data: form,
      validateStatus: () => true
    })
    return parsePayload<AttachmentItem>(resp)
  },

  async remove(id: number) {
    return apiDelete<void>(`/api/attachment/${id}`)
  },

  async download(id: number): Promise<{ filename: string; blob: Blob }> {
    const res = await fetchBlob(`/api/attachment/${id}/download`, '下载')
    return { filename: res.filename, blob: res.blob }
  },

  async preview(id: number): Promise<{ filename: string; blob: Blob; contentType: string }> {
    return fetchBlob(`/api/attachment/${id}/preview`, '预览')
  }
}
