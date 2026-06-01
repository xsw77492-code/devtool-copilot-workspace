import { apiGet, apiPost } from './http'

export interface UserMe {
  id: number
  username: string
  email: string
  role: 'USER' | 'ADMIN'
  disabled: number
  lastLoginTime: string | null
  createTime: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  me: UserMe
}

export interface UserSessionItem {
  id: number
  deviceName: string | null
  ip: string | null
  userAgent: string | null
  lastUseTime: string | null
  expireTime: string
  createTime: string
}

export interface UserSessionsResponse {
  sessions: UserSessionItem[]
}

export const authApi = {
  async register(payload: { username: string; email: string; password: string; emailVerifyToken?: string | null }) {
    return apiPost<number>('/api/user/register', payload)
  },

  async login(payload: { username: string; password: string }) {
    return apiPost<LoginResponse>('/api/user/login', payload)
  },

  async refresh(payload: { refreshToken: string }) {
    return apiPost<LoginResponse>('/api/user/refresh', payload)
  },

  async logout(payload: { refreshToken: string }) {
    return apiPost<void>('/api/user/logout', payload)
  },

  async passwordResetRequest(payload: { email: string }) {
    return apiPost<void>('/api/user/password-reset/request', payload)
  },

  async passwordResetConfirm(payload: { token: string; newPassword: string }) {
    return apiPost<void>('/api/user/password-reset/confirm', payload)
  },

  async requestEmailVerify(payload: { email: string }) {
    return apiPost<void>('/api/user/email-verify/request', payload)
  },

  async confirmEmailVerify(payload: { email: string; code: string }) {
    return apiPost<{ verifyToken: string }>('/api/user/email-verify/confirm', payload)
  },

  async logoutAll() {
    return apiPost<void>('/api/user/logout-all', {})
  },

  async changePassword(payload: { oldPassword: string; newPassword: string }) {
    return apiPost<void>('/api/user/password/change', payload)
  },

  async sessions() {
    return apiGet<UserSessionsResponse>('/api/user/sessions')
  },

  async revokeSession(id: number) {
    return apiPost<void>(`/api/user/sessions/${id}/revoke`, {})
  }
}
