import { apiGet, apiPost } from './http'

export type MilestoneStatus = 'OPEN' | 'PUBLISHED' | 'ARCHIVED'

export interface Milestone {
  id: number
  projectId: number
  userId: number
  name: string
  description?: string
  status: MilestoneStatus | string
  releaseAssetId?: number | null
  dueTime?: string
  publishedTime?: string
  archivedTime?: string
  createTime?: string
  updateTime?: string
}

export interface MilestonePublishResponse {
  milestoneId: number
  assetId: number
}

export const milestoneApi = {
  async list(projectId: number, includeArchived?: boolean): Promise<Milestone[]> {
    return apiGet<Milestone[]>('/api/milestone/list', { projectId, includeArchived })
  },

  async create(payload: { projectId: number; name: string; description?: string; dueTime?: number }): Promise<number> {
    return apiPost<number>('/api/milestone', payload)
  },

  async publish(milestoneId: number): Promise<MilestonePublishResponse> {
    return apiPost<MilestonePublishResponse>(`/api/milestone/${milestoneId}/publish`)
  },

  async archive(milestoneId: number): Promise<boolean> {
    await apiPost<void>(`/api/milestone/${milestoneId}/archive`)
    return true
  },

  async unarchive(milestoneId: number): Promise<boolean> {
    await apiPost<void>(`/api/milestone/${milestoneId}/unarchive`)
    return true
  }
}
