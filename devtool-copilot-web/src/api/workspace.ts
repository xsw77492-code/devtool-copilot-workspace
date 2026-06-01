import { apiGet, apiPost } from './http'

export const workspaceApi = {
  pinnedProjects(): Promise<number[]> {
    return apiGet<number[]>('/api/workspace/projects/pins')
  },
  setPinnedProject(projectId: number, pinned: boolean): Promise<void> {
    return apiPost<void>('/api/workspace/projects/pins', { projectId, pinned })
  }
}

