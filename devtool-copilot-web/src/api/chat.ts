import axios from 'axios'
import { apiDelete, apiGet, apiPatch, apiPost, type R } from './http'

export interface ChatSender {
  userId: number
  username: string
  nickname?: string | null
  avatarUrl?: string | null
  status?: string | null
}

export interface ChatAttachmentItem {
  id: number
  originalName: string
  contentType?: string | null
  fileType: 'IMAGE' | 'FILE'
  sizeBytes: number
  createTime?: string | null
}

export interface ChatMessageItem {
  id: number
  conversationId: number
  senderId: number
  sender: ChatSender
  msgType: 'TEXT' | 'IMAGE' | 'FILE' | 'SYSTEM'
  content?: string | null
  attachment?: ChatAttachmentItem | null
  replyTo?: ChatMessageItem | null
  recalled: boolean
  createTime: string
}

export interface ChatConversationItem {
  id: number
  type: 'SINGLE' | 'GROUP'
  name: string
  creatorId: number
  memberCount: number
  myRole: string
  unreadCount: number
  lastMessage?: ChatMessageItem | null
  otherUser?: ChatSender | null
  /** 是否置顶 */
  pinned?: boolean | null
  /** 是否消息免打扰 */
  muted?: boolean | null
  /** 单聊：对方已读到的最大消息 id（用于已读回执），群聊为 null */
  peerReadMessageId?: number | null
  createTime: string
}

export interface ChatMemberItem {
  userId: number
  username: string
  nickname?: string | null
  avatarUrl?: string | null
  email: string
  status?: string | null
}

export interface ChatConversationDetail {
  id: number
  type: 'SINGLE' | 'GROUP'
  name: string
  announcement?: string | null
  creatorId: number
  memberCount: number
  myRole: string
  muted: boolean
}

export interface ChatFileItem {
  attachmentId: number
  originalName: string
  contentType?: string | null
  fileType: 'IMAGE' | 'FILE'
  sizeBytes: number
  senderId: number
  senderName: string
  createTime?: string | null
}

/** WS 推送的聊天事件载荷（CHAT_MESSAGE / CHAT_MESSAGE_RECALLED） */
export interface ChatMessageEvent {
  type?: string
  conversationId: number
  message?: ChatMessageItem | null
  messageId?: number | null
  /** 已读事件：动作发起人 userId */
  userId?: number | null
  /** 已读事件：对方已读到的最大消息 id */
  lastReadMessageId?: number | null
}

function getAuthHeaders() {
  const token = localStorage.getItem('dtc_token')
  const headers: Record<string, string> = {}
  if (token) headers.Authorization = `Bearer ${token}`
  return headers
}

function parsePayload<T>(resp: { status: number; data: unknown }): T {
  const payload = resp.data as R<T> | undefined
  if (!payload || typeof payload.code !== 'number') throw new Error(`请求失败(${resp.status})`)
  if (payload.code !== 0) throw new Error(payload.message || '请求失败')
  return payload.data
}

