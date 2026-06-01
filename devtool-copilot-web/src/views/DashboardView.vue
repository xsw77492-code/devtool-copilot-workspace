<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NInput, NModal, NSelect, NSpin, NSwitch, useMessage } from 'naive-ui'
import { aiApi, type TaskPlan } from '../api/ai'
import { taskApi, type Task, type TaskStatus, type WorkspaceMyWorkItem } from '../api/task'
import { milestoneApi, type Milestone } from '../api/milestone'
import { useProjectStore } from '../stores/project'
import { useRealtimeStore } from '../stores/realtime'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const projectStore = useProjectStore()
const rt = useRealtimeStore()

const creating = ref(false)
const name = ref('')
const description = ref('')
const createOpen = ref(false)

const myWorkLoading = ref(false)
const myWork = ref<WorkspaceMyWorkItem[]>([])
const myWorkUpdatingId = ref<number | null>(null)
const myWorkLastLoadAt = ref(0)

const requirementInput = ref<any>(null)

const requirement = ref('')
const generating = ref(false)
const plans = ref<TaskPlan[]>([])
const streamText = ref('')

const projectId = ref<number | null>(null)
const addingKey = ref<string | null>(null)
const added = ref<Set<string>>(new Set())
const addingAll = ref(false)
const lastAdded = ref<{ projectId: number; count: number } | null>(null)

type PlannerDraft = {
  id: string
  order: number
  title: string
  description: string
  priority: string
  acceptanceCriteria: string
  deliverablesHint: string
}

const drafts = ref<PlannerDraft[]>([])
const expandedDraftId = ref<string | null>(null)

const milestoneId = ref<number | null>(null)
const parentTaskId = ref<number | null>(null)
const milestones = ref<Milestone[]>([])
const parentCandidates = ref<Task[]>([])

type PlannerSavedItem = {
  id: string
  createdAt: number
  title: string
  projectId: number | null
  milestoneId: number | null
  parentTaskId: number | null
  requirement: string
  drafts: PlannerDraft[]
}

const PLANNER_HISTORY_KEY = 'dtc_planner_history_v1'
const PLANNER_TEMPLATES_KEY = 'dtc_planner_templates_v1'
const PLANNER_CACHE_KEY = 'dtc_workspace_planner_cache_v1'
const savedOpen = ref(false)
const savedTab = ref<'history' | 'templates'>('history')
const savedHistory = ref<PlannerSavedItem[]>([])
const savedTemplates = ref<PlannerSavedItem[]>([])
let persistPlannerTimer: any = null

onMounted(async () => {
  await projectStore.load()
  restorePlannerCache()
  if (projectId.value != null) {
    const exists = projectStore.projects.some((p) => p.id === projectId.value)
    if (!exists) {
      projectId.value = null
    } else {
      const visible = projectStore.visibleProjects.some((p) => p.id === projectId.value)
      if (!visible) {
        const p = projectStore.projects.find((x) => x.id === projectId.value)
        if (p && Number(p.archived || 0) === 1) {
          projectStore.setShowArchived(true)
        }
      }
    }
  }
  loadSaved()
  const first = projectStore.projects[0]
  if (first) {
    if (!projectId.value) projectId.value = first.id
    rt.subscribe(first.id, 'PROJECT', first.id)
  }
  await loadMyWork()
})

onBeforeUnmount(() => {
  rt.subscribe(null)
  persistPlannerCache()
})

watch(
  () => projectId.value,
  async (id) => {
    const pid = Number(id || 0)
    if (!pid) return
    rt.subscribe(pid, 'PROJECT', pid)
    await loadPlannerMeta(pid)
  }
)

watch(
  [requirement, drafts, added, milestoneId, parentTaskId, projectId, expandedDraftId],
  () => schedulePersistPlannerCache(),
  { deep: true }
)

watch(
  () => rt.seq,
  async () => {
    const ev = rt.lastEvent
    const pid = Number(ev?.projectId || 0)
    if (!pid) return
    const t = String(ev?.type || '')
    if (
      t !== 'TASK_CREATED' &&
      t !== 'TASK_UPDATED' &&
      t !== 'TASK_STATUS_UPDATED' &&
      t !== 'TASK_MOVED' &&
      t !== 'TASK_DELETED' &&
      t !== 'AI_GENERATE_TASK' &&
      t !== 'AI_APPLY_DONE'
    )
      return
    await refreshMyWork()
  }
)

const projectOptions = computed(() =>
  projectStore.visibleProjects.map((p) => ({ label: p.name, value: p.id }))
)

const projectsSorted = computed(() =>
  projectStore.visibleProjects
    .slice()
    .sort((a, b) => (Number(b.createdAt || 0) || b.id) - (Number(a.createdAt || 0) || a.id))
)

function openProject(id: number) {
  router.push({ name: 'project-detail', params: { id } })
}

function fmtDate(ts?: number) {
  if (!ts) return ''
  const d = new Date(ts)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${dd}`
}

function fmtDateTime(ts?: number) {
  if (!ts) return ''
  const d = new Date(ts)
  const ymd = fmtDate(ts)
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${ymd} ${hh}:${mm}`
}

