<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  NButton,
  NDropdown,
  NEmpty,
  NInput,
  NModal,
  NPopconfirm,
  NSpin,
  NTag,
  useMessage
} from 'naive-ui'
import {
  chatApi,
  type ChatConversationItem,
  type ChatMemberItem,
  type ChatMessageItem
} from '../api/chat'
import { fetchWithAuth } from '../api/http'
import ChatSettingDrawer from '../components/ChatSettingDrawer.vue'
import { useAuthStore } from '../stores/auth'
import { useChatStore } from '../stores/chat'

const message = useMessage()
const auth = useAuthStore()
const chat = useChatStore()

const myId = computed(() => Number(auth.me?.id || 0))

// ── 左栏会话列表 ────────────────────────────────────────
const searchQuery = ref('')
const filteredConversations = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return chat.conversations
  return chat.conversations.filter((c) => c.name.toLowerCase().includes(q))
})

// ── 创建会话弹窗 ────────────────────────────────────────
const createOpen = ref(false)
const createMode = ref<'single' | 'group'>('single')
const groupName = ref('')
const selectedIds = ref<number[]>([])
const memberQuery = ref('')
const creating = ref(false)

const filteredMembers = computed(() => {
  const q = memberQuery.value.trim().toLowerCase()
  return chat.availableMembers.filter(
    (m) => !q || m.username.toLowerCase().includes(q) || (m.nickname || '').toLowerCase().includes(q)
  )
})

function openCreate(mode: 'single' | 'group') {
  createMode.value = mode
  groupName.value = ''
  selectedIds.value = []
  memberQuery.value = ''
  createOpen.value = true
}

function toggleMember(userId: number) {
  if (createMode.value === 'single') {
    selectedIds.value = [userId]
    return
  }
  const idx = selectedIds.value.indexOf(userId)
  if (idx >= 0) selectedIds.value.splice(idx, 1)
  else selectedIds.value.push(userId)
}

function toggleAdd(userId: number) {
  const idx = addIds.value.indexOf(userId)
  if (idx >= 0) addIds.value.splice(idx, 1)
  else addIds.value.push(userId)
}

async function confirmCreate() {
  if (creating.value) return
  if (createMode.value === 'group' && !groupName.value.trim()) {
    message.warning('请输入群名称')
    return
  }
  if (!selectedIds.value.length) {
    message.warning(createMode.value === 'single' ? '请选择聊天对象' : '请选择至少一位成员')
    return
  }
  creating.value = true
  try {
    let id: number
    if (createMode.value === 'single') {
      id = await chat.createSingle(selectedIds.value[0])
    } else {
      id = await chat.createGroup(groupName.value.trim(), selectedIds.value)
    }
    createOpen.value = false
    await chat.selectConversation(id)
  } catch (e: any) {
    message.error(e.message || '创建失败')
  } finally {
    creating.value = false
  }
}

// ── 聊天窗口 ────────────────────────────────────────────
const draft = ref('')
const sending = ref(false)
const imageInput = ref<HTMLInputElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const msgListEl = ref<HTMLElement | null>(null)
const textareaEl = ref<HTMLTextAreaElement | null>(null)

const activeConv = computed(() => chat.activeConversation)
const activeMessages = computed(() => chat.activeMessages)
const convMembers = computed(() => (chat.activeId ? chat.conversationMembers[chat.activeId] || [] : []))
const isOwner = computed(() => activeConv.value?.myRole === 'OWNER')
const isGroup = computed(() => activeConv.value?.type === 'GROUP')
const showSettingDrawer = ref(false)
function openSettingDrawer() {
  if (!activeConv.value) return
  showSettingDrawer.value = true
}

// ── 图片消息：接口需鉴权，用 fetch + objectURL 加载 ──
const imgUrls = ref<Record<number, string>>({})

async function ensureImageUrl(id: number) {
  if (imgUrls.value[id]) return
  try {
    const resp = await fetchWithAuth(`/api/chat/attachments/${id}/image`)
    if (!resp.ok) return
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    imgUrls.value = { ...imgUrls.value, [id]: url }
  } catch {
    /* 图片加载失败时仅显示占位 */
  }
}

watch(
  () => chat.activeMessages.map((m) => m.attachment?.id).filter(Boolean),
  (ids) => {
    for (const id of ids) if (id) ensureImageUrl(id)
  },
  { immediate: true, deep: false }
)

// ── 图片大图预览（点击消息内图片弹出，支持滚轮缩放） ──
const imgPreviewUrl = ref('')
const imgPreviewScale = ref(1)

function openImagePreview(url: string) {
  imgPreviewUrl.value = url
  imgPreviewScale.value = 1
}

function closeImagePreview() {
  imgPreviewUrl.value = ''
}

function onPreviewWheel(e: WheelEvent) {
  const delta = e.deltaY < 0 ? 0.12 : -0.12
  imgPreviewScale.value = Math.min(3, Math.max(0.5, imgPreviewScale.value + delta))
}

function onPreviewKey(e: KeyboardEvent) {
  if (e.key === 'Escape' && imgPreviewUrl.value) closeImagePreview()
}

// ── 已读回执缓存（key: conversationId:messageId → userIds） ──
const readersCache = ref<Record<string, number[]>>({})
const readersLoading = ref<Record<string, boolean>>({})

async function ensureReaders(m: ChatMessageItem) {
  if (!chat.activeId || !isMine(m) || m.recalled || m.msgType === 'SYSTEM') return
  const key = `${chat.activeId}:${m.id}`
  if (readersCache.value[key] || readersLoading.value[key]) return
  readersLoading.value = { ...readersLoading.value, [key]: true }
  try {
    const ids = await chatApi.messageReaders(chat.activeId, m.id)
    readersCache.value = { ...readersCache.value, [key]: ids }
  } catch {
    /* 忽略已读状态获取失败 */
  } finally {
    readersLoading.value = { ...readersLoading.value, [key]: false }
  }
}

const readersOf = (m: ChatMessageItem): number[] => {
  if (!chat.activeId) return []
  return readersCache.value[`${chat.activeId}:${m.id}`] || []
}

const readCountText = computed(() => (m: ChatMessageItem) => {
  const ids = readersOf(m)
  if (!ids.length) return ''
  if (activeConv.value?.type === 'GROUP') return `已读 ${ids.length} 人`
  // 单聊的已读回执由 singleReadText 统一显示，这里不再重复输出
  return ''
})

// ── 已读详情弹窗 ────────────────────────────────────────
const readersModal = ref(false)
const readersMsg = ref<ChatMessageItem | null>(null)
const readersMembers = computed<ChatMemberItem[]>(() => {
  if (!readersMsg.value) return []
  const ids = new Set(readersOf(readersMsg.value))
  const map = new Map(convMembers.value.map((m) => [m.userId, m]))
  const list: ChatMemberItem[] = []
  for (const id of ids) {
    const m = map.get(Number(id))
    if (m) list.push(m)
  }
  const known = new Set(convMembers.value.map((m) => m.userId))
  for (const id of ids) {
    if (!known.has(Number(id))) list.push({ userId: Number(id), username: '成员 ' + id, email: '' })
  }
  return list
})

function openReaders(m: ChatMessageItem) {
  if (!isMine(m) || m.recalled) return
  const ids = readersOf(m)
  if (!ids.length) return
  readersMsg.value = m
  readersModal.value = true
}

// ── 消息定位与高亮（搜索结果跳转） ──
const highlightId = ref<number | null>(null)
const searchOpen = ref(false)
const searchKw = ref('')
const searchResults = ref<ChatMessageItem[]>([])
const searchLoading = ref(false)
const searchInputEl = ref<any>(null)

function jumpToMessage(id: number) {
  searchOpen.value = false
  const inList = activeMessages.value.some((m) => m.id === id)
  const doScroll = async () => {
    await nextTick()
    const el = document.getElementById(`msg-${id}`)
    if (el) {
      highlightId.value = id
      el.scrollIntoView({ block: 'center', behavior: 'smooth' })
      setTimeout(() => (highlightId.value = null), 2400)
    }
  }
  if (inList) {
    doScroll()
    return
  }
  chat
    .loadMessages(chat.activeId as number)
    .catch(() => {})
    .then(doScroll)
}

// ── 会话切换 / 滚动 ─────────────────────────────────────
async function onSelectConversation(id: number) {
  if (chat.activeId === id) return
  ctxMenu.value = null
  replyTo.value = null
  searchOpen.value = false
  await chat.selectConversation(id)
  scrollToBottom(true)
}

watch(
  () => chat.activeMessages.length,
  () => {
    if (chat.activeId) {
      scrollToBottom()
      for (const m of chat.activeMessages.slice(-20)) ensureReaders(m)
    }
  }
)

function scrollToBottom(force = false) {
  nextTick(() => {
    const el = msgListEl.value
    if (!el) return
    if (force) {
      el.scrollTop = el.scrollHeight
      return
    }
    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 120
    if (nearBottom) el.scrollTop = el.scrollHeight
  })
}

function onMsgScroll() {
  const el = msgListEl.value
  if (!el) return
  if (el.scrollTop < 60) chat.loadMore().catch(() => {})
}

// ── 发送（支持引用回复） ─────────────────────────────────
const replyTo = ref<ChatMessageItem | null>(null)

function setReply(m: ChatMessageItem) {
  ctxMenu.value = null
  replyTo.value = m
  nextTick(() => textareaEl.value?.focus())
}

function cancelReply() {
  replyTo.value = null
}

function send() {
  const id = chat.activeId
  if (!id || sending.value) return
  const text = draft.value.trim()
  if (!text) return
  draft.value = ''
  sending.value = true
  chat
    .sendMessage(id, text, 'TEXT', null, replyTo.value?.id ?? null)
    .then((msg) => {
      replyTo.value = null
      ensureReaders(msg)
    })
    .catch((e: any) => {
      message.error(e.message || '发送失败')
      draft.value = text
    })
    .finally(() => {
      sending.value = false
    })
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    send()
  }
}

function onPickImage(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  uploadAndSend(file, true)
}

function onPickFile(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  uploadAndSend(file, false)
}

async function uploadAndSend(file: File | undefined, isImage: boolean) {
  if (!file || !chat.activeId) return
  sending.value = true
  try {
    const att = await chat.uploadAttachment(file)
    const img = isImage || att.fileType === 'IMAGE'
    const msg = await chat.sendMessage(chat.activeId, file.name, img ? 'IMAGE' : 'FILE', att.id, replyTo.value?.id ?? null)
    replyTo.value = null
    ensureReaders(msg)
  } catch (e: any) {
    message.error(e.message || '发送失败')
  } finally {
    sending.value = false
  }
}

function downloadFile(m: ChatMessageItem) {
  if (!m.attachment) return
  chatApi
    .downloadAttachment(m.attachment.id, m.attachment.originalName)
    .catch((e: any) => message.error(e.message || '下载失败'))
}

