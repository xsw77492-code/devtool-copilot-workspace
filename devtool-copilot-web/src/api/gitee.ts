import { apiGet, apiPost } from './http'

export interface GiteeRepoConfigDTO {
  projectId: number
  owner?: string | null
  repo?: string | null
  hasToken: boolean
}

export interface GiteePanelDTO {
  owner: string
  repo: string
  tasks: Array<{
    taskId: number
    title: string
    status: string
    prs: Array<{
      number: number
      title: string
      state: string
      url?: string | null
      ciState?: string | null
      ciUrl?: string | null
      source: 'AUTO' | 'MANUAL'
      linkId?: number | null
    }>
  }>
}

export const giteeApi = {
  async getConfig(projectId: number) {
    return apiGet<GiteeRepoConfigDTO>('/api/gitee/config', { projectId })
  },
  async saveConfig(input: { projectId: number; owner: string; repo: string; accessToken: string }) {
    return apiPost<GiteeRepoConfigDTO>('/api/gitee/config', input)
  },
  async panel(projectId: number) {
    return apiGet<GiteePanelDTO>('/api/gitee/panel', { projectId })
  },
  async linkTask(input: { projectId: number; taskId: number; pr: string }) {
    return apiPost<number>('/api/gitee/link', input)
  },
  async unlink(input: { id: number }) {
    await apiPost<void>('/api/gitee/unlink', input)
  }
}