const remainingCount = computed(() => drafts.value.filter((p) => !added.value.has(p.id)).length)
const addedCount = computed(() => drafts.value.filter((p) => added.value.has(p.id)).length)
const totalCount = computed(() => drafts.value.length)
const progressPct = computed(() => {
  if (!totalCount.value) return 0
  return Math.round((addedCount.value / totalCount.value) * 100)
})

const milestoneOptions = computed(() => [
  { label: '无里程碑', value: 0 },
  ...milestones.value
    .filter((m) => String(m.status || '') !== 'ARCHIVED')
    .map((m) => ({ label: m.name, value: m.id }))
])

const parentOptions = computed(() => [
  { label: '无父任务', value: 0 },
  ...parentCandidates.value.map((t) => ({ label: `#${t.id} ${t.title}`, value: t.id }))
])

async function createProject() {
  creating.value = true
  try {
    const p = await projectStore.create({ name: name.value, description: description.value })
    name.value = ''
    description.value = ''
    createOpen.value = false
    if (!projectId.value) projectId.value = p.id
    router.push({ name: 'project-detail', params: { id: p.id } })
  } finally {
    creating.value = false
  }
}

function openCreate() {
  name.value = ''
  description.value = ''
  createOpen.value = true
}

function loadSaved() {
  try {
    const raw = localStorage.getItem(PLANNER_HISTORY_KEY) || '[]'
    const arr = JSON.parse(raw)
    savedHistory.value = Array.isArray(arr) ? (arr as PlannerSavedItem[]) : []
  } catch {
    savedHistory.value = []
  }
  try {
    const raw = localStorage.getItem(PLANNER_TEMPLATES_KEY) || '[]'
    const arr = JSON.parse(raw)
    savedTemplates.value = Array.isArray(arr) ? (arr as PlannerSavedItem[]) : []
  } catch {
    savedTemplates.value = []
  }
}

function persistSaved() {
  try {
    localStorage.setItem(PLANNER_HISTORY_KEY, JSON.stringify(savedHistory.value.slice(0, 12)))
  } catch {
  }
  try {
    localStorage.setItem(PLANNER_TEMPLATES_KEY, JSON.stringify(savedTemplates.value.slice(0, 24)))
  } catch {
  }
}

function openSaved(tabKey: 'history' | 'templates') {
  savedTab.value = tabKey
  savedOpen.value = true
}

function plannerTitle(req: string) {
  const first = String(req || '').trim().split('\n')[0] || ''
  const t = first.trim() || '未命名需求'
  return t.length > 36 ? t.slice(0, 36) + '…' : t
}

function toSavedItem(): PlannerSavedItem | null {
  const req = requirement.value.trim()
  if (!req || !drafts.value.length) return null
  return {
    id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    createdAt: Date.now(),
    title: plannerTitle(req),
    projectId: projectId.value ?? null,
    milestoneId: milestoneId.value || null,
    parentTaskId: parentTaskId.value || null,
    requirement: req,
    drafts: drafts.value.map((d) => ({ ...d }))
  }
}

function saveToHistory(item: PlannerSavedItem) {
  savedHistory.value = [item, ...savedHistory.value.filter((x) => x.id !== item.id)].slice(0, 12)
  persistSaved()
}

function saveCurrentAsTemplate() {
  const item = toSavedItem()
  if (!item) {
    message.warning('请先生成任务再保存模板')
    return
  }
  savedTemplates.value = [item, ...savedTemplates.value].slice(0, 24)
  persistSaved()
  message.success('已保存为模板')
}

function applySaved(item: PlannerSavedItem) {
  requirement.value = item.requirement
  projectId.value = item.projectId ?? projectId.value
  milestoneId.value = Number(item.milestoneId || 0) || null
  parentTaskId.value = Number(item.parentTaskId || 0) || null
  drafts.value = (item.drafts || []).map((d, i) => ({
    id: d.id || `${Date.now()}-${i}`,
    order: Number(d.order || i + 1),
    title: String(d.title || ''),
    description: String(d.description || ''),
    priority: String(d.priority || 'MEDIUM'),
    acceptanceCriteria: String((d as any).acceptanceCriteria || ''),
    deliverablesHint: String((d as any).deliverablesHint || '')
  }))
  added.value = new Set()
  expandedDraftId.value = null
  message.success('已载入')
}

function deleteSaved(id: string, tabKey: 'history' | 'templates') {
  if (tabKey === 'history') savedHistory.value = savedHistory.value.filter((x) => x.id !== id)
  else savedTemplates.value = savedTemplates.value.filter((x) => x.id !== id)
  persistSaved()
}

function schedulePersistPlannerCache() {
  if (persistPlannerTimer) clearTimeout(persistPlannerTimer)
  persistPlannerTimer = setTimeout(() => persistPlannerCache(), 240)
}

function persistPlannerCache() {
  try {
    const payload = {
      ts: Date.now(),
      projectId: projectId.value ?? null,
      milestoneId: milestoneId.value || null,
      parentTaskId: parentTaskId.value || null,
      requirement: requirement.value ?? '',
      expandedDraftId: expandedDraftId.value ?? null,
      drafts: drafts.value ?? [],
      added: Array.from(added.value ?? []),
      lastAdded: lastAdded.value ?? null
    }
    localStorage.setItem(PLANNER_CACHE_KEY, JSON.stringify(payload))
  } catch {
  }
}

