<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NDatePicker, NInput, NModal, NPagination, NSelect, NSpin, NSwitch, useMessage } from 'naive-ui'
import { taskApi, type TaskSearchItem, type TaskSearchResponse, type TaskStatus } from '../api/task'
import { milestoneApi } from '../api/milestone'
import { projectCollabApi } from '../api/projectCollab'
import { useProjectStore } from '../stores/project'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const ps = useProjectStore()

const q = ref('')
const filterOpen = ref(false)
const loading = ref(false)
const result = ref<TaskSearchResponse>({ total: 0, items: [] })

const projectId = ref<number | null>(null)
const includeArchived = ref(false)
const statuses = ref<TaskStatus[]>([])
const assigneeId = ref<number | null>(null)
const milestoneId = ref<number | null>(null)
const tagsText = ref('')
const updatedRange = ref<[number, number] | null>(null)

const membersLoading = ref(false)
const memberOptions = ref<{ label: string; value: number }[]>([])
const milestonesLoading = ref(false)
const milestoneOptions = ref<{ label: string; value: number }[]>([])

const page = ref(1)
const pageSize = ref(30)

const hasAnyFilter = computed(() => {
  return (
    !!projectId.value ||
    includeArchived.value ||
    statuses.value.length > 0 ||
    assigneeId.value != null ||
    milestoneId.value != null ||
    tagsText.value.trim() !== '' ||
    !!updatedRange.value
  )
})

const projectOptions = computed(() => [
  { label: '全部项目', value: 0 },
  ...ps.projects.map((p: any) => ({
    label: Number(p.archived || 0) === 1 ? `${p.name} · 已归档` : p.name,
    value: p.id
  }))
])

const statusOptions = [
  { label: '待办', value: 'TODO' },
  { label: '进行中', value: 'DOING' },
  { label: '已完成', value: 'DONE' }
]

const assigneeOptions = computed(() => [
  { label: '全部', value: 0 },
  { label: '未分配', value: -1 },
  ...memberOptions.value
])

const isProjectSelected = computed(() => Number(projectId.value || 0) > 0)

function parseCsv(s?: string | null) {
  const raw = String(s || '')
    .split(',')
    .map((x) => x.trim())
    .filter(Boolean)
  return raw
}

function syncFromRoute() {
  q.value = String(route.query.q || '').trim()
  const pid = Number(route.query.projectId || 0)
  projectId.value = pid > 0 ? pid : null
  includeArchived.value = String(route.query.arch || '') === '1'
  statuses.value = parseCsv(String(route.query.status || '')).filter((s) => s === 'TODO' || s === 'DOING' || s === 'DONE') as TaskStatus[]
  const aid = Number(route.query.assigneeId || 0)
  assigneeId.value = Number.isFinite(aid) && aid !== 0 ? aid : null
  const mid = Number(route.query.milestoneId || 0)
  milestoneId.value = Number.isFinite(mid) && mid > 0 ? mid : null
  tagsText.value = String(route.query.tags || '')
  const from = Number(route.query.uf || 0)
  const to = Number(route.query.ut || 0)
  updatedRange.value = from && to ? [from, to] : null
  const p = Number(route.query.page || 1)
  page.value = Number.isFinite(p) && p > 0 ? p : 1
}

function syncToRoute() {
  const query: any = { ...route.query }
  query.q = q.value.trim() ? q.value.trim() : undefined
  query.projectId = projectId.value ? String(projectId.value) : undefined
  query.arch = includeArchived.value ? '1' : undefined
  query.status = statuses.value.length ? statuses.value.join(',') : undefined
  query.assigneeId = assigneeId.value != null ? String(assigneeId.value) : undefined
  query.milestoneId = milestoneId.value != null ? String(milestoneId.value) : undefined
  query.tags = tagsText.value.trim() ? tagsText.value.trim() : undefined
  query.uf = updatedRange.value ? String(updatedRange.value[0]) : undefined
  query.ut = updatedRange.value ? String(updatedRange.value[1]) : undefined
  query.page = page.value > 1 ? String(page.value) : undefined
  router.replace({ name: 'task-search', query })
}

async function loadMetaForProject(pid: number | null) {
  memberOptions.value = []
  milestoneOptions.value = []
  assigneeId.value = null
  milestoneId.value = null
  if (!pid) return

  membersLoading.value = true
  milestonesLoading.value = true
  try {
    const res = await projectCollabApi.members(pid)
    memberOptions.value = (res.members || [])
      .filter((m: any) => Number(m.disabled || 0) !== 1)
      .map((m: any) => ({ label: m.username, value: Number(m.userId) }))
  } catch {
    memberOptions.value = []
  } finally {
    membersLoading.value = false
  }
  try {
    const ms = await milestoneApi.list(pid, true)
    milestoneOptions.value = [{ label: '全部', value: 0 }, ...ms.map((m) => ({ label: m.name, value: m.id }))]
  } catch {
    milestoneOptions.value = [{ label: '全部', value: 0 }]
  } finally {
    milestonesLoading.value = false
  }
}

