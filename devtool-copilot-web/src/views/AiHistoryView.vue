<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NCheckbox, NModal, NSelect, NSpin, useDialog, useMessage } from 'naive-ui'
import { aiApi, type AiChatHistoryItem } from '../api/ai'
import { useProjectStore } from '../stores/project'
import MarkdownView from '../components/MarkdownView.vue'

const message = useMessage()
const dialog = useDialog()
const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()

const projectId = ref<number | null>(null)
const typeFilter = ref('')
const loading = ref(false)
const list = ref<AiChatHistoryItem[]>([])

const TYPE_OPTIONS = [
  { label: '全部类型', value: '' },
  { label: '对话', value: 'chat' },
  { label: 'AI 规划', value: 'plan' },
  { label: 'AI 诊断', value: 'diagnosis' },
  { label: '团队洞察', value: 'insight' },
  { label: 'AI 根因', value: 'rootcause' },
  { label: '节奏解读', value: 'rhythm' },
  { label: '代码审查', value: 'code-review' }
]

const TYPE_LABEL: Record<string, string> = {
  chat: '对话',
  plan: 'AI 规划',
  diagnosis: 'AI 诊断',
  insight: '团队洞察',
  rootcause: 'AI 根因',
  rhythm: '节奏解读',
  'code-review': '代码审查'
}
const labelOf = (t?: string) => (t && TYPE_LABEL[t]) || t || '其它'

const projectOptions = computed(() => projectStore.visibleProjects.map((p) => ({ label: p.name, value: p.id })))

const projectName = computed(() => {
  const map = new Map<number, string>()
  for (const p of projectStore.visibleProjects) map.set(p.id, p.name)
  return map
})

function syncQuery(id: number | null) {
  const q = { ...route.query }
  if (id == null) delete q.projectId
  else q.projectId = String(id)
  router.replace({ query: q })
}

function fmtTime(ts?: number) {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  const isToday = d.toDateString() === now.toDateString()
  const yest = new Date(now); yest.setDate(now.getDate() - 1)
  const isYest = d.toDateString() === yest.toDateString()
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  if (isToday) return `今天 ${hh}:${mm}`
  if (isYest) return `昨天 ${hh}:${mm}`
  const M = String(d.getMonth() + 1).padStart(2, '0')
  const D = String(d.getDate()).padStart(2, '0')
  return `${M}-${D} ${hh}:${mm}`
}