function restorePlannerCache() {
  try {
    const raw = localStorage.getItem(PLANNER_CACHE_KEY)
    const obj = raw ? (JSON.parse(raw) as any) : null
    if (!obj) return
    requirement.value = String(obj.requirement || '')
    milestoneId.value = obj.milestoneId != null ? Number(obj.milestoneId) || null : null
    parentTaskId.value = obj.parentTaskId != null ? Number(obj.parentTaskId) || null : null
    expandedDraftId.value = obj.expandedDraftId ? String(obj.expandedDraftId) : null
    if (obj.projectId != null && Number.isFinite(Number(obj.projectId))) {
      projectId.value = Number(obj.projectId)
    }
    const ds = Array.isArray(obj.drafts) ? obj.drafts : []
    drafts.value = ds.map((d: any, i: number) => ({
      id: d?.id ? String(d.id) : `${Date.now()}-${i}`,
      order: Number(d?.order || i + 1),
      title: String(d?.title || ''),
      description: String(d?.description || ''),
      priority: String(d?.priority || 'MEDIUM'),
      acceptanceCriteria: String(d?.acceptanceCriteria || ''),
      deliverablesHint: String(d?.deliverablesHint || '')
    }))
    const arr = Array.isArray(obj.added) ? obj.added.map((x: any) => String(x)) : []
    added.value = new Set(arr)
    lastAdded.value = obj.lastAdded && obj.lastAdded.projectId ? obj.lastAdded : null
  } catch {
  }
}

async function loadPlannerMeta(pid: number) {
  try {
    milestones.value = await milestoneApi.list(pid, false)
  } catch {
    milestones.value = []
  }
  try {
    const list = await taskApi.listByProject(pid)
    parentCandidates.value = list.filter((t) => !t.parentTaskId)
  } catch {
    parentCandidates.value = []
  }
}

function toggleExpand(id: string) {
  expandedDraftId.value = expandedDraftId.value === id ? null : id
}

async function generate() {
  const text = requirement.value.trim()
  if (!text) {
    message.warning('请先描述需求')
    return
  }
  generating.value = true
  plans.value = []
  added.value = new Set()
  drafts.value = []
  expandedDraftId.value = null
  streamText.value = ''
  try {
    let buf = ''
    let scheduled = false
    const flush = () => {
      scheduled = false
      if (!buf) return
      streamText.value += buf
      buf = ''
    }
    plans.value = await aiApi.taskSplitStream(
      { requirement: text },
      (delta) => {
        buf += delta
        if (!scheduled) {
          scheduled = true
          setTimeout(flush, 40)
        }
      }
    )
    const now = Date.now()
    drafts.value = (plans.value || []).map((p, i) => ({
      id: `${now}-${i}`,
      order: Number(p.order || i + 1),
      title: String(p.title || '').trim(),
      description: String(p.description || '').trim(),
      priority: String(p.priority || 'MEDIUM'),
      acceptanceCriteria: '',
      deliverablesHint: ''
    }))
    const saved = toSavedItem()
    if (saved) saveToHistory(saved)
  } catch (e: any) {
    message.error(e?.message || '生成失败')
  } finally {
    generating.value = false
  }
}

function buildTaskDescription(d: PlannerDraft) {
  const lines: string[] = []
  if (d.description.trim()) lines.push(d.description.trim())
  if (d.deliverablesHint.trim()) {
    lines.push('')
    lines.push('交付物建议：')
    for (const s of d.deliverablesHint.split('\n').map((x) => x.trim()).filter(Boolean)) {
      lines.push(`- ${s}`)
    }
  }
  return lines.join('\n').trim() || null
}

async function addToProject(d: PlannerDraft) {
  if (!projectId.value) {
    message.warning('请先选择一个项目')
    return
  }
  const pid = projectId.value
  addingKey.value = d.id
  try {
    await taskApi.create(pid, d.title, {
      description: buildTaskDescription(d),
      acceptanceCriteria: d.acceptanceCriteria.trim() ? d.acceptanceCriteria.trim() : null,
      priority: d.priority || null,
      milestoneId: milestoneId.value || null,
      parentTaskId: parentTaskId.value || null,
      source: 'AI'
    })
    added.value = new Set([...added.value, d.id])
    lastAdded.value = { projectId: pid, count: 1 }
    await refreshMyWork()
    message.success('已添加到项目')
  } catch (e: any) {
    message.error(e?.message || '添加失败')
  } finally {
    addingKey.value = null
  }
}

async function addAllToProject() {
  if (!projectId.value) {
    message.warning('请先选择一个项目')
    return
  }
  if (!drafts.value.length) return

  const pid = projectId.value
  const list = drafts.value.filter((d) => !added.value.has(d.id))
  if (!list.length) return

  addingAll.value = true
  try {
    for (const d of list) {
      await taskApi.create(pid, d.title, {
        description: buildTaskDescription(d),
        acceptanceCriteria: d.acceptanceCriteria.trim() ? d.acceptanceCriteria.trim() : null,
        priority: d.priority || null,
        milestoneId: milestoneId.value || null,
        parentTaskId: parentTaskId.value || null,
        source: 'AI'
      })
      added.value = new Set([...added.value, d.id])
    }
    lastAdded.value = { projectId: pid, count: list.length }
    await refreshMyWork()
    message.success('已全部添加到项目')
  } catch (e: any) {
    message.error(e?.message || '添加失败')
  } finally {
    addingAll.value = false
  }
}

