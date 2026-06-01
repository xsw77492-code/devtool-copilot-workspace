<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NCheckbox, NDropdown, NInput, NSelect, NSpin, useMessage } from 'naive-ui'
import { inboxApi, type InboxItem } from '../api/inbox'
import { useProjectStore } from '../stores/project'

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

function normalizeCategory(v: string) {
  if (!v) return undefined
  const raw = String(v).trim()
  if (!raw || raw === 'all') return undefined
  return raw
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

onMounted(async () => {
  if (!ps.projects.length) await ps.load()
  await load()
})
</script>

<template>
  <div class="page lightPage">
    <div class="head">
      <div class="left">
        <h1 class="h1">收件箱</h1>
      </div>
      <div class="right">
        <n-button tertiary @click="load">刷新</n-button>
        <n-dropdown :options="actionOptions" trigger="click" @select="onActionSelect">
          <n-button secondary class="accentBtn">操作</n-button>
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
      <div />
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
                  <span v-if="it.projectId">项目 #{{ it.projectId }}</span>
                  <span v-if="it.taskId">· 任务 #{{ it.taskId }}</span>
                  <span v-if="it.commentId">· 评论 #{{ it.commentId }}</span>
                </div>
              </div>
              <div class="state">
                <span class="pill" :class="{ unreadPill: it.isRead === 0 }">{{ it.isRead === 0 ? 'UNREAD' : 'READ' }}</span>
                <span class="pill" :class="{ handledPill: it.isHandled === 0 }">{{ it.isHandled === 0 ? 'TODO' : 'DONE' }}</span>
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
  flex-wrap: wrap;
  gap: 8px;
  padding: 6px;
  border-radius: 16px;
  background: rgba(15, 23, 42, 0.02);
  box-shadow: 0 10px 28px rgba(2, 6, 23, 0.06);
}

.seg {
  border: 1px solid transparent;
  background: transparent;
  padding: 8px 10px;
  border-radius: 12px;
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
  background: rgba(20, 184, 166, 0.11);
  border-color: rgba(20, 184, 166, 0.20);
  color: rgba(15, 23, 42, 0.95);
}

.count {
  margin-left: 6px;
  font-weight: 700;
}

.divider {
  width: 1px;
  background: rgba(15, 23, 42, 0.08);
  margin: 0 2px;
}

.filters {
  margin-top: 12px;
  display: grid;
  grid-template-columns: 1fr 260px 1fr;
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
  padding: 12px 12px;
  border-radius: 18px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  box-shadow: 0 14px 44px rgba(2, 6, 23, 0.08);
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-top: 6px;
}

.right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.accentBtn {
  background: rgba(20, 184, 166, 0.12) !important;
}

.empty {
  padding: 30px 10px;
  text-align: center;
}

.emptyTitle {
  font-size: 14px;
  font-weight: 700;
}

.emptyDesc {
  margin-top: 8px;
  font-size: 12.5px;
}

.list {
  display: grid;
  gap: 10px;
}

.item {
  display: flex;
  gap: 12px;
  padding: 12px 12px;
  border-radius: 16px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.75);
  transition: transform 120ms ease, box-shadow 120ms ease, border-color 120ms ease, background-color 120ms ease;
  cursor: pointer;
}

.item:hover {
  transform: translateY(-1px);
  box-shadow: 0 14px 40px rgba(2, 6, 23, 0.10);
  border-color: rgba(15, 23, 42, 0.10);
}

.item.unread {
  background: rgba(20, 184, 166, 0.06);
  border-color: rgba(20, 184, 166, 0.18);
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
  box-shadow: 0 0 0 5px rgba(20, 184, 166, 0.14);
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
  border-color: rgba(20, 184, 166, 0.22);
  background: rgba(20, 184, 166, 0.10);
  color: rgba(15, 23, 42, 0.90);
}

.handledPill {
  border-color: rgba(20, 184, 166, 0.22);
  background: rgba(20, 184, 166, 0.10);
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
  padding: 10px 12px;
  border-radius: 16px;
  border: 1px solid rgba(20, 184, 166, 0.16);
  background: rgba(20, 184, 166, 0.06);
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
    grid-template-columns: 1fr 1fr;
  }
  .filters > :nth-child(3) {
    display: none;
  }
  .state {
    display: none;
  }
}
</style>
