<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  NButton,
  NDrawer,
  NDrawerContent,
  NEmpty,
  NInput,
  NSpin,
  NSwitch,
  NTab,
  NTabs,
  useMessage
} from 'naive-ui'
import { chatApi, type ChatConversationDetail, type ChatConversationItem, type ChatFileItem, type ChatMemberItem } from '../api/chat'
import { fetchWithAuth } from '../api/http'

const props = defineProps<{
  show: boolean
  conversation: ChatConversationItem | null
  myId: number
}>()

const emit = defineEmits<{
  (e: 'update:show', v: boolean): void
  (e: 'leave'): void
  (e: 'clear-local'): void
  (e: 'remove-local'): void
  (e: 'toggle-pin', v: boolean): void
}>()

function onClearLocal() {
  emit('clear-local')
}

function onRemoveLocal() {
  emit('remove-local')
}

const message = useMessage()

const detail = ref<ChatConversationDetail | null>(null)
const members = ref<ChatMemberItem[]>([])
const files = ref<ChatFileItem[]>([])
const loadingDetail = ref(false)
const loadingFiles = ref(false)
const activeTab = ref<'ALL' | 'IMAGE' | 'FILE'>('ALL')
const imgUrls = ref<Record<number, string>>({})
const editingAnnouncement = ref(false)
const announcementDraft = ref('')
const savingAnnouncement = ref(false)
const previewImg = ref('')

const isGroup = computed(() => props.conversation?.type === 'GROUP')
const isOwner = computed(() => detail.value != null && Number(detail.value.creatorId) === Number(props.myId))
const conversationId = computed(() => props.conversation?.id)

// 默认无图：白底黑字（素净专业），不再使用彩色渐变色板
const AVATAR_BG_DEFAULT = '#ffffff'

function avatarBg(_seed?: string | number): string {
  // 统一克制：默认返回白底（与 ChatView 一致），描边由 CSS 提供
  return '#ffffff'
}

function avatarText(name: string): string {
  const v = (name || '?').trim()
  if (!v) return '?'
  const cleaned = v.replace(/\s+/g, '')
  if (/^[A-Za-z]+$/.test(cleaned)) {
    return cleaned.slice(0, 2).toUpperCase()
  }
  return cleaned.slice(0, 2) || '?'
}

