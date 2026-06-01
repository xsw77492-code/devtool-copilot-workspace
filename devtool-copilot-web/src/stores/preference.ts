import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { userPreferencesApi, type AccentKey, type UserPreferences, type UserPreferencesUpdate } from '../api/userPreferences'
import { applyAccent, paletteByKey } from '../styles/accent'

export const usePreferenceStore = defineStore('preference', () => {
  const pref = ref<UserPreferences | null>(safeParse<UserPreferences>(localStorage.getItem('dtc_prefs')))
  const accentKey = computed<AccentKey>(() => (pref.value?.accentKey || 'teal') as AccentKey)

  function apply() {
    applyAccent(accentKey.value)
  }

  async function load() {
    const p = await userPreferencesApi.get()
    pref.value = p
    localStorage.setItem('dtc_prefs', JSON.stringify(p))
    applyAccent(p.accentKey)
    return p
  }

  async function update(payload: UserPreferencesUpdate) {
    await userPreferencesApi.update(payload)
    const next: UserPreferences = {
      accentKey: (payload.accentKey || pref.value?.accentKey || 'teal') as AccentKey,
      timezone: payload.timezone ?? pref.value?.timezone ?? 'Asia/Shanghai',
      weekStart: payload.weekStart ?? pref.value?.weekStart ?? 1,
      reduceMotion: payload.reduceMotion ?? pref.value?.reduceMotion ?? 0
    }
    pref.value = next
    localStorage.setItem('dtc_prefs', JSON.stringify(next))
    applyAccent(next.accentKey)
    return next
  }

  const accent = computed(() => paletteByKey(accentKey.value).accent)

  return { pref, accentKey, accent, apply, load, update }
})

function safeParse<T>(raw: string | null): T | null {
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

