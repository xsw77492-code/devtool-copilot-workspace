<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NCheckbox, NDropdown, NInput, NSelect, NSpin, useMessage } from 'naive-ui'
import { inboxApi, type InboxItem } from '../api/inbox'
import { useProjectStore } from '../stores/project'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const ps = useProjectStore()

const loading = ref(false)
const view = ref<'todo' | 'unread' | 'handled' | 'all'>('todo')
const category = ref<'all' | string>('all')
const q = ref('')
const projectId = ref<number | null>(null)

const cursor = ref<number | null>(null)
const list = ref<InboxItem[]>([])
const hasMore = ref(false)

const unreadCount = ref(0)
const unhandledCount = ref(0)

const selecting = ref(false)
const selectedIds = ref<Set<number>>(new Set())
const selectedCount = computed(() => selectedIds.value.size)

const CATEGORY_META: { key: string; label: string }[] = [
  { key: 'MENTION', label: '提及' },
  { key: 'REPLY', label: '回复' },
  { key: 'FOLLOW', label: '关注更新' },
  { key: 'ASSIGNED', label: '分配给我' },
  { key: 'TASK', label: '任务' },
  { key: 'PROJECT', label: '项目' },
  { key: 'SYSTEM', label: '系统' }
]

const categoryLabel = computed(() => {
  const m: Record<string, string> = {}
  for (const it of CATEGORY_META) m[it.key] = it.label
  return m
})

const projectOptions = computed(() => {
  const opts = [{ label: '全部项目', value: 0 }]
  for (const p of ps.projects) opts.push({ label: p.name || `项目 #${p.id}`, value: p.id })
  return opts
})

const projectName = (id?: number | null) => {
  if (!id) return ''
  const p = ps.projects.find((x) => x.id === id)
  return p?.name || `项目 #${id}`
}

const viewTitle = computed(() => {
  const map: Record<string, string> = { todo: '待处理', unread: '未读', handled: '已处理', all: '全部' }
  return map[view.value] || '全部'
})

function normalizeCategory(v: string) {
  if (!v) return undefined
  const raw = String(v).trim()
  if (!raw || raw === 'all') return undefined
  return raw
}

function normalizeView(v: unknown): 'todo' | 'unread' | 'handled' | 'all' {
  const raw = String(v || '').trim()
  if (raw === 'unread' || raw === 'handled' || raw === 'all') return raw
  return 'todo'
}

function syncFromRoute() {
  view.value = normalizeView(route.query.view)
  category.value = normalizeCategory(String(route.query.category || '')) || 'all'
  q.value = String(route.query.q || '').trim()
  const pid = Number(route.query.projectId || 0)
  projectId.value = Number.isFinite(pid) && pid > 0 ? pid : null
}

function timeText(v?: string) {
  if (!v) return ''
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return v
  return d.toLocaleString()
}

function queryByView() {
  if (view.value === 'todo') return { handled: false, unreadOnly: undefined }
  if (view.value === 'unread') return { handled: false, unreadOnly: true }
  if (view.value === 'handled') return { handled: true, unreadOnly: undefined }
  return { handled: undefined, unreadOnly: undefined }
}

