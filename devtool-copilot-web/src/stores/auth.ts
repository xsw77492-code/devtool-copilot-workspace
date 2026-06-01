import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi, type UserMe } from '../api/auth'

let sessionClearedListenerBound = false

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('dtc_token'))
  const refreshToken = ref<string | null>(localStorage.getItem('dtc_refresh_token'))
  const me = ref<UserMe | null>(safeParse<UserMe>(localStorage.getItem('dtc_me')))

  if (!sessionClearedListenerBound && typeof window !== 'undefined') {
    sessionClearedListenerBound = true
    window.addEventListener('dtc:session-cleared', () => {
      token.value = null
      refreshToken.value = null
      me.value = null
    })
  }

  const isAuthed = computed(() => !!token.value)
  const role = computed(() => me.value?.role || null)

  async function login(payload: { username: string; password: string }) {
    const res = await authApi.login(payload)
    token.value = res.accessToken
    refreshToken.value = res.refreshToken
    me.value = res.me
    localStorage.setItem('dtc_token', res.accessToken)
    localStorage.setItem('dtc_refresh_token', res.refreshToken)
    localStorage.setItem('dtc_me', JSON.stringify(res.me))
  }

  async function logout() {
    if (refreshToken.value) {
      try {
        await authApi.logout({ refreshToken: refreshToken.value })
      } catch {
      }
    }
    token.value = null
    refreshToken.value = null
    me.value = null
    localStorage.removeItem('dtc_token')
    localStorage.removeItem('dtc_refresh_token')
    localStorage.removeItem('dtc_me')
  }

  async function logoutAllDevices() {
    try {
      await authApi.logoutAll()
    } catch {
    }
    await logout()
  }

  async function changePassword(payload: { oldPassword: string; newPassword: string }) {
    await authApi.changePassword(payload)
    await logout()
  }

  function setSession(accessToken: string, newRefreshToken: string, newMe: UserMe) {
    token.value = accessToken
    refreshToken.value = newRefreshToken
    me.value = newMe
    localStorage.setItem('dtc_token', accessToken)
    localStorage.setItem('dtc_refresh_token', newRefreshToken)
    localStorage.setItem('dtc_me', JSON.stringify(newMe))
  }

  return { token, refreshToken, me, role, isAuthed, login, logout, logoutAllDevices, changePassword, setSession }
})

function safeParse<T>(raw: string | null): T | null {
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}