function applySearch() {
  page.value = 1
  syncToRoute()
}

function applyFilters() {
  filterOpen.value = false
  applySearch()
}

function clearFilters() {
  projectId.value = null
  includeArchived.value = false
  statuses.value = []
  assigneeId.value = null
  milestoneId.value = null
  tagsText.value = ''
  updatedRange.value = null
  page.value = 1
  memberOptions.value = []
  milestoneOptions.value = []
  syncToRoute()
}

function tagList() {
  return parseCsv(tagsText.value)
    .map((t) => t.replace(/^#/, ''))
    .filter(Boolean)
    .slice(0, 8)
}

async function load() {
  loading.value = true
  try {
    const res = await taskApi.search({
      q: q.value,
      projectId: projectId.value,
      includeArchived: includeArchived.value,
      statuses: statuses.value,
      assigneeId: assigneeId.value,
      milestoneId: milestoneId.value,
      tags: tagList(),
      updatedFrom: updatedRange.value ? updatedRange.value[0] : null,
      updatedTo: updatedRange.value ? updatedRange.value[1] : null,
      page: page.value,
      pageSize: pageSize.value
    })
    result.value = res
  } catch (e: any) {
    result.value = { total: 0, items: [] }
    message.error(e?.message || '搜索失败')
  } finally {
    loading.value = false
  }
}

function openTask(t: TaskSearchItem) {
  if (!t || !t.id || !t.projectId) return
  router.push({ name: 'task-detail', params: { projectId: t.projectId, taskId: t.id } })
}

function fmt(ts?: string | null) {
  if (!ts) return ''
  const d = new Date(ts)
  if (Number.isNaN(d.getTime())) return ts
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${dd} ${hh}:${mm}`
}

function shortDue(ts?: string | null) {
  if (!ts) return ''
  const d = new Date(ts)
  if (Number.isNaN(d.getTime())) return ts
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${mm}-${dd}`
}

function statusClass(s?: string | null) {
  const v = String(s || '').toUpperCase()
  if (v === 'DONE') return 'pill done'
  if (v === 'DOING') return 'pill doing'
  return 'pill todo'
}

function statusLabel(s?: string | null) {
  const v = String(s || '').toUpperCase()
  if (v === 'DONE') return '已完成'
  if (v === 'DOING') return '进行中'
  return '待办'
}

function priorityLabel(s?: string | null) {
  const v = String(s || '').toUpperCase()
  if (v === 'HIGH') return '高'
  if (v === 'MEDIUM') return '中'
  if (v === 'LOW') return '低'
  return v
}

watch(
  () => route.fullPath,
  async () => {
    syncFromRoute()
    await loadMetaForProject(projectId.value)
    await load()
  }
)

watch(projectId, async (pid, prev) => {
  const p0 = Number(prev || 0)
  const p1 = Number(pid || 0)
  if (p0 === p1) return
  await loadMetaForProject(pid)
})

onMounted(async () => {
  if (!ps.projects.length) await ps.load()
  syncFromRoute()
  await loadMetaForProject(projectId.value)
  await load()
})
</script>

<template>
  <div class="page">
    <div class="head">
      <div class="left">
        <div class="h1">任务搜索</div>
        <div class="muted sub">共 {{ result.total }} 条结果</div>
      </div>
      <div class="right">
        <div class="searchBox">
          <n-input v-model:value="q" placeholder="搜索标题 / 描述 / 评论 / 交付物…" @keyup.enter="applySearch" />
        </div>
        <n-button tertiary @click="filterOpen = true">筛选<span v-if="hasAnyFilter" class="badge">•</span></n-button>
      </div>
    </div>

    <section class="panel block">
      <n-spin :show="loading">
        <div v-if="!result.items.length" class="empty-summary" />
        <div v-else class="list">
          <button v-for="t in result.items" :key="t.id" class="row hover-row" type="button" @click="openTask(t)">
            <div class="main">
              <div class="title">{{ t.title }}</div>
              <div class="meta">
                <span class="chip proj" :class="{ archived: Number(t.projectArchived || 0) === 1 }">{{ t.projectName }}</span>
                <span v-if="t.milestoneName" class="chip muted">{{ t.milestoneName }}</span>
                <span v-if="t.assignee" class="chip muted">{{ t.assignee }}</span>
                <span v-if="t.dueTime" class="chip muted">截止 {{ shortDue(t.dueTime) }}</span>
                <span v-if="t.updatedAt" class="chip muted">更新 {{ fmt(t.updatedAt) }}</span>
              </div>
              <div v-if="t.tags" class="tags">
                <span v-for="tag in String(t.tags).split(',').map((x) => x.trim()).filter(Boolean).slice(0, 4)" :key="tag" class="tag">#{{ tag }}</span>
              </div>
            </div>
            <div class="side">
              <span :class="statusClass(t.status)">{{ statusLabel(t.status) }}</span>
              <span v-if="t.priority" class="chip pri">{{ priorityLabel(t.priority) }}</span>
            </div>
          </button>
        </div>

        <div v-if="result.total > pageSize" class="pager">
          <n-pagination
            :page="page"
            :page-size="pageSize"
            :item-count="result.total"
            :page-slot="7"
            @update:page="
              (p) => {
                page = p
                syncToRoute()
              }
            "
          />
        </div>
      </n-spin>
    </section>

    <n-modal v-model:show="filterOpen" preset="card" title="筛选" class="filterModal">
      <div class="fgrid">
        <div class="frow">
          <div class="muted label">项目</div>
          <n-select
            :value="projectId || 0"
            :options="projectOptions"
            @update:value="(v) => (projectId = Number(v) > 0 ? Number(v) : null)"
          />
        </div>
        <div class="frow">
          <div class="muted label">包含已归档项目</div>
          <n-switch size="small" :value="includeArchived" @update:value="(v) => (includeArchived = !!v)" />
        </div>
        <div class="frow">
          <div class="muted label">状态</div>
          <n-select v-model:value="statuses" multiple :options="statusOptions" placeholder="全部" />
        </div>
        <div class="frow">
          <div class="muted label">负责人</div>
          <n-select
            :value="assigneeId == null ? 0 : assigneeId"
            :options="assigneeOptions"
            :loading="membersLoading"
            :disabled="!isProjectSelected"
            @update:value="(v) => (assigneeId = Number(v) === 0 ? null : Number(v))"
          />
        </div>
        <div class="frow">
          <div class="muted label">里程碑</div>
          <n-select
            :value="milestoneId || 0"
            :options="milestoneOptions"
            :loading="milestonesLoading"
            :disabled="!isProjectSelected"
            @update:value="(v) => (milestoneId = Number(v) > 0 ? Number(v) : null)"
          />
        </div>
        <div class="frow">
          <div class="muted label">标签</div>
          <n-input v-model:value="tagsText" placeholder="如:前端, 后端" />
        </div>
        <div class="frow">
          <div class="muted label">更新时间范围</div>
          <n-date-picker v-model:value="updatedRange" type="datetimerange" clearable />
        </div>
      </div>
      <template #footer>
        <div class="factions">
          <n-button tertiary :disabled="!hasAnyFilter" @click="clearFilters">清空</n-button>
          <n-button type="primary" @click="applyFilters">应用</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.left {
  min-width: 0;
}
.h1 {
  font-size: 28px;
  font-weight: 900;
  letter-spacing: -0.5px;
  line-height: 1.1;
}
.sub {
  font-size: 12px;
  margin-top: 6px;
}
.right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.searchBox {
  width: min(520px, 46vw);
}
.badge {
  margin-left: 6px;
  font-weight: 900;
}
.block {
  padding: 14px 14px;
}
.list {
  display: grid;
  gap: 8px;
}
.row {
  width: 100%;
  text-align: left;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.72);
  padding: 12px 12px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.main {
  min-width: 0;
  display: grid;
  gap: 6px;
}
.title {
  font-weight: 900;
  letter-spacing: -0.25px;
  font-size: 14px;
}
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.chip {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.10);
  color: rgba(15, 23, 42, 0.82);
  background: rgba(15, 23, 42, 0.03);
}
.chip.muted {
  color: rgba(15, 23, 42, 0.62);
  background: rgba(15, 23, 42, 0.02);
}
.chip.proj.archived {
  opacity: 0.72;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid rgba(var(--accent-rgb), 0.12);
  background: rgba(var(--accent-rgb), 0.04);
  color: rgba(15, 23, 42, 0.78);
  font-weight: 800;
}
.side {
  flex-shrink: 0;
  display: grid;
  gap: 8px;
  justify-items: end;
}
.pill {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  letter-spacing: 0.4px;
  border: 1px solid rgba(15, 23, 42, 0.10);
}
.pill.todo {
  background: rgba(15, 23, 42, 0.03);
  color: rgba(15, 23, 42, 0.70);
  font-weight: 900;
}
.pill.doing {
  background: rgba(var(--accent2-rgb), 0.06);
  border-color: rgba(var(--accent2-rgb), 0.14);
  color: rgba(15, 23, 42, 0.92);
  font-weight: 900;
}
.pill.done {
  background: rgba(var(--accent-rgb), 0.05);
  border-color: rgba(var(--accent-rgb), 0.14);
  color: rgba(15, 23, 42, 0.78);
  font-weight: 900;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
:global(.filterModal) {
  width: min(680px, calc(100vw - 28px));
}
.fgrid {
  display: grid;
  gap: 12px;
}
.frow {
  display: grid;
  gap: 6px;
}
.label {
  font-size: 12px;
}
.factions {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}
</style>
