import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { notificationApi, type NotificationItem } from '../api/notification'

export const useNotificationStore = defineStore('notification', () => {
  const items = ref<NotificationItem[]>([])
  const unreadCount = ref<number>(0)
  const lastPushed = ref<NotificationItem | null>(null)

  // #region debug-point B:init
  function __dtcDbgUrl() {
    const v = typeof localStorage === 'undefined' ? null : localStorage.getItem('dtc_debug_url')
    return v || 'http://127.0.0.1:7777/event'
  }
  function __dtcDbgRunId() {
    const v = typeof localStorage === 'undefined' ? null : localStorage.getItem('dtc_dbg_run')
    return v || 'pre'
  }
  function __dtcDbg(hypothesisId: string, location: string, msg: string, data?: any) {
    const on = typeof localStorage === 'undefined' ? null : localStorage.getItem('dtc_debug_on')
    if (on !== '1') return
    if (typeof fetch === 'undefined') return
    fetch(__dtcDbgUrl(), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        sessionId: 'ui-slow-lag',
        runId: __dtcDbgRunId(),
        hypothesisId,
        location,
        msg,
        data: data || {},
        ts: Date.now()
      })
    }).catch(() => {})
  }
  // #endregion

  const connecting = ref(false)
  let abortCtrl: AbortController | null = null

  const hasUnread = computed(() => unreadCount.value > 0)

  async function refresh(params?: { unreadOnly?: boolean }) {
    const res = await notificationApi.list({ limit: 80, unreadOnly: params?.unreadOnly })
    items.value = res.list
    unreadCount.value = res.unreadCount
  }

  async function refreshUnreadCount() {
    unreadCount.value = await notificationApi.unreadCount()
  }

  async function markRead(id: number) {
    await notificationApi.read(id)
    items.value = items.value.map((x) => (x.id === id ? { ...x, isRead: 1 } : x))
    await refreshUnreadCount()
  }

  async function markAllRead() {
    await notificationApi.readAll()
    items.value = items.value.map((x) => ({ ...x, isRead: 1 }))
    await refreshUnreadCount()
  }

  function disconnect() {
    if (abortCtrl) {
      abortCtrl.abort()
      abortCtrl = null
    }
    connecting.value = false
    // #region debug-point B:notify-disconnect
    __dtcDbg('B', 'stores/notification.ts:disconnect', '[DEBUG] notification stream disconnect')
    // #endregion
  }

  async function connect() {
    if (connecting.value) return
    disconnect()
    connecting.value = true
    abortCtrl = new AbortController()

    // #region debug-point B:notify-connect
    __dtcDbg('B', 'stores/notification.ts:connect', '[DEBUG] notification stream connect')
    // #endregion

    let syncTimer: any = null
    function scheduleSync() {
      if (syncTimer) return
      syncTimer = setTimeout(async () => {
        syncTimer = null
        try {
          await refreshUnreadCount()
        } catch {
        }
      }, 800)
    }

    try {
      await notificationApi.stream(
        (ev) => {
          if (ev.type !== 'notification' && ev.type !== 'notification-update') return
          const n = safeJson(ev.data)
          const item: NotificationItem = {
            id: Number(n?.id || Date.now()),
            projectId: n?.projectId === undefined ? null : Number(n.projectId),
            taskId: n?.taskId === undefined ? null : Number(n.taskId),
            commentId: n?.commentId === undefined ? null : Number(n.commentId),
            type: String(n?.type || 'SYSTEM'),
            title: String(n?.title || '通知'),
            content: n?.content ? String(n.content) : null,
            dataJson: n?.dataJson ? String(n.dataJson) : null,
            isRead: Number(n?.isRead ?? 0),
            aggCount: n?.aggCount === undefined ? null : Number(n.aggCount),
            updateTime: n?.updateTime ? String(n.updateTime) : undefined,
            createTime: n?.createTime ? String(n.createTime) : undefined
          }
          const idx = items.value.findIndex((x) => x.id === item.id)
          if (idx >= 0) {
            const next = [...items.value]
            next[idx] = { ...next[idx], ...item }
            items.value = next
          } else {
            items.value = [item, ...items.value].slice(0, 200)
          }
          if (ev.type === 'notification' && item.isRead === 0) {
            lastPushed.value = item
          }
          scheduleSync()
        },
        abortCtrl.signal
      )
    } catch {
    } finally {
      connecting.value = false
    }
  }

  return {
    items,
    unreadCount,
    hasUnread,
    lastPushed,
    refresh,
    refreshUnreadCount,
    markRead,
    markAllRead,
    connect,
    disconnect
  }
})

function safeJson(raw: string): any {
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}
