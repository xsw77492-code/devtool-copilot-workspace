// 轻量 toast：单例式 ref，跨页面持久；调用 .success / .error / .info / .warning
import { ref } from 'vue'

export type ToastType = 'success' | 'error' | 'info' | 'warning'

export interface ToastItem {
  id: number
  type: ToastType
  text: string
}

const list = ref<ToastItem[]>([])
let counter = 0
const DEFAULT_DURATION = 2400

function push(text: string, type: ToastType = 'info', duration = DEFAULT_DURATION) {
  const id = ++counter
  list.value.push({ id, type, text })
  window.setTimeout(() => {
    list.value = list.value.filter((t) => t.id !== id)
  }, duration)
}

export function useToast() {
  return {
    list,
    success: (text: string, duration?: number) => push(text, 'success', duration),
    error: (text: string, duration?: number) => push(text, 'error', duration),
    info: (text: string, duration?: number) => push(text, 'info', duration),
    warning: (text: string, duration?: number) => push(text, 'warning', duration)
  }
}
