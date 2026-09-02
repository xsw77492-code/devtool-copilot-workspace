import { apiDelete, apiGet, apiPost, apiPut } from './http'

export type ReleaseStatus = 'DRAFT' | 'PUBLISHED'

export interface ProjectRelease {
  id: number
  projectId: number
  milestoneId: number | null
  userId: number
  version: string
  summary: string | null
  notesAssetId: number | null
  status: ReleaseStatus
  publishedTime: string | null
  createTime: string
  updateTime: string
}

export const releaseApi = {
  list(projectId: number): Promise<ProjectRelease[]> {
    return apiGet('/api/release/list', { projectId })
  },
  get(id: number): Promise<ProjectRelease> {
    return apiGet(`/api/release/${id}`)
  },
  create(payload: { projectId: number; milestoneId?: number | null; version: string; summary?: string | null; generateNotes?: boolean }): Promise<number> {
    return apiPost('/api/release', payload)
  },
  updateDraft(id: number, payload: { milestoneId?: number | null; version?: string | null; summary?: string | null }): Promise<void> {
    return apiPut(`/api/release/${id}`, payload)
  },
  generateNotes(id: number): Promise<{ releaseId: number; assetId: number }> {
    return apiPost(`/api/release/${id}/generate-notes`)
  },
  publish(id: number): Promise<void> {
    return apiPost(`/api/release/${id}/publish`)
  },
  remove(id: number): Promise<void> {
    return apiDelete(`/api/release/${id}`)
  }
}