// ── 表情面板 ────────────────────────────────────────────
const EMOJIS = [
  '😀','😄','😁','😆','😂','🤣','😊','😇','🙂','😉','😍','🥰','😘','😋','😎','🤩',
  '😏','😜','🤪','😭','😅','😳','🤔','🙄','😴','🤯','😱','🥳','😡','🤗','🤫','😶',
  '👍','👎','👏','🙏','🤝','💪','✌️','🤞','👌','🤙','🫶','❤️','🧡','💛','💚','💙',
  '💜','🖤','💯','🔥','✨','🎉','🎊','🌟','⭐','💡','📌','✅','❌','⏰','📅','📎',
  '🔗','👀','💬','📢','🚀','🍀','🌈','☕','🍺','🎁','🏆','🎯','🧩','📈','💼'
]
const emojiOpen = ref(false)

function insertEmoji(e: string) {
  const el = textareaEl.value
  if (el) {
    const start = el.selectionStart ?? draft.value.length
    const end = el.selectionEnd ?? draft.value.length
    draft.value = draft.value.slice(0, start) + e + draft.value.slice(end)
    nextTick(() => {
      el.focus()
      const pos = start + e.length
      el.setSelectionRange(pos, pos)
    })
  } else {
    draft.value += e
  }
  emojiOpen.value = false
}

// ── 右键菜单 ────────────────────────────────────────────
const ctxMenu = ref<{ x: number; y: number; msg: ChatMessageItem } | null>(null)

function onBubbleContext(e: MouseEvent, m: ChatMessageItem) {
  if (m.msgType === 'SYSTEM' || m.recalled) return
  e.preventDefault()
  e.stopPropagation()
  const pad = 8
  const x = Math.min(e.clientX, window.innerWidth - 190 - pad)
  const y = Math.min(e.clientY, window.innerHeight - 210 - pad)
  ctxMenu.value = { x, y, msg: m }
}

function closeCtx() {
  ctxMenu.value = null
  convCtx.value = null
}

const ctxItems = computed(() => {
  const m = ctxMenu.value?.msg
  if (!m) return []
  const items: Array<{ key: string; label: string; danger?: boolean }> = [
    { key: 'copy', label: '复制' },
    { key: 'quote', label: '引用回复' },
    { key: 'forward', label: '转发' }
  ]
  if (isMine(m) && m.msgType !== 'SYSTEM') {
    const age = Date.now() - new Date(m.createTime).getTime()
    if (age < 2 * 60 * 1000) items.push({ key: 'recall', label: '撤回', danger: true })
  }
  return items
})

// ── 会话列表右键菜单（钉钉式） ──────────────────────────────
const convCtx = ref<{ x: number; y: number; conv: ChatConversationItem } | null>(null)

function onConvContext(e: MouseEvent, c: ChatConversationItem) {
  e.preventDefault()
  e.stopPropagation()
  closeCtx()
  const pad = 8
  const x = Math.min(e.clientX, window.innerWidth - 200 - pad)
  const y = Math.min(e.clientY, window.innerHeight - 270 - pad)
  convCtx.value = { x, y, conv: c }
}

const convCtxItems = computed(() => {
  const c = convCtx.value?.conv
  if (!c) return []
  const items: { key: string; label: string; danger?: boolean }[] = [
    { key: 'pin', label: c.pinned ? '取消置顶' : '置顶会话' },
    { key: 'unread', label: '标为未读' },
    { key: 'muted', label: c.muted ? '取消消息免打扰' : '消息免打扰' }
  ]
  if (c.type === 'SINGLE') items.push({ key: 'info', label: '个人信息' })
  items.push(
    { key: 'clear', label: '清空聊天记录' },
    { key: 'hide', label: '不显示该会话', danger: true }
  )
  return items
})

async function onQuickPin(c: ChatConversationItem) {
  try {
    await chat.togglePin(c.id, !c.pinned)
  } catch (e) {
    console.error('[ChatView] 置顶失败', e)
    message.error((e as Error)?.message || '置顶失败')
  }
}

async function onConvCtxAction(key: string) {
  const c = convCtx.value?.conv
  convCtx.value = null
  if (!c) return
  switch (key) {
    case 'pin':
      await chat.togglePin(c.id, !c.pinned)
      break
    case 'unread':
      await chat.markConversationUnread(c.id)
      break
    case 'muted':
      await chat.updateMutedConversation(c.id, !c.muted)
      break
    case 'info':
      // 个人信息 / 群信息：切换到该会话并打开设置抽屉
      if (chat.activeId !== c.id) await chat.selectConversation(c.id)
      showSettingDrawer.value = true
      break
    case 'clear':
      chat.clearMessagesLocal(c.id)
      break
    case 'hide':
      chat.removeConversation(c.id)
      break
  }
}

/** 单聊：我发的消息是否被对方已读 */
const singleReadText = computed(() => (m: ChatMessageItem) => {
  if (!isMine(m) || m.recalled || m.msgType === 'SYSTEM') return ''
  const conv = activeConv.value
  if (!conv || conv.type !== 'SINGLE' || !conv.otherUser) return ''
  // 已加载过已读详情缓存则优先（接口权威）
  const ids = readersOf(m)
  if (ids.length) return ids.includes(Number(conv.otherUser.userId)) ? '已读' : '未读'
  // 后端已读位点兜底：我发的消息 id <= 对方已读位点 → 已读
  const peerRead = conv.peerReadMessageId
  if (peerRead != null && Number(m.id) <= Number(peerRead)) return '已读'
  return '未读'
})

async function copyMsg(m: ChatMessageItem) {
  try {
    if (m.msgType === 'IMAGE' && m.attachment) {
      const url = imgUrls.value[m.attachment.id]
      await navigator.clipboard.writeText(url || `[图片] ${m.attachment.originalName}`)
      message.success('已复制图片')
    } else if (m.msgType === 'FILE') {
      await navigator.clipboard.writeText(`[文件] ${m.attachment?.originalName || m.content || ''}`)
      message.success('已复制文件信息')
    } else {
      await navigator.clipboard.writeText(m.content || '')
      message.success('已复制')
    }
  } catch {
    message.error('复制失败')
  }
  closeCtx()
}

async function onCtxAction(key: string) {
  const m = ctxMenu.value?.msg
  if (!m) return
  if (key === 'copy') {
    copyMsg(m)
  } else if (key === 'quote') {
    setReply(m)
  } else if (key === 'forward') {
    forwardTarget.value = m
    forwardOpen.value = true
    closeCtx()
  } else if (key === 'recall') {
    closeCtx()
    try {
      await chat.recallMessage(m.id)
      message.success('已撤回')
    } catch (e: any) {
      message.error(e.message || '撤回失败')
    }
  }
}

function onWindowClick() {
  closeCtx()
  emojiOpen.value = false
}

// ── 转发弹窗 ────────────────────────────────────────────
const forwardOpen = ref(false)
const forwardTarget = ref<ChatMessageItem | null>(null)
const forwarding = ref(false)
const forwardConvs = computed(() => chat.conversations.filter((c) => c.id !== chat.activeId))

async function confirmForward(c: ChatConversationItem) {
  if (!forwardTarget.value || forwarding.value) return
  forwarding.value = true
  try {
    await chat.forwardMessage(forwardTarget.value.id, c.id)
    forwardOpen.value = false
    message.success(`已转发到「${convTitle(c)}」`)
  } catch (e: any) {
    message.error(e.message || '转发失败')
  } finally {
    forwarding.value = false
  }
}

// ── 会话内消息搜索 ──────────────────────────────────────
async function runSearch() {
  if (!chat.activeId || !searchKw.value.trim() || searchLoading.value) return
  searchLoading.value = true
  try {
    searchResults.value = await chatApi.searchMessages(chat.activeId, searchKw.value.trim())
    if (!searchResults.value.length) message.info('没有找到匹配的消息')
  } catch (e: any) {
    message.error(e.message || '搜索失败')
  } finally {
    searchLoading.value = false
  }
}

function openSearchPanel() {
  searchKw.value = ''
  searchResults.value = []
  searchOpen.value = true
  nextTick(() => searchInputEl.value?.focus())
}

// ── 成员管理 ────────────────────────────────────────────
const membersOpen = ref(false)
const addOpen = ref(false)
const addIds = ref<number[]>([])
const renameOpen = ref(false)
const renameName = ref('')

async function openMembers() {
  if (!chat.activeId) return
  membersOpen.value = true
  await chat.loadConversationMembers(chat.activeId).catch(() => {})
}

async function openAdd() {
  if (!chat.activeId) return
  addIds.value = []
  addOpen.value = true
  await chat.loadAvailableMembers()
}

const addableMembers = computed(() => {
  const q = memberQuery.value.trim().toLowerCase()
  const existing = new Set(convMembers.value.map((m) => m.userId))
  return chat.availableMembers.filter(
    (m) =>
      !existing.has(m.userId) &&
      (!q || m.username.toLowerCase().includes(q) || (m.nickname || '').toLowerCase().includes(q))
  )
})

async function confirmAdd() {
  if (!chat.activeId || !addIds.value.length) return
  try {
    await chat.addMembers(chat.activeId, addIds.value)
    addOpen.value = false
    message.success('已添加成员')
    if (membersOpen.value) openMembers()
  } catch (e: any) {
    message.error(e.message || '添加失败')
  }
}

async function onRemoveMember(m: ChatMemberItem) {
  if (!chat.activeId) return
  try {
    await chat.removeMember(chat.activeId, m.userId)
    message.success('已移除')
  } catch (e: any) {
    message.error(e.message || '操作失败')
  }
}

async function onLeave() {
  if (!chat.activeId) return
  const conv = chat.activeConversation
  if (!conv) return
  // 群主点的是"解散群聊"，走 dissolve；非群主"退出群聊"，走 leaveConversation
  try {
    if (conv.type === 'GROUP' && Number(conv.creatorId) === myId.value) {
      await chat.dissolveGroup(chat.activeId)
      message.success('群聊已解散')
    } else {
      await chat.leaveConversation(chat.activeId, myId.value)
      message.success('已退出群聊')
    }
  } catch (e: any) {
    message.error(e?.message || '操作失败')
  }
}

/** 仅本地抹除当前会话的历史消息（不影响服务器，不影响对方） */
function onClearLocal() {
  if (!chat.activeId) return
  chat.clearMessagesLocal(chat.activeId)
  message.success('已清空本地聊天记录')
}

/** 仅本地移除该会话（不影响服务器）；单聊=删除聊天，群聊=同上 */
function onRemoveLocal() {
  if (!chat.activeId) return
  chat.removeConversation(chat.activeId)
  showSettingDrawer.value = false
  message.success('已从本地移除该会话')
}

function onTogglePinFromDrawer(v: boolean) {
  if (!chat.activeId) return
  chat.togglePin(chat.activeId, v)
}

