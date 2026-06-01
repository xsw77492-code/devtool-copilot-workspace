import { apiDelete, apiGet } from './http'

export interface ProjectAuditItem {
  id: number
  projectId: number
  actorUserId: number
  actorUsername?: string | null
  actorEmail?: string | null
  action: string
  targetType?: string | null
  targetId?: number | null
  summary?: string | null
  detail?: string | null
  ip?: string | null
  userAgent?: string | null
  createTime?: string | null
}

export interface ProjectAuditListResponse {
  nextCursor?: number | null
  list: ProjectAuditItem[]
}

export interface ProjectAuditExportResponse {
  filename: string
  content: string
}

export const projectAuditApi = {
  list(projectId: number, params?: Record<string, string | number | boolean | undefined>) {
    return apiGet<ProjectAuditListResponse>(`/api/project/${projectId}/audits`, params)
  },
  exportCsv(projectId: number, params?: Record<string, string | number | boolean | undefined>) {
    return apiGet<ProjectAuditExportResponse>(`/api/project/${projectId}/audits/export`, params)
  },
  deleteOne(projectId: number, id: number) {
    return apiDelete<void>(`/api/project/${projectId}/audits/${id}`)
  },
  clear(projectId: number, params?: Record<string, string | number | boolean | undefined>) {
    const usp = new URLSearchParams()
    if (params) {
      for (const [k, v] of Object.entries(params)) {
        if (v === undefined || v === null) continue
        usp.set(k, String(v))
      }
    }
    const qs = usp.toString()
    const url = qs ? `/api/project/${projectId}/audits?${qs}` : `/api/project/${projectId}/audits`
    return apiDelete<number>(url)
  }
}
