import { apiGet, apiPost, apiPut } from './http'

export interface AdminUserItem {
  id: number
  username: string
  email: string
  role: 'USER' | 'ADMIN'
  disabled: number
  failedLoginAttempts: number
  lockUntil: string | null
  lastLoginTime: string | null
  lastLoginIp: string | null
  createTime: string
}

export interface AdminLoginAuditItem {
  id: number
  success: number
  failReason: string | null
  ip: string | null
  userAgent: string | null
  createTime: string
}

export const adminApi = {
  async listUsers() {
    return apiGet<AdminUserItem[]>('/api/admin/users')
  },

  async updateUserStatus(id: number, payload: { disabled: boolean }) {
    return apiPut<void>(`/api/admin/users/${id}/status`, payload)
  },

  async resetUserPassword(id: number, payload: { newPassword: string }) {
    return apiPost<void>(`/api/admin/users/${id}/reset-password`, payload)
  },

  async getLoginAudits(id: number, limit = 20) {
    return apiGet<AdminLoginAuditItem[]>(`/api/admin/users/${id}/login-audits`, { limit })
  }
}

