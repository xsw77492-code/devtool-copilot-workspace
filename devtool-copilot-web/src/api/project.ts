import { apiDelete, apiGet, apiPost } from './http'

export interface Project {
  id: number
  name: string
  description: string
  createdAt?: number
  createTime?: string
  archived?: number
  archivedTime?: string
}

export const projectApi = {
  async list(includeArchived?: boolean): Promise<Project[]> {
    const list = await apiGet<Project[]>('/api/project/list', { includeArchived })
    return list
      .map((p) => ({
        ...p,
        createdAt: p.createTime ? Date.parse(p.createTime) : undefined
      }))
      .slice()
      .sort((a, b) => b.id - a.id)
  },

  async create(payload: { name: string; description: string }): Promise<number> {
    return apiPost<number>('/api/project', payload)
  },

  async delete(projectId: number): Promise<boolean> {
    if (!projectId) return false
    await apiDelete<void>(`/api/project/${projectId}`)
    return true
  },

  async archive(projectId: number): Promise<boolean> {
    if (!projectId) return false
    await apiPost<void>(`/api/project/${projectId}/archive`)
    return true
  },

  async unarchive(projectId: number): Promise<boolean> {
    if (!projectId) return false
    await apiPost<void>(`/api/project/${projectId}/unarchive`)
    return true
  }
}