async function onDissolve() {
  if (!chat.activeId) return
  try {
    await chat.dissolve(chat.activeId)
    message.success('群聊已解散')
  } catch (e: any) {
    message.error(e.message || '操作失败')
  }
}

function openRename() {
  renameName.value = activeConv.value?.name || ''
  renameOpen.value = true
}

async function confirmRename() {
  if (!chat.activeId || !renameName.value.trim()) return
  try {
    await chat.renameConversation(chat.activeId, renameName.value.trim())
    renameOpen.value = false
    message.success('已改名')
  } catch (e: any) {
    message.error(e.message || '改名失败')
  }
}

const groupActions = computed(() => {
  const list: any[] = [{ label: '查看成员', key: 'members' }]
  if (isOwner.value) list.push({ label: '添加成员', key: 'add' })
  if (isOwner.value) list.push({ label: '修改群名', key: 'rename' })
  list.push({ label: '退出群聊', key: 'leave' })
  if (isOwner.value) list.push({ label: '解散群聊', key: 'dissolve', danger: true })
  return list
})

function onGroupAction(key: string) {
  if (key === 'members') openMembers()
  else if (key === 'add') openAdd()
  else if (key === 'rename') openRename()
  else if (key === 'leave') onLeave()
  else if (key === 'dissolve') onDissolve()
}

// ── 展示工具 ────────────────────────────────────────────
function displayName(m: ChatMemberItem) {
  return m.nickname || m.username
}

function isMine(m: ChatMessageItem): boolean {
  const self = myId.value
  // myId 还没拿到（auth 未就绪）时，为避免误判，不把任何消息判成 mine
  if (!self || self <= 0) return false
  // senderId 可能是 string/number，统一 Number 比较
  const sid = Number(m.senderId)
  return sid === self
}

function senderName(m: ChatMessageItem): string {
  return m.sender?.nickname || m.sender?.username || `用户${m.senderId}`
}

function msgSummary(m: ChatMessageItem | null | undefined): string {
  if (!m) return ''
  if (m.recalled) return '[消息已撤回]'
  if (m.msgType === 'IMAGE') return '[图片]'
  if (m.msgType === 'FILE') return `[文件] ${m.attachment?.originalName || m.content || ''}`
  if (m.msgType === 'SYSTEM') return m.content || ''
  return m.content || ''
}

// ── 钉钉式时间格式：长版（气泡下方/分割线用）+ 短版（会话列表列用） ──
const _weekZh = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
const _pad = (n: number) => String(n).padStart(2, '0')

function _startOfDay(d: Date): Date {
  const x = new Date(d)
  x.setHours(0, 0, 0, 0)
  return x
}

function isSameDay(a: Date, b: Date): boolean {
  return _startOfDay(a).getTime() === _startOfDay(b).getTime()
}

function isYesterday(d: Date, now: Date): boolean {
  const y = _startOfDay(now)
  y.setDate(y.getDate() - 1)
  return _startOfDay(d).getTime() === y.getTime()
}

function dayDiff(d: Date, now: Date): number {
  // 跨年时会自动校正：取整后除以 86400000
  return Math.floor((_startOfDay(now).getTime() - _startOfDay(d).getTime()) / 86400000)
}

/** 长版（气泡下方/日分隔）：刚刚 / x 分钟前 / 今天 HH:MM / 昨天 HH:MM / 星期X HH:MM / YYYY-MM-DD HH:MM */
function formatTimeFull(ts: string | null | undefined): string {
  if (!ts) return ''
  const d = new Date(ts)
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  const diff = Math.floor((now.getTime() - d.getTime()) / 1000) // 总秒
  if (diff < 0) return _pad(d.getHours()) + ':' + _pad(d.getMinutes())
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  const hm = _pad(d.getHours()) + ':' + _pad(d.getMinutes())
  if (isSameDay(d, now)) return `今天 ${hm}`
  if (isYesterday(d, now)) return `昨天 ${hm}`
  const days = dayDiff(d, now)
  if (days > 0 && days < 7) return `${_weekZh[d.getDay()]} ${hm}`
  // 跨年自动走 YYYY-MM-DD
  if (d.getFullYear() === now.getFullYear()) {
    return `${_pad(d.getMonth() + 1)}-${_pad(d.getDate())} ${hm}`
  }
  return `${d.getFullYear()}-${_pad(d.getMonth() + 1)}-${_pad(d.getDate())} ${hm}`
}

/** 短版（会话列右侧）：刚刚 / HH:MM / 昨天 / MM-DD / 星期X / YYYY-MM-DD */
function formatTimeShort(ts: string | null | undefined): string {
  if (!ts) return ''
  const d = new Date(ts)
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  const diff = Math.floor((now.getTime() - d.getTime()) / 1000)
  if (diff >= 0 && diff < 60) return '刚刚'
  const hm = _pad(d.getHours()) + ':' + _pad(d.getMinutes())
  if (isSameDay(d, now)) return hm
  if (isYesterday(d, now)) return '昨天'
  const days = dayDiff(d, now)
  if (days > 0 && days < 7) return _weekZh[d.getDay()]
  if (d.getFullYear() === now.getFullYear()) return `${_pad(d.getMonth() + 1)}-${_pad(d.getDate())}`
  return `${d.getFullYear()}-${_pad(d.getMonth() + 1)}-${_pad(d.getDate())}`
}

/** 保留兼容：之前用 timeText 的位置仍可正常工作（默认走短版） */
function timeText(ts: string | null | undefined): string {
  return formatTimeFull(ts)
}

function convTime(c: ChatConversationItem): string {
  return formatTimeShort(c.lastMessage?.createTime || c.createTime)
}

function formatSize(bytes: number): string {
  if (!bytes) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`
}

function fileExt(name: string): string {
  const idx = name.lastIndexOf('.')
  return idx >= 0 && idx < name.length - 1 ? name.slice(idx + 1).toUpperCase() : 'FILE'
}

// ── 头像（默认无图时：白底黑字 + 名称前两字/两字母；圆角矩形） ──
// 白底黑字最素净专业；有 avatarUrl 时仍会渲染成图片，本函数只服务于「字当头像」的 fallback。
const AVATAR_BG = '#ffffff'

function avatarBg(_seed?: string | number, _isGroup = false): string {
  // 一律白底，由 CSS 提供 1px 浅灰描边，克制专业
  return AVATAR_BG
}

function avatarText(name: string): string {
  const v = (name || '?').trim()
  if (!v) return '?'
  // 中文：取前 2 字；英文/拼音：取前 2 个字母并大写
  const cleaned = v.replace(/\s+/g, '')
  if (/^[A-Za-z]+$/.test(cleaned)) {
    return cleaned.slice(0, 2).toUpperCase()
  }
  // 中文/混合：取前 2 个字符
  return cleaned.slice(0, 2) || '?'
}

function convTitle(c: ChatConversationItem): string {
  if (c.type === 'GROUP') return c.name || '群聊'
  const u = c.otherUser
  return u ? u.nickname || u.username : c.name || '会话'
}

function convAvatarText(c: ChatConversationItem): string {
  if (c.type === 'GROUP') return ''
  return avatarText(convTitle(c))
}

function convSub(c: ChatConversationItem): string {
  if (!c.lastMessage) return '暂无消息'
  const m = c.lastMessage
  if (c.type === 'GROUP' && m.msgType !== 'SYSTEM' && Number(m.senderId) !== myId.value) {
    return `${senderName(m)}: ${msgSummary(m)}`
  }
  return msgSummary(m)
}

function statusText(s: string | null | undefined): string {
  if (!s || s === 'OFFLINE') return '离线'
  if (s === 'ONLINE') return '在线'
  if (s === 'AWAY') return '离开'
  if (s === 'BUSY') return '忙碌'
  return '离线'
}

// ── 消息时间分组（相邻超过 5 分钟显示分隔条） ──
function showTimeSep(idx: number): boolean {
  if (idx === 0) return true
  const prev = activeMessages.value[idx - 1]
  const cur = activeMessages.value[idx]
  const t1 = new Date(prev.createTime).getTime()
  const t2 = new Date(cur.createTime).getTime()
  if (isNaN(t1) || isNaN(t2)) return false
  return t2 - t1 > 5 * 60 * 1000
}

// ── 富文本渲染：代码块 / 行内代码 / URL / 换行 ──
function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function renderRichText(text: string | null | undefined): string {
  if (!text) return ''
  const blocks: string[] = []
  const placeholder = text.replace(/```([\w+#-]*)\n?([\s\S]*?)```/g, (_m, lang, code) => {
    const clean = String(code || '').replace(/\n$/, '')
    blocks.push(
      `<div class="code-block"><div class="code-lang">${escapeHtml(String(lang || 'code'))}</div><pre><code>${escapeHtml(clean)}</code></pre></div>`
    )
    return `\u0000CB${blocks.length - 1}\u0000`
  })
  let html = escapeHtml(placeholder)
  html = html.replace(/(https?:\/\/[^\s<]+)/g, '<a href="$1" target="_blank" rel="noopener noreferrer" class="msg-link">$1</a>')
  html = html.replace(/`([^`\n]+)`/g, '<code class="inline-code">$1</code>')
  html = html.replace(/\n/g, '<br/>')
  html = html.replace(/\u0000CB(\d+)\u0000/g, (_m, i) => blocks[Number(i)])
  return html
}

// 整段为单个链接 → 渲染为链接卡片
function singleLink(text: string | null | undefined): string | null {
  if (!text) return null
  const t = text.trim()
  if (!t || t.includes('\n')) return null
  const m = t.match(/^https?:\/\/\S+$/)
  return m ? m[0] : null
}

function linkDomain(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return url
  }
}

function linkFavicon(url: string): string {
  try {
    const u = new URL(url)
    return `https://${u.hostname}/favicon.ico`
  } catch {
    return ''
  }
}

// ── 生命周期 ────────────────────────────────────────────
onMounted(() => {
  chat.loadConversations().catch(() => {})
  chat.loadAvailableMembers().catch(() => {})
  window.addEventListener('click', onWindowClick)
  window.addEventListener('scroll', onWindowClick, true)
  window.addEventListener('keydown', onPreviewKey)
})

onBeforeUnmount(() => {
  window.removeEventListener('click', onWindowClick)
  window.removeEventListener('scroll', onWindowClick, true)
  window.removeEventListener('keydown', onPreviewKey)
  for (const url of Object.values(imgUrls.value)) {
    try {
      URL.revokeObjectURL(url)
    } catch {
    }
  }
})
</script>