async function load() {
  loading.value = true
  try {
    const { handled, unreadOnly } = queryByView()
    const res = await inboxApi.list({
      limit: 60,
      handled,
      unreadOnly,
      category: normalizeCategory(category.value),
      projectId: projectId.value ?? undefined,
      q: q.value.trim() || undefined
    })
    list.value = res.list || []
    cursor.value = list.value.length ? list.value[list.value.length - 1].id : null
    hasMore.value = list.value.length >= 60
    unreadCount.value = Number(res.unreadCount || 0)
    unhandledCount.value = Number(res.unhandledCount || 0)
    selecting.value = false
    selectedIds.value = new Set()
  } catch (e: any) {
    const msg = String(e?.message || '')
    if (msg === '接口不存在') {
      message.error('后端尚未部署收件箱接口，请更新/重启后端后重试')
    } else {
      message.error(msg || '加载失败')
    }
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (!cursor.value) return
  if (loading.value) return
  loading.value = true
  try {
    const { handled, unreadOnly } = queryByView()
    const res = await inboxApi.list({
      cursor: cursor.value,
      limit: 60,
      handled,
      unreadOnly,
      category: normalizeCategory(category.value),
      projectId: projectId.value ?? undefined,
      q: q.value.trim() || undefined
    })
    const next = res.list || []
    list.value = [...list.value, ...next]
    cursor.value = next.length ? next[next.length - 1].id : cursor.value
    hasMore.value = next.length >= 60
    unreadCount.value = Number(res.unreadCount || unreadCount.value || 0)
    unhandledCount.value = Number(res.unhandledCount || unhandledCount.value || 0)
  } catch (e: any) {
    const msg = String(e?.message || '')
    if (msg === '接口不存在') {
      message.error('后端尚未部署收件箱接口，请更新/重启后端后重试')
    } else {
      message.error(msg || '加载失败')
    }
  } finally {
    loading.value = false
  }
}

function toggleSelecting() {
  selecting.value = !selecting.value
  selectedIds.value = new Set()
}

function toggleSelected(id: number, checked: boolean) {
  const next = new Set(selectedIds.value)
  if (checked) next.add(id)
  else next.delete(id)
  selectedIds.value = next
}

async function markSelectedRead() {
  const ids = [...selectedIds.value]
  if (!ids.length) return
  try {
    await inboxApi.readBatch(ids)
    message.success('已标记为已读')
    await load()
  } catch (e: any) {
    message.error(e?.message || '操作失败')
  }
}

async function markSelectedHandled() {
  const ids = [...selectedIds.value]
  if (!ids.length) return
  try {
    await inboxApi.handleBatch(ids)
    message.success('已标记为已处理')
    await load()
  } catch (e: any) {
    message.error(e?.message || '操作失败')
  }
}

async function openItem(it: InboxItem) {
  const pid = Number(it.projectId || 0)
  const tid = Number(it.taskId || 0)
  const cid = Number(it.commentId || 0)
  if (pid && tid) {
    router.push({ name: 'task-detail', params: { projectId: pid, taskId: tid }, query: cid ? { commentId: cid } : undefined })
  } else if (pid) {
    router.push({ name: 'project-detail', params: { id: pid } })
  }
  if (it.isRead === 0) {
    try {
      await inboxApi.readBatch([it.id])
      it.isRead = 1
      unreadCount.value = Math.max(0, Number(unreadCount.value || 0) - 1)
    } catch {}
  }
}

function clickItem(it: InboxItem) {
  if (selecting.value) return toggleSelected(it.id, !selectedIds.value.has(it.id))
  return openItem(it)
}

const actionOptions = computed(() => {
  if (!selecting.value) return [{ label: '批量选择', key: 'toggleSelecting' }]
  return [
    { label: `已选 ${selectedCount.value}`, key: 'hint', disabled: true },
    { label: '标记已读', key: 'readSelected', disabled: selectedCount.value === 0 },
    { label: '标记已处理', key: 'handleSelected', disabled: selectedCount.value === 0 },
    { type: 'divider', key: 'd1' },
    { label: '取消批量', key: 'toggleSelecting' }
  ] as any
})

async function onActionSelect(key: string | number) {
  if (key === 'toggleSelecting') return toggleSelecting()
  if (key === 'readSelected') return markSelectedRead()
  if (key === 'handleSelected') return markSelectedHandled()
}

let watchTimer: any = null
watch(
  () => [view.value, category.value, projectId.value, q.value],
  () => {
    if (watchTimer) clearTimeout(watchTimer)
    watchTimer = setTimeout(() => load(), 120)
  }
)

watch(
  () => route.query,
  () => {
    syncFromRoute()
  },
  { immediate: true }
)

onMounted(async () => {
  if (!ps.projects.length) await ps.load()
  if (!route.query.view && !route.query.category && !route.query.projectId && !route.query.q) {
    await load()
  }
})
</script>

<template>
  <div class="page lightPage">
    <div class="panelHead">
      <div class="statLine">
        共 {{ list.length }} 条<span v-if="unhandledCount || unreadCount"> · 待处理 {{ unhandledCount }} 条<span v-if="unreadCount"> · 未读 {{ unreadCount }} 条</span></span>
      </div>
      <div class="headActions">
        <n-button secondary class="accentBtn" @click="load">刷新</n-button>
        <n-dropdown :options="actionOptions" trigger="click" @select="onActionSelect">
          <n-button tertiary>操作</n-button>
        </n-dropdown>
      </div>
    </div>

    <div class="toolbar">
      <button class="seg" :class="{ active: view === 'todo' }" @click="view = 'todo'">
        待处理 <span class="count muted">{{ unhandledCount }}</span>
      </button>
      <button class="seg" :class="{ active: view === 'unread' }" @click="view = 'unread'">
        未读 <span class="count muted">{{ unreadCount }}</span>
      </button>
      <button class="seg" :class="{ active: view === 'handled' }" @click="view = 'handled'">已处理</button>
      <button class="seg" :class="{ active: view === 'all' }" @click="view = 'all'">全部</button>
      <div class="divider" />
      <button class="seg" :class="{ active: category === 'all' }" @click="category = 'all'">全部分类</button>
      <button class="seg" :class="{ active: category === 'ASSIGNED' }" @click="category = 'ASSIGNED'">分配给我</button>
      <button class="seg" :class="{ active: category === 'MENTION' }" @click="category = 'MENTION'">提及</button>
      <button class="seg" :class="{ active: category === 'REPLY' }" @click="category = 'REPLY'">回复</button>
      <button class="seg" :class="{ active: category === 'FOLLOW' }" @click="category = 'FOLLOW'">关注</button>
      <button class="seg" :class="{ active: category === 'TASK' }" @click="category = 'TASK'">任务</button>
      <button class="seg" :class="{ active: category === 'PROJECT' }" @click="category = 'PROJECT'">项目</button>
      <button class="seg" :class="{ active: category === 'SYSTEM' }" @click="category = 'SYSTEM'">系统</button>
    </div>

    <div class="filters">
      <n-input v-model:value="q" placeholder="搜索收件箱…" class="fInput" />
      <n-select
        :value="projectId === null ? 0 : projectId"
        :options="projectOptions"
        class="fSel"
        @update:value="(v) => (projectId = v ? Number(v) : null)"
      />
    </div>

    <section class="panel lightPanel">
      <n-spin :show="loading">
        <div v-if="!list.length && !loading" class="empty">
          <div class="emptyTitle">—</div>
        </div>

        <div v-else>
          <div v-if="selecting" class="bulkBar">
            <div class="bulkLeft">
              <span class="bulkTitle">已选 {{ selectedCount }}</span>
              <span class="muted bulkHint">仅对已选条目生效</span>
            </div>
            <div class="bulkRight">
              <n-button secondary class="accentBtn" :disabled="selectedCount === 0" @click="markSelectedRead">标记已读</n-button>
              <n-button secondary class="accentBtn" :disabled="selectedCount === 0" @click="markSelectedHandled">标记已处理</n-button>
              <n-button tertiary @click="toggleSelecting">取消</n-button>
            </div>
          </div>

          <TransitionGroup name="fadeUp" tag="div" class="list">
            <div
              v-for="it in list"
              :key="it.id"
              class="item"
              :class="{ unread: it.isRead === 0, selecting, handled: it.isHandled === 1 }"
              @click="clickItem(it)"
            >
              <div v-if="selecting" class="sel" @click.stop>
                <n-checkbox :checked="selectedIds.has(it.id)" @update:checked="(v) => toggleSelected(it.id, v)" />
              </div>
              <div class="badge" />
              <div class="main">
                <div class="top">
                  <div class="title">{{ it.title }}</div>
                  <div class="rightMeta">
                    <span class="typeTag">{{ categoryLabel[it.category] || it.category }}</span>
                    <div class="time muted">{{ timeText(it.createTime) }}</div>
                  </div>
                </div>
                <div v-if="it.content" class="content">{{ it.content }}</div>
                <div class="meta muted">
                  <span v-if="it.projectId">{{ projectName(it.projectId) }}</span>
                  <span v-if="it.taskId">· 任务 #{{ it.taskId }}</span>
                  <span v-if="it.commentId">· 评论 #{{ it.commentId }}</span>
                </div>
              </div>
              <div class="state">
                <span class="pill" :class="{ unreadPill: it.isRead === 0 }">{{ it.isRead === 0 ? '未读' : '已读' }}</span>
                <span class="pill" :class="{ handledPill: it.isHandled === 0 }">{{ it.isHandled === 0 ? '待办' : '已处理' }}</span>
              </div>
            </div>
          </TransitionGroup>

          <div v-if="hasMore" class="moreRow">
            <n-button tertiary @click="loadMore">加载更多</n-button>
          </div>
        </div>
      </n-spin>
    </section>
  </div>
</template>

<style scoped>
.lightPage {
  background: transparent;
  color: #0f172a;
}

.sub {
  margin-top: 8px;
  font-size: 13px;
}

.toolbar {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 6px;
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.04);
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.seg {
  border: 1px solid transparent;
  background: transparent;
  padding: 7px 12px;
  border-radius: 10px;
  font-size: 12.5px;
  font-weight: 650;
  color: rgba(15, 23, 42, 0.78);
  cursor: pointer;
  transition: background-color 120ms ease, border-color 120ms ease, color 120ms ease;
}

.seg:hover {
  background: rgba(15, 23, 42, 0.04);
  border-color: rgba(15, 23, 42, 0.06);
  color: rgba(15, 23, 42, 0.92);
}

.seg.active {
  background: rgba(var(--accent-rgb), 0.06);
  border-color: rgba(var(--accent-rgb), 0.12);
  color: rgba(15, 23, 42, 0.95);
}

.count {
  margin-left: 6px;
  font-weight: 700;
}

.divider {
  width: 1px;
  height: 18px;
  align-self: center;
  background: rgba(15, 23, 42, 0.08);
  margin: 0 4px;
}

.filters {
  margin-top: 12px;
  display: grid;
  grid-template-columns: 1fr 260px;
  gap: 10px;
  align-items: center;
}

.fInput {
  border-radius: 14px;
}

.fSel {
  border-radius: 14px;
}

.lightPanel {
  margin-top: 12px;
  padding: 0;
  border-radius: 0;
  border: 0;
  box-shadow: none;
  background: transparent;
}

.panelHead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.headActions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.statLine {
  font-size: 12px;
  color: rgba(15, 23, 42, 0.45);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.accentBtn {
  background: #fff !important;
  border-color: rgba(15, 23, 42, 0.16) !important;
  color: #0f172a !important;
}

.accentBtn:hover {
  background: rgba(15, 23, 42, 0.04) !important;
  border-color: rgba(15, 23, 42, 0.28) !important;
  color: #0f172a !important;
}

.empty {
  padding: 60px 16px;
}

.emptyCard {
  max-width: 360px;
  margin: 0 auto;
  padding: 32px 16px;
  border: 1px dashed rgba(15, 23, 42, 0.14);
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.02);
  text-align: center;
}

.emptyTitle {
  font-size: 14px;
  font-weight: 860;
}

.emptyDesc {
  margin-top: 8px;
  line-height: 1.6;
  color: rgba(15, 23, 42, 0.55);
}

.list {
  display: grid;
  gap: 0;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}

.item {
  display: flex;
  gap: 12px;
  padding: 12px 6px;
  border-radius: 0;
  border: 0;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  background: transparent;
  transition: background-color 120ms ease;
  cursor: pointer;
}

.item:hover {
  background: rgba(15, 23, 42, 0.03);
}

.item.unread {
  background: rgba(var(--accent-rgb), 0.03);
}

.sel {
  display: grid;
  place-items: center;
  padding-left: 4px;
}

.badge {
  width: 10px;
  flex-shrink: 0;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.10);
  margin-top: 6px;
}

