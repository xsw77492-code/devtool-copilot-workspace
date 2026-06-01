import { apiGet } from './http'

function getAuthHeaders() {
  const token = localStorage.getItem('dtc_token')
  const headers: Record<string, string> = {}
  if (token) headers.Authorization = `Bearer ${token}`
  return headers
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

export interface AssetItem {
  id: number
  projectId: number
  userId: number
  kind: string
  name: string
  ext?: string | null
  contentType?: string | null
  sizeBytes: number
  createTime?: string
  updateTime?: string
}

export const assetApi = {
  list(projectId: number, limit?: number) {
    return apiGet<AssetItem[]>('/api/assets/list', { projectId, limit })
  },

  preview(id: number) {
    return fetchBlob(`/api/assets/${id}/preview`, '预览')
  },

  download(id: number) {
    return fetchBlob(`/api/assets/${id}/download`, '下载')
  }
}
