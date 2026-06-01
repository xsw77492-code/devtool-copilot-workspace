<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NCheckbox, NDropdown, NInput, NModal, NSelect, NSpin, useMessage } from 'naive-ui'
import { useNotificationStore } from '../stores/notification'
import { notificationApi, type NotificationItem } from '../api/notification'
import { taskApi } from '../api/task'
import { useProjectStore } from '../stores/project'
import NotificationSettingsPanel from '../components/NotificationSettingsPanel.vue'
import { NOTIFICATION_TYPE_META } from '../constants/notificationTypes'

const router = useRouter()
const message = useMessage()
const ns = useNotificationStore()
const ps = useProjectStore()

const loading = ref(false)
const state = ref<'all' | 'unread'>('all')
const category = ref<'all' | 'mentions' | 'task' | 'project' | 'system'>('all')
const q = ref('')
const projectId = ref<number | null>(null)
const type = ref<string | null>(null)
const cursor = ref<number | null>(null)
const list = ref<NotificationItem[]>([])
const hasMore = ref(false)

const labelByType = computed(() => {
  const m: Record<string, string> = {}
  for (const it of NOTIFICATION_TYPE_META) m[it.type] = it.label
  return m
})

const categoryTypes = computed(() => {
  if (category.value === 'mentions') return ['TASK_MENTION', 'TASK_REPLY']
  if (category.value === 'task') return NOTIFICATION_TYPE_META.filter((x) => x.type.startsWith('TASK_')).map((x) => x.type)
  if (category.value === 'project') return NOTIFICATION_TYPE_META.filter((x) => x.type.startsWith('PROJECT_')).map((x) => x.type)
  if (category.value === 'system') return ['SYSTEM']
  return null
})

watch(
  () => category.value,
  () => {
    if (!type.value) return
    const allow = categoryTypes.value
    if (allow && !allow.includes(type.value)) type.value = null
  }
)

const selecting = ref(false)
const selectedIds = ref<Set<number>>(new Set())
const selectedCount = computed(() => selectedIds.value.size)

const viewList = computed(() => list.value)
const visibleUnreadCount = computed(() => list.value.filter((x) => x.isRead === 0).length)

function parseData(n: NotificationItem) {
  if (!n.dataJson) return null
  try {
    return JSON.parse(n.dataJson) as any
  } catch {
    return null
  }
}

function timeText(v?: string) {
  if (!v) return ''
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return v
  return d.toLocaleString()
}

