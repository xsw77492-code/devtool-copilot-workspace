import { apiGet, apiPut } from './http'

export type AccentKey = 'teal' | 'sky' | 'emerald' | 'amber' | 'rose' | 'slate'

export interface UserPreferences {
  accentKey: AccentKey
  timezone: string
  weekStart: number
  reduceMotion: number
}

export interface UserPreferencesUpdate {
  accentKey?: AccentKey
  timezone?: string
  weekStart?: number
  reduceMotion?: number
}

export const userPreferencesApi = {
  get: () => apiGet<UserPreferences>('/api/user/preferences'),
  update: (payload: UserPreferencesUpdate) => apiPut<void>('/api/user/preferences', payload)
}