function memberName(m: ChatMemberItem): string {
  return m.nickname || m.username
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

async function loadDetail() {
  const id = conversationId.value
  if (id == null) return
  loadingDetail.value = true
  try {
    const [d, ms] = await Promise.all([
      chatApi.conversationDetail(id),
      chatApi.conversationMembers(id)
    ])
    detail.value = d
    members.value = ms
    announcementDraft.value = d.announcement || ''
    editingAnnouncement.value = false
  } catch (e: any) {
    message.error(e?.message || '加载会话信息失败')
  } finally {
    loadingDetail.value = false
  }
}

async function loadFiles() {
  const id = conversationId.value
  if (id == null) return
  loadingFiles.value = true
  try {
    files.value = await chatApi.sharedFiles(id, activeTab.value === 'ALL' ? undefined : activeTab.value)
    files.value.forEach((f) => {
      if (f.fileType === 'IMAGE') void ensureImageUrl(f.attachmentId)
    })
  } catch (e: any) {
    message.error(e?.message || '加载共享文件失败')
  } finally {
    loadingFiles.value = false
  }
}

async function ensureImageUrl(attachmentId: number): Promise<string | undefined> {
  if (imgUrls.value[attachmentId]) return imgUrls.value[attachmentId]
  try {
    const resp = await fetchWithAuth(`/api/chat/attachments/${attachmentId}/image`)
    if (!resp.ok) return undefined
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    imgUrls.value[attachmentId] = url
    return url
  } catch {
    return undefined
  }
}

async function openPreview(attachmentId: number) {
  const url = await ensureImageUrl(attachmentId)
  if (url) previewImg.value = url
  else message.error('图片加载失败')
}

async function onTabChange() {
  await loadFiles()
}

async function saveAnnouncement() {
  const id = conversationId.value
  if (id == null) return
  savingAnnouncement.value = true
  try {
    await chatApi.updateAnnouncement(id, announcementDraft.value)
    detail.value!.announcement = announcementDraft.value
    editingAnnouncement.value = false
    message.success('公告已更新')
  } catch (e: any) {
    message.error(e?.message || '更新公告失败')
  } finally {
    savingAnnouncement.value = false
  }
}

async function onMutedChange(v: boolean) {
  const id = conversationId.value
  if (id == null) return
  try {
    await chatApi.updateMuted(id, v)
    if (detail.value) detail.value.muted = v
    message.success(v ? '已开启免打扰' : '已关闭免打扰')
  } catch (e: any) {
    message.error(e?.message || '操作失败')
  }
}

function onLeave() {
  emit('leave')
}

function onPinnedChange(v: boolean) {
  emit('toggle-pin', v)
}

watch(
  () => props.show,
  (v) => {
    if (v) {
      void loadDetail()
      void loadFiles()
    } else {
      imgUrls.value = {}
      previewImg.value = ''
    }
  }
)
</script>

<template>
  <n-drawer :show="show" :width="380" placement="right" @update:show="(v: boolean) => emit('update:show', v)">
    <n-drawer-content :title="isGroup ? '群设置' : '聊天信息'" closable>
      <div v-if="!conversation" class="drawer-empty">
        <n-empty description="未选择会话" />
      </div>

      <n-spin :show="loadingDetail" v-else>
        <div class="info-head">
          <span class="info-avatar" :style="{ background: avatarBg(isGroup ? '群聊' : (conversation.otherUser?.nickname || conversation.otherUser?.username || '')) }">
            <img v-if="!isGroup && conversation.otherUser?.avatarUrl" :src="conversation.otherUser.avatarUrl" alt="" />
            <template v-else-if="isGroup">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path d="M9 11.2a2.8 2.8 0 1 1 5.6 0 2.8 2.8 0 0 1-5.6 0Z" stroke="currentColor" stroke-width="1.7" />
                <path d="M5.5 17.5c.8-2.2 2.7-3.5 5.2-3.5 1 0 1.9.2 2.7.6" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
                <path d="M15.5 10.3a2 2 0 1 1 3.9 0 2 2 0 0 1-3.9 0Z" stroke="currentColor" stroke-width="1.7" />
                <path d="M17.8 15.9c.6-1.3 1.8-2.1 3.4-2.1.8 0 1.5.2 2 .5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
              </svg>
            </template>
            <template v-else>{{ avatarText(conversation.otherUser?.nickname || conversation.otherUser?.username || '') }}</template>
          </span>
          <div class="info-title">
            <div class="info-name">{{ isGroup ? conversation.name || '群聊' : (conversation.otherUser?.nickname || conversation.otherUser?.username || conversation.name) }}</div>
            <div class="info-sub">{{ isGroup ? `${detail?.memberCount ?? 0} 位成员` : (conversation.otherUser?.email || '') }}</div>
          </div>
        </div>

        <!-- 群公告 -->
        <section v-if="isGroup" class="drawer-section">
          <div class="section-head">
            <span class="section-title">群公告</span>
            <n-button v-if="!editingAnnouncement" text type="primary" size="tiny" @click="editingAnnouncement = true">编辑</n-button>
          </div>
          <div v-if="!editingAnnouncement" class="announcement-view">
            <p v-if="detail?.announcement" class="announcement-text">{{ detail.announcement }}</p>
            <p v-else class="announcement-empty">暂无公告</p>
          </div>
          <div v-else class="announcement-edit">
            <n-input v-model:value="announcementDraft" type="textarea" :rows="3" maxlength="500" show-count placeholder="输入群公告内容" />
            <div class="edit-actions">
              <n-button size="small" @click="editingAnnouncement = false">取消</n-button>
              <n-button size="small" type="primary" :loading="savingAnnouncement" @click="saveAnnouncement">保存</n-button>
            </div>
          </div>
        </section>

        <!-- 群成员 -->
        <section v-if="isGroup" class="drawer-section">
          <div class="section-head">
            <span class="section-title">群成员</span>
            <span class="member-count">{{ members.length }}</span>
          </div>
          <div class="member-grid">
            <div v-for="m in members" :key="m.userId" class="member-cell">
              <span class="member-avatar" :style="{ background: avatarBg(memberName(m)) }">
                <img v-if="m.avatarUrl" :src="m.avatarUrl" alt="" />
                <template v-else>{{ avatarText(memberName(m)) }}</template>
              </span>
              <span class="member-name" :title="memberName(m)">{{ memberName(m) }}</span>
            </div>
          </div>
        </section>

        <!-- 共享文件 -->
        <section class="drawer-section">
          <div class="section-head">
            <span class="section-title">共享文件</span>
          </div>
          <n-tabs v-model:value="activeTab" size="small" type="line" @update:value="onTabChange">
            <n-tab name="ALL" :tab="'全部'">全部</n-tab>
            <n-tab name="IMAGE" :tab="'图片'">图片</n-tab>
            <n-tab name="FILE" :tab="'文件'">文件</n-tab>
          </n-tabs>
          <div class="file-list" v-show="activeTab !== 'IMAGE' || true">
            <n-spin :show="loadingFiles">
              <div v-if="!files.length && !loadingFiles" class="file-empty">
                <n-empty description="暂无文件" size="small" />
              </div>
              <div v-for="f in files" :key="f.attachmentId" class="file-item">
                <template v-if="f.fileType === 'IMAGE'">
                  <div class="file-img-wrap" @click="openPreview(f.attachmentId)">
                    <img v-if="imgUrls[f.attachmentId]" :src="imgUrls[f.attachmentId]" alt="" loading="lazy" />
                    <span v-else class="file-img-loading">加载中</span>
                  </div>
                </template>
                <template v-else>
                  <div class="file-ico">{{ fileExt(f.originalName) }}</div>
                  <div class="file-meta">
                    <div class="file-name" :title="f.originalName">{{ f.originalName }}</div>
                    <div class="file-info">{{ f.senderName }} · {{ formatSize(f.sizeBytes) }}</div>
                  </div>
                </template>
              </div>
            </n-spin>
          </div>
        </section>

        <!-- 设置 -->
        <section class="drawer-section drawer-settings">
          <div class="setting-row">
            <span>消息免打扰</span>
            <n-switch :value="detail?.muted ?? false" size="small" @update:value="onMutedChange" />
          </div>
          <div class="setting-row">
            <span>置顶会话</span>
            <n-switch :value="conversation?.pinned ?? false" size="small" @update:value="onPinnedChange" />
          </div>
        </section>

        <!-- 数据/危险操作 -->
        <section class="drawer-section drawer-actions">
          <n-button block tertiary size="small" class="action-btn" @click="onClearLocal">
            <template #icon>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M4 7h16" />
                <path d="M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" />
                <path d="M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12" />
                <path d="M10 11v6M14 11v6" />
              </svg>
            </template>
            清空聊天记录
          </n-button>

          <!-- 单聊仅本机删除；群聊（非群主）=退出群聊；(群主)= 解散群聊 -->
          <n-popconfirm
            v-if="!isGroup"
            positive-text="删除"
            negative-text="取消"
            @positive-click="onRemoveLocal"
          >
            <template #trigger>
              <n-button block tertiary size="small" type="error" class="action-btn danger-btn">
                <template #icon>
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M3 6h18" />
                    <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                    <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
                    <path d="M10 11v6M14 11v6" />
                  </svg>
                </template>
                删除聊天
              </n-button>
            </template>
            确定删除该会话？
          </n-popconfirm>

          <n-popconfirm
            v-else-if="!isOwner"
            positive-text="退出"
            negative-text="取消"
            @positive-click="onLeave"
          >
            <template #trigger>
              <n-button block tertiary size="small" type="error" class="action-btn danger-btn">
                <template #icon>
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                    <path d="m16 17 5-5-5-5" />
                    <path d="M21 12H9" />
                  </svg>
                </template>
                退出群聊
              </n-button>
            </template>
            确定退出该群聊？
          </n-popconfirm>

          <n-popconfirm
            v-else
            positive-text="解散"
            negative-text="取消"
            @positive-click="onLeave"
          >
            <template #trigger>
              <n-button block tertiary size="small" type="error" class="action-btn danger-btn">
                <template #icon>
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="12" cy="12" r="10" />
                    <path d="m4.93 4.93 14.14 14.14" />
                  </svg>
                </template>
                解散群聊
              </n-button>
            </template>
            确定解散该群聊？
          </n-popconfirm>
        </section>
      </n-spin>
    </n-drawer-content>
  </n-drawer>

  <!-- 图片预览 -->
  <Teleport to="body">
    <div v-if="previewImg" class="drawer-preview-layer" @click="previewImg = ''">
      <img :src="previewImg" alt="" class="drawer-preview-img" @click.stop />
    </div>
  </Teleport>
</template>

<script lang="ts">
export default {
  name: 'ChatSettingDrawer'
}
</script>

<style scoped>
/* 钉钉式端正字体：本组件所有元素强制使用全局 font-stack 兜底 */
.drawer-empty,
.info-head,
.section-title,
.info-name,
.info-sub,
.member-name,
.member-empty,
.announcement-text,
.announcement-empty,
.file-name,
.file-info,
.setting-row,
.setting-label,
.tab-row,
.tab-pill,
.upload-empty {
  font-family: var(--app-font,
    'PingFang SC', 'Microsoft YaHei', 'Hiragino Sans GB',
    'Source Han Sans SC', 'Noto Sans CJK SC', 'Segoe UI',
    'Helvetica Neue', Inter, Arial, sans-serif);
}
.drawer-empty {
  padding: 60px 0;
}
.info-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 0 16px;
  border-bottom: 1px solid #f0f1f3;
}
.info-avatar {
  width: 50px;
  height: 50px;
  border-radius: 10px;
  color: #303540;
  background: #ffffff;
  border: 1px solid #e3e5e8;
  display: grid;
  place-items: center;
  font-size: 18px;
  font-weight: 600;
  flex: 0 0 50px;
  overflow: hidden;
  box-sizing: border-box;
}
.info-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.info-title {
  min-width: 0;
}
.info-name {
  font-size: 16px;
  font-weight: 600;
  color: #303540;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--app-font, inherit);
  letter-spacing: 0.01em;
}
.info-sub {
  font-size: 13px;
  font-weight: 500;
  color: #8b9199;
  margin-top: 3px;
}
.drawer-section {
  padding: 14px 0;
  border-bottom: 1px solid #f0f1f3;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.section-title {
  font-size: 14px;
  font-weight: 700;
  color: #1f2329;
}
.member-count {
  font-size: 13px;
  color: #98a0aa;
}
.announcement-view {
  background: #f7f8fa;
  border-radius: 8px;
  padding: 12px 14px;
}
.announcement-text {
  font-size: 14px;
  font-weight: 500;
  line-height: 1.7;
  color: #1f2329;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}
.announcement-empty {
  font-size: 13px;
  font-weight: 500;
  color: #98a0aa;
  margin: 0;
}
.announcement-edit {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.member-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px 6px;
  max-height: 240px;
  overflow-y: auto;
}
.member-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  min-width: 0;
}
.member-avatar {
  width: 38px;
  height: 38px;
  border-radius: 8px;
  color: #303540;
  background: #ffffff;
  border: 1px solid #e3e5e8;
  display: grid;
  place-items: center;
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  flex: 0 0 38px;
  box-sizing: border-box;
}
.member-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.member-name {
  font-size: 12.5px;
  font-weight: 600;
  color: #646a73;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-list {
  min-height: 60px;
}
.file-empty {
  padding: 16px 0;
}
.file-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 4px;
  border-radius: 6px;
  cursor: pointer;
}
.file-item:hover {
  background: #f7f8fa;
}
.file-img-wrap {
  width: 100%;
}
.file-img-wrap img {
  width: 100%;
  max-height: 200px;
  object-fit: contain;
  border-radius: 6px;
  background: #f7f8fa;
  display: block;
}
.file-img-loading {
  display: block;
  text-align: center;
  padding: 30px 0;
  color: #98a0aa;
  font-size: 12px;
  background: #f7f8fa;
  border-radius: 6px;
}
.file-ico {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #eef2f9;
  color: #1769e0;
  display: grid;
  place-items: center;
  font-size: 10px;
  font-weight: 700;
  flex: 0 0 40px;
}
.file-meta {
  min-width: 0;
}
.file-name {
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-info {
  font-size: 13px;
  font-weight: 500;
  color: #98a0aa;
  margin-top: 2px;
}
.drawer-settings {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 2px;
  font-size: 13px;
  color: #1f2329;
}
.drawer-danger {
  border-bottom: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-bottom: 4px;
}
.drawer-preview-layer {
  position: fixed;
  inset: 0;
  z-index: 5000;
  background: rgba(0, 0, 0, 0.72);
  display: grid;
  place-items: center;
  cursor: zoom-out;
}
.drawer-preview-img {
  max-width: 86vw;
  max-height: 86vh;
  object-fit: contain;
  border-radius: 4px;
  cursor: zoom-out;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.5);
}
</style>
