import { apiDelete, apiGet, apiPost, apiPut } from './http'

export type ProjectMemberRole = 'OWNER' | 'DEVELOPER' | 'VIEWER'

export interface ProjectMemberItem {
  userId: number
  username: string
  email: string
  role: ProjectMemberRole
  disabled?: number | null
  disabledTime?: string | null
  online?: number | null
  lastSeenAt?: string | null
  joinedAt: string
}

export interface ProjectMembersResponse {
  myRole: ProjectMemberRole
  members: ProjectMemberItem[]
}

export interface ProjectInviteItem {
  id: number
  email: string
  role: ProjectMemberRole
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED' | 'CANCELED'
  expireTime: string
  createTime: string
}

export interface ProjectInviteCreateResponse {
  inviteId: number
  inviteToken: string
  inviteLink: string
}

export interface ProjectMembersExportResponse {
  filename: string
  content: string
}

export interface ProjectActivityItem {
  id: number
  actorUserId: number | null
  actorUsername: string | null
  type: string
  detail: string | null
  createTime: string
}

export const projectCollabApi = {
  async members(projectId: number) {
    return apiGet<ProjectMembersResponse>(`/api/project/${projectId}/members`)
  },

  async invite(projectId: number, payload: { email: string; role?: ProjectMemberRole }) {
    return apiPost<ProjectInviteCreateResponse>(`/api/project/${projectId}/invites`, payload)
  },

  async invites(projectId: number) {
    return apiGet<ProjectInviteItem[]>(`/api/project/${projectId}/invites`)
  },

  async removeMember(projectId: number, userId: number) {
    return apiDelete<void>(`/api/project/${projectId}/members/${userId}`)
  },

  async updateMemberRole(projectId: number, userId: number, role: ProjectMemberRole) {
    return apiPut<void>(`/api/project/${projectId}/members/${userId}/role`, { role })
  },

  async setMemberDisabled(projectId: number, userId: number, disabled: boolean) {
    return apiPut<void>(`/api/project/${projectId}/members/${userId}/disabled`, { disabled })
  },

  async transferOwner(projectId: number, userId: number) {
    return apiPost<void>(`/api/project/${projectId}/members/${userId}/transfer-owner`)
  },

  async leaveProject(projectId: number) {
    return apiDelete<void>(`/api/project/${projectId}/members/me`)
  },

  async acceptInvite(token: string) {
    return apiPost<number>(`/api/project/invites/accept`, { token })
  },

  async rejectInvite(token: string) {
    return apiPost<number>(`/api/project/invites/reject`, { token })
  },

  async cancelInvite(projectId: number, inviteId: number) {
    return apiDelete<void>(`/api/project/${projectId}/invites/${inviteId}`)
  },

  async reissueInvite(projectId: number, inviteId: number) {
    return apiPost<ProjectInviteCreateResponse>(`/api/project/${projectId}/invites/${inviteId}/reissue`)
  },

  async exportMembers(projectId: number) {
    return apiGet<ProjectMembersExportResponse>(`/api/project/${projectId}/members/export`)
  },

  async activities(projectId: number, limit = 100) {
    return apiGet<ProjectActivityItem[]>(`/api/project/${projectId}/activities`, { limit })
  },

  async deleteActivity(projectId: number, activityId: number) {
    return apiDelete<void>(`/api/project/${projectId}/activities/${activityId}`)
  },

  async clearActivities(projectId: number) {
    return apiDelete<number>(`/api/project/${projectId}/activities`)
  }
}