<template>
  <div class="chat-page" @contextmenu.prevent="closeCtx">
    <!-- 左栏：会话列表 -->
    <aside class="conv-panel">
      <div class="conv-head">
        <div class="conv-actions">
          <button class="icon-btn" title="发起私聊" @click.stop="openCreate('single')">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            </svg>
          </button>
          <button class="icon-btn" title="创建群聊" @click.stop="openCreate('group')">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <path d="M9 11.2a2.8 2.8 0 1 1 5.6 0 2.8 2.8 0 0 1-5.6 0Z" stroke="currentColor" stroke-width="1.6" />
              <path d="M5.5 17.5c.8-2.2 2.7-3.5 5.2-3.5 1 0 1.9.2 2.7.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
              <path d="M15.5 10.3a2 2 0 1 1 3.9 0 2 2 0 0 1-3.9 0Z" stroke="currentColor" stroke-width="1.6" />
              <path d="M17.8 15.9c.6-1.3 1.8-2.1 3.4-2.1.8 0 1.5.2 2 .5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            </svg>
          </button>
        </div>
      </div>

      <div class="conv-search">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" class="search-icon">
          <circle cx="11" cy="11" r="6.5" stroke="currentColor" stroke-width="1.8" />
          <path d="m16 16 4 4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
        </svg>
        <input v-model="searchQuery" class="search-input" placeholder="搜索会话" />
      </div>

      <div class="conv-list">
        <n-spin :show="chat.loading && !chat.conversations.length" class="conv-spin">
          <template v-if="!chat.conversations.length && !chat.loading">
            <div class="conv-empty">
              <n-empty description="暂无会话" :show-description="false" />
              <span class="conv-empty-tip">点击右上角发起私聊或创建群聊</span>
            </div>
          </template>

          <button
            v-for="c in filteredConversations"
            :key="c.id"
            class="conv-item"
            :class="{ active: chat.activeId === c.id }"
            @click="onSelectConversation(c.id)"
            @contextmenu.prevent.stop="onConvContext($event, c)"
          >
            <span class="conv-avatar" :style="{ background: avatarBg(c.type === 'GROUP' ? c.id : convTitle(c), c.type === 'GROUP') }">
              <img v-if="c.type === 'SINGLE' && c.otherUser?.avatarUrl" :src="c.otherUser.avatarUrl" alt="" />
              <template v-else-if="c.type === 'GROUP'">
                <svg class="group-avatar-ico" width="17" height="17" viewBox="0 0 24 24" fill="none">
                  <path d="M9 11.2a2.8 2.8 0 1 1 5.6 0 2.8 2.8 0 0 1-5.6 0Z" stroke="currentColor" stroke-width="1.7" />
                  <path d="M5.5 17.5c.8-2.2 2.7-3.5 5.2-3.5 1 0 1.9.2 2.7.6" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
                  <path d="M15.5 10.3a2 2 0 1 1 3.9 0 2 2 0 0 1-3.9 0Z" stroke="currentColor" stroke-width="1.7" />
                  <path d="M17.8 15.9c.6-1.3 1.8-2.1 3.4-2.1.8 0 1.5.2 2 .5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
                </svg>
              </template>
              <template v-else>{{ convAvatarText(c) }}</template>
              <span
                v-if="c.type === 'SINGLE' && c.otherUser"
                class="presence-dot"
                :class="'st-' + (c.otherUser.status || 'OFFLINE')"
              />
            </span>
            <span class="conv-body">
              <span class="conv-line">
                <span class="conv-name">
                  <span v-if="c.pinned" class="conv-pin">置顶</span>{{ convTitle(c) }}
                </span>
                <span class="conv-meta">
                  <button
                    class="conv-pin-btn"
                    :class="{ pinned: c.pinned }"
                    :title="c.pinned ? '取消置顶' : '置顶会话'"
                    @click.stop="onQuickPin(c)"
                  >
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M14 3l7 7-3 1-4 4-1 3-7-7 3-1 4-4z" />
                      <path d="M3 21l5-5" />
                    </svg>
                  </button>
                  <span class="conv-time">
                  <svg v-if="c.muted" class="conv-muted-ico" width="13" height="13" viewBox="0 0 24 24" fill="none">
                    <path d="M6 9v6a1 1 0 0 0 1 1h1.3a2 2 0 0 1 1.6.8l1.9 2.4a.6.6 0 0 0 1.2-.2V7.2a.6.6 0 0 0-1.2-.2L9.9 9.4a2 2 0 0 1-1.6.8H7a1 1 0 0 0-1 1Z" fill="currentColor" opacity="0.85" />
                    <path d="m9.5 7.5 9 9M18.5 7.5l-9 9" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
                  </svg>
                  {{ convTime(c) }}
                </span>
                </span>
              </span>
              <span class="conv-line">
                <span class="conv-last" :class="{ unread: c.unreadCount > 0 }">{{ convSub(c) }}</span>
                <span v-if="c.unreadCount > 0" class="badge">{{ c.unreadCount > 99 ? '99+' : c.unreadCount }}</span>
              </span>
            </span>
          </button>
        </n-spin>
      </div>
    </aside>

    <!-- 右栏：聊天窗口 -->
    <section class="chat-panel">
      <template v-if="activeConv">
        <header class="chat-head">
          <div class="chat-head-left">
            <span class="head-avatar" :style="{ background: avatarBg(activeConv.type === 'GROUP' ? activeConv.id : convTitle(activeConv), activeConv.type === 'GROUP') }">
              <img v-if="activeConv.type === 'SINGLE' && activeConv.otherUser?.avatarUrl" :src="activeConv.otherUser.avatarUrl" alt="" />
              <template v-else-if="activeConv.type === 'GROUP'">
                <svg class="group-avatar-ico" width="16" height="16" viewBox="0 0 24 24" fill="none">
                  <path d="M9 11.2a2.8 2.8 0 1 1 5.6 0 2.8 2.8 0 0 1-5.6 0Z" stroke="currentColor" stroke-width="1.7" />
                  <path d="M5.5 17.5c.8-2.2 2.7-3.5 5.2-3.5 1 0 1.9.2 2.7.6" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
                  <path d="M15.5 10.3a2 2 0 1 1 3.9 0 2 2 0 0 1-3.9 0Z" stroke="currentColor" stroke-width="1.7" />
                  <path d="M17.8 15.9c.6-1.3 1.8-2.1 3.4-2.1.8 0 1.5.2 2 .5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
                </svg>
              </template>
              <template v-else>{{ avatarText(convTitle(activeConv)) }}</template>
            </span>
            <div class="head-text">
              <div class="head-name">{{ convTitle(activeConv) }}</div>
              <div class="head-sub">
                <template v-if="activeConv.type === 'GROUP'">
                  {{ activeConv.memberCount }} 位成员
                </template>
                <template v-else>
                  {{ statusText(activeConv.otherUser?.status) }}
                </template>
              </div>
            </div>
          </div>
          <div class="chat-head-right">
            <button class="head-btn head-search" title="搜索聊天记录" @click="openSearchPanel">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="11" cy="11" r="6.5" />
                <path d="m16 16 4.2 4.2" />
              </svg>
            </button>
            <button class="head-btn" :title="isGroup ? '群设置' : '聊天信息'" @click="openSettingDrawer">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                <!-- 双气泡对话图标（聊天的"信息"语义） -->
                <path d="M4 7.5a2.5 2.5 0 0 1 2.5-2.5h7A2.5 2.5 0 0 1 14 7.5v3a2.5 2.5 0 0 1-2.5 2.5H8l-2.6 2.1a.4.4 0 0 1-.65-.32v-1.78A2.5 2.5 0 0 1 4 10.5z" />
                <path d="M10 12.5a2.5 2.5 0 0 1 2.5-2.5h5A2.5 2.5 0 0 1 20 12.5v5a2.5 2.5 0 0 1-2.5 2.5h-2l-1.6 1.4a.4.4 0 0 1-.65-.32v-1.08" />
              </svg>
            </button>
            <n-popconfirm
              v-if="isGroup && !isOwner"
              positive-text="退出"
              negative-text="取消"
              @positive-click="onLeave"
            >
              <template #trigger>
                <button class="head-btn">退出</button>
              </template>
              退出该群聊？退群后将无法继续接收消息。
            </n-popconfirm>
            <n-dropdown v-if="isGroup" :options="groupActions" @select="onGroupAction">
              <button class="head-btn head-more">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                  <circle cx="5" cy="12" r="1.4" fill="currentColor" />
                  <circle cx="12" cy="12" r="1.4" fill="currentColor" />
                  <circle cx="19" cy="12" r="1.4" fill="currentColor" />
                </svg>
              </button>
            </n-dropdown>
          </div>
        </header>

        <div class="msg-scroll" ref="msgListEl" @scroll.passive="onMsgScroll">
          <div class="msg-inner">
            <div v-if="chat.messagesLoading && !chat.activeMessages.length" class="msg-loading">
              <n-spin size="small" />
            </div>
            <div v-if="!chat.activeMessages.length && !chat.messagesLoading" class="msg-empty">
              <n-empty description="开始聊天吧" :show-description="false" />
            </div>

            <template v-for="(m, idx) in activeMessages" :key="m.id">
              <!-- 时间分隔条 -->
              <div v-if="showTimeSep(idx)" class="time-sep">{{ timeText(m.createTime) }}</div>

              <!-- 系统消息 -->
              <div v-if="m.msgType === 'SYSTEM'" class="sys-msg">{{ m.content }}</div>

              <!-- 普通消息 -->
              <div
                v-else
                :id="`msg-${m.id}`"
                class="bubble-row"
                :class="{ mine: isMine(m), recalled: m.recalled, highlight: highlightId === m.id }"
                @contextmenu.prevent="onBubbleContext($event, m)"
              >
                <span class="bubble-avatar" :style="{ background: avatarBg(senderName(m)) }">
                  <img v-if="m.sender.avatarUrl" :src="m.sender.avatarUrl" alt="" />
                  <template v-else>{{ avatarText(senderName(m)) }}</template>
                </span>
                <div class="bubble-body">
                  <div v-if="isGroup && !isMine(m)" class="bubble-name">{{ senderName(m) }}</div>
                  <div class="bubble-wrap">
                    <!-- 引用回复卡片 -->
                    <div v-if="m.replyTo" class="reply-card" @click="jumpToMessage(m.replyTo.id)">
                      <div class="reply-head">{{ isMine(m) ? '我' : m.replyTo.sender.nickname || m.replyTo.sender.username }}</div>
                      <div class="reply-text">{{ msgSummary(m.replyTo) }}</div>
                    </div>

                    <div class="bubble">
                      <!-- 消息 hover 快捷操作（飞书/钉钉式） -->
                      <div v-if="!m.recalled" class="msg-actions">
                        <button class="ma-btn" title="引用回复" @click.stop="setReply(m)">
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                            <path d="M9 8.5 4.5 12 9 15.5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
                            <path d="M4.5 12h11a4 4 0 0 1 4 4v.5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
                          </svg>
                        </button>
                        <button class="ma-btn" title="更多操作" @click.stop="onBubbleContext($event, m)">
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                            <circle cx="5" cy="12" r="1.4" fill="currentColor" />
                            <circle cx="12" cy="12" r="1.4" fill="currentColor" />
                            <circle cx="19" cy="12" r="1.4" fill="currentColor" />
                          </svg>
                        </button>
                      </div>
                      <!-- 已撤回 -->
                      <span v-if="m.recalled" class="recalled-text">消息已撤回</span>
                      <template v-else-if="m.msgType === 'IMAGE' && m.attachment">
                        <div
                          v-if="imgUrls[m.attachment.id]"
                          class="msg-img"
                          @click.stop="openImagePreview(imgUrls[m.attachment.id])"
                        >
                          <img :src="imgUrls[m.attachment.id]" alt="" loading="lazy" />
                          <span class="msg-img-mask">
                            <svg width="15" height="15" viewBox="0 0 24 24" fill="none">
                              <circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="1.7" />
                              <path d="m16 16 4.5 4.5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
                            </svg>
                            查看大图
                          </span>
                        </div>
                        <span v-else class="img-loading-tip">图片加载中…</span>
                      </template>
                      <template v-else-if="m.msgType === 'FILE' && m.attachment">
                        <button class="file-card" @click="downloadFile(m)">
                          <span class="file-ico">{{ fileExt(m.attachment.originalName) }}</span>
                          <span class="file-meta">
                            <span class="file-name">{{ m.attachment.originalName }}</span>
                            <span class="file-size">{{ formatSize(m.attachment.sizeBytes) }} · 点击下载</span>
                          </span>
                          <svg class="file-dl" width="16" height="16" viewBox="0 0 24 24" fill="none">
                            <path d="M12 4v10m0 0 4-4m-4 4-4-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
                            <path d="M5 18.5h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                          </svg>
                        </button>
                      </template>
                      <!-- 链接卡片 -->
                      <template v-else-if="singleLink(m.content)">
                        <a
                          :href="singleLink(m.content)!"
                          target="_blank"
                          rel="noopener noreferrer"
                          class="link-card"
                        >
                          <span class="link-card-fav">
                            <img v-if="linkFavicon(singleLink(m.content)!)" :src="linkFavicon(singleLink(m.content)!)" alt="" @error="(e) => ((e.target as HTMLImageElement).style.display = 'none')" />
                          </span>
                          <span class="link-card-meta">
                            <span class="link-card-domain">{{ linkDomain(singleLink(m.content)!) }}</span>
                            <span class="link-card-url">{{ singleLink(m.content) }}</span>
                          </span>
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" class="link-card-go">
                            <path d="M7 17 17 7M9 7h8v8" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
                          </svg>
                        </a>
                      </template>
                      <template v-else>
                        <span class="text-msg" v-html="renderRichText(m.content)"></span>
                      </template>
                    </div>

                    <div class="bubble-foot">
                      <span class="bubble-time">{{ timeText(m.createTime) }}</span>
                      <!-- 单聊：对方已读回执 -->
                      <span
                        v-if="singleReadText(m)"
                        class="read-state"
                        :class="{ 'read-ok': singleReadText(m) === '已读' }"
                      >{{ singleReadText(m) }}</span>
                      <!-- 群聊：已读 N 人 -->
                      <span
                        v-if="readCountText(m)"
                        class="read-state"
                        :title="'查看已读详情'"
                        @click="openReaders(m)"
                      >{{ readCountText(m) }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>

        <footer class="chat-input">
          <!-- 引用回复提示条 -->
          <div v-if="replyTo" class="reply-bar">
            <span class="reply-bar-ico">↩</span>
            <span class="reply-bar-text">
              回复 <b>{{ isMine(replyTo) ? '我' : senderName(replyTo) }}</b>：{{ msgSummary(replyTo) }}
            </span>
            <button class="reply-bar-close" title="取消引用" @click="cancelReply">✕</button>
          </div>
          <div class="input-card">
            <div class="input-toolbar">
              <button class="tool-btn" title="发送图片" @click="imageInput?.click()">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                  <rect x="4" y="5" width="16" height="14" rx="2" stroke="currentColor" stroke-width="1.6" />
                  <circle cx="9" cy="10" r="1.5" stroke="currentColor" stroke-width="1.6" />
                  <path d="m4.5 17 4.5-4 3 2.5 3.5-3.5 4 3.5" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" />
                </svg>
              </button>
              <button class="tool-btn" title="发送文件" @click="fileInput?.click()">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                  <path d="M14 3.5H8a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2v-11l-4-4Z" stroke="currentColor" stroke-width="1.6" />
                  <path d="M14 3.5v4h4" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" />
                </svg>
              </button>
              <div class="emoji-wrap">
                <button class="tool-btn" title="表情" @click.stop="emojiOpen = !emojiOpen">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                    <circle cx="12" cy="12" r="8.5" stroke="currentColor" stroke-width="1.6" />
                    <path d="M8.7 14.5a4.5 4.5 0 0 0 6.6 0" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
                    <circle cx="9" cy="9.6" r="0.9" fill="currentColor" />
                    <circle cx="15" cy="9.6" r="0.9" fill="currentColor" />
                  </svg>
                </button>
                <div v-if="emojiOpen" class="emoji-panel" @click.stop>
                  <div class="emoji-grid">
                    <button v-for="e in EMOJIS" :key="e" class="emoji-cell" @click="insertEmoji(e)">{{ e }}</button>
                  </div>
                </div>
              </div>
              <input ref="imageInput" type="file" accept="image/*" hidden @change="onPickImage" />
              <input ref="fileInput" type="file" hidden @change="onPickFile" />
            </div>
            <div class="input-area">
              <textarea
                ref="textareaEl"
                v-model="draft"
                class="msg-textarea"
                placeholder="输入消息，Enter 发送，Shift+Enter 换行"
                :disabled="sending"
                @keydown="onKeydown"
              />
              <button class="send-btn" :disabled="sending || !draft.trim()" @click="send">
                <n-spin v-if="sending" size="small" :stroke-width="2" />
                <template v-else>发送</template>
              </button>
            </div>
          </div>
        </footer>
      </template>

      <div v-else class="chat-empty">
        <div class="chat-empty-ico">
          <svg width="44" height="44" viewBox="0 0 24 24" fill="none">
            <path d="M20 12c0 4.42-3.58 8-8 8-1.3 0-2.53-.31-3.62-.87L4 20l.9-3.7A7.97 7.97 0 0 1 4 12c0-4.42 3.58-8 8-8s8 3.58 8 8Z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round" />
            <path d="M8.3 12h.01M12 12h.01M15.7 12h.01" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" />
          </svg>
        </div>
        <div class="chat-empty-title">选择会话开始沟通</div>
        <div class="chat-empty-sub">支持成员间私聊、多人群聊，以及图片和文件传输</div>
        <div class="chat-empty-actions">
          <button class="primary-btn" @click="openCreate('single')">发起私聊</button>
          <button class="ghost-btn" @click="openCreate('group')">创建群聊</button>
        </div>
      </div>
    </section>

    <!-- 右键菜单 -->
    <teleport to="body">
      <div
        v-if="ctxMenu"
        class="ctx-menu"
        :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
        @click.stop
      >
        <button
          v-for="it in ctxItems"
          :key="it.key"
          class="ctx-item"
          :class="{ danger: it.danger }"
          @click="onCtxAction(it.key)"
        >{{ it.label }}</button>
      </div>
    </teleport>

    <!-- 会话列表右键菜单（钉钉式） -->
    <teleport to="body">
      <div
        v-if="convCtx"
        class="ctx-menu conv-ctx-menu"
        :style="{ left: convCtx.x + 'px', top: convCtx.y + 'px' }"
        @click.stop
      >
        <button
          v-for="it in convCtxItems"
          :key="it.key"
          class="ctx-item"
          :class="{ danger: it.danger }"
          @click="onConvCtxAction(it.key)"
        >{{ it.label }}</button>
      </div>
    </teleport>

    <!-- 图片大图预览 -->
    <teleport to="body">
      <div
        v-if="imgPreviewUrl"
        class="img-preview-layer"
        @click.self="closeImagePreview"
        @wheel.prevent="onPreviewWheel"
        tabindex="-1"
      >
        <div class="img-preview-card">
          <img :src="imgPreviewUrl" alt="图片预览" :style="{ transform: `scale(${imgPreviewScale})` }" />
          <div class="img-preview-bar">
            <span class="img-preview-hint">滚轮缩放 · Esc 关闭</span>
            <button class="img-preview-btn" @click.stop="imgPreviewScale = Math.min(3, imgPreviewScale + 0.25)">放大</button>
            <button class="img-preview-btn" @click.stop="imgPreviewScale = Math.max(0.5, imgPreviewScale - 0.25)">缩小</button>
            <button class="img-preview-btn" @click.stop="imgPreviewScale = 1">1:1</button>
            <button class="img-preview-btn img-preview-close" @click.stop="closeImagePreview">关闭</button>
          </div>
        </div>
      </div>
    </teleport>

    <!-- 创建会话弹窗 -->
    <n-modal
      v-model:show="createOpen"
      preset="card"
      :title="createMode === 'single' ? '发起私聊' : '创建群聊'"
      class="create-modal"
      :style="{ width: '420px' }"
      :bordered="false"
    >
      <div class="modal-body">
        <n-input
          v-if="createMode === 'group'"
          v-model:value="groupName"
          placeholder="群名称（必填）"
          class="modal-field"
          :maxlength="32"
        />
        <div class="member-pick">
          <n-input v-model:value="memberQuery" placeholder="搜索成员" size="small" class="modal-field" />
          <div class="member-list">
            <label
              v-for="m in filteredMembers"
              :key="m.userId"
              class="member-row"
            >
              <input
                :type="createMode === 'single' ? 'radio' : 'checkbox'"
                :name="'pick' + createMode"
                :checked="selectedIds.includes(m.userId)"
                class="member-check"
                @change="toggleMember(m.userId)"
              />
              <span class="member-avatar" :style="{ background: avatarBg(m.username) }">
                <img v-if="m.avatarUrl" :src="m.avatarUrl" alt="" />
                <template v-else>{{ avatarText(displayName(m)) }}</template>
              </span>
              <span class="member-info">
                <span class="member-name">{{ displayName(m) }}</span>
                <span class="member-username">@{{ m.username }}</span>
              </span>
            </label>
            <div v-if="!filteredMembers.length" class="member-empty">没有可选的成员</div>
          </div>
        </div>
        <div class="modal-foot">
          <n-button size="small" quaternary @click="createOpen = false">取消</n-button>
          <n-button size="small" type="primary" :loading="creating" @click="confirmCreate">
            {{ createMode === 'single' ? '开始聊天' : '创建群聊' }}
          </n-button>
        </div>
      </div>
    </n-modal>

    <!-- 成员管理弹窗 -->
    <n-modal
      v-model:show="membersOpen"
      preset="card"
      :title="`群成员（${convMembers.length}）`"
      class="members-modal"
      :style="{ width: '400px' }"
      :bordered="false"
    >
      <div class="modal-body">
        <div class="member-list">
          <div v-for="m in convMembers" :key="m.userId" class="member-row member-row-static">
            <span class="member-avatar" :style="{ background: avatarBg(m.username) }">
              <img v-if="m.avatarUrl" :src="m.avatarUrl" alt="" />
              <template v-else>{{ avatarText(displayName(m)) }}</template>
            </span>
            <span class="member-info">
              <span class="member-name">
                {{ displayName(m) }}
                <n-tag v-if="Number(m.userId) === activeConv?.creatorId" size="tiny" type="info" :bordered="false">群主</n-tag>
                <n-tag v-else-if="Number(m.userId) === myId" size="tiny" type="default" :bordered="false">我</n-tag>
              </span>
              <span class="member-username">@{{ m.username }} · {{ statusText(m.status) }}</span>
            </span>
            <n-button
              v-if="isOwner && Number(m.userId) !== myId"
              size="tiny"
              quaternary
              type="error"
              @click="onRemoveMember(m)"
            >
              移除
            </n-button>
          </div>
        </div>
        <div v-if="isOwner" class="modal-foot">
          <n-button size="small" type="primary" @click="openAdd">添加成员</n-button>
        </div>
      </div>
    </n-modal>

    <!-- 添加成员弹窗 -->
    <n-modal
      v-model:show="addOpen"
      preset="card"
      title="添加成员"
      class="members-modal"
      :style="{ width: '400px' }"
      :bordered="false"
    >
      <div class="modal-body">
        <n-input v-model:value="memberQuery" placeholder="搜索成员" size="small" class="modal-field" />
        <div class="member-list">
          <label v-for="m in addableMembers" :key="m.userId" class="member-row">
            <input
              type="checkbox"
              :checked="addIds.includes(m.userId)"
              class="member-check"
              @change="toggleAdd(m.userId)"
            />
            <span class="member-avatar" :style="{ background: avatarBg(m.username) }">
              <img v-if="m.avatarUrl" :src="m.avatarUrl" alt="" />
              <template v-else>{{ avatarText(displayName(m)) }}</template>
            </span>
            <span class="member-info">
              <span class="member-name">{{ displayName(m) }}</span>
              <span class="member-username">@{{ m.username }}</span>
            </span>
          </label>
          <div v-if="!addableMembers.length" class="member-empty">所有成员都已在群里</div>
        </div>
        <div class="modal-foot">
          <n-button size="small" quaternary @click="addOpen = false">取消</n-button>
          <n-button size="small" type="primary" :disabled="!addIds.length" @click="confirmAdd">确认添加</n-button>
        </div>
      </div>
    </n-modal>

    <!-- 修改群名弹窗 -->
    <n-modal
      v-model:show="renameOpen"
      preset="card"
      title="修改群名"
      class="members-modal"
      :style="{ width: '360px' }"
      :bordered="false"
    >
      <div class="modal-body">
        <n-input v-model:value="renameName" placeholder="群名称" :maxlength="32" class="modal-field" />
        <div class="modal-foot">
          <n-button size="small" quaternary @click="renameOpen = false">取消</n-button>
          <n-button size="small" type="primary" :disabled="!renameName.trim()" @click="confirmRename">保存</n-button>
        </div>
      </div>
    </n-modal>

    <!-- 转发弹窗 -->
    <n-modal
      v-model:show="forwardOpen"
      preset="card"
      title="转发消息"
      class="members-modal"
      :style="{ width: '400px' }"
      :bordered="false"
    >
      <div class="modal-body">
        <div v-if="forwardTarget" class="forward-preview">
          <span class="forward-tag">转发</span>
          <span class="forward-text">{{ msgSummary(forwardTarget) }}</span>
        </div>
        <div class="member-list">
          <button
            v-for="c in forwardConvs"
            :key="c.id"
            class="conv-row"
            :disabled="forwarding"
            @click="confirmForward(c)"
          >
            <span class="member-avatar" :style="{ background: avatarBg(c.type === 'GROUP' ? c.id : convTitle(c), c.type === 'GROUP') }">
              <img v-if="c.type === 'SINGLE' && c.otherUser?.avatarUrl" :src="c.otherUser.avatarUrl" alt="" />
              <template v-else>{{ c.type === 'GROUP' ? '群' : avatarText(convTitle(c)) }}</template>
            </span>
            <span class="member-info">
              <span class="member-name">{{ convTitle(c) }}</span>
              <span class="member-username">{{ c.type === 'GROUP' ? `${c.memberCount} 位成员` : '单聊' }}</span>
            </span>
            <n-spin v-if="forwarding" size="small" />
            <span v-else class="forward-arrow">→</span>
          </button>
          <div v-if="!forwardConvs.length" class="member-empty">没有可转发的会话</div>
        </div>
        <div class="modal-foot">
          <n-button size="small" quaternary @click="forwardOpen = false">取消</n-button>
        </div>
      </div>
    </n-modal>

    <!-- 会话内消息搜索弹窗 -->
    <n-modal
      v-model:show="searchOpen"
      preset="card"
      title="搜索聊天记录"
      class="search-modal"
      :style="{ width: '520px' }"
      :bordered="false"
    >
      <div class="modal-body">
        <div class="search-bar">
          <n-input
            ref="searchInputEl"
            v-model:value="searchKw"
            placeholder="输入关键词，回车搜索本会话消息"
            size="medium"
            clearable
            @keydown.enter="runSearch"
          />
          <n-button size="medium" type="primary" :loading="searchLoading" @click="runSearch">搜索</n-button>
        </div>
        <div class="search-results">
          <n-spin :show="searchLoading" class="search-spin">
            <template v-if="searchResults.length">
              <button
                v-for="r in searchResults"
                :key="r.id"
                class="search-result-row"
                @click="jumpToMessage(r.id)"
              >
                <span class="search-result-head">
                  <b>{{ senderName(r) }}</b>
                  <span class="search-result-time">{{ timeText(r.createTime) }}</span>
                </span>
                <span class="search-result-text">{{ msgSummary(r) }}</span>
              </button>
            </template>
            <div v-else-if="!searchLoading && searchKw" class="member-empty">没有找到匹配的消息</div>
            <div v-else-if="!searchLoading" class="member-empty">输入关键词搜索本会话内的文本消息</div>
          </n-spin>
        </div>
      </div>
    </n-modal>

    <!-- 已读详情弹窗 -->
    <n-modal
      v-model:show="readersModal"
      preset="card"
      :title="`已读详情（${readersMembers.length}）`"
      class="members-modal"
      :style="{ width: '400px' }"
      :bordered="false"
    >
      <div class="modal-body">
        <div class="member-list">
          <div v-for="m in readersMembers" :key="m.userId" class="member-row member-row-static">
            <span class="member-avatar" :style="{ background: avatarBg(m.username) }">
              <img v-if="m.avatarUrl" :src="m.avatarUrl" alt="" />
              <template v-else>{{ avatarText(displayName(m)) }}</template>
            </span>
            <span class="member-info">
              <span class="member-name">{{ displayName(m) }}</span>
              <span class="member-username">@{{ m.username }}</span>
            </span>
            <n-tag size="tiny" type="success" :bordered="false">已读</n-tag>
          </div>
          <div v-if="!readersMembers.length" class="member-empty">还没有成员阅读</div>
        </div>
      </div>
    </n-modal>

    <ChatSettingDrawer
      :show="showSettingDrawer"
      :conversation="activeConv"
      :my-id="myId"
      @update:show="(v: boolean) => (showSettingDrawer = v)"
      @leave="onLeave"
      @clear-local="onClearLocal"
      @remove-local="onRemoveLocal"
      @toggle-pin="onTogglePinFromDrawer"
    />
  </div>
</template>

<style scoped>
/* 钉钉式端正字体：本页面所有元素强制使用全局 font-stack 兜底，避免被 fallback 到奇怪的字体 */
.chat-page,
.chat-page * {
  font-family: var(--app-font,
    'PingFang SC', 'Microsoft YaHei', 'Hiragino Sans GB',
    'Source Han Sans SC', 'Noto Sans CJK SC', 'Segoe UI',
    'Helvetica Neue', Inter, Arial, sans-serif);
}
.chat-page {
  height: calc(100vh - 52px);
  display: flex;
  overflow: hidden;
  background: #fff;
  box-sizing: border-box;
}

/* ── 左栏 ─────────────────────────────── */
.conv-panel {
  width: 300px;
  flex: 0 0 300px;
  min-width: 0;
  border-right: 1px solid #e7e9ed;
  display: flex;
  flex-direction: column;
  background: #fafbfc;
}
.conv-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px 8px;
}
.conv-actions {
  display: flex;
  gap: 4px;
}
.icon-btn {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: #646a73;
  cursor: pointer;
  display: grid;
  place-items: center;
}
.icon-btn:hover {
  background: #eef1f4;
  color: var(--brand);
}
.conv-search {
  position: relative;
  margin: 0 14px 10px;
  box-sizing: border-box;
}
.search-icon {
  position: absolute;
  left: 9px;
  top: 50%;
  transform: translateY(-50%);
  color: #98a0aa;
  pointer-events: none;
  z-index: 1;
}
.search-input {
  width: 100%;
  height: 32px;
  border: 1px solid #e7e9ed;
  border-radius: 8px;
  background: #fff;
  padding: 0 10px 0 32px;
  font-size: 13px;
  font-weight: 500;
  color: #1f2329;
  outline: none;
  box-sizing: border-box;
  transition: border-color 120ms ease;
  font-family: inherit;
}
.search-input:focus {
  border-color: var(--brand);
}
.conv-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 10px;
  min-height: 0;
}
.conv-list::-webkit-scrollbar {
  width: 5px;
}
.conv-list::-webkit-scrollbar-thumb {
  background: #d7dbe1;
  border-radius: 3px;
}
.conv-spin {
  width: 100%;
}
.conv-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 40px 0;
}
.conv-empty-tip {
  font-size: 12px;
  color: #98a0aa;
}
.conv-item {
  position: relative;
  width: 100%;
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px;
  border: none;
  background: transparent;
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
  box-sizing: border-box;
}
.conv-item:hover {
  background: #eef1f4;
}
.conv-item.active {
  background: var(--brand-soft);
}
.conv-item.active::before {
  content: '';
  position: absolute;
  left: -8px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 20px;
  border-radius: 2px;
  background: var(--brand);
}
.conv-avatar {
  position: relative;
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  border-radius: 8px;
  color: #303540;
  background: #ffffff;
  border: 1px solid #e3e5e8;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.02em;
  display: grid;
  place-items: center;
  overflow: hidden;
  box-sizing: border-box;
}
.conv-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.group-avatar-ico {
  opacity: 0.92;
}
.presence-dot {
  position: absolute;
  right: -1px;
  bottom: -1px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid #fafbfc;
  background: #c2c8d0;
  box-sizing: border-box;
}
.presence-dot.st-ONLINE {
  background: #0e9f6e;
}
.presence-dot.st-AWAY {
  background: #d97706;
}
.presence-dot.st-BUSY {
  background: #db2777;
}
.conv-body {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 3px;
}
.conv-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.conv-name {
  font-size: 14px;
  font-weight: 600;
  color: #303540;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  letter-spacing: 0.01em;
}
.conv-pin {
  display: inline-block;
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
  padding: 2px 4px;
  margin-right: 6px;
  border-radius: 3px;
  color: #fff;
  background: var(--brand);
  vertical-align: 2px;
}
.conv-time {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  color: #98a0aa;
  font-weight: 500;
  flex: 0 0 auto;
}
.conv-meta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
}
.conv-pin-btn {
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: #b8bfc9;
  border-radius: 4px;
  cursor: pointer;
  opacity: 0;
  padding: 0;
  transition: opacity 0.15s, background 0.15s, color 0.15s;
}
.conv-item:hover .conv-pin-btn { opacity: 1; }
.conv-pin-btn:hover { background: #eef1f5; color: #6b7280; }
.conv-pin-btn.pinned {
  opacity: 1;
  color: var(--brand);
  background: rgba(30, 113, 255, 0.1);
}
.conv-pin-btn.pinned:hover { background: rgba(30, 113, 255, 0.18); color: var(--brand); }
.conv-muted-ico {
  color: #98a0aa;
  flex: 0 0 auto;
}
.conv-last {
  font-size: 13px;
  color: #6b7280;
  font-weight: 500;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  flex: 1;
}
.conv-last.unread {
  color: #303540;
  font-weight: 600;
}
.badge {
  min-width: 17px;
  height: 17px;
  padding: 0 5px;
  border-radius: 9px;
  background: #e5484d;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  box-sizing: border-box;
}

/* ── 右栏 ─────────────────────────────── */
.chat-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
}
.chat-head {
  height: 58px;
  flex: 0 0 58px;
  border-bottom: 1px solid #eef0f3;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.chat-head-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.head-avatar {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  color: #303540;
  background: #ffffff;
  border: 1px solid #e3e5e8;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.02em;
  display: grid;
  place-items: center;
  overflow: hidden;
  flex: 0 0 36px;
  box-sizing: border-box;
}
.head-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.head-text {
  display: grid;
  gap: 1px;
  min-width: 0;
}
.head-name {
  font-size: 16px;
  font-weight: 600;
  color: #303540;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  letter-spacing: 0.01em;
}
.head-sub {
  font-size: 12px;
  color: #8b9199;
  font-weight: 500;
}
.chat-head-right {
  display: flex;
  align-items: center;
  gap: 6px;
}
.head-btn {
  height: 32px;
  padding: 0 14px;
  border: 1px solid #e2e6ea;
  border-radius: 8px;
  background: #fff;
  color: #303540;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  font-family: inherit;
  transition: background 120ms ease, border-color 120ms ease;
}
.head-btn:hover {
  background: #f7f8fa;
  border-color: #d8dde2;
}
.head-btn:hover {
  border-color: var(--brand);
  color: var(--brand);
}
.head-search {
  width: 28px;
  padding: 0;
  display: grid;
  place-items: center;
}
.head-more {
  width: 28px;
  padding: 0;
  display: grid;
  place-items: center;
}

