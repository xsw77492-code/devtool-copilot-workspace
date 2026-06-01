import type { AccentKey } from '../api/userPreferences'

export type AccentPaletteItem = { key: AccentKey; label: string; accent: string; accent2: string; dotRgb: string }

export const ACCENT_PALETTE: AccentPaletteItem[] = [
  { key: 'teal', label: 'Teal', accent: '#14b8a6', accent2: '#06b6d4', dotRgb: '20, 184, 166' },
  { key: 'sky', label: 'Sky', accent: '#0ea5e9', accent2: '#38bdf8', dotRgb: '14, 165, 233' },
  { key: 'emerald', label: 'Emerald', accent: '#10b981', accent2: '#34d399', dotRgb: '16, 185, 129' },
  { key: 'amber', label: 'Amber', accent: '#f59e0b', accent2: '#fbbf24', dotRgb: '245, 158, 11' },
  { key: 'rose', label: 'Rose', accent: '#f43f5e', accent2: '#fb7185', dotRgb: '244, 63, 94' },
  { key: 'slate', label: 'Slate', accent: '#64748b', accent2: '#94a3b8', dotRgb: '100, 116, 139' }
]

export function paletteByKey(key: AccentKey | string | null | undefined) {
  return ACCENT_PALETTE.find((x) => x.key === key) || ACCENT_PALETTE[0]
}

export function applyAccent(key: AccentKey | string | null | undefined) {
  const p = paletteByKey(key)
  const el = document.documentElement
  el.style.setProperty('--accent', p.accent)
  el.style.setProperty('--accent2', p.accent2)
  return p
}