async function loadMyWork() {
  myWorkLoading.value = true
  try {
    const list = await taskApi.myWork(6)
    myWork.value = list
  } catch {
    myWork.value = []
    message.error('My Work 加载失败')
  } finally {
    myWorkLoading.value = false
  }
}

async function refreshMyWork() {
  const now = Date.now()
  if (now - myWorkLastLoadAt.value < 800) return
  myWorkLastLoadAt.value = now
  await loadMyWork()
}

function dueBadge(it: WorkspaceMyWorkItem): { label: string; tone: 'overdue' | 'today' | 'soon' } | null {
  if (!it.dueTime) return null
  const due = new Date(it.dueTime)
  if (Number.isNaN(due.getTime())) return null
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const end = start + 24 * 60 * 60 * 1000
  const ts = due.getTime()
  if (ts < now.getTime()) return { label: 'Overdue', tone: 'overdue' }
  if (ts >= start && ts < end) return { label: 'Today', tone: 'today' }
  const days = Math.ceil((ts - end) / (24 * 60 * 60 * 1000))
  if (days >= 0 && days <= 3) return { label: 'Soon', tone: 'soon' }
  return null
}

function openMyWork(it: WorkspaceMyWorkItem) {
  router.push({ name: 'task-detail', params: { projectId: it.projectId, taskId: it.taskId } })
}

function nextStatus(st: TaskStatus): TaskStatus | null {
  if (st === 'TODO') return 'DOING'
  if (st === 'DOING') return 'DONE'
  return null
}

function nextLabel(st: TaskStatus) {
  if (st === 'TODO') return '开始'
  if (st === 'DOING') return '完成'
  return ''
}

async function advanceMyWork(it: WorkspaceMyWorkItem) {
  const ns = nextStatus(it.status)
  if (!ns) return
  myWorkUpdatingId.value = it.taskId
  try {
    await taskApi.updateStatusSafe(it.taskId, ns, it.updatedAt ?? null)
    if (ns === 'DONE') {
      myWork.value = myWork.value.filter((x) => x.taskId !== it.taskId)
    } else {
      it.status = ns
    }
  } catch (e: any) {
    message.error(e?.message || '操作失败')
  } finally {
    myWorkUpdatingId.value = null
  }
}

function pillClass(status: string) {
  if (status === 'DONE') return 'pill done'
  if (status === 'DOING') return 'pill doing'
  return 'pill todo'
}

const myWorkStats = computed(() => {
  let overdue = 0
  let today = 0
  let doing = 0
  for (const it of myWork.value) {
    if (it.status === 'DOING') doing += 1
    const b = dueBadge(it)
    if (b?.tone === 'overdue') overdue += 1
    if (b?.tone === 'today') today += 1
  }
  return { overdue, today, doing }
})

const myWorkSmart = computed(() => {
  const now = Date.now()
  const order = (it: WorkspaceMyWorkItem) => {
    const due = it.dueTime ? Date.parse(it.dueTime) : NaN
    const b = dueBadge(it)
    const dueRank = b?.tone === 'overdue' ? 0 : b?.tone === 'today' ? 1 : b?.tone === 'soon' ? 2 : 3
    const stRank = it.status === 'DOING' ? 0 : it.status === 'TODO' ? 1 : 2
    const updated = it.updatedAt ? Date.parse(it.updatedAt) : 0
    const dueTs = Number.isFinite(due) ? due : now + 365 * 24 * 60 * 60 * 1000
    return { stRank, dueRank, dueTs, updated }
  }
  return myWork.value
    .slice()
    .sort((a, b) => {
      const aa = order(a)
      const bb = order(b)
      if (aa.stRank !== bb.stRank) return aa.stRank - bb.stRank
      if (aa.dueRank !== bb.dueRank) return aa.dueRank - bb.dueRank
      if (aa.dueTs !== bb.dueTs) return aa.dueTs - bb.dueTs
      if (aa.updated !== bb.updated) return bb.updated - aa.updated
      return b.taskId - a.taskId
    })
})

let autoRefreshTimer: any = null
const onFocus = () => {
  if (route.name === 'workspace') refreshMyWork()
}
const onVisibility = () => {
  if (document.visibilityState === 'visible' && route.name === 'workspace') refreshMyWork()
}