/* 消息区 */
.msg-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 16px 18px;
  background: #f7f8fa;
  min-height: 0;
}
.msg-scroll::-webkit-scrollbar {
  width: 5px;
}
.msg-scroll::-webkit-scrollbar-thumb {
  background: #d7dbe1;
  border-radius: 3px;
}
.msg-inner {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 100%;
  box-sizing: border-box;
}
.msg-loading,
.msg-empty {
  display: grid;
  place-items: center;
  padding: 30px 0;
}
.time-sep {
  align-self: center;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 11px;
  color: #a0a6ad;
  padding: 2px 0;
  user-select: none;
}
.time-sep::before,
.time-sep::after {
  content: '';
  height: 1px;
  width: 48px;
  background: #e6e9ee;
}
.sys-msg {
  align-self: center;
  font-size: 12px;
  color: #8b9199;
  background: #eef0f3;
  border-radius: 10px;
  padding: 4px 12px;
  max-width: 70%;
  text-align: center;
  line-height: 1.5;
}
.bubble-row {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  border-radius: 6px;
  padding: 3px 6px;
  margin: -3px -6px;
}
.bubble-row.mine {
  flex-direction: row-reverse;
}
.bubble-row.highlight {
  background: rgba(30, 64, 175, 0.12);
  transition: background-color 200ms ease;
}
.bubble-avatar {
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  border-radius: 8px;
  color: #303540;
  background: #ffffff;
  border: 1px solid #e3e5e8;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.02em;
  display: grid;
  place-items: center;
  overflow: hidden;
  box-sizing: border-box;
}
.bubble-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.bubble-body {
  max-width: 66%;
  display: grid;
  gap: 3px;
  min-width: 0;
}
.bubble-row.mine .bubble-body {
  align-items: flex-end;
}
.bubble-name {
  font-size: 13px;
  font-weight: 600;
  color: #5b6168;
  padding: 0 2px;
}
.bubble-wrap {
  position: relative;
  display: grid;
  gap: 3px;
  max-width: 100%;
  min-width: 0;
}
.bubble-row.mine .bubble-wrap {
  justify-items: end;
}
/* 消息 hover 快捷操作（飞书/钉钉式） */
.msg-actions {
  position: absolute;
  top: -13px;
  right: 6px;
  display: flex;
  gap: 2px;
  padding: 3px;
  background: #fff;
  border: 1px solid #e6e9ee;
  border-radius: 7px;
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.12);
  opacity: 0;
  pointer-events: none;
  transition: opacity 140ms ease;
  z-index: 4;
}
.bubble:hover .msg-actions {
  opacity: 1;
  pointer-events: auto;
}
.bubble-row.mine .msg-actions {
  right: auto;
  left: 6px;
}
.ma-btn {
  width: 26px;
  height: 26px;
  border: none;
  background: transparent;
  border-radius: 5px;
  color: #6b7280;
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: background-color 100ms ease, color 100ms ease;
}
.ma-btn:hover {
  background: var(--brand-soft);
  color: var(--brand);
}
.bubble {
  position: relative;
  border-radius: 2px 10px 10px 10px;
  background: #ffffff;
  border: 1px solid #eef0f2;
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.6;
  font-weight: 500;
  color: #303540;
  word-break: break-word;
  max-width: 100%;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.bubble-row.mine .bubble {
  border-radius: 10px 2px 10px 10px;
  background: #1E71FF; /* 钉钉式消息蓝：纯色而非渐变，文字白色端庄 */
  border-color: #1E71FF;
  color: #ffffff;
  box-shadow: 0 2px 6px rgba(30, 113, 255, 0.18);
}
.text-msg {
  white-space: pre-wrap;
}
.recalled-text {
  font-style: normal;
  color: #98a0aa;
  font-size: 12px;
}
.bubble-row.mine .recalled-text {
  color: rgba(255, 255, 255, 0.75);
}
.msg-img {
  position: relative;
  display: inline-block;
  max-width: 240px;
  max-height: 240px;
  border-radius: 8px;
  overflow: hidden;
  cursor: zoom-in;
  background: #f2f4f7;
  line-height: 0;
}
.msg-img img {
  display: block;
  width: auto;
  height: auto;
  max-width: 240px;
  max-height: 240px;
  object-fit: contain;
  border-radius: 8px;
}
.msg-img-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
  color: #fff;
  background: rgba(15, 23, 42, 0.42);
  opacity: 0;
  transition: opacity 150ms ease;
  border-radius: 8px;
}
.msg-img:hover .msg-img-mask {
  opacity: 1;
}
.img-loading-tip {
  font-size: 13px;
  font-weight: 500;
  color: #98a0aa;
}
.bubble-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 2px;
}
.bubble-row.mine .bubble-foot {
  justify-content: flex-end;
}
.bubble-time {
  display: none; /* 每条消息不再单独显示时间，只保留 5 分钟时间分隔条 */
  font-size: 12px;
  font-weight: 500;
  color: #b0b6bf;
}
.read-state {
  font-size: 12px;
  font-weight: 500;
  color: #8b9199;
  cursor: default;
  border-radius: 3px;
  padding: 0 3px;
}
.read-state.read-ok {
  color: var(--brand);
}
.bubble-row.mine .read-state {
  color: rgba(255, 255, 255, 0.78);
}
.bubble-row.mine .read-state.read-ok {
  color: rgba(255, 255, 255, 0.95);
}