.item.unread .badge {
  background: var(--accent);
  box-shadow: 0 0 0 5px rgba(var(--accent-rgb), 0.10);
}

.main {
  min-width: 0;
  flex: 1;
}

.top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.title {
  font-size: 14px;
  font-weight: 760;
  letter-spacing: -0.1px;
  color: rgba(15, 23, 42, 0.92);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rightMeta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.time {
  font-size: 12px;
  white-space: nowrap;
}

.content {
  margin-top: 6px;
  font-size: 13px;
  color: rgba(15, 23, 42, 0.72);
  line-height: 1.6;
  white-space: pre-wrap;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.meta {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  font-size: 12px;
}

.typeTag {
  padding: 3px 8px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.10);
  background: rgba(255, 255, 255, 0.72);
  font-weight: 700;
}

.state {
  display: grid;
  align-content: start;
  gap: 8px;
  padding-left: 6px;
}

.pill {
  font-size: 11px;
  letter-spacing: 0.5px;
  padding: 4px 8px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.7);
  color: rgba(15, 23, 42, 0.68);
  font-weight: 800;
  text-align: center;
}

.unreadPill {
  border-color: rgba(var(--accent-rgb), 0.14);
  background: rgba(var(--accent-rgb), 0.05);
  color: rgba(15, 23, 42, 0.90);
}

.handledPill {
  border-color: rgba(var(--accent-rgb), 0.14);
  background: rgba(var(--accent-rgb), 0.05);
  color: rgba(15, 23, 42, 0.90);
}

.moreRow {
  display: flex;
  justify-content: center;
  padding-top: 12px;
}

.bulkBar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 6px;
  border-radius: 0;
  border: 0;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(15, 23, 42, 0.02);
  margin-bottom: 10px;
}

.bulkLeft {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.bulkTitle {
  font-weight: 800;
}

.bulkHint {
  font-size: 12px;
}

.bulkRight {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.fadeUp-enter-active,
.fadeUp-leave-active {
  transition: all 160ms ease;
}

.fadeUp-enter-from,
.fadeUp-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

@media (max-width: 980px) {
  .filters {
    grid-template-columns: 1fr;
  }
  .state {
    display: none;
  }
}
</style>