export const chatApi = {
  /** 会话列表 */
  conversations() {
    return apiGet<ChatConversationItem[]>('/api/chat/conversations')
  },

  /** 创建或获取单聊会话 */
  createSingle(targetUserId: number) {
    return apiPost<ChatConversationItem>('/api/chat/conversations/single', { targetUserId })
  },

  /** 创建群聊 */
  createGroup(name: string, memberIds: number[]) {
    return apiPost<ChatConversationItem>('/api/chat/conversations/group', { name, memberIds })
  },

  /** 群聊改名 */
  rename(id: number, name: string) {
    return apiPatch<void>(`/api/chat/conversations/${id}`, { name })
  },

  /** 解散群聊 */
  dissolve(id: number) {
    return apiDelete<void>(`/api/chat/conversations/${id}`)
  },

  /** 历史消息（游标前翻） */
  messages(id: number, beforeId?: number | null, limit = 30) {
    return apiGet<ChatMessageItem[]>(`/api/chat/conversations/${id}/messages`, {
      beforeId: beforeId ?? undefined,
      limit
    })
  },

  /** 发送消息（支持引用回复 replyToId） */
  send(id: number, content: string, msgType: 'TEXT' | 'IMAGE' | 'FILE', attachmentId?: number | null, replyToId?: number | null) {
    return apiPost<ChatMessageItem>(`/api/chat/conversations/${id}/messages`, {
      content,
      msgType,
      attachmentId: attachmentId ?? null,
      replyToId: replyToId ?? null
    })
  },

  /** 撤回消息（仅本人、2 分钟内） */
  recall(messageId: number) {
    return apiPost<ChatMessageItem>(`/api/chat/messages/${messageId}/recall`, {})
  },

  /** 会话内消息搜索 */
  searchMessages(id: number, keyword: string, limit = 30) {
    return apiGet<ChatMessageItem[]>(`/api/chat/conversations/${id}/messages/search`, {
      q: keyword,
      limit
    })
  },

  /** 已读该消息的成员 userId 列表 */
  messageReaders(id: number, messageId: number) {
    return apiGet<number[]>(`/api/chat/conversations/${id}/messages/${messageId}/readers`)
  },

  /** 转发消息到目标会话 */
  forward(messageId: number, targetConversationId: number) {
    return apiPost<ChatMessageItem>(`/api/chat/messages/${messageId}/forward`, {
      targetConversationId
    })
  },

  /** 标记已读 */
  markRead(id: number) {
    return apiPost<ChatMessageItem | null>(`/api/chat/conversations/${id}/read`, {})
  },

  /** 置顶会话 */
  pin(id: number) {
    return apiPut<void>(`/api/chat/conversations/${id}/pin`)
  },

  /** 取消置顶 */
  unpin(id: number) {
    return apiDelete<void>(`/api/chat/conversations/${id}/pin`)
  },

  /** 标为未读 */
  markUnread(id: number) {
    return apiPost<void>(`/api/chat/conversations/${id}/unread`, {})
  },

  /** 可聊天成员（全部启用用户，排除自己） */
  members() {
    return apiGet<ChatMemberItem[]>('/api/chat/members')
  },

  /** 某会话成员列表 */
  conversationMembers(id: number) {
    return apiGet<ChatMemberItem[]>(`/api/chat/conversations/${id}/members`)
  },

  /** 加人 */
  addMembers(id: number, userIds: number[]) {
    return apiPost<void>(`/api/chat/conversations/${id}/members`, { userIds })
  },

  /** 移除成员 / 自己退群 */
  removeMember(id: number, userId: number) {
    return apiDelete<void>(`/api/chat/conversations/${id}/members/${userId}`)
  },

  /** 上传附件 */
  async uploadAttachment(file: File): Promise<ChatAttachmentItem> {
    const form = new FormData()
    form.append('file', file)
    const resp = await axios.request({
      url: '/api/chat/attachments',
      method: 'POST',
      headers: getAuthHeaders(),
      data: form,
      validateStatus: () => true
    })
    return parsePayload<ChatAttachmentItem>(resp)
  },

  /** 会话详情（含群公告、成员数、免打扰状态） */
  conversationDetail(id: number) {
    return apiGet<ChatConversationDetail>(`/api/chat/conversations/${id}/detail`)
  },

  /** 更新群公告 */
  updateAnnouncement(id: number, announcement: string) {
    return apiPut<void>(`/api/chat/conversations/${id}/announcement`, { announcement })
  },

  /** 会话共享文件列表（type: IMAGE / FILE / 空=全部） */
  sharedFiles(id: number, type?: string) {
    return apiGet<ChatFileItem[]>(`/api/chat/conversations/${id}/files`, { type: type ?? undefined })
  },

  /** 更新免打扰状态 */
  updateMuted(id: number, muted: boolean) {
    return apiPatch<void>(`/api/chat/conversations/${id}/settings`, { muted })
  },

  /** 附件内联图片地址 */
  attachmentImageUrl(id: number) {
    return `/api/chat/attachments/${id}/image`
  },

  /** 附件预览地址 */
  attachmentPreviewUrl(id: number) {
    return `/api/chat/attachments/${id}/preview`
  },

  /** 附件下载 */
  async downloadAttachment(id: number, filename: string) {
    const resp = await fetch(`/api/chat/attachments/${id}/download`, {
      method: 'GET',
      headers: getAuthHeaders()
    })
    if (!resp.ok) throw new Error(`下载失败(${resp.status})`)
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename || 'file'
    document.body.appendChild(a)
    a.click()
    a.remove()
    setTimeout(() => URL.revokeObjectURL(url), 4000)
  }
}