/* 引用回复卡片 */
.reply-card {
  max-width: 100%;
  border-left: 3px solid var(--brand);
  background: rgba(30, 64, 175, 0.06);
  border-radius: 4px 8px 8px 4px;
  padding: 6px 10px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.5;
  min-width: 160px;
}
.bubble-row.mine .reply-card {
  background: rgba(255, 255, 255, 0.12);
  border-left-color: rgba(255, 255, 255, 0.85);
}
.reply-card:hover {
  background: rgba(30, 64, 175, 0.12);
}
.bubble-row.mine .reply-card:hover {
  background: rgba(255, 255, 255, 0.18);
}
.reply-head {
  font-weight: 600;
  color: var(--brand);
  font-size: 11.5px;
}
.bubble-row.mine .reply-head {
  color: #fff;
}
.reply-text {
  color: #6b7280;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 1px;
}
.bubble-row.mine .reply-text {
  color: rgba(255, 255, 255, 0.85);
}

/* 链接卡片 */
.link-card {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid #e2e6ea;
  border-radius: 8px;
  background: #fff;
  padding: 8px 12px;
  min-width: 220px;
  max-width: 320px;
  text-decoration: none;
  color: inherit;
  transition: border-color 120ms ease;
}
.bubble-row.mine .link-card {
  background: rgba(255, 255, 255, 0.95);
  border-color: rgba(255, 255, 255, 0.6);
}
.link-card:hover {
  border-color: var(--brand);
}
.link-card-fav {
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  border-radius: 6px;
  background: #f2f4f7;
  display: grid;
  place-items: center;
  overflow: hidden;
}
.link-card-fav img {
  width: 16px;
  height: 16px;
  object-fit: contain;
}
.link-card-meta {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 2px;
}
.link-card-domain {
  font-size: 13px;
  font-weight: 650;
  color: #1f2329;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.link-card-url {
  font-size: 12px;
  font-weight: 500;
  color: #8b9199;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.link-card-go {
  color: #98a0aa;
  flex: 0 0 auto;
}

