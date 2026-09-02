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

export interface TeamDigest {
  memberCount: number
  onlineCount: number
  ownerCount: number
  disabledCount: number
  pendingInviteCount: number
  groupCount: number
}

export interface TeamContactItem {
  userId: number
  username: string
  email: string
  roles: string[]
  groupIds: number[]
  groupNames: string[]
  groupCount: number
  online: number
  disabled: number
  lastSeenAt?: string | null
}

export interface TeamGroupItem {
  groupId: number
  groupName: string
  myRole: string
  ownerUserId: number
  systemGroup: boolean
  memberCount: number
  onlineCount: number
  ownerCount: number
  pendingInviteCount: number
  latestSignal: string
  topMembers: string[]
}

export interface TeamInviteItem {
  id: number
  groupId: number
  groupName: string
  email: string
  role: string
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED' | 'CANCELED'
  expireTime?: string | null
  createTime: string
}

export interface TeamActivityItem {
  id: number
  groupId: number
  groupName: string
  actorUsername?: string | null
  type: string
  detail: string
  createTime: string
}

export interface TeamCenterResponse {
  digest: TeamDigest
  contacts: TeamContactItem[]
  groups: TeamGroupItem[]
  invites: TeamInviteItem[]
  activities: TeamActivityItem[]
}

export interface TeamGroupInviteCreateResponse {
  inviteId: number
  inviteToken: string
  inviteLink: string
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

  async teamCenter(groupId?: number | null) {
    return apiGet<TeamCenterResponse>('/api/project/team/center', { groupId: groupId || undefined })
  },

  async createTeamGroup(name: string) {
    return apiPost<number>('/api/project/team/groups', { name })
  },

  async renameTeamGroup(groupId: number, name: string) {
    return apiPut<void>(`/api/project/team/groups/${groupId}`, { name })
  },

  async deleteTeamGroup(groupId: number) {
    return apiDelete<void>(`/api/project/team/groups/${groupId}`)
  },

  async inviteToTeamGroup(groupId: number, email: string) {
    return apiPost<TeamGroupInviteCreateResponse>(`/api/project/team/groups/${groupId}/invites`, { email })
  },

  async acceptTeamGroupInvite(token: string) {
    return apiPost<number>('/api/project/team/invites/accept', { token })
  },

  async rejectTeamGroupInvite(token: string) {
    return apiPost<number>('/api/project/team/invites/reject', { token })
  },

  async cancelTeamGroupInvite(groupId: number, inviteId: number) {
    return apiDelete<void>(`/api/project/team/groups/${groupId}/invites/${inviteId}`)
  },

  async reissueTeamGroupInvite(groupId: number, inviteId: number) {
    return apiPost<TeamGroupInviteCreateResponse>(`/api/project/team/groups/${groupId}/invites/${inviteId}/reissue`)
  },

  async removeTeamGroupMember(groupId: number, userId: number) {
    return apiDelete<void>(`/api/project/team/groups/${groupId}/members/${userId}`)
  },

  async leaveTeamGroup(groupId: number) {
    return apiDelete<void>(`/api/project/team/groups/${groupId}/members/me`)
  },

  async transferTeamGroupOwner(groupId: number, userId: number) {
    return apiPost<void>(`/api/project/team/groups/${groupId}/members/${userId}/transfer-owner`)
  },

  async deleteActivity(projectId: number, activityId: number) {
    return apiDelete<void>(`/api/project/${projectId}/activities/${activityId}`)
  },

  async clearActivities(projectId: number) {
    return apiDelete<number>(`/api/project/${projectId}/activities`)
  },

  teamInvitesMine(): Promise<TeamInviteItem[]> {
    return apiGet<TeamInviteItem[]>('/api/project/team/invites/mine')
  },

  acceptTeamGroupInviteById(inviteId: number): Promise<number> {
    return apiPost<number>(`/api/project/team/invites/${inviteId}/accept-by-id`, {})
  },

  rejectTeamGroupInviteById(inviteId: number): Promise<number> {
    return apiPost<number>(`/api/project/team/invites/${inviteId}/reject-by-id`, {})
  },

  async deleteTeamActivity(groupId: number, activityId: number) {
    return apiDelete<void>(`/api/project/team/groups/${groupId}/activities/${activityId}`)
  },

  async clearTeamActivities(groupId: number) {
    return apiDelete<number>(`/api/project/team/groups/${groupId}/activities`)
  }
}