onMounted(() => {
  window.addEventListener('focus', onFocus)
  document.addEventListener('visibilitychange', onVisibility)
  if (!autoRefreshTimer) {
    autoRefreshTimer = setInterval(() => {
      if (document.visibilityState !== 'visible') return
      if (route.name !== 'workspace') return
      void refreshMyWork()
    }, 60000)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('focus', onFocus)
  document.removeEventListener('visibilitychange', onVisibility)
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
})
</script>

<template>
  <div class="page wsPage">
    <div class="head">
      <div class="left">
        <h1 class="h1">Workspace</h1>
      </div>
    </div>

    <div class="wsGrid">
      <section class="panel block projPanel">
          <div class="block-head">
            <div class="h2">Projects</div>
            <div class="projHeadRight">
              <div class="muted meta">{{ projectStore.visibleProjects.length }}</div>
              <div class="archToggle">
                <div class="muted archLabel">Archived</div>
                <n-switch
                  size="small"
                  :value="projectStore.showArchived"
                  @update:value="(v) => projectStore.setShowArchived(!!v)"
                />
              </div>
              <button class="iconBtn" type="button" aria-label="新建项目" @click="openCreate">＋</button>
            </div>
          </div>

          <div class="block-body scroll">
            <div class="list compact">
              <div v-for="p in projectsSorted" :key="p.id" class="projRow hover-row" @click="openProject(p.id)">
                <div class="pname">{{ p.name }}</div>
                <div class="ptime muted">{{ fmtDateTime(p.createdAt) }}</div>
              </div>
              <div v-if="!projectsSorted.length">
                <div v-for="i in 4" :key="`p-ph-${i}`" class="projRow placeholderRow" />
              </div>
            </div>
          </div>
        </section>

      <section class="panel block mywork">
            <div class="block-head">
              <div class="h2">My Work</div>
              <div class="mwHeadRight">
                <div class="muted meta">Doing {{ myWorkStats.doing }} · Today {{ myWorkStats.today }} · Overdue {{ myWorkStats.overdue }}</div>
                <button class="iconBtn" type="button" aria-label="刷新" @click="refreshMyWork">⟳</button>
              </div>
            </div>
            <div class="block-body scroll">
              <n-spin :show="myWorkLoading">
                <div v-if="!myWork.length" class="mywork-list">
                  <div v-for="i in 5" :key="`mw-ph-${i}`" class="mw-row placeholderRow" />
                </div>
                <div v-else class="mywork-list">
                  <button v-for="it in myWorkSmart" :key="it.taskId" class="mw-row hover-row" @click="openMyWork(it)">
                    <div class="mw-main">
                      <div class="mw-title">{{ it.title }}</div>
                      <div class="mw-meta muted">
                        <span class="mw-proj">{{ it.projectName }}</span>
                        <span
                          v-if="dueBadge(it)"
                          class="mw-badge"
                          :class="`tone-${dueBadge(it)!.tone}`"
                        >{{ dueBadge(it)!.label }}</span>
                      </div>
                    </div>
                    <div class="mw-actions" @click.stop>
                      <button
                        class="mw-act"
                        :disabled="myWorkUpdatingId === it.taskId"
                        @click="advanceMyWork(it)"
                      >
                        <span>{{ nextLabel(it.status as any) }}</span>
                        <span
                          v-if="myWorkUpdatingId === it.taskId"
                          class="ml-2 inline-block h-4 w-4 rounded-full border-2 border-white/30 border-t-white animate-spin"
                        />
                      </button>
                    </div>
                  </button>
                </div>
              </n-spin>
            </div>
          </section>

        <section class="panel block planner">
          <div class="block-head">
            <div class="h2">需求拆解</div>
            <div class="plannerHeadRight">
              <button class="iconBtn" type="button" aria-label="历史" @click="openSaved('history')">⏱</button>
              <button class="iconBtn" type="button" aria-label="保存模板" @click="saveCurrentAsTemplate">☆</button>
            </div>
          </div>
          <div class="block-body plannerBody">
            <div class="plannerTop">
              <n-input
                ref="requirementInput"
                v-model:value="requirement"
                type="textarea"
                placeholder="描述需求（目标/范围/验收/约束）。例如：做一个后台管理平台，包含用户/角色/权限/字典…"
                :autosize="{ minRows: 4, maxRows: 6 }"
              />
              <div class="planner-bar">
                <div class="muted hint">生成后可微调，再一键落地到项目任务（支持里程碑/父任务）</div>
                <n-button type="primary" :loading="generating" @click="generate">生成任务</n-button>
              </div>
            </div>

            <div class="result-head">
              <div class="h2 sub">落地</div>
              <div class="land">
                <div class="landRow">
                  <n-select
                    v-model:value="projectId"
                    :options="projectOptions"
                    size="small"
                    placeholder="选择项目"
                    class="sel grow"
                  />
                  <n-button
                    size="small"
                    type="primary"
                    class="landBtn"
                    :disabled="!projectId || !drafts.length || remainingCount === 0"
                    :loading="addingAll"
                    @click="addAllToProject"
                  >
                    全部添加
                  </n-button>
                </div>
                <div class="landRow">
                  <n-select
                    v-model:value="milestoneId"
                    :options="milestoneOptions"
                    size="small"
                    placeholder="里程碑"
                    clearable
                    class="sel"
                  />
                  <n-select
                    v-model:value="parentTaskId"
                    :options="parentOptions"
                    size="small"
                    placeholder="父任务"
                    clearable
                    filterable
                    class="sel grow"
                  />
                </div>
              </div>
            </div>

            <div v-if="drafts.length" class="progress">
              <div class="muted progressMeta">
                本次生成 {{ totalCount }} · 已落地 {{ addedCount }} · 剩余 {{ remainingCount }} · {{ progressPct }}%
              </div>
              <div class="progressBar">
                <div class="progressFill" :style="{ width: `${progressPct}%` }" />
              </div>
            </div>

            <div v-if="lastAdded" class="addedNote">
              <div class="muted">已添加 {{ lastAdded.count }} 条到项目</div>
              <n-button size="small" tertiary @click="openProject(lastAdded.projectId)">打开项目</n-button>
              <n-button size="small" tertiary @click="router.push({ name: 'board', query: { projectId: String(lastAdded.projectId) } })">
                打开看板
              </n-button>
            </div>

            <div class="plannerScroll">
              <n-spin :show="generating">
                <div v-if="!drafts.length" class="empty muted planner-empty">
                  <div v-if="!generating">还没有生成任务。先描述需求，再点击生成。</div>
                  <div v-else class="stream-wrap">
                    <div class="muted stream-label">AI 正在生成任务…</div>
                    <pre class="stream">{{ streamText || '...' }}</pre>
                  </div>
                </div>
                <div v-else class="drafts">
                  <div
                    v-for="d in drafts"
                    :key="d.id"
                    class="draftCard hover-row"
                    :class="{ open: expandedDraftId === d.id, added: added.has(d.id) }"
                    @click="toggleExpand(d.id)"
                  >
                    <div class="dTop">
                      <div class="dTitle">
                        <span class="order mono">{{ d.order }}</span>
                        <span class="dot">·</span>
                        <input v-model="d.title" class="titleInput" @click.stop />
                      </div>
                      <div class="dRight" @click.stop>
                        <span class="prio" :class="`p-${String(d.priority || 'MEDIUM').toLowerCase()}`">{{ d.priority }}</span>
                        <button
                          class="addBtn"
                          :disabled="!projectId || added.has(d.id)"
                          @click="addToProject(d)"
                        >
                          <span v-if="added.has(d.id)">已落地</span>
                          <span v-else>落地</span>
                          <span
                            v-if="addingKey === d.id"
                            class="ml-2 inline-block h-4 w-4 rounded-full border-2 border-white/30 border-t-white animate-spin"
                          />
                        </button>
                      </div>
                    </div>
                    <div v-if="expandedDraftId === d.id" class="dEdit" @click.stop>
                      <n-input
                        v-model:value="d.description"
                        type="textarea"
                        placeholder="任务描述（可编辑）"
                        :autosize="{ minRows: 3, maxRows: 6 }"
                      />
                      <n-input
                        v-model:value="d.acceptanceCriteria"
                        type="textarea"
                        placeholder="验收标准（可选）"
                        :autosize="{ minRows: 2, maxRows: 4 }"
                      />
                      <n-input
                        v-model:value="d.deliverablesHint"
                        type="textarea"
                        placeholder="交付物建议（每行一个，可选）"
                        :autosize="{ minRows: 2, maxRows: 4 }"
                      />
                    </div>
                  </div>
                </div>
              </n-spin>
            </div>
          </div>
        </section>
    </div>
  </div>

  <n-modal v-model:show="createOpen" preset="card" title="新建项目" class="createModal">
    <div class="createForm">
      <n-input v-model:value="name" placeholder="项目名称" />
      <n-input
        v-model:value="description"
        placeholder="描述（可选）"
        type="textarea"
        :autosize="{ minRows: 3, maxRows: 6 }"
      />
      <div class="createActions">
        <n-button size="small" @click="createOpen = false">取消</n-button>
        <n-button size="small" type="primary" :loading="creating" :disabled="!name.trim()" @click="createProject">
          创建
        </n-button>
      </div>
    </div>
  </n-modal>

  <n-modal v-model:show="savedOpen" preset="card" title="Planner" class="savedModal">
    <div class="savedTop">
      <button class="savedTab" :class="{ on: savedTab === 'history' }" @click="savedTab = 'history'">历史</button>
      <button class="savedTab" :class="{ on: savedTab === 'templates' }" @click="savedTab = 'templates'">模板</button>
    </div>
    <div class="savedList">
      <div
        v-for="it in (savedTab === 'history' ? savedHistory : savedTemplates)"
        :key="it.id"
        class="savedRow hover-row"
      >
        <div class="savedMain">
          <div class="savedTitle">{{ it.title }}</div>
          <div class="muted savedMeta">
            <span class="mono">{{ fmtDateTime(it.createdAt) }}</span>
            <span class="dot">·</span>
            <span>{{ it.drafts?.length || 0 }} 条</span>
          </div>
        </div>
        <div class="savedActions">
          <n-button size="small" tertiary @click="applySaved(it)">使用</n-button>
          <n-button size="small" tertiary @click="deleteSaved(it.id, savedTab)">删除</n-button>
        </div>
      </div>
      <div
        v-if="(savedTab === 'history' ? savedHistory : savedTemplates).length === 0"
        class="muted empty"
      >
        暂无{{ savedTab === 'history' ? '历史记录' : '模板' }}。
      </div>
    </div>
  </n-modal>
</template>

<style scoped>
.wsPage {
  height: calc(100vh - 52px - 18px - 34px);
  display: flex;
  flex-direction: column;
}

.head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.wsGrid {
  display: grid;
  grid-template-columns: 320px 1fr 440px;
  gap: var(--space-4);
  align-items: stretch;
  flex: 1;
  min-height: 0;
}

.projPanel {
  padding: 14px 14px;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.projHeadRight {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
.archToggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.archLabel {
  font-size: 12px;
}

.mwHeadRight {
  display: flex;
  align-items: center;
  gap: 10px;
}

.iconBtn {
  height: 28px;
  width: 28px;
  border-radius: 10px;
  border: 1px solid var(--stroke);
  background: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  font-weight: 900;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: transform 140ms ease, box-shadow 140ms ease, background 140ms ease;
}

.iconBtn:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 34px rgba(15, 23, 42, 0.12);
  background: rgba(255, 255, 255, 0.92);
}

.iconBtn:active {
  transform: translateY(0px);
}

.pinBtn {
  flex: 0 0 auto;
  height: 28px;
  width: 28px;
  border-radius: 10px;
  border: 1px solid transparent;
  background: transparent;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 140ms ease, transform 140ms ease;
}

.pinBtn:hover {
  background: rgba(20, 184, 166, 0.08);
  transform: translateY(-1px);
}

.pinDot {
  height: 8px;
  width: 8px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.14);
}

.pinDot.on {
  background: var(--accent);
  box-shadow: 0 0 0 4px rgba(20, 184, 166, 0.14);
}

.link {
  color: rgba(15, 23, 42, 0.78);
}

.grid {
  display: grid;
  grid-template-columns: 1.4fr 0.6fr;
  gap: 16px;
  align-items: start;
}
.main {
  display: grid;
  gap: 16px;
}
.stack {
  display: grid;
  gap: 16px;
}
.block {
  padding: 14px 14px;
}
.block-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}
.meta {
  font-size: 12px;
}