function fmtFull(ts?: number) {
  if (!ts) return ''
  const d = new Date(ts)
  const Y = d.getFullYear()
  const M = String(d.getMonth() + 1).padStart(2, '0')
  const D = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${Y}-${M}-${D} ${hh}:${mm}`
}

function titleOf(h: AiChatHistoryItem) {
  const p = (h.prompt || '').trim()
  const line = p.split('\n')[0] || ''
  const t = line.trim() || '（空）'
  return t.length > 60 ? t.slice(0, 60) + '…' : t
}

function snippetOf(h: AiChatHistoryItem) {
  const s = (h.response || '').trim().replace(/\s+/g, ' ')
  if (!s) return '（无回复）'
  return s.length > 90 ? s.slice(0, 90) + '…' : s
}

const selected = ref<Set<number>>(new Set())
const detail = ref<AiChatHistoryItem | null>(null)
const showDetail = computed(() => !!detail.value)
const HISTORY_STATE_KEY = 'dtc_ai_history_state_v1'
const pendingDetailId = ref<number | null>(null)
let persistTimer: any = null

const selectedCount = computed(() => selected.value.size)

function isSelected(id: number) {
  return selected.value.has(id)
}

function setSelected(id: number, v: boolean) {
  const next = new Set(selected.value)
  if (v) next.add(id)
  else next.delete(id)
  selected.value = next
}

function toggleRow(id: number) {
  setSelected(id, !isSelected(id))
}

function clearSelected() {
  selected.value = new Set()
}

function openDetail(h: AiChatHistoryItem) {
  detail.value = h
  pendingDetailId.value = h.id
  schedulePersist()
}

function closeDetail() {
  detail.value = null
  pendingDetailId.value = null
  schedulePersist()
}

async function load() {
  loading.value = true
  try {
    list.value = await aiApi.historyList({ projectId: projectId.value, type: typeFilter.value || undefined, limit: 100 })
    clearSelected()
    if (pendingDetailId.value) {
      const hit = list.value.find((x) => x.id === pendingDetailId.value) || null
      if (hit) detail.value = hit
      else pendingDetailId.value = null
    }
  } catch (e: any) {
    message.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function persist() {
  try {
    localStorage.setItem(
      HISTORY_STATE_KEY,
      JSON.stringify({ projectId: projectId.value ?? null, detailId: pendingDetailId.value ?? null, ts: Date.now() })
    )
  } catch {}
}

function schedulePersist() {
  if (persistTimer) clearTimeout(persistTimer)
  persistTimer = setTimeout(() => persist(), 240)
}

async function deleteSelected() {
  const ids = Array.from(selected.value)
  if (!ids.length) return
  await deleteByIds(ids)
}

async function deleteByIds(ids: number[]) {
  if (!ids.length) return
  return new Promise<void>((resolve) => {
    dialog.warning({
      title: '删除历史记录',
      content: ids.length === 1 ? '确认删除这条记录？' : `确认删除选中的 ${ids.length} 条记录？`,
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          await aiApi.historyDelete(ids)
          const set = new Set(ids)
          list.value = list.value.filter((h) => !set.has(h.id))
          if (detail.value && set.has(detail.value.id)) closeDetail()
          selected.value = new Set(Array.from(selected.value).filter((id) => !set.has(id)))
          message.success('已删除')
        } catch (e: any) {
          message.error(e?.message || '删除失败')
        } finally {
          resolve()
        }
      },
      onNegativeClick: () => resolve()
    })
  })
}

async function clearAll() {
  dialog.warning({
    title: '清空历史记录',
    content: projectId.value ? '确认清空当前项目下的全部历史记录？' : '确认清空全部历史记录？',
    positiveText: '清空',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await aiApi.historyClear(projectId.value)
        list.value = []
        closeDetail()
        clearSelected()
        message.success('已清空')
      } catch (e: any) {
        message.error(e?.message || '清空失败')
      }
    }
  })
}

onMounted(async () => {
  if (!projectStore.projects.length) {
    await projectStore.load()
  }
  try {
    const raw = localStorage.getItem(HISTORY_STATE_KEY)
    const obj = raw ? (JSON.parse(raw) as any) : null
    if (!route.query.projectId && obj && obj.projectId && Number.isFinite(Number(obj.projectId))) {
      projectId.value = Number(obj.projectId)
    }
    if (obj && obj.detailId && Number.isFinite(Number(obj.detailId))) {
      pendingDetailId.value = Number(obj.detailId)
    }
  } catch {}
  const qid = route.query.projectId ? Number(route.query.projectId) : null
  if (qid && Number.isFinite(qid)) {
    projectId.value = qid
  } else if (projectId.value == null && projectStore.visibleProjects[0]) {
    projectId.value = projectStore.visibleProjects[0].id
    syncQuery(projectId.value)
  }
  await load()
  schedulePersist()
})

watch(projectId, async (id) => {
  syncQuery(id)
  schedulePersist()
  await load()
})

watch(typeFilter, async () => {
  await load()
})
</script>

<template>
  <div class="history-root">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <div class="left-tools">
        <n-select
          v-model:value="projectId"
          size="small"
          :options="projectOptions"
          placeholder="全部项目"
          clearable
          style="width: 200px"
        />
        <n-select
          v-model:value="typeFilter"
          size="small"
          :options="TYPE_OPTIONS"
          placeholder="全部类型"
          style="width: 130px"
        />
        <span class="count-tip">共 {{ list.length }} 条</span>
      </div>
      <div class="right-tools">
        <button class="btn" :disabled="loading" @click="load">刷新</button>
        <button class="btn btn-danger-text" :disabled="selectedCount === 0" @click="deleteSelected">
          删除<span v-if="selectedCount > 0" class="num"> · {{ selectedCount }}</span>
        </button>
        <button class="btn btn-danger" :disabled="!list.length" @click="clearAll">清空</button>
      </div>
    </div>

    <!-- 表格 -->
    <div class="card">
      <n-spin :show="loading">
        <div v-if="!list.length" class="empty">
          <div class="empty-title">暂无历史记录</div>
          <div class="empty-sub">开始一次 AI 对话、规划或审查，记录会出现在这里</div>
        </div>

        <table v-else class="t">
          <thead>
            <tr>
              <th class="th-check">
                <n-checkbox
                  :checked="list.length > 0 && selectedCount === list.length"
                  :indeterminate="selectedCount > 0 && selectedCount < list.length"
                  size="small"
                  @update:checked="(v) => (v ? (selected = new Set(list.map((x) => x.id))) : clearSelected())"
                />
              </th>
              <th class="th-type">类型</th>
              <th>标题</th>
              <th class="th-proj">项目</th>
              <th class="th-time">时间</th>
              <th class="th-act">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="h in list" :key="h.id" :class="{ active: isSelected(h.id) }" @click="toggleRow(h.id)">
              <td class="td-check" @click.stop>
                <n-checkbox
                  :checked="isSelected(h.id)"
                  size="small"
                  @update:checked="(v) => setSelected(h.id, v)"
                />
              </td>
              <td>
                <span class="tag">{{ labelOf(h.type) }}</span>
              </td>
              <td class="td-title" :title="titleOf(h)" @click.stop="openDetail(h)">
                <div class="title-row">{{ titleOf(h) }}</div>
                <div class="snippet">{{ snippetOf(h) }}</div>
              </td>
              <td class="muted">
                {{ h.projectId ? projectName.get(h.projectId) || `项目 #${h.projectId}` : '—' }}
              </td>
              <td class="time-cell">{{ fmtTime(h.createdAt) }}</td>
              <td class="td-act" @click.stop>
                <button class="link" @click="openDetail(h)">查看</button>
                <button class="link danger" @click="deleteByIds([h.id])">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </n-spin>
    </div>

    <!-- 详情弹窗 -->
    <n-modal
      :show="showDetail"
      preset="card"
      class="detail-modal"
      :bordered="false"
      @update:show="(v) => (v ? null : closeDetail())"
    >
      <template #header>
        <div class="dhead">
          <div class="dtitle">{{ detail ? titleOf(detail) : '' }}</div>
          <div class="dmeta">
            <span class="tag">{{ detail ? labelOf(detail.type) : '' }}</span>
            <span class="dot">·</span>
            <span>{{ detail ? fmtFull(detail.createdAt) : '' }}</span>
            <span class="dot">·</span>
            <span>
              {{
                detail && detail.projectId
                  ? projectName.get(detail.projectId) || `项目 #${detail.projectId}`
                  : '未关联项目'
              }}
            </span>
          </div>
        </div>
      </template>
      <template #header-extra>
        <button class="btn btn-danger-text" :disabled="!detail" @click="detail ? deleteByIds([detail.id]) : null">
          删除
        </button>
      </template>

      <div v-if="detail" class="dbody">
        <div class="block">
          <div class="block-label">提示词</div>
          <div class="block-content qtext">{{ detail.prompt }}</div>
        </div>
        <div class="block">
          <div class="block-label">AI 回复</div>
          <div class="block-content atext">
            <markdown-view :content="detail.response" />
          </div>
        </div>
      </div>
    </n-modal>
  </div>
</template>

<style scoped>
.history-root {
  padding: 0 0 24px;
  color: #111827;
}

/* 工具栏 */
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.left-tools,
.right-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.count-tip {
  font-size: 12px;
  color: #9ca3af;
  margin-left: 4px;
}

/* 按钮（白底 + 灰边） */
.btn {
  height: 30px;
  padding: 0 14px;
  background: #ffffff;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 13px;
  color: #111827;
  cursor: pointer;
  transition: all 0.15s ease;
}
.btn:hover:not(:disabled) {
  border-color: #111827;
}
.btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.btn .num {
  color: #111827;
  font-weight: 600;
}
.btn-danger-text {
  color: #374151;
}
.btn-danger-text:hover:not(:disabled) {
  border-color: #111827;
  background: #f3f4f6;
}
.btn-danger {
  color: #ffffff;
  background: #111827;
  border-color: #111827;
}
.btn-danger:hover:not(:disabled) {
  background: #1f2937;
  border-color: #1f2937;
}

/* 卡片 */
.card {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
}

/* 表格 */
.t {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.t thead th {
  text-align: left;
  font-weight: 600;
  color: #6b7280;
  font-size: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid #e5e7eb;
  background: #f9fafb;
}
.t tbody td {
  padding: 12px 14px;
  color: #111827;
  border-bottom: 1px solid #f3f4f6;
  vertical-align: middle;
}
.t tbody tr:last-child td {
  border-bottom: none;
}
.t tbody tr {
  cursor: pointer;
  transition: background 0.12s ease;
}
.t tbody tr:hover {
  background: #f9fafb;
}
.t tbody tr.active {
  background: #f3f4f6;
}
.th-check,
.td-check {
  width: 40px;
}
.th-type {
  width: 90px;
}
.th-proj {
  width: 130px;
}
.th-time {
  width: 110px;
}
.th-act {
  width: 130px;
}
.td-title {
  max-width: 360px;
}
.title-row {
  font-weight: 600;
  color: #111827;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.snippet {
  margin-top: 2px;
  font-size: 12px;
  color: #9ca3af;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.muted {
  color: #6b7280;
  font-size: 13px;
}
.time-cell {
  font-size: 12px;
  color: #4b5563;
  font-variant-numeric: tabular-nums;
}
.td-act {
  white-space: nowrap;
}
.link {
  background: transparent;
  border: none;
  padding: 4px 8px;
  font-size: 12px;
  color: #111827;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.12s ease;
}
.link:hover {
  background: #f3f4f6;
}
.link.danger {
  color: #374151;
}
.link.danger:hover {
  background: #f3f4f6;
}

/* 类型徽章（单一灰阶） */
.tag {
  display: inline-block;
  font-size: 11px;
  font-weight: 500;
  line-height: 1;
  padding: 4px 8px;
  border-radius: 4px;
  white-space: nowrap;
  background: #f3f4f6;
  color: #374151;
  border: 1px solid #e5e7eb;
}

/* 空态 */
.empty {
  padding: 60px 0;
  text-align: center;
}
.empty-title {
  font-size: 14px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 4px;
}
.empty-sub {
  font-size: 12px;
  color: #9ca3af;
}

/* 详情弹窗 */
.detail-modal :deep(.n-card) {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}
.dhead {
  display: grid;
  gap: 6px;
}
.dtitle {
  font-weight: 600;
  font-size: 16px;
  color: #111827;
}
.dmeta {
  font-size: 12px;
  color: #6b7280;
  display: flex;
  gap: 8px;
  align-items: center;
}
.dot {
  color: #d1d5db;
}

.dbody {
  display: grid;
  gap: 14px;
}
.block {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
}
.block-label {
  padding: 8px 14px;
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  background: #f9fafb;
  border-bottom: 1px solid #f3f4f6;
}
.block-content {
  padding: 14px;
}
.qtext {
  white-space: pre-wrap;
  line-height: 1.65;
  font-size: 13px;
  color: #111827;
}
.atext {
  padding: 0;
  background: #ffffff;
}

/* 响应式 */
@media (max-width: 880px) {
  .th-proj,
  .t td:nth-child(4) {
    display: none;
  }
  .td-title {
    max-width: none;
  }
}
</style>