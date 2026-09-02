import axios from 'axios'
import { apiGet, apiPut, type R } from './http'

export type UserStatus = 'ONLINE' | 'AWAY' | 'BUSY' | 'OFFLINE'

export interface ProfileResponse {
  userId: number
  username: string
  email: string
  nickname?: string | null
  signature?: string | null
  avatarUrl?: string | null
  status: UserStatus
  createTime: string
}

function getAuthHeaders() {
  const token = localStorage.getItem('dtc_token')
  const headers: Record<string, string> = {}
  if (token) headers.Authorization = `Bearer ${token}`
  return headers
}

function parsePayload<T>(resp: { status: number; data: unknown }): T {
  const payload = resp.data as R<T> | undefined
  if (!payload || typeof payload.code !== 'number') throw new Error(`请求失败(${resp.status})`)
  if (payload.code !== 0) throw new Error(payload.message || '请求失败')
  return payload.data
}

export const profileApi = {
  async getProfile() {
    return apiGet<ProfileResponse>('/api/user/profile')
  },

  async updateProfile(payload: { nickname?: string | null; signature?: string | null; status?: UserStatus }) {
    return apiPut<void>('/api/user/profile', payload)
  },

  async uploadAvatar(file: File): Promise<string> {
    const form = new FormData()
    form.append('file', file)
    const resp = await axios.request({
      url: '/api/user/avatar',
      method: 'POST',
      headers: getAuthHeaders(),
      data: form,
      validateStatus: () => true
    })
    return parsePayload<string>(resp)
  }
}
