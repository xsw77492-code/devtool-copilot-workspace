import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { userPreferencesApi, type AccentKey, type UserPreferences, type UserPreferencesUpdate } from '../api/userPreferences'
import { applyAccent, paletteByKey } from '../styles/accent'

export const usePreferenceStore = defineStore('preference', () => {
  const pref = ref<UserPreferences | null>(safeParse<UserPreferences>(localStorage.getItem('dtc_prefs')))
  const accentKey = computed<AccentKey>(() => {
    const k = (pref.value?.accentKey || 'slate') as AccentKey
    return k === 'teal' ? 'slate' : k
  })

  function apply() {
    applyAccent(accentKey.value)
  }

  async function load() {
    const p = await userPreferencesApi.get()
    const next: UserPreferences = { ...p, accentKey: ((p.accentKey as any) === 'teal' ? 'slate' : p.accentKey) as AccentKey }
    pref.value = next
    localStorage.setItem('dtc_prefs', JSON.stringify(next))
    applyAccent(next.accentKey)
    return next
  }

  async function update(payload: UserPreferencesUpdate) {
    await userPreferencesApi.update(payload)
    const next: UserPreferences = {
      accentKey: ((payload.accentKey || pref.value?.accentKey || 'slate') as AccentKey) === 'teal' ? 'slate' : ((payload.accentKey || pref.value?.accentKey || 'slate') as AccentKey),
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