/* 代码块 */
.code-block {
  margin: 6px 0 2px;
  border: 1px solid #e2e6ea;
  border-radius: 8px;
  overflow: hidden;
  background: #0f172a;
  max-width: 100%;
}
.code-lang {
  font-size: 11px;
  color: #94a3b8;
  padding: 5px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.04);
  font-family: ui-monospace, 'Cascadia Mono', Consolas, monospace;
}
.code-block pre {
  margin: 0;
  padding: 10px 12px;
  overflow-x: auto;
}
.code-block pre code {
  font-family: ui-monospace, 'Cascadia Mono', Consolas, 'SF Mono', Menlo, monospace;
  font-size: 12.5px;
  line-height: 1.6;
  color: #e2e8f0;
  white-space: pre;
}
.inline-code {
  font-family: ui-monospace, 'Cascadia Mono', Consolas, 'SF Mono', Menlo, monospace;
  font-size: 12px;
  background: rgba(30, 64, 175, 0.08);
  color: #1e50b5;
  border-radius: 4px;
  padding: 1px 5px;
}
.bubble-row.mine .inline-code {
  background: rgba(255, 255, 255, 0.16);
  color: #fff;
}
.msg-link {
  color: var(--brand);
  text-decoration: none;
  word-break: break-all;
}
.msg-link:hover {
  text-decoration: underline;
}
.bubble-row.mine .msg-link {
  color: #eaf2ff;
}

