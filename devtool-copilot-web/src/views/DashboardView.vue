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
const myWorkError = ref('')
const myWorkUpdatingId = ref<number | null>(null)
const myWorkLastLoadAt = ref(0)

const requirementInput = ref<any>(null)
const plannerOpen = ref(false)

const requirement = ref('')
type ClarifyItem = { id: string; question: string; answer: string }
const clarifying = ref(false)
const clarifyStreamText = ref('')
const clarifyItems = ref<ClarifyItem[]>([])
const generating = ref(false)
const plans = ref<TaskPlan[]>([])
const streamText = ref('')

const projectId = ref<number | null>(null)
const selectedProject = computed(() => (projectId.value != null ? projectStore.byId.get(Number(projectId.value || 0)) : null))
const projectArchived = computed(() => Number((selectedProject.value as any)?.archived || 0) === 1)
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
  [requirement, clarifyItems, drafts, added, milestoneId, parentTaskId, projectId, expandedDraftId],
  () => schedulePersistPlannerCache(),
  { deep: true }
)

watch(
  () => requirement.value,
  (v, prev) => {
    if (String(v || '').trim() === String(prev || '').trim()) return
    if (clarifying.value) return
    if (clarifyItems.value.length) {
      clarifyItems.value = []
      clarifyStreamText.value = ''
    }
  }
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

function openPlanner() {
  plannerOpen.value = true
  nextTick(() => {
    try {
      requirementInput.value?.focus?.()
    } catch {
    }
  })
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

function prioLabel(p: string) {
  const v = String(p || '').toUpperCase()
  if (v === 'HIGH') return '高优先级'
  if (v === 'LOW') return '低优先级'
  return '中优先级'
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
  clarifyItems.value = []
  clarifyStreamText.value = ''
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
      clarifyItems: clarifyItems.value ?? [],
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
    const cis = Array.isArray(obj.clarifyItems) ? obj.clarifyItems : []
    clarifyItems.value = cis
      .map((x: any, i: number) => ({
        id: x?.id ? String(x.id) : `${Date.now()}-q-${i}`,
        question: String(x?.question || ''),
        answer: String(x?.answer || '')
      }))
      .filter((x: any) => String(x.question || '').trim())
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

function parseClarifyQuestions(text: string): string[] {
  const raw = String(text || '').trim()
  if (!raw) return []

  try {
    const obj = JSON.parse(raw)
    if (Array.isArray(obj)) {
      return obj
        .map((x) => String(x || '').trim())
        .filter(Boolean)
        .slice(0, 6)
    }
  } catch {
  }

  const lines = raw
    .split('\n')
    .map((x) => x.trim())
    .filter(Boolean)
    .map((x) => x.replace(/^[-*•]\s+/, ''))
    .map((x) => x.replace(/^Q\d+[:：]\s*/i, ''))
    .map((x) => x.replace(/^\d+[.)、]\s*/, ''))
    .filter(Boolean)

  const uniq: string[] = []
  for (const s of lines) {
    const v = s.trim()
    if (!v) continue
    if (uniq.includes(v)) continue
    uniq.push(v)
    if (uniq.length >= 6) break
  }
  return uniq
}

function buildClarifiedRequirement(base: string) {
  const qas = (clarifyItems.value || [])
    .map((x) => ({ q: String(x.question || '').trim(), a: String(x.answer || '').trim() }))
    .filter((x) => x.q && x.a)
  if (!qas.length) return base
  const lines: string[] = []
  lines.push(base.trim())
  lines.push('')
  lines.push('澄清问答：')
  for (const qa of qas) {
    lines.push(`- Q: ${qa.q}`)
    lines.push(`  A: ${qa.a}`)
  }
  return lines.join('\n').trim()
}

async function clarifyRequirement() {
  const text = requirement.value.trim()
  if (!text) {
    message.warning('请先描述需求')
    return
  }
  clarifying.value = true
  clarifyStreamText.value = ''
  clarifyItems.value = []
  try {
    let buf = ''
    let scheduled = false
    const flush = () => {
      scheduled = false
      if (!buf) return
      clarifyStreamText.value += buf
      buf = ''
    }
    const full = await aiApi.chatStream(
      {
        projectId: projectId.value || undefined,
        type: 'plan',
        messages: [
          {
            role: 'user',
            content:
              `你是资深产品经理。下面是一段用户需求，请先提出 4-6 个必须澄清的问题，帮助后续拆解任务更准确。\n` +
              `要求：只输出问题列表（每行一个问题），不要给答案和解释。\n\n需求：\n${text.trim()}`
          }
        ]
      },
      (delta) => {
        buf += delta
        if (!scheduled) {
          scheduled = true
          setTimeout(flush, 40)
        }
      }
    )
    if (buf) flush()
    const qs = parseClarifyQuestions(full || clarifyStreamText.value)
    const now = Date.now()
    clarifyItems.value = qs.map((q, i) => ({ id: `${now}-${i}`, question: q, answer: '' }))
    if (!clarifyItems.value.length) message.warning('没有解析到澄清问题，可直接生成任务')
    else message.success('已生成澄清问题')
  } catch (e: any) {
    message.error(e?.message || '澄清失败')
  } finally {
    clarifying.value = false
  }
}

async function generate() {
  const text = requirement.value.trim()
  if (!text) {
    message.warning('请先描述需求')
    return
  }
  if (clarifyItems.value.length) {
    const miss = clarifyItems.value.some((x) => !String(x.answer || '').trim())
    if (miss) {
      message.warning('请先把澄清问题回答完整（或清空澄清后直接生成）')
      return
    }
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
    const req = buildClarifiedRequirement(text)
    plans.value = await aiApi.taskSplitStream(
      { requirement: req },
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
  if (projectArchived.value) {
    message.warning('项目已归档，只读')
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
  if (projectArchived.value) {
    message.warning('项目已归档，只读')
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
  myWorkError.value = ''
  try {
    const list = await taskApi.myWork(6)
    myWork.value = list
  } catch (e: any) {
    myWork.value = []
    myWorkError.value = String(e?.message || 'My Work 加载失败')
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
  if (ts < now.getTime()) return { label: '已逾期', tone: 'overdue' }
  if (ts >= start && ts < end) return { label: '今天', tone: 'today' }
  const days = Math.ceil((ts - end) / (24 * 60 * 60 * 1000))
  if (days >= 0 && days <= 3) return { label: '3 天内', tone: 'soon' }
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
        <h1 class="h1">工作台</h1>
      </div>
    </div>

    <div class="wsBody">
      <div class="wsInner">
        <section class="wsSection">
          <div class="secHead">
            <div class="secTitle">项目</div>
            <div class="projHeadRight">
              <button class="toolBtn plannerBtn" type="button" @click="openPlanner">AI规划</button>
              <div class="muted meta">{{ projectStore.visibleProjects.length }}</div>
              <div class="archToggle">
                <div class="muted archLabel">已归档</div>
                <n-switch
                  size="small"
                  :value="projectStore.showArchived"
                  @update:value="(v) => projectStore.setShowArchived(!!v)"
                />
              </div>
              <button class="iconBtn" type="button" aria-label="新建项目" @click="openCreate">＋</button>
            </div>
          </div>

          <div class="secBody">
            <div class="list compact">
              <div
                v-for="p in projectsSorted"
                :key="p.id"
                class="projRow hover-row"
                :class="{ archived: Number(p.archived || 0) === 1 }"
                @click="openProject(p.id)"
              >
                <div class="pname">
                  <span class="ptext">{{ p.name }}</span>
                  <span v-if="Number(p.archived || 0) === 1" class="pbadge">已归档</span>
                </div>
                <div class="ptime muted">{{ fmtDateTime(p.createdAt) }}</div>
              </div>
              <div v-if="!projectsSorted.length">
                <div v-for="i in 4" :key="`p-ph-${i}`" class="projRow placeholderRow" />
              </div>
            </div>
          </div>
        </section>

        <section class="wsSection">
          <div class="secHead">
            <div class="secTitle">我的任务</div>
            <div class="mwHeadRight">
              <div class="muted meta">Doing {{ myWorkStats.doing }} · Today {{ myWorkStats.today }} · Overdue {{ myWorkStats.overdue }}</div>
              <button class="iconBtn" type="button" aria-label="刷新" @click="refreshMyWork">⟳</button>
            </div>
          </div>
          <div class="secBody">
            <n-spin :show="myWorkLoading">
              <div v-if="myWorkError" class="mywork-error">
                <div class="mywork-error-title">我的任务暂时无法加载</div>
                <div class="mywork-error-message">{{ myWorkError }}</div>
                <button class="mywork-retry" type="button" @click="loadMyWork">重试</button>
              </div>
              <div v-else-if="!myWork.length" class="mywork-list">
                <div v-for="i in 5" :key="`mw-ph-${i}`" class="mw-row placeholderRow" />
              </div>
              <div v-else class="mywork-list">
                <button v-for="it in myWorkSmart" :key="it.taskId" class="mw-row hover-row" @click="openMyWork(it)">
                  <div class="mw-main">
                    <div class="mw-top">
                      <span class="mw-state" :class="`s-${String(it.status || '').toLowerCase()}`">{{ String(it.status || '').toUpperCase() }}</span>
                      <div class="mw-title">{{ it.title }}</div>
                    </div>
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
      </div>
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

  <n-modal
    v-model:show="plannerOpen"
    preset="card"
    class="plannerModal ai-anim"
    :mask-closable="false"
    :closable="false"
  >
    <template #header>
      <div class="plannerHeader">
        <div class="plHeaderLeft">
          <div class="plHeaderText">
            <div class="plTitle">AI 规划</div>
          </div>
        </div>
        <div class="plHeaderRight">
          <button class="plIconBtn" type="button" @click="openSaved('history')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M3 12a9 9 0 1 0 3-6.7L3 8" />
              <path d="M3 3v5h5" />
              <path d="M12 7v5l3 2" />
            </svg>
            <span>历史</span>
          </button>
          <button class="plIconBtn" type="button" @click="saveCurrentAsTemplate">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z" />
              <path d="M14 2v6h6" />
              <path d="M12 18v-6" />
              <path d="M9 15h6" />
            </svg>
            <span>模板</span>
          </button>
          <button class="plIconBtn plClose" type="button" aria-label="关闭" @click="plannerOpen = false">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>
      </div>
    </template>

    <template #default>
      <div class="plannerSheet">
        <section class="plHero">
          <div class="plHeroHead">
            <div class="plHeroTitle">描述你要推进的需求</div>
          </div>
          <n-input
            ref="requirementInput"
            v-model:value="requirement"
            type="textarea"
            class="plHeroInput"
            :autosize="{ minRows: 4, maxRows: 7 }"
          />
          <div class="plHeroBar">
            <div class="plHeroActions">
              <n-button size="small" secondary :loading="clarifying" @click="clarifyRequirement">澄清需求</n-button>
              <n-button size="small" type="primary" :loading="generating" @click="generate">生成任务</n-button>
            </div>
          </div>
        </section>

        <section v-if="clarifying || clarifyItems.length" class="plSection plClarify">
          <div class="plSectionHead">
            <span class="plSectionTitle">需求澄清</span>
          </div>
          <div v-if="clarifying" class="plStreamBox">
            <span class="plStreamDots"><i /><i /><i /></span>
            <span class="plStreamLabel">AI 正在生成澄清问题…</span>
            <pre class="plStream">{{ clarifyStreamText || '…' }}</pre>
          </div>
          <div v-else class="plQaList">
            <div v-for="(q, qi) in clarifyItems" :key="q.id" class="plQa ai-anim" :style="{ animationDelay: `${qi * 60}ms` }">
              <span class="plQaNum">{{ qi + 1 }}</span>
              <div class="plQaBody">
                <div class="plQaText">{{ q.question }}</div>
                <n-input v-model:value="q.answer" size="small" placeholder="你的回答…" />
              </div>
            </div>
          </div>
        </section>

        <section class="plSection plLand">
          <div class="plSectionHead">
            <span class="plSectionTitle">落地到项目</span>
          </div>
          <div class="plLandGrid">
            <n-select v-model:value="projectId" :options="projectOptions" size="small" placeholder="选择项目" class="plSel" />
            <n-select v-model:value="milestoneId" :options="milestoneOptions" size="small" placeholder="里程碑" clearable class="plSel" />
            <n-select v-model:value="parentTaskId" :options="parentOptions" size="small" placeholder="父任务" clearable filterable class="plSel" />
          </div>
        </section>

        <section v-if="drafts.length" class="plProgress">
          <div class="plProgressMeta">
            <span>本次生成 {{ totalCount }} 条任务</span>
            <span class="plProgressNums">已落地 <b>{{ addedCount }}</b> · 剩余 {{ remainingCount }}</span>
          </div>
          <div class="plProgressBar">
            <div class="plProgressFill" :style="{ width: `${progressPct}%` }" />
          </div>
        </section>

        <div class="plScroll">
          <n-spin :show="generating">
            <div v-if="!drafts.length" class="plEmpty">
              <template v-if="!generating">
                <div class="plEmptyTitle">还没有生成任务</div>
              </template>
              <div v-else class="plStreamBox plStreamBoxBig">
                <span class="plStreamDots"><i /><i /><i /></span>
                <span class="plStreamLabel">AI 正在拆解任务…</span>
                <pre class="plStream">{{ streamText || '…' }}</pre>
              </div>
            </div>
            <div v-else class="plDrafts">
              <div
                v-for="(d, di) in drafts"
                :key="d.id"
                class="plDraft ai-anim"
                :class="{ open: expandedDraftId === d.id, added: added.has(d.id) }"
                :style="{ animationDelay: `${Math.min(di, 8) * 40}ms` }"
                @click="toggleExpand(d.id)"
              >
                <span class="plDraftBar" :class="`p-${String(d.priority || 'MEDIUM').toLowerCase()}`" />
                <div class="plDraftMain">
                  <div class="plDraftTop">
                    <div class="plDraftTitle">
                      <span class="plDraftOrder">{{ d.order }}</span>
                      <input v-model="d.title" class="plTitleInput" @click.stop />
                    </div>
                    <div class="plDraftRight" @click.stop>
                      <span class="plPrio" :class="`p-${String(d.priority || 'MEDIUM').toLowerCase()}`">{{ prioLabel(d.priority) }}</span>
                      <button
                        class="plAddBtn"
                        :disabled="!projectId || projectArchived || added.has(d.id) || addingKey === d.id"
                        @click="addToProject(d)"
                      >
                        <svg v-if="added.has(d.id)" class="plAddCheck" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                          <path d="M5 13l4 4L19 7" />
                        </svg>
                        <span>{{ added.has(d.id) ? '已落地' : '落地' }}</span>
                        <span
                          v-if="addingKey === d.id"
                          class="plAddSpin"
                        />
                      </button>
                    </div>
                  </div>
                  <div v-if="expandedDraftId === d.id" class="plDraftEdit" @click.stop>
                    <n-input v-model:value="d.description" type="textarea" placeholder="任务描述（可编辑）" :autosize="{ minRows: 3, maxRows: 6 }" />
                    <n-input v-model:value="d.acceptanceCriteria" type="textarea" placeholder="验收标准（可选）" :autosize="{ minRows: 2, maxRows: 4 }" />
                    <n-input v-model:value="d.deliverablesHint" type="textarea" placeholder="交付物建议（每行一个，可选）" :autosize="{ minRows: 2, maxRows: 4 }" />
                  </div>
                </div>
              </div>
            </div>
          </n-spin>
        </div>

        <div v-if="lastAdded" class="plAddedNote ai-anim">
          <span class="plAddedIcon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
              <path d="M5 13l4 4L19 7" />
            </svg>
          </span>
          <span class="plAddedText">已添加 {{ lastAdded.count }} 条任务到项目</span>
          <button type="button" @click="openProject(lastAdded.projectId)">打开项目</button>
          <button type="button" @click="router.push({ name: 'board', query: { projectId: String(lastAdded.projectId) } })">打开看板</button>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="plFooter">
        <div class="plFooterLeft">
          <template v-if="drafts.length">
            <span class="plFooterPct" :class="{ full: progressPct === 100 }">{{ progressPct }}%</span>
            <span class="plFooterText">已落地 {{ addedCount }} / {{ totalCount }} 条</span>
          </template>
        </div>
        <div class="plFooterRight">
          <n-button size="small" quaternary @click="plannerOpen = false">关闭</n-button>
          <n-button
            size="small"
            type="primary"
            class="plMainCta"
            :disabled="!projectId || projectArchived || !drafts.length || remainingCount === 0"
            :loading="addingAll"
            @click="addAllToProject"
          >
            全部落地
          </n-button>
        </div>
      </div>
    </template>
  </n-modal>

  <n-modal v-model:show="savedOpen" preset="card" class="savedModal ai-anim" :mask-closable="true" :closable="false">
    <template #header>
      <div class="savedHeader">
        <div class="plTitle">规划存档</div>
        <button class="savedClose" type="button" aria-label="关闭" @click="savedOpen = false">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M6 6l12 12M18 6l-12 12"/>
          </svg>
        </button>
      </div>
    </template>
    <template #default>
      <div class="savedTop">
        <button class="savedTab" :class="{ on: savedTab === 'history' }" @click="savedTab = 'history'">
          历史<span v-if="savedHistory.length" class="savedCount">{{ savedHistory.length }}</span>
        </button>
        <button class="savedTab" :class="{ on: savedTab === 'templates' }" @click="savedTab = 'templates'">
          模板<span v-if="savedTemplates.length" class="savedCount">{{ savedTemplates.length }}</span>
        </button>
      </div>
      <div class="savedList">
        <div
          v-for="it in (savedTab === 'history' ? savedHistory : savedTemplates)"
          :key="it.id"
          class="savedRow ai-anim"
        >
          <div class="savedMain">
            <div class="savedTitle">{{ it.title }}</div>
            <div class="savedMeta">
              <span class="mono">{{ fmtDateTime(it.createdAt) }}</span>
              <span class="dot">·</span>
              <span>{{ it.drafts?.length || 0 }} 条任务</span>
            </div>
          </div>
          <div class="savedActions">
            <n-button size="small" secondary @click="applySaved(it)">使用</n-button>
            <n-button size="small" tertiary @click="deleteSaved(it.id, savedTab)">删除</n-button>
          </div>
        </div>
        <div v-if="(savedTab === 'history' ? savedHistory : savedTemplates).length === 0" class="savedEmpty">
          <div class="plEmptyTitle">暂无{{ savedTab === 'history' ? '历史记录' : '模板' }}</div>
          <div class="plEmptyHint">在 AI 规划弹窗生成任务后，可点"保存到历史"或"存为模板"</div>
        </div>
      </div>
    </template>
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
  margin-bottom: 12px;
}

.headRight {
  display: flex;
  align-items: center;
  gap: 10px;
}

.wsBody {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.wsInner {
  width: 100%;
  max-width: 1080px;
  margin: 0 auto;
  display: grid;
  gap: 22px;
  padding: 0 2px 18px;
}

.wsSection {
  padding: 0;
}

.secHead {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 2px 10px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
}

.secTitle {
  font-size: 13px;
  font-weight: 850;
  letter-spacing: -0.25px;
  color: rgba(15, 23, 42, 0.92);
}

.secBody {
  padding: 8px 0 0;
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
  height: 24px;
  width: 24px;
  border-radius: 8px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(15, 23, 42, 0.02);
  cursor: pointer;
  font-weight: 900;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 120ms ease, border-color 120ms ease;
}

.iconBtn:hover {
  background: rgba(15, 23, 42, 0.05);
  border-color: rgba(15, 23, 42, 0.10);
}

.iconBtn:active {
  transform: translateY(0px);
}

.toolBtn {
  height: 24px;
  padding: 0 9px;
  border-radius: 8px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(15, 23, 42, 0.02);
  color: rgba(15, 23, 42, 0.74);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: -0.1px;
  white-space: nowrap;
  cursor: pointer;
  transition: background 120ms ease, border-color 120ms ease, color 120ms ease;
}
.toolBtn:hover {
  background: rgba(15, 23, 42, 0.05);
  border-color: rgba(15, 23, 42, 0.10);
  color: rgba(15, 23, 42, 0.92);
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
  background: rgba(15, 23, 42, 0.05);
  transform: translateY(-1px);
}

.pinDot {
  height: 8px;
  width: 8px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.14);
}

.pinDot.on {
  background: rgba(15, 23, 42, 0.84);
  box-shadow: 0 0 0 4px rgba(15, 23, 42, 0.08);
}

.link {
  color: rgba(15, 23, 42, 0.78);
}

.meta {
  font-size: 11px;
}

.list {
  display: grid;
  gap: 0px;
}
.list.compact {
  margin-top: 0;
}
.projRow {
  text-align: left;
  border-radius: 10px;
  border: 0;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  background: transparent;
  padding: 10px 8px;
  cursor: pointer;
  display: grid;
  gap: 4px;
  transition: background 120ms ease, border-color 120ms ease;
}
.projRow:hover {
  background: rgba(15, 23, 42, 0.03);
}
.projRow.archived {
  opacity: 0.72;
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
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 780;
  letter-spacing: -0.25px;
  font-size: 13px;
  line-height: 1.25;
}
.ptext {
  min-width: 0;
}
.pbadge {
  font-size: 11px;
  padding: 1px 7px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.10);
  background: rgba(15, 23, 42, 0.03);
  color: rgba(15, 23, 42, 0.56);
  font-weight: 800;
  letter-spacing: 0.4px;
}
.ptime {
  font-size: 11px;
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
  background: rgba(15, 23, 42, 0.08);
  border-color: rgba(15, 23, 42, 0.12);
  color: rgba(15, 23, 42, 0.92);
  font-weight: 900;
}
.pill.done {
  background: rgba(15, 23, 42, 0.05);
  border-color: rgba(15, 23, 42, 0.10);
  color: rgba(15, 23, 42, 0.78);
}
.mywork {
  padding: 12px 12px;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.mywork-list {
  display: grid;
  gap: 0px;
}
.mw-row {
  width: 100%;
  text-align: left;
  border-radius: 10px;
  border: 0;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  background: transparent;
  padding: 10px 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.mw-main {
  min-width: 0;
}
.mw-top {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.mw-state {
  height: 18px;
  padding: 0 7px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.10);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.4px;
  background: rgba(15, 23, 42, 0.03);
  color: rgba(15, 23, 42, 0.60);
  flex: 0 0 auto;
}
.mw-state.s-doing {
  background: rgba(15, 23, 42, 0.07);
  border-color: rgba(15, 23, 42, 0.12);
  color: rgba(15, 23, 42, 0.90);
}
.mw-state.s-done {
  background: rgba(15, 23, 42, 0.05);
  border-color: rgba(15, 23, 42, 0.10);
  color: rgba(15, 23, 42, 0.76);
}
.mw-title {
  font-weight: 760;
  letter-spacing: -0.2px;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mw-meta {
  margin-top: 3px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
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
  padding: 1px 7px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.10);
  font-weight: 700;
  letter-spacing: -0.1px;
}
.mw-badge.tone-overdue {
  background: rgba(15, 23, 42, 0.08);
  border-color: rgba(15, 23, 42, 0.14);
  color: rgba(15, 23, 42, 0.88);
}
.mw-badge.tone-today {
  background: rgba(15, 23, 42, 0.06);
  border-color: rgba(15, 23, 42, 0.12);
  color: rgba(15, 23, 42, 0.82);
}
.mw-badge.tone-soon {
  background: rgba(15, 23, 42, 0.04);
  border-color: rgba(15, 23, 42, 0.10);
  color: rgba(15, 23, 42, 0.78);
}
.mw-actions {
  flex: 0 0 auto;
}
.mw-act {
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.10);
  background: rgba(15, 23, 42, 0.92);
  color: rgba(255, 255, 255, 0.96);
  font-weight: 800;
  letter-spacing: -0.2px;
  cursor: pointer;
  transition: background 120ms ease, border-color 120ms ease;
}
.mw-act:hover {
  background: rgba(15, 23, 42, 0.98);
  border-color: rgba(15, 23, 42, 0.20);
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
.mywork-error {
  min-height: 112px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 6px;
  padding: 18px 8px;
  color: rgba(15, 23, 42, 0.72);
}
.mywork-error-title {
  color: rgba(15, 23, 42, 0.92);
  font-weight: 760;
}
.mywork-error-message {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: rgba(15, 23, 42, 0.52);
  font-size: 12px;
}
.mywork-retry {
  height: 28px;
  padding: 0 12px;
  border: 1px solid rgba(15, 23, 42, 0.16);
  border-radius: 7px;
  background: #fff;
  color: rgba(15, 23, 42, 0.86);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}
.mywork-retry:hover {
  background: rgba(15, 23, 42, 0.04);
}

/* ── AI 规划弹窗 · 设计系统 ──────────────────────────── */
/* 头部 */
.plannerHeader {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.plHeaderLeft {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.plHeaderText {
  min-width: 0;
}
.plTitle {
  font-size: 17px;
  font-weight: 800;
  letter-spacing: 0;
  color: var(--ai-ink);
  line-height: 1.25;
}
.plHeaderRight {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
}
.plIconBtn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border-radius: 9px;
  border: 1px solid #1f2329;
  background: #fff;
  color: #1f2329;
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
  transition:
    border-color 140ms ease,
    background 140ms ease,
    color 140ms ease,
    transform 120ms var(--ai-ease);
}
.plIconBtn svg {
  width: 15px;
  height: 15px;
}
.plIconBtn:hover {
  background: #1f2329;
  border-color: #1f2329;
  color: #fff;
}
.plIconBtn:active {
  transform: translateY(1px);
}
.plClose {
  width: 32px;
  padding: 0;
  justify-content: center;
}
.plClose:hover {
  background: #1f2329;
  border-color: #1f2329;
  color: #fff;
}

/* 需求 Hero */
.plHero {
  position: relative;
  border-radius: 16px;
  border: 1px solid var(--ai-line);
  background: var(--ai-surface);
  padding: 16px 16px 12px;
  transition: border-color 200ms ease, box-shadow 200ms ease;
}
.plHero:focus-within {
  border-color: #1f2329;
  box-shadow: 0 0 0 3px rgba(31, 35, 41, 0.12);
}
.plHeroHead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}
.plHeroTitle {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  font-weight: 750;
  letter-spacing: -0.15px;
  color: var(--ai-ink);
}
/* plHeroTag 样式已废弃（AI 拆解徽章已删除） */
.plannerSheet .plHero :deep(.n-input) {
  --n-border: transparent !important;
  --n-border-hover: transparent !important;
  --n-border-focus: transparent !important;
  --n-box-shadow-focus: none !important;
  --n-color: rgba(249, 250, 251, 0.7) !important;
  --n-color-focus: rgba(249, 250, 251, 0.9) !important;
  --n-border-radius: 12px !important;
  --n-placeholder-color: var(--ai-faint) !important;
  --n-font-size: 13.5px !important;
  --n-line-height: 1.7 !important;
}
.plHeroBar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--ai-line);
}
.plHeroActions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

/* 通用 Section */
.plSection {
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid var(--ai-line);
  background: var(--ai-surface-2);
}
.plSectionHead {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.plSectionTitle {
  font-size: 12.5px;
  font-weight: 750;
  letter-spacing: -0.1px;
  color: var(--ai-ink-2);
}

/* 澄清（让父级 .plannerSheet 整体滚动，不在此单独限高） */
.plQaList {
  display: grid;
  gap: 10px;
}
.plQa {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  animation: ai-fade-up 320ms var(--ai-ease) both;
}
.plQaNum {
  width: 22px;
  height: 22px;
  flex: 0 0 auto;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #1f2329;
  color: #fff;
  font-size: 11px;
  font-weight: 750;
}
.plQaBody {
  flex: 1;
  display: grid;
  gap: 7px;
  min-width: 0;
}
.plQaText {
  font-size: 12.5px;
  font-weight: 680;
  color: var(--ai-ink);
  line-height: 1.5;
}

/* 落地配置 */
.plLandGrid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

/* 进度 */
.plProgress {
  border-radius: 14px;
  padding: 12px 14px;
  border: 1px solid var(--ai-line);
  background: var(--ai-surface-2);
}
.plProgressMeta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 11.5px;
  color: var(--ai-muted);
  margin-bottom: 9px;
}
.plProgressNums b {
  color: var(--ai-grad-start);
  font-weight: 800;
}
.plProgressBar {
  height: 6px;
  border-radius: 999px;
  background: var(--ai-surface-3);
  overflow: hidden;
}
.plProgressFill {
  height: 100%;
  border-radius: 999px;
  background: var(--ai-grad);
  transition: width 400ms var(--ai-ease);
}

/* 任务草稿 */
.plScroll {
  display: grid;
  gap: 10px;
  flex: 1 1 auto;
  min-height: 180px;
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  padding-right: 6px;
  margin-right: -6px;
  scrollbar-gutter: stable;
}
.plScroll::-webkit-scrollbar {
  width: 8px;
}
.plScroll::-webkit-scrollbar-thumb {
  background: rgba(15, 23, 42, 0.18);
  border-radius: 999px;
}
.plScroll::-webkit-scrollbar-thumb:hover {
  background: rgba(15, 23, 42, 0.32);
}
.plDrafts {
  display: grid;
  gap: 10px;
}
.plDraft {
  position: relative;
  display: flex;
  border-radius: 14px;
  border: 1px solid var(--ai-line);
  background: var(--ai-surface);
  overflow: hidden;
  cursor: pointer;
  animation: ai-fade-up 320ms var(--ai-ease) both;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease,
    transform 120ms var(--ai-ease);
}
.plDraft:hover {
  border-color: var(--ai-line-strong);
  box-shadow: 0 4px 16px rgba(17, 24, 39, 0.06);
}
.plDraft.open {
  border-color: rgba(30, 64, 175, 0.35);
  box-shadow: 0 4px 20px rgba(30, 64, 175, 0.08);
}
.plDraft.added {
  opacity: 0.62;
}
.plDraftBar {
  width: 4px;
  flex: 0 0 auto;
  align-self: stretch;
}
.plDraftBar.p-high {
  background: #374151;
}
.plDraftBar.p-medium {
  background: #9ca3af;
}
.plDraftBar.p-low {
  background: #e5e7eb;
}
.plDraftMain {
  flex: 1;
  min-width: 0;
  padding: 12px 14px;
}
.plDraftTop {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.plDraftTitle {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
  flex: 1;
}
.plDraftOrder {
  font-family: ui-monospace, 'Cascadia Mono', 'SFMono-Regular', Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  font-weight: 700;
  color: var(--ai-faint);
  background: var(--ai-surface-2);
  border: 1px solid var(--ai-line);
  border-radius: 6px;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
}
.plTitleInput {
  border: 0;
  outline: none;
  background: transparent;
  width: 100%;
  min-width: 0;
  font-size: 13.5px;
  font-weight: 680;
  letter-spacing: -0.15px;
  color: var(--ai-ink);
}
.plDraftRight {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.plPrio {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 9px;
  border-radius: 999px;
  white-space: nowrap;
  letter-spacing: 0.2px;
}
.plPrio.p-high {
  background: var(--surface-3);
  color: var(--ink-2);
}
.plPrio.p-medium {
  background: var(--surface-3);
  color: var(--ink-3);
}
.plPrio.p-low {
  background: var(--surface-3);
  color: var(--ink-4);
}
.plAddBtn {
  height: 28px;
  padding: 0 12px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #1f2329;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  border: 0;
  cursor: pointer;
  box-shadow: none;
  transition: all 160ms var(--ai-ease);
}
.plAddBtn:hover {
  background: #111827;
}
.plAddBtn:active {
  transform: translateY(1px);
}
.plAddBtn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
  filter: none;
}
.plAddCheck {
  width: 13px;
  height: 13px;
}
.plAddSpin {
  width: 12px;
  height: 12px;
  border-radius: 999px;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  animation: pl-spin 0.7s linear infinite;
  display: inline-block;
}
@keyframes pl-spin {
  to {
    transform: rotate(360deg);
  }
}
.plDraftEdit {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--ai-line-strong);
  display: grid;
  gap: 10px;
  animation: ai-fade-in 200ms ease both;
}

/* 空状态 & 流式 */
.plEmpty {
  padding: 40px 16px;
  text-align: center;
  animation: ai-fade-in 300ms ease both;
}
.plEmptyTitle {
  font-size: 14px;
  font-weight: 750;
  color: var(--ai-ink);
}
.plStreamBox {
  border-radius: 12px;
  border: 1px solid var(--ai-line);
  background: var(--ai-surface);
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.plStreamBoxBig {
  padding: 16px;
}
.plStreamDots {
  display: inline-flex;
  gap: 5px;
}
.plStreamDots i {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--ai-grad);
  animation: ai-breathe 1.1s ease-in-out infinite;
}
.plStreamDots i:nth-child(2) {
  animation-delay: 0.18s;
}
.plStreamDots i:nth-child(3) {
  animation-delay: 0.36s;
}
.plStreamLabel {
  font-size: 12px;
  font-weight: 700;
  color: var(--ai-ink-2);
}
.plStream {
  margin: 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--ai-surface-2);
  border: 1px solid var(--ai-line);
  font-family: ui-monospace, 'Cascadia Mono', 'SFMono-Regular', Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.65;
  color: var(--ai-muted);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow: auto;
}

/* 添加成功提示 */
.plAddedNote {
  display: flex;
  align-items: center;
  gap: 10px;
  border-radius: 12px;
  padding: 10px 14px;
  background: rgba(31, 35, 41, 0.04);
  border: 1px solid var(--ai-line);
  animation: ai-fade-up 320ms var(--ai-ease) both;
}
.plAddedText {
  font-size: 12.5px;
  font-weight: 650;
  color: #1f2329;
  flex: 1;
}
.plAddedNote button {
  border: 0;
  background: transparent;
  color: #1f2329;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
}
.plAddedNote button:hover {
  background: rgba(31, 35, 41, 0.08);
}

/* 输入控件统一 */
.plannerSheet :deep(.n-input),
.plannerSheet :deep(.n-base-selection) {
  --n-border: var(--ai-line) !important;
  --n-border-hover: var(--ai-line-strong) !important;
  --n-border-focus: #1f2329 !important;
  --n-box-shadow-focus: 0 0 0 3px rgba(31, 35, 41, 0.12) !important;
  --n-color: var(--ai-surface) !important;
  --n-color-disabled: var(--ai-surface-2) !important;
  --n-color-active: var(--ai-surface) !important;
  --n-border-radius: 9px !important;
}
.plannerSheet :deep(.n-button--primary-type) {
  --n-color: var(--brand) !important;
  --n-color-hover: var(--brand-hover) !important;
  --n-color-pressed: #172554 !important;
  --n-border: transparent !important;
  --n-text-color: #fff !important;
}

.archToggle :deep(.n-switch) {
  --n-rail-color: rgba(15, 23, 42, 0.12) !important;
  --n-rail-color-active: rgba(15, 23, 42, 0.84) !important;
  --n-button-color: #fff !important;
  --n-button-color-active: #fff !important;
}

:global(.createModal) {
  width: min(520px, calc(100vw - 28px));
}
.savedTop {
  display: inline-flex;
  gap: 8px;
  margin-bottom: 14px;
}
.savedTab {
  height: 32px;
  padding: 0 14px;
  border-radius: 9px;
  border: 1px solid var(--ai-line);
  background: var(--ai-surface);
  color: var(--ai-muted);
  font-weight: 700;
  font-size: 12.5px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  transition: all 150ms ease;
}
.savedTab:hover {
  border-color: var(--ai-line-strong);
  color: var(--ai-ink);
}
.savedTab.on {
  background: #1f2329;
  border-color: #1f2329;
  color: #fff;
}
.savedCount {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #1f2329;
  color: #fff;
  font-size: 10.5px;
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.savedList {
  display: grid;
  gap: 8px;
  max-height: min(520px, calc(100vh - 220px));
  overflow: auto;
}
.savedRow {
  border-radius: 13px;
  border: 1px solid var(--ai-line);
  background: var(--ai-surface);
  padding: 13px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  animation: ai-fade-up 280ms var(--ai-ease) both;
  transition: border-color 140ms ease, box-shadow 140ms ease;
}
.savedRow:hover {
  border-color: var(--ai-line-strong);
  box-shadow: 0 4px 14px rgba(17, 24, 39, 0.05);
}
.savedMain {
  min-width: 0;
}
.savedTitle {
  font-weight: 720;
  letter-spacing: -0.15px;
  color: var(--ai-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.savedMeta {
  margin-top: 5px;
  font-size: 11.5px;
  color: var(--ai-faint);
  display: flex;
  align-items: center;
  gap: 8px;
}
.savedActions {
  flex: 0 0 auto;
  display: inline-flex;
  gap: 8px;
}
.savedEmpty {
  padding: 36px 16px;
  text-align: center;
  animation: ai-fade-in 300ms ease both;
}
:global(.savedModal) {
  --brand: #1f2329;
  --brand-hover: #111827;
  --brand-soft: rgba(31, 35, 41, 0.08);
  --ai-grad-start: #1f2329;
  --ai-grad: #1f2329;
  --ai-grad-soft: rgba(31, 35, 41, 0.08);
  --ai-modal-radius: 4px;
  width: min(640px, calc(100vw - 28px));
  margin: 0 auto;
}
.savedHeader {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}
.savedClose {
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  background: transparent;
  border: 1px solid #e5e7eb;
  color: #6b7280;
  cursor: pointer;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
}
.savedClose:hover {
  background: #f3f4f6;
  color: #111827;
  border-color: #d1d5db;
}
.plEmptyHint {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 4px;
  text-align: center;
}
.plannerSheet {
  width: 100%;
}

/* 底部操作栏 */
.plFooter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.plFooterLeft {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.plFooterPct {
  font-size: 12px;
  font-weight: 800;
  color: #1f2329;
  font-family: ui-monospace, 'Cascadia Mono', Menlo, monospace;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(31, 35, 41, 0.08);
}
.plFooterPct.full {
  color: #1f2329;
  background: rgba(31, 35, 41, 0.08);
}
.plFooterText {
  font-size: 12px;
  color: var(--ai-muted);
}
.plFooterMuted {
  color: var(--ai-faint);
}
.plFooterRight {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.plMainCta {
  min-width: 108px;
  font-weight: 700;
  background: var(--brand);
}
.plMainCta:hover {
  filter: brightness(1.06);
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

/* Commercial workspace layout: compact hierarchy, two focused work lanes. */
.wsPage {
  height: auto;
  min-height: calc(100vh - 104px);
  padding: 0 0 64px;
}

.wsPage .head {
  height: 0;
  margin: 0;
  overflow: visible;
}

.wsPage .head .left {
  display: none;
}

.wsPage :deep(.h1) {
  font-size: 26px;
  line-height: 1.2;
  letter-spacing: -0.45px;
  font-weight: 760;
}

.wsPage .headRight .toolBtn {
  height: 32px;
  padding: 0 13px;
  border-radius: 8px;
  background: var(--brand);
  border-color: var(--brand);
  color: #fff;
  font-size: 12px;
  font-weight: 720;
}

.wsPage .headRight .toolBtn:hover {
  background: var(--brand-hover);
  border-color: var(--brand-hover);
}

.wsPage .wsBody {
  overflow: visible;
}

.wsPage .wsInner {
  max-width: 1240px;
  margin: 0;
  grid-template-columns: 1fr;
  gap: 28px;
  padding: 0;
  align-items: start;
}

.wsPage .secHead {
  padding: 0 0 14px;
  border-bottom-color: rgba(15, 23, 42, 0.14);
}

.wsPage .secTitle {
  font-size: 15px;
  line-height: 1.3;
  font-weight: 780;
  letter-spacing: -0.2px;
}

.wsPage .secHead .meta,
.wsPage .secHead .archLabel {
  font-size: 11px;
}

.wsPage .secBody {
  padding-top: 4px;
}

.wsPage .projHeadRight,
.wsPage .mwHeadRight {
  gap: 8px;
}

.wsPage .projRow,
.wsPage .mw-row {
  min-height: 54px;
  padding: 10px 0;
  border-radius: 0;
  border-bottom-color: rgba(15, 23, 42, 0.10);
}

.wsPage .projRow:hover,
.wsPage .mw-row:hover {
  background: rgba(15, 23, 42, 0.025);
}

.wsPage .pname,
.wsPage .mw-title {
  font-size: 14px;
  font-weight: 720;
  letter-spacing: -0.15px;
}

.wsPage .ptime,
.wsPage .mw-meta {
  font-size: 12px;
  color: rgba(15, 23, 42, 0.52);
}

.wsPage .mw-row {
  align-items: center;
}

.wsPage .plannerBtn {
  height: 30px;
  padding: 0 11px;
  border-radius: 7px;
  border-color: #1f2329;
  background: #1f2329;
  color: #fff;
  font-size: 12px;
  font-weight: 720;
}

.wsPage .plannerBtn:hover {
  border-color: #111827;
  background: #111827;
}

.wsPage .mw-state {
  height: 20px;
  padding: 0 7px;
  font-size: 10px;
  border-radius: 5px;
}

.wsPage .mw-act {
  height: 30px;
  padding: 0 11px;
  border-radius: 7px;
  font-size: 12px;
  font-weight: 700;
}

.wsPage .iconBtn {
  border-radius: 7px;
  background: transparent;
}

.wsPage .mywork-error {
  min-height: 140px;
  padding: 22px 0;
}

.wsPage .mywork-error-title {
  font-size: 13px;
}

.wsPage .mywork-retry {
  border-radius: 7px;
}

.wsPage :deep(.n-switch) {
  --n-rail-color: rgba(15, 23, 42, 0.14) !important;
  --n-rail-color-active: rgba(15, 23, 42, 0.82) !important;
}

/* 弹窗外壳：固定头部 + 滚动内容 + 粘性底部 */
:global(.plannerModal) {
  --brand: #1f2329;
  --brand-hover: #111827;
  --brand-soft: rgba(31, 35, 41, 0.08);
  --ai-grad-start: #1f2329;
  --ai-grad: #1f2329;
  --ai-grad-soft: rgba(31, 35, 41, 0.08);
  width: min(800px, calc(100vw - 32px));
  height: auto;
  min-height: 0;
  max-height: calc(100vh - 28px);
}

:global(.plannerModal .n-card) {
  max-height: calc(100vh - 40px);
  overflow: hidden;
  border: 1px solid rgba(17, 24, 39, 0.1);
  border-radius: var(--ai-modal-radius);
  box-shadow: var(--ai-modal-shadow);
  display: flex;
  flex-direction: column;
}

:global(.plannerModal .n-card-header) {
  padding: 18px 24px 16px;
  border-bottom: 1px solid var(--ai-line);
  background: var(--ai-surface);
  flex-shrink: 0;
}

:global(.plannerModal .n-card__content) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px 24px;
}

:global(.plannerModal .n-card__footer) {
  padding: 12px 24px;
  border-top: 1px solid var(--ai-line);
  background: var(--ai-surface-2);
  flex-shrink: 0;
}

:global(.plannerModal.n-card) {
  max-height: calc(100vh - 28px);
  overflow: hidden;
  border: 1px solid rgba(17, 24, 39, 0.1);
  border-radius: var(--ai-modal-radius);
  box-shadow: var(--ai-modal-shadow);
  display: flex;
  flex-direction: column;
}

:global(.plannerModal.n-card .n-card__content) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px 24px;
}

:global(.plannerModal.n-card .n-card__footer) {
  padding: 12px 24px;
  border-top: 1px solid var(--ai-line);
  background: var(--ai-surface-2);
  flex-shrink: 0;
}

/* 存档弹窗外壳 */
:global(.savedModal .n-card) {
  border-radius: var(--ai-modal-radius);
  border: 1px solid rgba(17, 24, 39, 0.1);
  box-shadow: var(--ai-modal-shadow);
}
:global(.savedModal .n-card-header) {
  padding: 18px 24px 16px;
  border-bottom: 1px solid var(--ai-line);
  background: var(--ai-surface);
  flex-shrink: 0;
}
:global(.savedModal .n-card__content) {
  padding: 20px 24px;
}
:global(.savedModal.n-card) {
  border-radius: var(--ai-modal-radius);
  border: 1px solid rgba(17, 24, 39, 0.1);
  box-shadow: var(--ai-modal-shadow);
}

.plannerSheet {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  padding-right: 6px;
  margin-right: -6px;
  scrollbar-gutter: stable;
}
.plannerSheet::-webkit-scrollbar {
  width: 8px;
}
.plannerSheet::-webkit-scrollbar-thumb {
  background: rgba(15, 23, 42, 0.18);
  border-radius: 999px;
}
.plannerSheet::-webkit-scrollbar-thumb:hover {
  background: rgba(15, 23, 42, 0.32);
}

/* 让非滚动 section 不被压缩，保证任务列表区有空间 */
.plannerSheet > .plHero,
.plannerSheet > .plSection,
.plannerSheet > .plProgress,
.plannerSheet > .plAddedNote {
  flex-shrink: 0;
}

.plannerSheet :deep(.n-input),
.plannerSheet :deep(.n-base-selection) {
  --n-font-size: 13px !important;
}

.plHeroActions :deep(.n-button) {
  height: 32px;
  padding: 0 14px;
  border-radius: 9px;
  font-size: 12.5px;
  font-weight: 650;
}

.plFooterRight :deep(.n-button--quaternary-type) {
  font-size: 12.5px;
}

@media (max-width: 860px) {
  .wsPage {
    padding: 0 0 56px;
  }

  .wsPage .wsInner {
    gap: 30px;
  }

  .plLandGrid {
    grid-template-columns: 1fr;
  }

  .plHeroBar {
    flex-direction: column;
    align-items: stretch;
  }

  .plHeroActions {
    justify-content: flex-end;
  }

  .plDraftTop {
    align-items: flex-start;
    flex-direction: column;
  }
}

</style>
