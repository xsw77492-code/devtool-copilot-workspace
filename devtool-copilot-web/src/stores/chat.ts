import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import {
  chatApi,
  type ChatConversationItem,
  type ChatMemberItem,
  type ChatMessageEvent,
  type ChatMessageItem
} from '../api/chat'
import { useRealtimeStore } from './realtime'
import { useAuthStore } from './auth'

// ── 持久化：把会话列表/消息快照写到 localStorage，刷新页面能立即恢复 ──
const CACHE_KEY = 'dtc_chat_cache_v1'

function safeRead<T>(key: string): T | null {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return null
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

function safeWrite(key: string, data: unknown) {
  try {
    localStorage.setItem(key, JSON.stringify(data))
  } catch {
    // 配额满 / 序列化失败都安全忽略
  }
}

interface ChatCacheShape {
  conversations: ChatConversationItem[]
  activeId: number | null
  messages: Record<number, ChatMessageItem[]>
}

/** 全局聊天状态：会话列表 / 消息缓存 / 未读数 / 实时推送合并 */
export const useChatStore = defineStore('chat', () => {
  const rt = useRealtimeStore()

  // ── 从 localStorage 恢复：刷新页面后立刻呈现上次的状态（避免空白闪烁/丢消息） ──
  const _initialCache = safeRead<ChatCacheShape>(CACHE_KEY)
  const conversations = ref<ChatConversationItem[]>(_initialCache?.conversations || [])
  const messages = ref<Record<number, ChatMessageItem[]>>(_initialCache?.messages || {})
  const hasMore = ref<Record<number, boolean>>({})
  const conversationMembers = ref<Record<number, ChatMemberItem[]>>({})
  const availableMembers = ref<ChatMemberItem[]>([])
  const activeId = ref<number | null>(_initialCache?.activeId ?? null)
  const loading = ref(false)
  const messagesLoading = ref(false)

  const totalUnread = computed(() =>
    conversations.value.reduce((s, c) => s + (Number(c.unreadCount) || 0), 0)
  )
  const activeConversation = computed(
    () => conversations.value.find((c) => c.id === activeId.value) || null
  )
  const activeMessages = computed(() =>
    activeId.value ? messages.value[activeId.value] || [] : []
  )

  // ── 实时消息合并（避免循环依赖，由 store 内部监听 realtime） ──
  let handledChatEvents = 0
  watch(
    () => rt.chatEvents.length,
    () => {
      const evs = rt.chatEvents
      for (let i = handledChatEvents; i < evs.length; i++) {
        handleChatEvent(evs[i])
      }
      handledChatEvents = evs.length
    },
    { flush: 'post', immediate: true }
  )

  function handleChatEvent(ev: ChatMessageEvent) {
    if (!ev || !ev.conversationId) return
    // 撤回事件：本地将该消息标记为已撤回
    if (ev.type === 'CHAT_MESSAGE_RECALLED' && ev.messageId) {
      const convId = ev.conversationId
      const list = messages.value[convId]
      if (list) {
        const idx = list.findIndex((m) => m.id === ev.messageId)
        if (idx >= 0) {
          const copy = list.slice()
          copy[idx] = { ...copy[idx], recalled: true, content: null, attachment: null }
          messages.value = { ...messages.value, [convId]: copy }
        }
      }
      const conv = conversations.value.find((c) => c.id === convId)
      if (conv && conv.lastMessage && Number(conv.lastMessage.id) === Number(ev.messageId)) {
        conv.lastMessage = { ...conv.lastMessage, recalled: true, content: null, attachment: null }
      }
      return
    }
    // 已读事件：对方读了消息 → 更新单聊 peerReadMessageId（用于"已读"回执）
    if (ev.type === 'CHAT_MESSAGE_READ' && ev.userId != null && ev.lastReadMessageId != null) {
      const auth = useAuthStore()
      if (auth.me && Number(auth.me.id) !== Number(ev.userId)) {
        const conv = conversations.value.find((c) => c.id === ev.conversationId)
        if (conv) conv.peerReadMessageId = ev.lastReadMessageId
      }
      return
    }
    if (!ev.message || !ev.message.id) return
    const { conversationId, message } = ev
    const list = messages.value[conversationId]
    if (list && !list.some((m) => m.id === message.id)) {
      messages.value = { ...messages.value, [conversationId]: [...list, message] }
    }
    const conv = conversations.value.find((c) => c.id === conversationId)
    if (conv) {
      conv.lastMessage = message
      if (conversationId !== activeId.value) {
        conv.unreadCount = (Number(conv.unreadCount) || 0) + 1
      } else {
        conv.unreadCount = 0
        chatApi.markRead(conversationId).catch(() => {})
      }
      sortConversations()
    } else {
      // 新会话（他人主动发起的单聊）→ 刷新列表
      loadConversations().catch(() => {})
    }
  }

  // ── 会话 ────────────────────────────────────────────────

  async function loadConversations() {
    loading.value = true
    try {
      conversations.value = await chatApi.conversations()
      sortConversations()
    } finally {
      loading.value = false
    }
  }

  function sortConversations() {
    const arr = conversations.value.slice().sort((a, b) => {
      // 置顶会话优先
      if (!!a.pinned !== !!b.pinned) return !!a.pinned ? -1 : 1
      const ta = a.lastMessage?.createTime || a.createTime
      const tb = b.lastMessage?.createTime || b.createTime
      if (!ta) return 1
      if (!tb) return -1
      return tb < ta ? -1 : tb > ta ? 1 : 0
    })
    conversations.value = arr
  }

  /** 置顶 / 取消置顶 */
  async function togglePin(id: number, pinned: boolean) {
    try {
      if (pinned) await chatApi.pin(id)
      else await chatApi.unpin(id)
      const conv = conversations.value.find((c) => c.id === id)
      if (conv) {
        conv.pinned = pinned
        sortConversations()
      }
    } catch {
      // 网络异常时静默失败，下轮刷新会话后自然恢复
    }
  }

  /** 更新会话免打扰状态 */
  async function updateMutedConversation(id: number, muted: boolean) {
    try {
      await chatApi.updateMuted(id, muted)
      const conv = conversations.value.find((c) => c.id === id)
      if (conv) conv.muted = muted
    } catch {
      // 静默失败
    }
  }

  /** 手动标为未读 */
  async function markConversationUnread(id: number) {
    try {
      await chatApi.markUnread(id)
      const conv = conversations.value.find((c) => c.id === id)
      if (conv) {
        conv.unreadCount = Math.max(1, (conv.unreadCount || 0) + 1)
        sortConversations()
      }
    } catch {
      // 静默失败
    }
  }

  function upsertConversation(conv: ChatConversationItem) {
    const idx = conversations.value.findIndex((c) => c.id === conv.id)
    if (idx >= 0) {
      conversations.value[idx] = conv
    } else {
      conversations.value = [conv, ...conversations.value]
    }
    sortConversations()
  }

  async function createSingle(targetUserId: number): Promise<number> {
    const conv = await chatApi.createSingle(targetUserId)
    upsertConversation(conv)
    return conv.id
  }

  async function createGroup(name: string, memberIds: number[]): Promise<number> {
    const conv = await chatApi.createGroup(name, memberIds)
    upsertConversation(conv)
    return conv.id
  }

  async function renameConversation(id: number, name: string) {
    await chatApi.rename(id, name)
    const conv = conversations.value.find((c) => c.id === id)
    if (conv) conv.name = name
  }

  async function dissolve(id: number) {
    await chatApi.dissolve(id)
    removeConversationLocal(id)
  }

  async function leaveConversation(id: number, myUserId: number) {
    await chatApi.removeMember(id, myUserId)
    removeConversationLocal(id)
  }

  function removeConversationLocal(id: number) {
    conversations.value = conversations.value.filter((c) => c.id !== id)
    const next = { ...messages.value }
    delete next[id]
    messages.value = next
    const nextHas = { ...hasMore.value }
    delete nextHas[id]
    hasMore.value = nextHas
    if (activeId.value === id) activeId.value = null
  }

  // ── 消息 ────────────────────────────────────────────────

  async function selectConversation(id: number) {
    activeId.value = id
    await loadMessages(id)
    if (!conversationMembers.value[id]) {
      loadConversationMembers(id).catch(() => {})
    }
    markRead(id).catch(() => {})
  }

  async function loadMessages(id: number, beforeId?: number | null) {
    if (!beforeId) messagesLoading.value = true
    try {
      const rows = await chatApi.messages(id, beforeId ?? null)
      if (!beforeId) {
        messages.value = { ...messages.value, [id]: rows }
      } else {
        const existing = messages.value[id] || []
        const seen = new Set(existing.map((m) => m.id))
        const merged = [...rows.filter((m) => !seen.has(m.id)), ...existing]
        messages.value = { ...messages.value, [id]: merged }
      }
      hasMore.value = { ...hasMore.value, [id]: rows.length > 0 }
    } finally {
      messagesLoading.value = false
    }
  }

  async function loadMore() {
    const id = activeId.value
    if (!id || messagesLoading.value || !hasMore.value[id]) return
    const list = messages.value[id] || []
    const beforeId = list.length ? list[0].id : null
    if (beforeId == null) return
    await loadMessages(id, beforeId)
  }

  function appendMessage(conversationId: number, msg: ChatMessageItem) {
    const list = messages.value[conversationId]
    if (list) {
      if (!list.some((m) => m.id === msg.id)) {
        messages.value = { ...messages.value, [conversationId]: [...list, msg] }
      }
    }
    const conv = conversations.value.find((c) => c.id === conversationId)
    if (conv) {
      conv.lastMessage = msg
      conv.unreadCount = 0
      sortConversations()
    }
  }

  async function sendMessage(
    conversationId: number,
    content: string,
    msgType: 'TEXT' | 'IMAGE' | 'FILE' = 'TEXT',
    attachmentId?: number | null,
    replyToId?: number | null
  ): Promise<ChatMessageItem> {
    const msg = await chatApi.send(conversationId, content, msgType, attachmentId, replyToId ?? null)
    appendMessage(conversationId, msg)
    return msg
  }

  /** 撤回消息（仅本人、2 分钟内），本地同步更新 */
  async function recallMessage(messageId: number): Promise<ChatMessageItem> {
    const msg = await chatApi.recall(messageId)
    const convId = msg.conversationId
    const list = messages.value[convId]
    if (list) {
      const idx = list.findIndex((m) => m.id === msg.id)
      if (idx >= 0) {
        const copy = list.slice()
        copy[idx] = msg
        messages.value = { ...messages.value, [convId]: copy }
      }
    }
    return msg
  }

  /** 转发消息到目标会话（复制内容/附件引用，发送者为当前用户） */
  async function forwardMessage(messageId: number, targetConversationId: number): Promise<ChatMessageItem> {
    const msg = await chatApi.forward(messageId, targetConversationId)
    appendMessage(targetConversationId, msg)
    return msg
  }

  async function markRead(id: number) {
    await chatApi.markRead(id)
    const conv = conversations.value.find((c) => c.id === id)
    if (conv) conv.unreadCount = 0
  }

  // ── 成员 ────────────────────────────────────────────────

  async function loadAvailableMembers() {
    if (!availableMembers.value.length) {
      availableMembers.value = await chatApi.members()
    }
    return availableMembers.value
  }

  async function loadConversationMembers(id: number) {
    const rows = await chatApi.conversationMembers(id)
    conversationMembers.value = { ...conversationMembers.value, [id]: rows }
    return rows
  }

  async function addMembers(id: number, userIds: number[]) {
    await chatApi.addMembers(id, userIds)
    await loadConversationMembers(id)
  }

  async function removeMember(id: number, userId: number) {
    await chatApi.removeMember(id, userId)
    const conv = conversations.value.find((c) => c.id === id)
    if (conv) conv.memberCount = Math.max(0, (Number(conv.memberCount) || 0) - 1)
    await loadConversationMembers(id).catch(() => {})
  }

  // ── 附件 ────────────────────────────────────────────────

  async function uploadAttachment(file: File) {
    return chatApi.uploadAttachment(file)
  }

  // ── 本地操作（不与服务器交互） ─────────────────────────
  /** 清空聊天记录：只在本地抹除 messages[convId]，不影响服务器 */
  function clearMessagesLocal(conversationId: number) {
    if (!conversationId) return
    const next = { ...messages.value }
    next[conversationId] = []
    messages.value = next
    const conv = conversations.value.find((c) => c.id === conversationId)
    if (conv) conv.lastMessage = null
  }

  /** 移除会话：本地抹除展示；如需服务器侧删除，请外部自行调 API */
  function removeConversation(conversationId: number) {
    removeConversationLocal(conversationId)
  }

  /** 群聊（owner）解散 */
  async function dissolveGroup(conversationId: number) {
    await chatApi.dissolve(conversationId)
    removeConversationLocal(conversationId)
  }

  // ── 持久化：会话列表 / activeId / 每个会话最多 50 条最近消息 ──
  function persistCache() {
    const slimmedMessages: Record<number, ChatMessageItem[]> = {}
    for (const [k, v] of Object.entries(messages.value || {})) {
      if (!v || !v.length) continue
      // 只快照最近 50 条，避免占用过多 storage
      slimmedMessages[k] = v.slice(-50)
    }
    safeWrite(CACHE_KEY, {
      conversations: conversations.value || [],
      activeId: activeId.value,
      messages: slimmedMessages
    } satisfies ChatCacheShape)
  }

  let persistTimer: ReturnType<typeof setTimeout> | null = null
  watch(
    [conversations, messages, activeId],
    () => {
      // 防抖：200ms 后才落盘，避免连续 realtime 推送时频繁写入
      if (persistTimer) clearTimeout(persistTimer)
      persistTimer = setTimeout(persistCache, 200)
    },
    { deep: true }
  )

  return {
    conversations,
    messages,
    hasMore,
    conversationMembers,
    availableMembers,
    activeId,
    loading,
    messagesLoading,
    totalUnread,
    activeConversation,
    activeMessages,
    loadConversations,
    createSingle,
    createGroup,
    renameConversation,
    dissolve,
    leaveConversation,
    selectConversation,
    loadMessages,
    loadMore,
    appendMessage,
    sendMessage,
    recallMessage,
    forwardMessage,
    markRead,
    togglePin,
    updateMutedConversation,
    markConversationUnread,
    loadAvailableMembers,
    loadConversationMembers,
    addMembers,
    removeMember,
    uploadAttachment,
    clearMessagesLocal,
    removeConversation,
    dissolveGroup
  }
})
