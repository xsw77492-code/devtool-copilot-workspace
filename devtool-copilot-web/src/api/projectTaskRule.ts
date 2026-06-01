import { apiGet, apiPut } from './http'

export interface ProjectTaskRuleDTO {
  requireChecklistDoneForDone: boolean
}

export const projectTaskRuleApi = {
  get(projectId: number): Promise<ProjectTaskRuleDTO> {
    return apiGet<ProjectTaskRuleDTO>(`/api/project/${projectId}/task-rules`)
  },
  save(projectId: number, payload: Partial<ProjectTaskRuleDTO>): Promise<ProjectTaskRuleDTO> {
    return apiPut<ProjectTaskRuleDTO>(`/api/project/${projectId}/task-rules`, payload as any)
  }
}