.block-body {
  flex: 1;
  min-height: 0;
}
.scroll {
  overflow: auto;
}
.list {
  margin-top: 14px;
  display: grid;
  gap: 6px;
}
.list.compact {
  margin-top: 0;
}
.projRow {
  text-align: left;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.72);
  padding: 14px 14px;
  cursor: pointer;
  display: grid;
  gap: 6px;
  transition: transform 140ms ease, box-shadow 140ms ease, background 140ms ease, border-color 140ms ease;
}
.projRow:hover {
  transform: translateY(-1px);
  background: rgba(255, 255, 255, 0.88);
  border-color: rgba(20, 184, 166, 0.18);
  box-shadow: 0 18px 54px rgba(15, 23, 42, 0.12);
}
.placeholderRow {
  border-style: dashed !important;
  background: rgba(15, 23, 42, 0.02) !important;
  cursor: default !important;
  box-shadow: none !important;
  transform: none !important;
}
.placeholderRow:hover {
  transform: none;
  box-shadow: none;
}
.pname {
  font-weight: 820;
  letter-spacing: -0.25px;
  font-size: 16px;
  line-height: 1.25;
}
.ptime {
  font-size: 12px;
  line-height: 1.2;
}
.empty {
  padding: 10px 4px;
}
.pill {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.10);
  color: rgba(15, 23, 42, 0.82);
}
.pill.todo {
  background: rgba(15, 23, 42, 0.03);
}
.pill.doing {
  background: rgba(6, 182, 212, 0.10);
  border-color: rgba(6, 182, 212, 0.26);
  color: rgba(15, 23, 42, 0.92);
  font-weight: 900;
}
.pill.done {
  background: rgba(20, 184, 166, 0.14);
  border-color: rgba(20, 184, 166, 0.26);
  color: rgba(15, 23, 42, 0.92);
}
.mywork {
  padding: 14px 14px;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.mywork-list {
  display: grid;
  gap: 8px;
}
.mw-row {
  width: 100%;
  text-align: left;
  border-radius: 14px;
  border: 1px solid transparent;
  background: rgba(255, 255, 255, 0.74);
  padding: 10px 10px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.mw-main {
  min-width: 0;
}
.mw-title {
  font-weight: 650;
  letter-spacing: -0.1px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mw-meta {
  margin-top: 4px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.mw-proj {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 210px;
  display: inline-block;
  vertical-align: bottom;
}
.mw-badge {
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.10);
  font-weight: 800;
  letter-spacing: -0.1px;
}
.mw-badge.tone-overdue {
  background: rgba(244, 63, 94, 0.12);
  border-color: rgba(244, 63, 94, 0.28);
  color: rgba(15, 23, 42, 0.92);
}
.mw-badge.tone-today {
  background: rgba(245, 158, 11, 0.12);
  border-color: rgba(245, 158, 11, 0.28);
  color: rgba(15, 23, 42, 0.92);
}
.mw-badge.tone-soon {
  background: rgba(14, 165, 233, 0.10);
  border-color: rgba(14, 165, 233, 0.24);
  color: rgba(15, 23, 42, 0.92);
}
.mw-actions {
  flex: 0 0 auto;
}
.mw-act {
  height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid rgba(20, 184, 166, 0.22);
  background: rgba(20, 184, 166, 0.92);
  color: rgba(255, 255, 255, 0.92);
  font-weight: 900;
  letter-spacing: -0.2px;
  cursor: pointer;
  transition: transform 140ms ease, box-shadow 140ms ease, background 140ms ease;
}
.mw-act:hover {
  transform: translateY(-1px);
  background: rgba(20, 184, 166, 0.98);
  box-shadow: 0 16px 44px rgba(2, 6, 23, 0.18);
}
.mw-act:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}
.mywork-empty {
  padding: 6px 4px;
}
.planner {
  padding: 14px 14px;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.plannerHeadRight {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}
.plannerBody {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.plannerTop {
  display: grid;
  gap: 10px;
}
.plannerScroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.planner-bar {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.hint {
  font-size: 12px;
  line-height: 1.5;
}
.result-head {
  margin-top: 14px;
  display: grid;
  grid-template-columns: 52px 1fr;
  align-items: start;
  gap: 12px;
  margin-bottom: 12px;
}
.sub {
  font-size: 14px;
  font-weight: 700;
}
.land {
  min-width: 0;
  display: grid;
  gap: 8px;
}
.landRow {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  min-width: 0;
}
.sel {
  flex: 0 1 180px;
  min-width: 0;
}
.sel.grow {
  flex: 1 1 260px;
  min-width: 0;
}
.landBtn {
  white-space: nowrap;
}
.progress {
  display: grid;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.55);
}
.progressMeta {
  font-size: 12px;
}
.progressBar {
  height: 8px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
  overflow: hidden;
}
.progressFill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--accent2), var(--accent));
}

.drafts {
  display: grid;
  gap: 10px;
}
.draftCard {
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.66);
  padding: 12px 12px;
}
.draftCard.added {
  opacity: 0.68;
}
.dTop {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.dTitle {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}
.order {
  font-size: 12px;
  opacity: 0.85;
}
.dot {
  opacity: 0.35;
}
.titleInput {
  border: 0;
  outline: none;
  background: transparent;
  width: 100%;
  min-width: 0;
  font-size: 14px;
  font-weight: 720;
  letter-spacing: -0.2px;
  color: rgba(15, 23, 42, 0.92);
}
.dRight {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
.prio {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.10);
  color: rgba(15, 23, 42, 0.82);
  background: rgba(15, 23, 42, 0.03);
  white-space: nowrap;
}
.prio.p-high {
  background: rgba(20, 184, 166, 0.12);
  border-color: rgba(20, 184, 166, 0.24);
  color: rgba(15, 23, 42, 0.92);
  font-weight: 900;
}
.prio.p-medium {
  background: rgba(6, 182, 212, 0.10);
  border-color: rgba(6, 182, 212, 0.22);
  color: rgba(15, 23, 42, 0.92);
  font-weight: 850;
}
.addBtn {
  height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid rgba(20, 184, 166, 0.22);
  background: rgba(20, 184, 166, 0.92);
  color: rgba(255, 255, 255, 0.92);
  font-weight: 900;
  letter-spacing: -0.2px;
  cursor: pointer;
  transition: transform 140ms ease, box-shadow 140ms ease, background 140ms ease;
}
.addBtn:hover {
  transform: translateY(-1px);
  box-shadow: 0 16px 44px rgba(2, 6, 23, 0.16);
  background: rgba(20, 184, 166, 0.98);
}
.addBtn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}
.dEdit {
  margin-top: 10px;
  display: grid;
  gap: 10px;
}
.planner-empty {
  padding: 10px 4px;
}
.stream-wrap {
  margin-top: 8px;
}
.stream-label {
  font-size: 12px;
  margin-bottom: 8px;
}
.stream {
  margin: 0;
  padding: 12px 12px;
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.10);
  background: rgba(15, 23, 42, 0.04);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New',
    monospace;
  font-size: 12px;
  line-height: 1.65;
  color: rgba(15, 23, 42, 0.78);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 240px;
  overflow: auto;
}
@media (max-width: 980px) {
  .grid {
    grid-template-columns: 1fr;
  }

  .wsGrid {
    height: auto;
    grid-template-columns: 1fr;
  }
}

:global(.createModal) {
  width: min(520px, calc(100vw - 28px));
}
.savedTop {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.savedTab {
  height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.10);
  background: rgba(15, 23, 42, 0.03);
  color: rgba(15, 23, 42, 0.78);
  font-weight: 850;
  font-size: 12px;
  cursor: pointer;
}
.savedTab.on {
  background: rgba(20, 184, 166, 0.10);
  border-color: rgba(20, 184, 166, 0.22);
  color: rgba(15, 23, 42, 0.92);
}
.savedList {
  display: grid;
  gap: 8px;
  max-height: min(520px, calc(100vh - 220px));
  overflow: auto;
}
.savedRow {
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.66);
  padding: 12px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.savedMain {
  min-width: 0;
}
.savedTitle {
  font-weight: 760;
  letter-spacing: -0.2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.savedMeta {
  margin-top: 6px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.savedActions {
  flex: 0 0 auto;
  display: inline-flex;
  gap: 8px;
}
:global(.savedModal) {
  width: min(760px, calc(100vw - 28px));
}
.createForm {
  display: grid;
  gap: 12px;
}
.createActions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