async function load() {
  loading.value = true
  try {
    const unreadOnly = state.value === 'unread'
    const types = type.value ? undefined : categoryTypes.value || undefined
    const res = await notificationApi.list({
      limit: 60,
      unreadOnly,
      projectId: projectId.value ?? undefined,
      type: type.value ?? undefined,
      types,
      q: q.value.trim() || undefined
    })
    list.value = res.list
    cursor.value = res.list.length ? res.list[res.list.length - 1].id : null
    hasMore.value = res.list.length >= 60
    ns.refreshUnreadCount()
    selecting.value = false
    selectedIds.value = new Set()
  } catch (e: any) {
    message.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (!cursor.value) return
  if (loading.value) return
  loading.value = true
  try {
    const unreadOnly = state.value === 'unread'
    const types = type.value ? undefined : categoryTypes.value || undefined
    const res = await notificationApi.list({
      cursor: cursor.value,
      limit: 60,
      unreadOnly,
      projectId: projectId.value ?? undefined,
      type: type.value ?? undefined,
      types,
      q: q.value.trim() || undefined
    })
    list.value = [...list.value, ...res.list]
    cursor.value = res.list.length ? res.list[res.list.length - 1].id : cursor.value
    hasMore.value = res.list.length >= 60
  } catch (e: any) {
    message.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function markAllRead() {
  try {
    await ns.markAllRead()
    message.success('已全部标记为已读')
    await load()
  } catch (e: any) {
    message.error(e?.message || '操作失败')
  }
}

async function markFilteredRead() {
  try {
    const types = type.value ? undefined : categoryTypes.value || undefined
    await notificationApi.readByFilter({
      unreadOnly: true,
      projectId: projectId.value ?? undefined,
      type: type.value ?? undefined,
      types,
      q: q.value.trim() || undefined
    })
    await ns.refreshUnreadCount()
    message.success('已标记为已读')
    await load()
  } catch (e: any) {
    message.error(e?.message || '操作失败')
  }
}

async function clearRead() {
  try {
    await notificationApi.clearRead()
    message.success('已清空已读')
    await load()
  } catch (e: any) {
    message.error(e?.message || '操作失败')
  }
}

function downloadText(filename: string, content: string) {
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

async function exportCsv() {
  try {
    const types = type.value ? undefined : categoryTypes.value || undefined
    const res = await notificationApi.exportCsv({
      unreadOnly: state.value === 'unread' ? true : undefined,
      projectId: projectId.value ?? undefined,
      type: type.value ?? undefined,
      types,
      q: q.value.trim() || undefined
    })
    downloadText(res.filename || 'notifications.csv', res.content || '')
    message.success('已导出')
  } catch (e: any) {
    message.error(e?.message || '导出失败')
  }
}

async function openInvite(n: NotificationItem) {
  const data = parseData(n)
  const link = (data?.inviteLink as string | undefined) || undefined
  if (!link) return
  const url = new URL(link, window.location.origin)
  router.push({ name: 'project-invite', query: { token: url.searchParams.get('token') || '' } })
  if (n.isRead === 0) await ns.markRead(n.id)
}

async function openTarget(n: NotificationItem) {
  const data = parseData(n)
  const pid = Number(n.projectId || data?.projectId || 0)
  if (pid) {
    router.push({ name: 'project-detail', params: { id: pid } })
    if (n.isRead === 0) await ns.markRead(n.id)
  }
}

async function openTask(n: NotificationItem) {
  const data = parseData(n)
  const tid = Number(n.taskId || data?.taskId || 0)
  if (!tid) return
  if (n.isRead === 0) {
    try {
      await ns.markRead(n.id)
    } catch {
    }
  }
  const pid = Number(n.projectId || data?.projectId || 0)
  if (pid) {
    const cid = Number(n.commentId || data?.commentId || 0)
    router.push({
      name: 'task-detail',
      params: { projectId: pid, taskId: tid },
      query: cid ? { commentId: String(cid) } : undefined
    })
    return
  }
  const t = await taskApi.get(tid)
  const pid2 = Number((t as any)?.projectId || 0)
  if (!pid2) return
  const cid = Number(n.commentId || data?.commentId || 0)
  router.push({
    name: 'task-detail',
    params: { projectId: pid2, taskId: tid },
    query: cid ? { commentId: String(cid) } : undefined
  })
}

async function clickItem(n: NotificationItem) {
  if (selecting.value) {
    const s = new Set(selectedIds.value)
    if (s.has(n.id)) s.delete(n.id)
    else s.add(n.id)
    selectedIds.value = s
    return
  }
  const data = parseData(n)
  if (data?.inviteLink) return openInvite(n)
  if (data?.taskId) return openTask(n)
  if (data?.projectId) return openTarget(n)
  if (n.isRead === 0) {
    try {
      await ns.markRead(n.id)
    } catch {
    }
  }
}

const projectOptions = computed(() => {
  return [{ label: '全部项目', value: 0 }, ...ps.projects.map((p) => ({ label: p.name, value: p.id }))]
})

const typeOptions = computed(() => {
  const allow = categoryTypes.value
  const rows = allow ? NOTIFICATION_TYPE_META.filter((x) => allow.includes(x.type)) : NOTIFICATION_TYPE_META
  return [{ label: '全部类型', value: '' }, ...rows.map((x) => ({ label: x.label, value: x.type }))]
})

const settingsOpen = ref(false)

async function openSettings() {
  settingsOpen.value = true
}

watch([state, category, projectId, type], () => {
  load()
})

watch(
  () => q.value,
  () => {
    const v = q.value
    setTimeout(() => {
      if (q.value !== v) return
      load()
    }, 260)
  }
)

onMounted(async () => {
  if (!ps.projects.length) {
    try {
      await ps.load()
    } catch {
    }
  }
  await load()
})

function toggleSelecting() {
  selecting.value = !selecting.value
  selectedIds.value = new Set()
}

function toggleSelected(id: number, checked: boolean) {
  const s = new Set(selectedIds.value)
  if (checked) s.add(id)
  else s.delete(id)
  selectedIds.value = s
}

async function markSelectedRead() {
  const ids = Array.from(selectedIds.value)
  if (!ids.length) return
  try {
    await notificationApi.readBatch(ids)
    await ns.refreshUnreadCount()
    message.success('已标记为已读')
    await load()
  } catch (e: any) {
    message.error(e?.message || '操作失败')
  }
}

const actionOptions = computed(() => {
  const opts: any[] = [
    { label: selecting.value ? '取消批量选择' : '批量选择', key: 'toggleSelecting' },
    { type: 'divider', key: 'd1' },
    { label: '当前筛选已读', key: 'readFiltered', disabled: visibleUnreadCount.value === 0 },
    { label: '全部已读', key: 'readAll', disabled: ns.unreadCount === 0 },
    { type: 'divider', key: 'd2' },
    { label: '清空已读', key: 'clearRead' },
    { label: '导出', key: 'export' },
    { type: 'divider', key: 'd3' },
    { label: '设置', key: 'settings' }
  ]
  return opts
})

async function onActionSelect(key: string | number) {
  if (key === 'toggleSelecting') return toggleSelecting()
  if (key === 'readFiltered') return markFilteredRead()
  if (key === 'readAll') return markAllRead()
  if (key === 'clearRead') return clearRead()
  if (key === 'export') return exportCsv()
  if (key === 'settings') return openSettings()
}
</script>

<template>
  <div class="page lightPage">
    <div class="head">
      <div class="left">
        <h1 class="h1">通知中心</h1>
      </div>
      <div class="right">
        <n-button tertiary @click="load">刷新</n-button>
        <n-dropdown :options="actionOptions" trigger="click" @select="onActionSelect">
          <n-button secondary class="accentBtn">操作</n-button>
        </n-dropdown>
      </div>
    </div>

    <div class="toolbar">
      <button class="seg" :class="{ active: state === 'all' }" @click="state = 'all'">
        全部
      </button>
      <button class="seg" :class="{ active: state === 'unread' }" @click="state = 'unread'">
        未读 <span class="count muted">{{ ns.unreadCount }}</span>
      </button>
      <div class="divider" />
      <button class="seg" :class="{ active: category === 'all' }" @click="category = 'all'">全部分类</button>
      <button class="seg" :class="{ active: category === 'mentions' }" @click="category = 'mentions'">提及/回复</button>
      <button class="seg" :class="{ active: category === 'task' }" @click="category = 'task'">任务</button>
      <button class="seg" :class="{ active: category === 'project' }" @click="category = 'project'">项目</button>
      <button class="seg" :class="{ active: category === 'system' }" @click="category = 'system'">系统</button>
    </div>

    <div class="filters">
      <n-input v-model:value="q" placeholder="搜索通知…" class="fInput" />
      <n-select
        :value="projectId === null ? 0 : projectId"
        :options="projectOptions"
        class="fSel"
        @update:value="(v) => (projectId = v ? Number(v) : null)"
      />
      <n-select
        :value="type || ''"
        :options="typeOptions"
        class="fSel"
        @update:value="(v) => (type = v ? String(v) : null)"
      />
    </div>

    <section class="panel lightPanel">
      <n-spin :show="loading">
        <div v-if="!viewList.length && !loading" class="empty">
          <div class="emptyTitle">暂无通知</div>
          <div class="muted emptyDesc">当有人邀请你加入项目，或发生协作事件时，这里会立即出现通知。</div>
        </div>

        <div v-else>
          <div v-if="selecting" class="bulkBar">
            <div class="bulkLeft">
              <span class="bulkTitle">已选 {{ selectedCount }}</span>
              <span class="muted bulkHint">仅对已选通知生效</span>
            </div>
            <div class="bulkRight">
              <n-button secondary class="accentBtn" :disabled="selectedCount === 0" @click="markSelectedRead">标记已读</n-button>
              <n-button tertiary @click="toggleSelecting">取消</n-button>
            </div>
          </div>

          <TransitionGroup name="fadeUp" tag="div" class="list">
            <div
              v-for="n in viewList"
              :key="n.id"
              class="item"
              :class="{ unread: n.isRead === 0, selecting }"
              @click="clickItem(n)"
            >
              <div v-if="selecting" class="sel" @click.stop>
                <n-checkbox :checked="selectedIds.has(n.id)" @update:checked="(v) => toggleSelected(n.id, v)" />
              </div>
              <div class="badge" />
              <div class="main">
                <div class="top">
                  <div class="title">
                    {{ n.title }}
                    <span v-if="Number(n.aggCount || 1) > 1" class="agg">×{{ n.aggCount }}</span>
                  </div>
                  <div class="time muted">{{ timeText(n.createTime) }}</div>
                </div>
                <div v-if="n.content" class="content">{{ n.content }}</div>
                <div class="meta">
                  <span class="typeTag">{{ labelByType[n.type] || n.type }}</span>
                  <span v-if="n.projectId" class="muted">· 项目 #{{ n.projectId }}</span>
                  <span v-if="n.taskId" class="muted">· 任务 #{{ n.taskId }}</span>
                  <span v-if="n.commentId" class="muted">· 评论 #{{ n.commentId }}</span>
                </div>
                <div class="actions">
                  <button v-if="parseData(n)?.inviteLink" class="action primary" @click.stop="openInvite(n)">查看邀请</button>
                  <button v-if="parseData(n)?.taskId" class="action" @click.stop="openTask(n)">{{
                    parseData(n)?.commentId ? '定位评论' : '打开任务'
                  }}</button>
                  <button v-else-if="parseData(n)?.projectId" class="action" @click.stop="openTarget(n)">打开项目</button>
                </div>
              </div>
              <div class="state">
                <span class="pill" :class="{ unreadPill: n.isRead === 0 }">{{ n.isRead === 0 ? 'UNREAD' : 'READ' }}</span>
              </div>
            </div>
          </TransitionGroup>

          <div v-if="hasMore" class="moreRow">
            <n-button tertiary @click="loadMore">加载更多</n-button>
          </div>
        </div>
      </n-spin>
    </section>

    <n-modal v-model:show="settingsOpen" preset="card" title="通知设置" class="settingsModal">
      <notification-settings-panel @saved="settingsOpen = false" />
    </n-modal>
  </div>
</template>

<style scoped>
.lightPage {
  background: #ffffff;
  color: #0f172a;
}

.sub {
  margin-top: 8px;
  font-size: 13px;
}

.toolbar {
  margin-top: 14px;
  display: inline-flex;
  gap: 8px;
  padding: 6px;
  border-radius: 16px;
  background: rgba(15, 23, 42, 0.02);
  box-shadow: 0 10px 28px rgba(2, 6, 23, 0.06);
}

.filters {
  margin-top: 12px;
  display: grid;
  grid-template-columns: 1fr 220px 220px;
  gap: 10px;
  align-items: center;
}

.fInput {
  border-radius: 14px;
}

.fSel {
  border-radius: 14px;
}

.seg {
  height: 38px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  font-weight: 850;
  color: rgba(15, 23, 42, 0.70);
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.seg.active {
  background: #ffffff;
  border-color: rgba(20, 184, 166, 0.18);
  box-shadow: 0 14px 40px rgba(2, 6, 23, 0.08);
  color: rgba(15, 23, 42, 0.90);
}

.divider {
  width: 1px;
  margin: 0 6px;
  background: rgba(15, 23, 42, 0.10);
}

.accentBtn {
  background: rgba(20, 184, 166, 0.12) !important;
  border-color: rgba(20, 184, 166, 0.22) !important;
  color: rgba(15, 23, 42, 0.92) !important;
  transition: transform 160ms ease, filter 160ms ease;
}

.accentBtn:hover {
  filter: brightness(0.98);
  transform: translateY(-1px);
}

.count {
  font-weight: 900;
}

.lightPanel {
  margin-top: 14px;
  background:
    radial-gradient(1000px 460px at 10% 0%, rgba(6, 182, 212, 0.10), transparent 58%),
    #ffffff;
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 16px 45px rgba(2, 6, 23, 0.06);
}

.list {
  display: grid;
  gap: 10px;
}

.item {
  display: grid;
  grid-template-columns: 10px 1fr auto;
  gap: 14px;
  align-items: start;
  padding: 16px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 12px 30px rgba(2, 6, 23, 0.06);
  transition: transform 160ms ease, box-shadow 160ms ease;
}

.item.selecting {
  grid-template-columns: 28px 10px 1fr auto;
}

.item:hover {
  transform: translateY(-1px);
  box-shadow: 0 18px 44px rgba(2, 6, 23, 0.10);
}

.sel {
  margin-top: 2px;
}

.badge {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  margin-top: 7px;
  background: rgba(100, 116, 139, 0.28);
}

.item.unread .badge {
  background: rgba(20, 184, 166, 0.86);
  box-shadow: 0 0 0 4px rgba(20, 184, 166, 0.12);
}

.top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.title {
  font-weight: 930;
  letter-spacing: -0.2px;
  font-size: 16px;
}

.agg {
  margin-left: 8px;
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 900;
  background: rgba(15, 23, 42, 0.06);
  color: rgba(15, 23, 42, 0.70);
}

.time {
  font-size: 12px;
  white-space: nowrap;
}

.content {
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.7;
  color: rgba(15, 23, 42, 0.74);
}

.actions {
  margin-top: 10px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.meta {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  font-size: 12px;
}

.typeTag {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.04);
  color: rgba(15, 23, 42, 0.70);
  font-weight: 850;
}

.action {
  height: 34px;
  padding: 0 12px;
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.10);
  background: rgba(255, 255, 255, 0.9);
  cursor: pointer;
  font-weight: 850;
  color: rgba(15, 23, 42, 0.76);
}

.action:hover {
  border-color: rgba(20, 184, 166, 0.24);
  color: rgba(15, 23, 42, 0.90);
}

.action.primary {
  background: rgba(20, 184, 166, 0.10);
  border-color: rgba(20, 184, 166, 0.22);
  color: rgba(15, 23, 42, 0.90);
}

.pill {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 900;
  border: 1px solid rgba(15, 23, 42, 0.10);
  background: rgba(15, 23, 42, 0.03);
  color: rgba(15, 23, 42, 0.62);
}

.unreadPill {
  background: rgba(20, 184, 166, 0.10);
  border-color: rgba(20, 184, 166, 0.18);
  color: rgba(15, 23, 42, 0.90);
}

.empty {
  padding: 28px 16px 34px;
  border-radius: 18px;
  border: 1px dashed rgba(15, 23, 42, 0.14);
  background: rgba(15, 23, 42, 0.02);
}

.emptyTitle {
  font-weight: 930;
  font-size: 16px;
}

.emptyDesc {
  margin-top: 8px;
  line-height: 1.7;
}

.moreRow {
  display: flex;
  justify-content: center;
  padding: 8px 0 2px;
}

.bulkBar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 10px 6px;
}

.bulkLeft {
  display: inline-flex;
  align-items: baseline;
  gap: 10px;
}

.bulkTitle {
  font-weight: 930;
}

.bulkHint {
  font-size: 12px;
}

.bulkRight {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.settingsModal {
  max-width: 520px;
}

.settingsRow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.settingsTime {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.timeSel {
  width: 120px;
}

.settingsTools {
  margin-top: 12px;
  display: inline-flex;
  gap: 8px;
}

.settingsGroups {
  margin-top: 14px;
  display: grid;
  gap: 12px;
}

.groupTitle {
  font-weight: 930;
  letter-spacing: -0.2px;
}

.groupGrid {
  margin-top: 10px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.typeRow {
  padding: 10px 10px;
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.02);
}

.settingsActions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.fadeUp-enter-active,
.fadeUp-leave-active {
  transition: opacity 200ms ease, transform 200ms ease;
}

.fadeUp-enter-from,
.fadeUp-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

@media (prefers-reduced-motion: reduce) {
  .fadeUp-enter-active,
  .fadeUp-leave-active,
  .item,
  .accentBtn {
    transition: none !important;
  }
}
</style>
