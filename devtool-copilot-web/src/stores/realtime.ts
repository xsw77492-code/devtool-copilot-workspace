import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { useAuthStore } from './auth'

export interface RealtimeServerMessage {
  eventId?: number | null
  projectId?: number | null
  actorUserId?: number | null
  type?: string | null
  payloadJson?: string | null
  time?: string | null
}

export interface PresenceMember {
  userId: number
  username?: string | null
  viewType?: string | null
  viewId?: number | null
  lastSeenTime?: string | null
  online?: boolean | null
  editing?: boolean | null
}

export interface TeamPresenceMember {
  userId: number
  username?: string | null
  lastSeenTime?: string | null
}

export const useRealtimeStore = defineStore('realtime', () => {
  const auth = useAuthStore()

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
  const connected = ref(false)
  const activeProjectId = ref<number | null>(null)
  const activeViewType = ref<string | null>(null)
  const activeViewId = ref<number | null>(null)

  const seq = ref(0)
  const lastEvent = ref<RealtimeServerMessage | null>(null)
  const lastToast = ref<{ title: string; content?: string } | null>(null)

  const presenceSeq = ref(0)
  const presenceMembers = ref<PresenceMember[]>([])
  const teamPresence = ref<TeamPresenceMember[]>([])

  /** 聊天订阅状态：true 表示当前会话已订阅聊天频道 */
  const chatSubscribed = ref(false)
  /** 收到的实时聊天事件（CHAT_MESSAGE / CHAT_MESSAGE_RECALLED，跨标签页同步） */
  const chatEvents = ref<Array<{ type?: string; conversationId: number; message?: any; messageId?: number | null }>>([])

  function pushChatEvent(ev: { type?: string; conversationId: number; message?: any; messageId?: number | null }) {
    if (!ev || !ev.conversationId) return
    if (!ev.message && !ev.messageId) return
    chatEvents.value = [...chatEvents.value.slice(-199), ev]
  }

  const tabId = Math.random().toString(16).slice(2) + Date.now().toString(16)
  const bc =
    typeof window !== 'undefined' && typeof (window as any).BroadcastChannel !== 'undefined'
      ? new (window as any).BroadcastChannel('dtc:rt')
      : null

  function publishEvent(msg: RealtimeServerMessage) {
    try {
      if (bc) {
        bc.postMessage({ from: tabId, msg })
        return
      }
      const payload = JSON.stringify({ from: tabId, msg, ts: Date.now(), nonce: Math.random() })
      localStorage.setItem('dtc_rt_msg', payload)
    } catch {
    }
  }

  function acceptExternalEvent(msg: RealtimeServerMessage) {
    lastEvent.value = msg
    seq.value += 1
  }

  if (bc) {
    try {
      bc.onmessage = (ev: any) => {
        const from = String(ev?.data?.from || '')
        if (!from || from === tabId) return
        const msg = ev?.data?.msg as RealtimeServerMessage
        if (!msg) return
        acceptExternalEvent(msg)
        if (String(msg.type || '') === 'CHAT_MESSAGE' || String(msg.type || '') === 'CHAT_MESSAGE_RECALLED') {
          try {
            const payload = JSON.parse(String(msg.payloadJson || '{}'))
            pushChatEvent({ type: msg.type, ...payload })
          } catch {
          }
        }
      }
    } catch {
    }
  } else {
    try {
      window.addEventListener('storage', (e) => {
        if (!e || e.key !== 'dtc_rt_msg' || !e.newValue) return
        try {
          const obj = JSON.parse(String(e.newValue || '{}')) as any
          const from = String(obj?.from || '')
          if (!from || from === tabId) return
          const msg = obj?.msg as RealtimeServerMessage
          if (!msg) return
          acceptExternalEvent(msg)
          if (String(msg.type || '') === 'CHAT_MESSAGE' || String(msg.type || '') === 'CHAT_MESSAGE_RECALLED') {
            try {
              const payload = JSON.parse(String(msg.payloadJson || '{}'))
              pushChatEvent({ type: msg.type, ...payload })
            } catch {
            }
          }
        } catch {
        }
      })
    } catch {
    }
  }

  let ws: WebSocket | null = null
  let reconnectTimer: any = null
  let heartbeatTimer: any = null

  // 指数退避：避免后端 devtools 热重启时前端 1.2s 疯狂重连。
  // 重启窗口期通常 5~15s，封顶 30s 足以覆盖一次完整的重启周期。
  let reconnectDelay = 1200
  const RECONNECT_BASE = 1200
  const RECONNECT_MAX = 30000

  const isReady = computed(() => connected.value && !!ws && ws.readyState === WebSocket.OPEN)

  function connectUrl() {
    const token = auth.token || localStorage.getItem('dtc_token')
    if (!token) return null
    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
    return `${proto}://${window.location.host}/ws/collab?token=${encodeURIComponent(token)}`
  }

  function clearReconnect() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  function scheduleReconnect() {
    clearReconnect()
    if (!auth.isAuthed) return
    const delay = reconnectDelay
    // 1.2s -> ~2s -> ~3.4s -> ~5.8s -> ~9.8s -> ~16.6s -> 30s (封顶)
    reconnectDelay = Math.min(RECONNECT_MAX, Math.max(RECONNECT_BASE, Math.floor(reconnectDelay * 1.7)))
    reconnectTimer = setTimeout(() => {
      connect()
    }, delay)
  }

  function disconnect() {
    clearReconnect()
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
    connected.value = false
    connecting.value = false
    if (ws) {
      try {
        ws.close()
      } catch {
      }
    }
    ws = null
  }

  function send(obj: any) {
    if (!ws || ws.readyState !== WebSocket.OPEN) return
    ws.send(JSON.stringify(obj))
  }

  function subscribe(projectId: number | null, viewType?: string, viewId?: number | null) {
    activeProjectId.value = projectId
    activeViewType.value = viewType ?? null
    activeViewId.value = viewId ?? null
    presenceMembers.value = []
    if (!projectId) {
      activeViewType.value = null
      activeViewId.value = null
      send({ op: 'UNSUBSCRIBE' })
      if (heartbeatTimer) {
        clearInterval(heartbeatTimer)
        heartbeatTimer = null
      }
      return
    }
    if (viewType) {
      send({ op: 'SUBSCRIBE', projectId, viewType, viewId: viewId ?? null })
    } else {
      send({ op: 'SUBSCRIBE', projectId })
    }
    if (!heartbeatTimer) {
      heartbeatTimer = setInterval(() => {
        if (!activeProjectId.value) return
        send({ op: 'PING', projectId: activeProjectId.value })
      }, 15000)
    }
  }

  function setView(viewType: string, viewId?: number | null) {
    if (!activeProjectId.value) return
    activeViewType.value = viewType
    activeViewId.value = viewId ?? null
    send({ op: 'VIEW', projectId: activeProjectId.value, viewType, viewId: viewId ?? null })
  }

  function setEditing(editing: boolean) {
    if (!activeProjectId.value) return
    send({ op: 'EDIT', projectId: activeProjectId.value, editing })
  }

  function subscribeTeam() {
    if (!ws || ws.readyState !== WebSocket.OPEN) return
    send({ op: 'SUBSCRIBE_TEAM' })
  }

  function unsubscribeTeam() {
    if (!ws || ws.readyState !== WebSocket.OPEN) return
    send({ op: 'UNSUBSCRIBE_TEAM' })
  }

  /** 订阅聊天频道：接收自己参与会话的实时消息 */
  function subscribeChat() {
    chatSubscribed.value = true
    send({ op: 'SUBSCRIBE_CHAT' })
  }

  function unsubscribeChat() {
    chatSubscribed.value = false
    send({ op: 'UNSUBSCRIBE_CHAT' })
  }

  function connect() {
    if (!auth.isAuthed) return
    if (connecting.value || connected.value) return
    const url = connectUrl()
    if (!url) return

    connecting.value = true
    clearReconnect()

    // #region debug-point B:ws-connect
    __dtcDbg('B', 'stores/realtime.ts:connect', '[DEBUG] ws connect start', { hasProject: !!activeProjectId.value })
    // #endregion

    try {
      ws = new WebSocket(url)
    } catch {
      connecting.value = false
      scheduleReconnect()
      return
    }

    ws.onopen = () => {
      connecting.value = false
      connected.value = true
      // 连接成功，重置退避
      reconnectDelay = RECONNECT_BASE
      // #region debug-point B:ws-open
      __dtcDbg('B', 'stores/realtime.ts:onopen', '[DEBUG] ws open', { hasProject: !!activeProjectId.value })
      // #endregion
      if (activeProjectId.value) subscribe(activeProjectId.value, activeViewType.value || undefined, activeViewId.value)
      if (chatSubscribed.value) subscribeChat()
      if (teamPresence.length) {
        /* team 订阅由各页面按需发起 */
      }
    }

    ws.onclose = () => {
      connected.value = false
      connecting.value = false
      ws = null
      // #region debug-point B:ws-close
      __dtcDbg('B', 'stores/realtime.ts:onclose', '[DEBUG] ws close', { hasProject: !!activeProjectId.value })
      // #endregion
      if (heartbeatTimer) {
        clearInterval(heartbeatTimer)
        heartbeatTimer = null
      }
      scheduleReconnect()
    }

    ws.onerror = () => {
      try {
        ws?.close()
      } catch {
      }
    }

    ws.onmessage = (ev) => {
      let msg: any = null
      try {
        msg = JSON.parse(String(ev.data || ''))
      } catch {
        return
      }
      const t = String(msg?.type || '')
      if (t === 'ready' || t === 'subscribed') return
      if (t === 'error') {
        return
      }

      if (t === 'PRESENCE') {
        try {
          const list = JSON.parse(String(msg?.payloadJson || '[]'))
          if (Array.isArray(list)) {
            presenceMembers.value = list as PresenceMember[]
            presenceSeq.value += 1
          }
        } catch {
          presenceMembers.value = []
          presenceSeq.value += 1
        }
        return
      }

      if (t === 'TEAM_PRESENCE') {
        try {
          const list = JSON.parse(String(msg?.payloadJson || '[]'))
          teamPresence.value = Array.isArray(list) ? (list as TeamPresenceMember[]) : []
        } catch {
          teamPresence.value = []
        }
        return
      }

      if (t === 'CHAT_MESSAGE' || t === 'CHAT_MESSAGE_RECALLED') {
        try {
          const payload = JSON.parse(String(msg?.payloadJson || '{}'))
          pushChatEvent({ type: t, ...payload })
        } catch {
        }
        publishEvent(msg as RealtimeServerMessage)
        return
      }

      lastEvent.value = msg as RealtimeServerMessage
      seq.value += 1
      publishEvent(msg as RealtimeServerMessage)

      const toast = toToast(msg)
      if (toast) lastToast.value = toast
    }
  }

  function toToast(msg: RealtimeServerMessage): { title: string; content?: string } | null {
    const t = String(msg.type || '')
    if (!t) return null
    const me = Number((auth as any)?.me?.id || 0)
    const actor = Number((msg as any)?.actorUserId || 0)
    if (me && actor && me === actor) return null
    if (t === 'TASK_CREATED') return { title: '任务已创建' }
    if (t === 'TASK_UPDATED') return { title: '任务已更新' }
    if (t === 'TASK_STATUS_UPDATED') return { title: '任务状态已更新' }
    if (t === 'AI_GENERATE_TASK') return { title: 'AI生成了新任务' }
    if (t === 'MEMBER_JOINED') return { title: '成员已加入项目' }
    if (t === 'MEMBER_REMOVED') return { title: '成员已移出项目' }
    if (t === 'MEMBER_INVITED') return { title: '已邀请新成员' }
    if (t === 'TASK_COMMENT_CREATED') return { title: '新评论' }
    return { title: '项目已更新' }
  }

  return {
    connecting,
    connected,
    isReady,
    activeProjectId,
    seq,
    lastEvent,
    lastToast,
    presenceSeq,
    presenceMembers,
    teamPresence,
    publishEvent,
    connect,
    disconnect,
    subscribe,
    subscribeTeam,
    unsubscribeTeam,
    subscribeChat,
    unsubscribeChat,
    chatSubscribed,
    chatEvents,
    setView,
    setEditing
  }
})