/* 文件卡片 */
.file-card {
  display: flex;
  align-items: center;
  gap: 10px;
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  text-align: left;
  min-width: 200px;
  max-width: 280px;
  color: inherit;
}
.file-ico {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 10px;
  font-weight: 800;
  display: grid;
  place-items: center;
  flex: 0 0 36px;
}
.bubble-row.mine .file-ico {
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
}
.file-meta {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 2px;
}
.file-name {
  font-size: 12.5px;
  font-weight: 600;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.file-size {
  font-size: 12px;
  font-weight: 500;
  color: #8b9199;
}
.bubble-row.mine .file-size {
  color: rgba(255, 255, 255, 0.75);
}
.file-dl {
  color: #8b9199;
  flex: 0 0 auto;
}
.bubble-row.mine .file-dl {
  color: #fff;
}

/* 输入区 */
.chat-input {
  flex: 0 0 auto;
  border-top: 1px solid #eef0f3;
  padding: 10px 14px 12px;
  background: #fff;
  z-index: 2;
}
.reply-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f4f7ff;
  border: 1px solid #dbe7ff;
  border-radius: 7px;
  padding: 6px 10px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #4b5563;
}
.reply-bar-ico {
  color: var(--brand);
  font-weight: 700;
}
.reply-bar-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.reply-bar-text b {
  color: var(--brand);
  font-weight: 600;
}
.reply-bar-close {
  border: none;
  background: transparent;
  color: #98a0aa;
  cursor: pointer;
  font-size: 12px;
  padding: 2px 4px;
  border-radius: 4px;
}
.reply-bar-close:hover {
  color: #1f2329;
  background: #e8eefb;
}
.input-toolbar {
  display: flex;
  gap: 4px;
  margin-bottom: 2px;
  position: relative;
}
.tool-btn {
  width: 30px;
  height: 30px;
  border: none;
  background: transparent;
  color: #646a73;
  border-radius: 6px;
  cursor: pointer;
  display: grid;
  place-items: center;
}
.tool-btn:hover {
  background: #f2f4f7;
  color: var(--brand);
}
.emoji-wrap {
  position: relative;
}
.emoji-panel {
  position: absolute;
  bottom: 36px;
  left: 0;
  z-index: 50;
  width: 320px;
  background: #fff;
  border: 1px solid #e7e9ed;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(31, 35, 41, 0.12);
  padding: 10px;
}
.emoji-grid {
  display: grid;
  grid-template-columns: repeat(10, 1fr);
  gap: 2px;
  max-height: 200px;
  overflow-y: auto;
}
.emoji-cell {
  border: none;
  background: transparent;
  font-size: 17px;
  line-height: 1;
  padding: 5px 0;
  border-radius: 5px;
  cursor: pointer;
  transition: background-color 100ms ease;
}
.emoji-cell:hover {
  background: #f2f4f7;
}
.input-area {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}
.msg-textarea {
  flex: 1;
  min-height: 38px;
  max-height: 120px;
  border: none;
  outline: none;
  resize: none;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.6;
  color: #1f2329;
  background: transparent;
  font-family: inherit;
}
.send-btn {
  height: 32px;
  padding: 0 20px;
  border: none;
  border-radius: 7px;
  background: var(--brand);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  display: grid;
  place-items: center;
  min-width: 64px;
}
.send-btn:hover:not(:disabled) {
  background: var(--brand-hover);
}
.send-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* 空态 */
.chat-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: #fafbfc;
}
.chat-empty-ico {
  width: 84px;
  height: 84px;
  border-radius: 24px;
  background: #eef2f7;
  color: #b6bdc7;
  display: grid;
  place-items: center;
  margin-bottom: 6px;
}
.chat-empty-title {
  font-size: 15px;
  font-weight: 650;
  color: #1f2329;
}
.chat-empty-sub {
  font-size: 13px;
  font-weight: 500;
  color: #98a0aa;
}
.chat-empty-actions {
  display: flex;
  gap: 10px;
  margin-top: 14px;
}
.primary-btn,
.ghost-btn {
  height: 32px;
  padding: 0 18px;
  border-radius: 7px;
  font-size: 13px;
  cursor: pointer;
}
.primary-btn {
  border: none;
  background: var(--brand);
  color: #fff;
  font-weight: 600;
}
.primary-btn:hover {
  background: var(--brand-hover);
}
.ghost-btn {
  border: 1px solid #d4d9e0;
  background: #fff;
  color: #4b5563;
}
.ghost-btn:hover {
  border-color: var(--brand);
  color: var(--brand);
}

/* 右键菜单 */
.ctx-menu {
  position: fixed;
  z-index: 3000;
  min-width: 150px;
  background: #fff;
  border: 1px solid #e2e6ea;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(31, 35, 41, 0.14);
  padding: 4px;
}
.conv-ctx-menu {
  min-width: 192px;
}
.ctx-item {
  display: block;
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  padding: 7px 12px;
  font-size: 12.5px;
  color: #1f2329;
  border-radius: 5px;
  cursor: pointer;
}
.ctx-item:hover {
  background: #f2f4f7;
}
.ctx-item.danger {
  color: #e5484d;
}
.ctx-item.danger:hover {
  background: #fef0f0;
}

/* 弹窗 */
.modal-body {
  display: grid;
  gap: 12px;
}
.modal-field {
  width: 100%;
}
.member-pick {
  display: grid;
  gap: 8px;
}
.member-list {
  max-height: 300px;
  overflow-y: auto;
  display: grid;
  gap: 2px;
}
.member-list::-webkit-scrollbar {
  width: 5px;
}
.member-list::-webkit-scrollbar-thumb {
  background: #d7dbe1;
  border-radius: 3px;
}
.member-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 8px;
  border-radius: 8px;
  cursor: pointer;
}
.member-row:hover {
  background: #f4f6f8;
}
.member-row-static {
  cursor: default;
}
.member-check {
  width: 15px;
  height: 15px;
  accent-color: var(--brand);
  flex: 0 0 15px;
}
.member-avatar {
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  border-radius: 8px;
  color: #303540;
  background: #ffffff;
  border: 1px solid #e3e5e8;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.02em;
  display: grid;
  place-items: center;
  overflow: hidden;
  box-sizing: border-box;
}
.member-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.member-info {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 1px;
}
.member-name {
  font-size: 14px;
  font-weight: 600;
  color: #303540;
  display: flex;
  align-items: center;
  gap: 6px;
}
.member-username {
  font-size: 12px;
  font-weight: 500;
  color: #8b9199;
}
.member-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: #98a0aa;
}
.modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 4px;
}
.conv-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 8px;
  border-radius: 8px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  width: 100%;
}
.conv-row:hover {
  background: #f4f6f8;
}
.forward-arrow {
  color: #98a0aa;
  font-size: 14px;
}
.forward-preview {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f7f8fa;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 12.5px;
}
.forward-tag {
  flex: 0 0 auto;
  background: var(--brand);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  border-radius: 4px;
  padding: 2px 7px;
}
.forward-text {
  color: #4b5563;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.search-bar {
  display: flex;
  gap: 8px;
  align-items: center;
}
.search-results {
  max-height: 380px;
  overflow-y: auto;
  display: grid;
  gap: 2px;
}
.search-spin {
  min-height: 80px;
}
.search-result-row {
  width: 100%;
  border: none;
  background: transparent;
  border-radius: 8px;
  padding: 9px 10px;
  cursor: pointer;
  text-align: left;
  display: grid;
  gap: 3px;
}
.search-result-row:hover {
  background: #f2f5fa;
}
.search-result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12.5px;
  color: #1f2329;
}
.search-result-head b {
  font-weight: 600;
}
.search-result-time {
  font-size: 12px;
  font-weight: 500;
  color: #98a0aa;
}
.search-result-text {
  font-size: 13.5px;
  font-weight: 500;
  color: #4b5563;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* ── 输入区卡片（飞书/钉钉式） ─────────────── */
.input-card {
  border: 1px solid #e2e6ea;
  border-radius: 10px;
  background: #fff;
  padding: 6px 10px 8px;
  transition: border-color 150ms ease, box-shadow 150ms ease;
}
.input-card:focus-within {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px rgba(30, 64, 175, 0.12);
}

/* ── 图片大图预览层 ─────────────────────────── */
.img-preview-layer {
  position: fixed;
  inset: 0;
  z-index: 4000;
  background: rgba(15, 23, 42, 0.72);
  backdrop-filter: blur(5px);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: img-preview-in 160ms ease;
}
@keyframes img-preview-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}
.img-preview-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  max-width: 92vw;
  max-height: 92vh;
  outline: none;
}
.img-preview-card img {
  max-width: 86vw;
  max-height: 78vh;
  border-radius: 8px;
  object-fit: contain;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.4);
  transition: transform 120ms ease;
  transform-origin: center;
  user-select: none;
  background: #000;
}
.img-preview-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 20px;
  padding: 5px 8px;
}
.img-preview-hint {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.75);
  padding: 0 6px;
}
.img-preview-btn {
  border: none;
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  font-size: 12px;
  height: 26px;
  padding: 0 12px;
  border-radius: 13px;
  cursor: pointer;
  transition: background-color 120ms ease;
}
.img-preview-btn:hover {
  background: rgba(255, 255, 255, 0.28);
}
.img-preview-close {
  background: var(--brand);
}
.img-preview-close:hover {
  background: var(--brand-hover);
}
</style>
