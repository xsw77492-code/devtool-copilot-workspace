<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NCard, NCheckbox, NInput, NModal, NSelect, NSpin, useDialog, useMessage } from 'naive-ui'
import { taskApi, type Task, type TaskBoardView, type TaskStatus } from '../api/task'
import { useAuthStore } from '../stores/auth'
import { useProjectStore } from '../stores/project'
import { useRealtimeStore } from '../stores/realtime'
import LineChart from '../components/charts/LineChart.vue'
import ProjectAiPlannerModal from '../components/ProjectAiPlannerModal.vue'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const dialog = useDialog()
const ps = useProjectStore()
const auth = useAuthStore()
const rt = useRealtimeStore()

const loading = ref(false)
const projectId = ref<number | null>(null)
const tasks = ref<Task[]>([])
const plannerOpen = ref(false)
const boardWrapRef = ref<HTMLElement | null>(null)
const colBodyRef = reactive<Record<TaskStatus, HTMLElement | null>>({ TODO: null, DOING: null, DONE: null })

const projectOptions = computed(() => {
  const opts = ps.visibleProjects.map((p) => ({ label: p.name || `项目 #${p.id}`, value: p.id }))
  const pid = Number(projectId.value || 0)
  if (pid && !opts.some((o) => Number(o.value) === pid)) {
    const cur = ps.projects.find((p) => Number(p.id) === pid) as any
    if (cur && Number(cur.archived || 0) === 1) {
      opts.unshift({ label: `${cur.name || `项目 #${cur.id}`} · 已归档`, value: cur.id })
    }
  }
  return opts
})

const isArchived = computed(() => {
  const pid = Number(projectId.value || 0)
  if (!pid) return false
  const p = ps.projects.find((x) => Number(x.id) === pid) as any
  return Number(p?.archived || 0) === 1
})

const currentProjectName = computed(() => {
  const pid = Number(projectId.value || 0)
  if (!pid) return ''
  const p = ps.projects.find((x) => Number(x.id) === pid) as any
  return p?.name || `项目 #${pid}`
})

function onPlannerApplied() {
  plannerOpen.value = false
  load()
}

type FilterMode = 'all' | 'mine' | 'unassigned' | 'participated'

const mode = ref<FilterMode>('all')
const q = ref('')
const overdueOnly = ref(false)
const showSubtasks = ref(false)

const participatedIds = ref<Set<number>>(new Set())

const viewItems = ref<TaskBoardView[]>([])
const activeViewId = ref<number | null>(null)
const applyingView = ref(false)
const viewModalOpen = ref(false)
const viewName = ref('')
const viewColor = ref<string>('teal')
const savingView = ref(false)

let autoRefreshTimer: any = null

const viewColors = [
  { key: 'teal', rgb: '15, 23, 42' },
  { key: 'cyan', rgb: '51, 65, 85' },
  { key: 'amber', rgb: '100, 116, 139' },
  { key: 'rose', rgb: '148, 163, 184' },
  { key: 'emerald', rgb: '203, 213, 225' },
  { key: 'slate', rgb: '15, 23, 42' }
]

const activeView = computed(() => (activeViewId.value ? viewItems.value.find((x) => x.id === activeViewId.value) : null))
const activeViewColor = computed(() => String(activeView.value?.color || '').trim())

function myId() {
  return Number(auth.me?.id || 0)
}

function sortKey(t: Task) {
  const v = Number(t.boardSort || 0)
  return v || t.id
}

const filteredTasks = computed(() => {
  const text = q.value.trim().toLowerCase()
  const uid = myId()
  let list = tasks.value.slice()

  if (mode.value === 'mine') list = list.filter((t) => Number(t.assigneeId || 0) === uid)
  if (mode.value === 'unassigned') list = list.filter((t) => !t.assigneeId)
  if (mode.value === 'participated') {
    const set = participatedIds.value
    list = list.filter((t) => set.has(t.id) || Number(t.assigneeId || 0) === uid)
  }

  if (text) list = list.filter((t) => String(t.title || '').toLowerCase().includes(text))
  if (!showSubtasks.value) list = list.filter((t) => !t.parentTaskId)

  return list
})

const taskTitleById = computed(() => {
  const map = new Map<number, string>()
  for (const t of tasks.value) {
    if (!t || !t.id) continue
    map.set(t.id, String(t.title || `#${t.id}`))
  }
  return map
})

function parentTitle(pid: number) {
  return taskTitleById.value.get(pid) || `#${pid}`
}

const childStatsByParent = computed(() => {
  const map = new Map<number, { total: number; done: number }>()
  for (const t of tasks.value) {
    const pid = Number(t.parentTaskId || 0)
    if (!pid) continue
    const cur = map.get(pid) || { total: 0, done: 0 }
    cur.total += 1
    if (String(t.status || '') === 'DONE') cur.done += 1
    map.set(pid, cur)
  }
  return map
})

function childStat(pid: number) {
  return childStatsByParent.value.get(pid) || { total: 0, done: 0 }
}

const columns = computed(() => {
  const g: Record<TaskStatus, Task[]> = { TODO: [], DOING: [], DONE: [] }
  for (const t of filteredTasks.value) {
    if (!t || !t.status) continue
    if (t.status === 'TODO' || t.status === 'DOING' || t.status === 'DONE') g[t.status].push(t)
  }
  for (const k of Object.keys(g) as TaskStatus[]) {
    g[k] = g[k]
      .slice()
      .sort((a, b) => (sortKey(b) - sortKey(a)) || (b.id - a.id))
  }
  return g
})

// ─── 数据窗（融合统计） ───
const winOpen = reactive<Record<'trend' | 'member' | 'risk', boolean>>({ trend: false, member: false, risk: false })

function loadWinState() {
  try {
    const s = localStorage.getItem('dtc_board_windows')
    if (!s) return
    const o = JSON.parse(s) as Record<string, unknown>
    if (o && typeof o === 'object') {
      if (o.trend) winOpen.trend = true
      if (o.member) winOpen.member = true
      if (o.risk) winOpen.risk = true
    }
  } catch {
    /* ignore */
  }
}
loadWinState()

watch(
  winOpen,
  (v) => {
    try {
      localStorage.setItem('dtc_board_windows', JSON.stringify(v))
    } catch {
      /* ignore */
    }
  },
  { deep: true }
)

function toggleWin(k: 'trend' | 'member' | 'risk') {
  winOpen[k] = !winOpen[k]
}

function tsOf(v?: string | number | null): number {
  if (v === undefined || v === null || v === '') return NaN
  const n = Number(v)
  if (Number.isFinite(n)) return n > 10000000000 ? n : n * 1000
  const d = new Date(String(v)).getTime()
  return Number.isFinite(d) ? d : NaN
}

type MemberStat = { name: string; total: number; done: number }

const stat = computed(() => {
  const list = filteredTasks.value
  const now = Date.now()
  let todo = 0
  let doing = 0
  let done = 0
  let overdue = 0
  const memberMap = new Map<string, MemberStat>()
  for (const t of list) {
    if (t.status === 'TODO') todo += 1
    else if (t.status === 'DOING') doing += 1
    else if (t.status === 'DONE') done += 1
    const due = tsOf(t.dueTime)
    if (t.status !== 'DONE' && Number.isFinite(due) && due < now) overdue += 1
    const name = String(t.assignee || '').trim() || '未分配'
    const cur = memberMap.get(name) || { name, total: 0, done: 0 }
    cur.total += 1
    if (t.status === 'DONE') cur.done += 1
    memberMap.set(name, cur)
  }
  const total = todo + doing + done
  const rate = total ? Math.round((done / total) * 100) : 0
  const members = [...memberMap.values()].sort((a, b) => b.total - a.total)
  return { todo, doing, done, overdue, total, rate, members }
})

const trend7 = computed(() => {
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 6)
  const startMs = start.getTime()
  const days: Array<{ label: string; value: number }> = []
  for (let i = 0; i < 7; i += 1) {
    const d = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i)
    days.push({ label: `${d.getMonth() + 1}/${d.getDate()}`, value: 0 })
  }
  for (const t of filteredTasks.value) {
    const ts = tsOf(t.createdAt ?? t.createTime)
    if (!Number.isFinite(ts) || ts < startMs || ts > now.getTime()) continue
    const d = new Date(ts)
    const dayStart = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
    const idx = Math.round((dayStart - startMs) / 86400000)
    if (idx >= 0 && idx < 7) days[idx].value += 1
  }
  return days
})

const trendTotal = computed(() => trend7.value.reduce((s, p) => s + p.value, 0))

const riskTasks = computed(() => {
  const now = Date.now()
  const list = filteredTasks.value.filter((t) => {
    if (t.status === 'DONE') return false
    if (String(t.priority || '').toUpperCase() === 'HIGH') return true
    const due = tsOf(t.dueTime)
    return Number.isFinite(due) && due < now
  })
  return list
    .slice()
    .sort((a, b) => {
      const da = tsOf(a.dueTime)
      const db = tsOf(b.dueTime)
      return (Number.isFinite(da) ? da : Infinity) - (Number.isFinite(db) ? db : Infinity)
    })
    .slice(0, 5)
})

function overdueDays(t: Task): number {
  const due = tsOf(t.dueTime)
  if (!Number.isFinite(due)) return 0
  return Math.max(0, Math.floor((Date.now() - due) / 86400000))
}

async function load() {
  if (!projectId.value) return
  loading.value = true
  try {
    tasks.value = await taskApi.kanban(projectId.value)
  } catch (e: any) {
    const msg = String(e?.message || '')
    message.error(msg || '加载失败')
  } finally {
    loading.value = false
  }
}

function timeShort(v?: string) {
  if (!v) return ''
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return v
  const now = new Date()
  const sameYear = now.getFullYear() === d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return sameYear ? `${mm}-${dd} ${hh}:${mi}` : `${d.getFullYear()}-${mm}-${dd} ${hh}:${mi}`
}

function priorityLabel(s?: string | null) {
  const v = String(s || '').toUpperCase()
  if (v === 'HIGH') return '高'
  if (v === 'MEDIUM') return '中'
  if (v === 'LOW') return '低'
  return v
}

type PriorityTone = 'high' | 'medium' | 'low' | 'na'
function priorityTone(s?: string | null): PriorityTone {
  const v = String(s || '').toUpperCase()
  if (v === 'HIGH') return 'high'
  if (v === 'MEDIUM') return 'medium'
  if (v === 'LOW') return 'low'
  return 'na'
}

function openTask(t: Task) {
  if (!t || !t.id || !t.projectId) return
  router.push({ name: 'task-detail', params: { projectId: t.projectId, taskId: t.id } })
}

const dragging = ref<{ taskId: number; from: TaskStatus } | null>(null)
const over = ref<{ to: TaskStatus; index: number } | null>(null)

function onDragStart(t: Task) {
  if (isArchived.value) return
  dragging.value = { taskId: t.id, from: t.status }
}

function onDragEnd() {
  dragging.value = null
  over.value = null
}

function listWithoutDragged(status: TaskStatus) {
  const arr = columns.value[status]
  if (!dragging.value) return arr
  return arr.filter((x) => x.id !== dragging.value?.taskId)
}

function handleAutoScroll(e: DragEvent, status?: TaskStatus) {
  const col = status ? colBodyRef[status] : null
  if (col) {
    const r = col.getBoundingClientRect()
    const y = e.clientY
    const edge = 42
    const speed = 14
    if (y < r.top + edge) col.scrollTop -= speed
    else if (y > r.bottom - edge) col.scrollTop += speed
  }

  const wrap = boardWrapRef.value
  if (wrap) {
    const r2 = wrap.getBoundingClientRect()
    const x = e.clientX
    const edge2 = 54
    const speed2 = 16
    if (x < r2.left + edge2) wrap.scrollLeft -= speed2
    else if (x > r2.right - edge2) wrap.scrollLeft += speed2
  }
}

function onColumnDragOver(e: DragEvent, to: TaskStatus) {
  if (!dragging.value) return
  over.value = { to, index: listWithoutDragged(to).length }
  handleAutoScroll(e, to)
}

function onCardDragOver(e: DragEvent, to: TaskStatus, index: number) {
  if (!dragging.value) return
  const el = e.currentTarget as HTMLElement | null
  if (!el) return
  const r = el.getBoundingClientRect()
  const before = e.clientY < r.top + r.height / 2
  over.value = { to, index: before ? index : index + 1 }
  handleAutoScroll(e, to)
}

async function onDrop(to: TaskStatus) {
  if (isArchived.value) {
    message.warning('项目已归档，只读')
    dragging.value = null
    over.value = null
    return
  }
  const d = dragging.value
  const o = over.value
  if (!d || !o || !projectId.value) return
  if (to !== o.to) return

  const targetList = listWithoutDragged(to)
  let idx = Math.max(0, Math.min(o.index, targetList.length))
  if (d.from === to) {
    const orig = columns.value[to].findIndex((x) => x.id === d.taskId)
    if (orig >= 0 && orig < o.index) idx = Math.max(0, idx - 1)
  }
  const beforeId = idx > 0 ? targetList[idx - 1]?.id : null
  const afterId = idx < targetList.length ? targetList[idx]?.id : null

  const moved = tasks.value.find((x) => x.id === d.taskId) || null
  if (moved) {
    const before = idx > 0 ? targetList[idx - 1] : null
    const after = idx < targetList.length ? targetList[idx] : null
    const beforeSort = before ? sortKey(before) : null
    const afterSort = after ? sortKey(after) : null
    let nextSort = Number(moved.boardSort || 0) || moved.id
    if (beforeSort === null && afterSort === null) {
      nextSort = Date.now()
    } else if (beforeSort === null && afterSort !== null) {
      nextSort = afterSort + 10000
    } else if (beforeSort !== null && afterSort === null) {
      nextSort = beforeSort - 10000
    } else if (beforeSort !== null && afterSort !== null) {
      const gap = beforeSort - afterSort
      if (gap > 2) nextSort = Math.floor((beforeSort + afterSort) / 2)
      else nextSort = afterSort + 1
    }
    moved.status = to
    moved.boardSort = nextSort
  }

  dragging.value = null
  over.value = null

  ;(async () => {
    try {
      await taskApi.kanbanMove({
        projectId: projectId.value!,
        taskId: d.taskId,
        toStatus: to,
        beforeId,
        afterId
      })
    } catch (e: any) {
      const msg = String(e?.message || '')
      if (to === 'DONE' && msg.includes('验收清单未全部完成')) {
        dialog.warning({
          title: '验收清单未完成',
          content: msg,
          positiveText: '强制完成',
          negativeText: '取消',
          onPositiveClick: async () => {
            try {
              await taskApi.kanbanMove({
                projectId: projectId.value!,
                taskId: d.taskId,
                toStatus: to,
                beforeId,
                afterId,
                forceDone: true
              })
            } catch (e2: any) {
              message.error(e2?.message || '移动失败')
              await load()
              return
            }
            await load()
          }
        })
      } else {
        message.error(msg || '移动失败')
        await load()
      }
    }
  })()
}

type RenderItem = { kind: 'line'; key: string } | { kind: 'task'; key: string; task: Task; index: number }

function renderItems(status: TaskStatus): RenderItem[] {
  const list = listWithoutDragged(status)
  const idx =
    dragging.value && over.value?.to === status ? Math.max(0, Math.min(over.value.index, list.length)) : -1
  const out: RenderItem[] = []
  for (let i = 0; i < list.length; i++) {
    if (i === idx) out.push({ kind: 'line', key: `line-${status}-${idx}` })
    out.push({ kind: 'task', key: `task-${list[i].id}`, task: list[i], index: i })
  }
  if (idx === list.length) out.push({ kind: 'line', key: `line-${status}-${idx}` })
  return out
}

const quickCreate = reactive<{ status: TaskStatus | null; title: string; creating: boolean }>({
  status: null,
  title: '',
  creating: false
})

function openQuickCreate(status: TaskStatus) {
  if (!projectId.value) return
  if (isArchived.value) {
    message.warning('项目已归档，只读')
    return
  }
  quickCreate.status = status
  quickCreate.title = ''
}

function closeQuickCreate() {
  quickCreate.status = null
  quickCreate.title = ''
}

async function submitQuickCreate() {
  if (!projectId.value || !quickCreate.status) return
  if (isArchived.value) {
    message.warning('项目已归档，只读')
    return
  }
  const title = quickCreate.title.trim()
  if (!title) return
  if (quickCreate.creating) return
  quickCreate.creating = true
  try {
    const id = await taskApi.create(projectId.value, title, { source: 'KANBAN' })
    if (quickCreate.status !== 'TODO') {
      const first = columns.value[quickCreate.status][0]?.id ?? null
      await taskApi.kanbanMove({
        projectId: projectId.value,
        taskId: id,
        toStatus: quickCreate.status,
        beforeId: null,
        afterId: first
      })
    }
    await load()
    closeQuickCreate()
  } catch (e: any) {
    message.error(e?.message || '创建失败')
  } finally {
    quickCreate.creating = false
  }
}

function setColBodyEl(status: TaskStatus, el: any) {
  colBodyRef[status] = (el as HTMLElement) || null
}

const modeOptions = [
  { label: '全部', value: 'all' },
  { label: '我负责', value: 'mine' },
  { label: '未分配', value: 'unassigned' },
  { label: '我参与', value: 'participated' }
]

const viewSelectOptions = computed(() => viewItems.value.map((v) => ({ label: v.name, value: v.id })))

function currentFiltersJson() {
  return JSON.stringify({
    m: mode.value,
    q: q.value.trim(),
    overdue: overdueOnly.value ? 1 : 0,
    sub: showSubtasks.value ? 1 : 0
  })
}

function applyFiltersJson(filtersJson: string) {
  applyingView.value = true
  try {
    const obj = JSON.parse(filtersJson) as any
    const m = String(obj.m || '')
    if (m === 'all' || m === 'mine' || m === 'unassigned' || m === 'participated') mode.value = m
    q.value = String(obj.q || '')
    overdueOnly.value = String(obj.overdue || 0) === '1' || obj.overdue === 1
    showSubtasks.value = String(obj.sub || 0) === '1' || obj.sub === 1
  } catch {
  }
  applyingView.value = false
}

async function activateView(id: number | null) {
  activeViewId.value = id
  if (!id) {
    syncQuery()
    return
  }
  const v = viewItems.value.find((x) => x.id === id)
  if (!v) return
  applyFiltersJson(v.filtersJson)
  viewColor.value = String(v.color || '').trim() || 'teal'
  syncQuery()
}

async function loadViews() {
  if (!projectId.value) {
    viewItems.value = []
    return
  }
  try {
    viewItems.value = await taskApi.listViews(projectId.value)
  } catch {
    viewItems.value = []
  }
}

async function loadParticipated() {
  if (!projectId.value) {
    participatedIds.value = new Set()
    return
  }
  try {
    const ids = await taskApi.participatedIds(projectId.value)
    participatedIds.value = new Set(ids || [])
  } catch {
    participatedIds.value = new Set()
  }
}

function openSaveView() {
  if (isArchived.value) {
    message.warning('项目已归档，只读')
    return
  }
  viewName.value = String(activeView.value?.name || '').trim()
  viewColor.value = activeViewColor.value || 'teal'
  viewModalOpen.value = true
}

async function saveViewAsNew() {
  if (!projectId.value) return
  if (isArchived.value) {
    message.warning('项目已归档，只读')
    return
  }
  const name = viewName.value.trim()
  if (!name) {
    message.warning('请输入视图名称')
    return
  }
  savingView.value = true
  try {
    const v = await taskApi.createView({ projectId: projectId.value, name, color: viewColor.value, filtersJson: currentFiltersJson() })
    viewModalOpen.value = false
    await loadViews()
    await activateView(v.id)
    message.success('已保存视图')
  } catch (e: any) {
    message.error(e?.message || '保存失败')
  } finally {
    savingView.value = false
  }
}

async function updateActiveView() {
  if (!activeViewId.value) return
  if (isArchived.value) {
    message.warning('项目已归档，只读')
    return
  }
  const name = viewName.value.trim()
  if (!name) {
    message.warning('请输入视图名称')
    return
  }
  savingView.value = true
  try {
    const v = await taskApi.updateView(activeViewId.value, { name, color: viewColor.value, filtersJson: currentFiltersJson() })
    viewModalOpen.value = false
    await loadViews()
    await activateView(v.id)
    message.success('已更新视图')
  } catch (e: any) {
    message.error(e?.message || '更新失败')
  } finally {
    savingView.value = false
  }
}

async function deleteActiveView() {
  if (!activeViewId.value) return
  if (isArchived.value) {
    message.warning('项目已归档，只读')
    return
  }
  try {
    await taskApi.deleteView(activeViewId.value)
    activeViewId.value = null
    await loadViews()
    syncQuery()
    message.success('已删除视图')
  } catch (e: any) {
    message.error(e?.message || '删除失败')
  }
}

function applyQuery() {
  const hasAny =
    typeof route.query.m !== 'undefined' ||
    typeof route.query.q !== 'undefined' ||
    typeof route.query.overdue !== 'undefined' ||
    typeof route.query.sub !== 'undefined' ||
    typeof route.query.viewId !== 'undefined'
  if (!hasAny) {
    try {
      const s = localStorage.getItem('board:lastFilters')
      if (s) {
        const obj = JSON.parse(s) as any
        const m = String(obj.m || '')
        if (m === 'all' || m === 'mine' || m === 'unassigned' || m === 'participated') mode.value = m
        q.value = String(obj.q || '')
        overdueOnly.value = String(obj.overdue || 0) === '1' || obj.overdue === 1
        showSubtasks.value = String(obj.sub || 0) === '1' || obj.sub === 1
        const vid = Number(obj.viewId || 0)
        activeViewId.value = vid && Number.isFinite(vid) ? vid : null
      }
    } catch {
    }
  }
  const m = String(route.query.m || '')
  if (m === 'all' || m === 'mine' || m === 'unassigned' || m === 'participated') mode.value = m
  q.value = String(route.query.q || '')
  overdueOnly.value = String(route.query.overdue || '') === '1'
  showSubtasks.value = String(route.query.sub || '') === '1'
  const vid = Number(route.query.viewId || 0)
  activeViewId.value = vid && Number.isFinite(vid) ? vid : null
  if (activeViewId.value && viewItems.value.length) {
    const v = viewItems.value.find((x) => x.id === activeViewId.value)
    if (v) {
      applyFiltersJson(v.filtersJson)
      viewColor.value = String(v.color || '').trim() || 'teal'
    }
  }
}

function syncQuery() {
  const query: any = { ...route.query }
  query.projectId = projectId.value ? String(projectId.value) : undefined
  query.m = mode.value !== 'all' ? mode.value : undefined
  query.q = q.value.trim() ? q.value.trim() : undefined
  query.overdue = overdueOnly.value ? '1' : undefined
  query.sub = showSubtasks.value ? '1' : undefined
  query.viewId = activeViewId.value ? String(activeViewId.value) : undefined
  router.replace({ name: 'board', query })
  try {
    const s = JSON.stringify({
      m: mode.value,
      q: q.value.trim(),
      overdue: overdueOnly.value ? 1 : 0,
      sub: showSubtasks.value ? 1 : 0,
      viewId: activeViewId.value || 0
    })
    localStorage.setItem('board:lastFilters', s)
  } catch {
  }
}

watch(
  () => route.fullPath,
  () => {
    applyQuery()
  }
)

watch([mode, q, overdueOnly, showSubtasks], () => {
  if (!applyingView.value) activeViewId.value = null
  syncQuery()
})

async function setProjectId(next: number | null, opts?: { silentRoute?: boolean }) {
  projectId.value = next
  try {
    if (next) localStorage.setItem('board:lastProjectId', String(next))
  } catch {
  }
  rt.subscribe(next, 'PROJECT', next)
  if (!opts?.silentRoute) {
    syncQuery()
  }
}

async function onProjectChange(v: any) {
  const next = v ? Number(v) : null
  await setProjectId(next)
  await Promise.all([loadViews(), loadParticipated(), load()])
  applyQuery()
}

function restoreProjectIdFromRouteOrStorage() {
  const q = route.query?.projectId
  const fromQuery = q ? Number(Array.isArray(q) ? q[0] : q) : NaN
  if (Number.isFinite(fromQuery) && fromQuery > 0) return fromQuery
  try {
    const s = localStorage.getItem('board:lastProjectId')
    const n = s ? Number(s) : NaN
    if (Number.isFinite(n) && n > 0) return n
  } catch {
  }
  return null
}

onMounted(async () => {
  if (!ps.projects.length) await ps.load()
  if (!projectId.value && ps.visibleProjects.length) {
    const restored = restoreProjectIdFromRouteOrStorage()
    const valid =
      restored && ps.visibleProjects.some((p) => Number(p.id) === Number(restored)) ? restored : ps.visibleProjects[0].id
    await setProjectId(valid, { silentRoute: true })
  }
  await Promise.all([loadViews(), loadParticipated(), load()])
  applyQuery()
  if (!autoRefreshTimer) {
    autoRefreshTimer = setInterval(() => {
      if (document.visibilityState !== 'visible') return
      if (loading.value) return
      void load()
    }, 45000)
  }
})

onUnmounted(() => {
  rt.subscribe(null)
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
})

watch(
  () => rt.seq,
  async () => {
    const ev = rt.lastEvent
    const pid = Number(ev?.projectId || 0)
    if (!pid || pid !== projectId.value) return
    const t = String(ev?.type || '')
    if (
      t === 'TASK_CREATED' ||
      t === 'TASK_UPDATED' ||
      t === 'TASK_STATUS_UPDATED' ||
      t === 'TASK_MOVED' ||
      t === 'TASK_DELETED' ||
      t === 'AI_GENERATE_TASK' ||
      t === 'AI_APPLY_DONE'
    ) {
      await load()
    }
  }
)

watch(
  () => route.query?.projectId,
  async (v) => {
    const next = v ? Number(Array.isArray(v) ? v[0] : v) : NaN
    if (!Number.isFinite(next) || next <= 0) return
    if (projectId.value === next) return
    if (ps.visibleProjects.length && !ps.visibleProjects.some((p) => Number(p.id) === Number(next))) return
    await setProjectId(next, { silentRoute: true })
    await Promise.all([loadViews(), loadParticipated(), load()])
    applyQuery()
  }
)
</script>

<template>
  <div class="page boardPage">
    <div class="filters">
      <n-select
        :value="projectId || 0"
        :options="projectOptions"
        class="fSel"
        @update:value="onProjectChange"
      />
      <div class="viewBox">
        <div v-if="activeViewId" class="viewDot" :style="{ '--dot': (viewColors.find((x) => x.key === (activeViewColor || viewColor))?.rgb || viewColors[0].rgb) } as any" />
        <n-select
          v-model:value="activeViewId"
          clearable
          placeholder="视图"
          :options="viewSelectOptions"
          class="fView"
          @update:value="activateView"
        />
      </div>
      <n-select v-model:value="mode" :options="modeOptions" class="fMode" />
      <n-input v-model:value="q" placeholder="搜索任务…" class="fSearch" />
      <div class="fActions">
        <label class="ck muted">
          <n-checkbox v-model:checked="overdueOnly" />逾期
        </label>
        <label class="ck muted">
          <n-checkbox v-model:checked="showSubtasks" />子任务
        </label>
        <button class="btnGhost" type="button" :disabled="isArchived" @click="openSaveView">保存视图</button>
        <button v-if="activeViewId" class="btnGhost danger" type="button" :disabled="isArchived" @click="deleteActiveView">删除</button>
        <button class="btnDark" type="button" :disabled="isArchived || !projectId" @click="plannerOpen = true">AI 规划</button>
      </div>
    </div>

    <ProjectAiPlannerModal
      :show="plannerOpen"
      :project-id="Number(projectId || 0)"
      :project-name="currentProjectName"
      :archived="isArchived"
      @update:show="(v: boolean) => (plannerOpen = v)"
      @applied="onPlannerApplied"
    />

    <div class="statBar">
      <div class="kpis">
        <div class="kpi">
          <div class="kpiNum">{{ stat.todo }}</div>
          <div class="kpiLabel">待办</div>
        </div>
        <div class="kpi">
          <div class="kpiNum">{{ stat.doing }}</div>
          <div class="kpiLabel">进行中</div>
        </div>
        <div class="kpi">
          <div class="kpiNum">{{ stat.done }}</div>
          <div class="kpiLabel">已完成</div>
        </div>
        <div class="kpi">
          <div class="kpiNum" :class="{ warn: stat.overdue > 0 }">{{ stat.overdue }}</div>
          <div class="kpiLabel">逾期</div>
        </div>
        <div class="rate">
          <div class="rateTop">
            <span class="rateLabel">完成率</span>
            <span class="rateVal">{{ stat.rate }}%</span>
          </div>
          <div class="rateTrack">
            <div class="rateFill" :style="{ width: `${stat.rate}%` }" />
          </div>
        </div>
      </div>
      <div class="winToggles">
        <span class="winCap">数据窗</span>
        <button class="winBtn" :class="{ on: winOpen.trend }" type="button" @click="toggleWin('trend')">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
            <path d="M4 19V5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            <path d="M4 19h16" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            <path d="m7 14 3.2-3.4 2.6 2.2L18 7.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          趋势
        </button>
        <button class="winBtn" :class="{ on: winOpen.member }" type="button" @click="toggleWin('member')">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
            <path d="M12 12a3.6 3.6 0 1 0 0-7.2A3.6 3.6 0 0 0 12 12Z" stroke="currentColor" stroke-width="1.8" />
            <path d="M5.5 19.4c1-3 3.4-4.7 6.5-4.7s5.5 1.7 6.5 4.7" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          </svg>
          成员
        </button>
        <button class="winBtn" :class="{ on: winOpen.risk }" type="button" @click="toggleWin('risk')">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
            <path d="M12 4.2 21 19H3L12 4.2Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" />
            <path d="M12 9.5v4.2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            <path d="M12 16.6h.01" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" />
          </svg>
          风险
        </button>
      </div>
    </div>

    <div v-if="winOpen.trend || winOpen.member || winOpen.risk" class="winGrid">
      <section v-if="winOpen.trend" class="winCard">
        <header class="winHead">
          <h3 class="winTitle">近 7 天新增</h3>
          <span class="muted winSub">按创建时间统计</span>
          <button class="winClose" type="button" @click="toggleWin('trend')">×</button>
        </header>
        <template v-if="trendTotal > 0">
          <LineChart :points="trend7" :height="150" />
        </template>
        <template v-else>
          <div class="winEmpty">
            <p class="muted">近 7 天还没有新增任务</p>
            <button class="btnGhost" type="button" :disabled="isArchived" @click="openQuickCreate('TODO')">创建第一个任务</button>
          </div>
        </template>
      </section>

      <section v-if="winOpen.member" class="winCard">
        <header class="winHead">
          <h3 class="winTitle">成员分布</h3>
          <span class="muted winSub">按负责人统计</span>
          <button class="winClose" type="button" @click="toggleWin('member')">×</button>
        </header>
        <div v-if="stat.members.length" class="memList">
          <div v-for="m in stat.members" :key="m.name" class="memRow">
            <span class="memAvatar">{{ m.name.slice(0, 1).toUpperCase() }}</span>
            <span class="memName">{{ m.name }}</span>
            <span class="memBar"><i :style="{ width: m.total ? `${Math.round((m.done / m.total) * 100)}%` : '0%' }" /></span>
            <span class="memNum">{{ m.done }}/{{ m.total }}</span>
          </div>
        </div>
        <div v-else class="winEmpty">
          <p class="muted">任务尚未分配负责人</p>
        </div>
      </section>

      <section v-if="winOpen.risk" class="winCard">
        <header class="winHead">
          <h3 class="winTitle">风险</h3>
          <span class="muted winSub">逾期与高优先级</span>
          <button class="winClose" type="button" @click="toggleWin('risk')">×</button>
        </header>
        <template v-if="riskTasks.length">
          <ul class="riskList">
            <li v-for="t in riskTasks" :key="t.id" class="riskItem" @click="openTask(t)">
              <span class="riskDot" :class="{ warn: overdueDays(t) > 0 }" />
              <span class="riskTitle">{{ t.title }}</span>
              <span v-if="overdueDays(t) > 0" class="pill riskDays">逾期 {{ overdueDays(t) }} 天</span>
              <span v-else class="pill riskHigh">高优</span>
            </li>
          </ul>
        </template>
        <template v-else>
          <div class="winEmpty ok">
            <span class="okDot" />
            <p>无逾期任务 · 无高优先级阻塞</p>
          </div>
        </template>
      </section>
    </div>

    <section class="panel lightPanel">
      <n-spin :show="loading">
        <div ref="boardWrapRef" class="boardWrap">
          <div class="board">
          <div
            class="col todo"
            @dragover.prevent="(e) => onColumnDragOver(e, 'TODO')"
            @drop.prevent="onDrop('TODO')"
          >
            <div class="colHead">
              <div class="colTitle">TODO</div>
              <div class="colRight">
                <span class="pill count">{{ columns.TODO.length }}</span>
                <button class="iconBtn" type="button" :disabled="isArchived" @click="openQuickCreate('TODO')">+</button>
              </div>
            </div>
            <div :ref="(el) => setColBodyEl('TODO', el)" class="colBody">
              <div v-if="quickCreate.status === 'TODO'" class="compose">
                <n-input
                  v-model:value="quickCreate.title"
                  placeholder="新任务标题…"
                  :disabled="quickCreate.creating"
                  @keyup.enter="submitQuickCreate"
                />
                <div class="composeActions">
                  <button class="btnGhost" type="button" :disabled="quickCreate.creating" @click="closeQuickCreate">取消</button>
                  <button class="btnPrimary" type="button" :disabled="quickCreate.creating || !quickCreate.title.trim()" @click="submitQuickCreate">
                    创建
                  </button>
                </div>
              </div>
              <div v-if="loading && !tasks.length" class="skeletonList">
                <div v-for="n in 4" :key="n" class="skeletonCard" />
              </div>
              <div v-else-if="!columns.TODO.length" class="emptyCol">
                <div class="muted">这里还没有任务</div>
                <button class="btnGhost" type="button" :disabled="isArchived" @click="openQuickCreate('TODO')">添加任务</button>
              </div>
              <transition-group v-else name="cardMove" tag="div" class="list">
                <template v-for="ri in renderItems('TODO')" :key="ri.key">
                  <div v-if="ri.kind === 'line'" class="dropLine" />
                  <div
                    v-else
                    class="card"
                    :class="{ sub: !!ri.task.parentTaskId }"
                    draggable="true"
                    @click="openTask(ri.task)"
                    @dragstart.stop="onDragStart(ri.task)"
                    @dragend.stop="onDragEnd"
                    @dragover.prevent="(e) => onCardDragOver(e, 'TODO', ri.index)"
                  >
                    <div class="cardTop">
                      <div class="grip" aria-hidden="true" />
                      <div class="cardTitle">{{ ri.task.title }}</div>
                    </div>
                    <div class="meta">
                      <span v-if="ri.task.priority" class="chip" :class="['prio', `prio-${priorityTone(ri.task.priority)}`]">{{ priorityLabel(ri.task.priority) }}</span>
                      <span v-if="ri.task.assignee" class="chip muted">{{ ri.task.assignee }}</span>
                      <span v-if="ri.task.dueTime" class="chip muted">截止 {{ timeShort(ri.task.dueTime) }}</span>
                      <span
                        v-if="ri.task.parentTaskId"
                        class="chip parent"
                        :title="parentTitle(Number(ri.task.parentTaskId))"
                      >↳ {{ parentTitle(Number(ri.task.parentTaskId)) }}</span>
                      <span v-if="!ri.task.parentTaskId && childStat(ri.task.id).total" class="chip prog"
                        >{{ childStat(ri.task.id).done }}/{{ childStat(ri.task.id).total }}</span
                      >
                    </div>
                  </div>
                </template>
              </transition-group>
            </div>
          </div>

          <div
            class="col doing"
            @dragover.prevent="(e) => onColumnDragOver(e, 'DOING')"
            @drop.prevent="onDrop('DOING')"
          >
            <div class="colHead">
              <div class="colTitle">DOING</div>
              <div class="colRight">
                <span class="pill count">{{ columns.DOING.length }}</span>
                <button class="iconBtn" type="button" :disabled="isArchived" @click="openQuickCreate('DOING')">+</button>
              </div>
            </div>
            <div :ref="(el) => setColBodyEl('DOING', el)" class="colBody">
              <div v-if="quickCreate.status === 'DOING'" class="compose">
                <n-input
                  v-model:value="quickCreate.title"
                  placeholder="新任务标题…"
                  :disabled="quickCreate.creating"
                  @keyup.enter="submitQuickCreate"
                />
                <div class="composeActions">
                  <button class="btnGhost" type="button" :disabled="quickCreate.creating" @click="closeQuickCreate">取消</button>
                  <button class="btnPrimary" type="button" :disabled="quickCreate.creating || !quickCreate.title.trim()" @click="submitQuickCreate">
                    创建
                  </button>
                </div>
              </div>
              <div v-if="loading && !tasks.length" class="skeletonList">
                <div v-for="n in 4" :key="n" class="skeletonCard" />
              </div>
              <div v-else-if="!columns.DOING.length" class="emptyCol">
                <div class="muted">这里还没有任务</div>
                <button class="btnGhost" type="button" :disabled="isArchived" @click="openQuickCreate('DOING')">添加任务</button>
              </div>
              <transition-group v-else name="cardMove" tag="div" class="list">
                <template v-for="ri in renderItems('DOING')" :key="ri.key">
                  <div v-if="ri.kind === 'line'" class="dropLine" />
                  <div
                    v-else
                    class="card"
                    :class="{ sub: !!ri.task.parentTaskId }"
                    draggable="true"
                    @click="openTask(ri.task)"
                    @dragstart.stop="onDragStart(ri.task)"
                    @dragend.stop="onDragEnd"
                    @dragover.prevent="(e) => onCardDragOver(e, 'DOING', ri.index)"
                  >
                    <div class="cardTop">
                      <div class="grip" aria-hidden="true" />
                      <div class="cardTitle">{{ ri.task.title }}</div>
                    </div>
                    <div class="meta">
                      <span v-if="ri.task.priority" class="chip" :class="['prio', `prio-${priorityTone(ri.task.priority)}`]">{{ priorityLabel(ri.task.priority) }}</span>
                      <span v-if="ri.task.assignee" class="chip muted">{{ ri.task.assignee }}</span>
                      <span v-if="ri.task.dueTime" class="chip muted">截止 {{ timeShort(ri.task.dueTime) }}</span>
                      <span
                        v-if="ri.task.parentTaskId"
                        class="chip parent"
                        :title="parentTitle(Number(ri.task.parentTaskId))"
                      >↳ {{ parentTitle(Number(ri.task.parentTaskId)) }}</span>
                      <span v-if="!ri.task.parentTaskId && childStat(ri.task.id).total" class="chip prog"
                        >{{ childStat(ri.task.id).done }}/{{ childStat(ri.task.id).total }}</span
                      >
                    </div>
                  </div>
                </template>
              </transition-group>
            </div>
          </div>

          <div
            class="col done"
            @dragover.prevent="(e) => onColumnDragOver(e, 'DONE')"
            @drop.prevent="onDrop('DONE')"
          >
            <div class="colHead">
              <div class="colTitle">DONE</div>
              <div class="colRight">
                <span class="pill count">{{ columns.DONE.length }}</span>
                <button class="iconBtn" type="button" :disabled="isArchived" @click="openQuickCreate('DONE')">+</button>
              </div>
            </div>
            <div :ref="(el) => setColBodyEl('DONE', el)" class="colBody">
              <div v-if="quickCreate.status === 'DONE'" class="compose">
                <n-input
                  v-model:value="quickCreate.title"
                  placeholder="新任务标题…"
                  :disabled="quickCreate.creating"
                  @keyup.enter="submitQuickCreate"
                />
                <div class="composeActions">
                  <button class="btnGhost" type="button" :disabled="quickCreate.creating" @click="closeQuickCreate">取消</button>
                  <button class="btnPrimary" type="button" :disabled="quickCreate.creating || !quickCreate.title.trim()" @click="submitQuickCreate">
                    创建
                  </button>
                </div>
              </div>
              <div v-if="loading && !tasks.length" class="skeletonList">
                <div v-for="n in 4" :key="n" class="skeletonCard" />
              </div>
              <div v-else-if="!columns.DONE.length" class="emptyCol">
                <div class="muted">这里还没有任务</div>
                <button class="btnGhost" type="button" :disabled="isArchived" @click="openQuickCreate('DONE')">添加任务</button>
              </div>
              <transition-group v-else name="cardMove" tag="div" class="list">
                <template v-for="ri in renderItems('DONE')" :key="ri.key">
                  <div v-if="ri.kind === 'line'" class="dropLine" />
                  <div
                    v-else
                    class="card"
                    :class="{ sub: !!ri.task.parentTaskId }"
                    draggable="true"
                    @click="openTask(ri.task)"
                    @dragstart.stop="onDragStart(ri.task)"
                    @dragend.stop="onDragEnd"
                    @dragover.prevent="(e) => onCardDragOver(e, 'DONE', ri.index)"
                  >
                    <div class="cardTop">
                      <div class="grip" aria-hidden="true" />
                      <div class="cardTitle">{{ ri.task.title }}</div>
                    </div>
                    <div class="meta">
                      <span v-if="ri.task.priority" class="chip" :class="['prio', `prio-${priorityTone(ri.task.priority)}`]">{{ priorityLabel(ri.task.priority) }}</span>
                      <span v-if="ri.task.assignee" class="chip muted">{{ ri.task.assignee }}</span>
                      <span v-if="ri.task.dueTime" class="chip muted">截止 {{ timeShort(ri.task.dueTime) }}</span>
                      <span
                        v-if="ri.task.parentTaskId"
                        class="chip parent"
                        :title="parentTitle(Number(ri.task.parentTaskId))"
                      >↳ {{ parentTitle(Number(ri.task.parentTaskId)) }}</span>
                      <span v-if="!ri.task.parentTaskId && childStat(ri.task.id).total" class="chip prog"
                        >{{ childStat(ri.task.id).done }}/{{ childStat(ri.task.id).total }}</span
                      >
                    </div>
                  </div>
                </template>
              </transition-group>
            </div>
          </div>
          </div>
        </div>
      </n-spin>
    </section>

    <n-modal v-model:show="viewModalOpen" :mask-closable="false">
      <n-card style="width: 520px" title="保存视图" :bordered="false">
        <div class="formRow">
          <div class="muted label">名称</div>
          <n-input v-model:value="viewName" placeholder="例如：我负责·逾期" />
        </div>
        <div class="formRow">
          <div class="muted label">颜色</div>
          <div class="colorRow">
            <button
              v-for="c in viewColors"
              :key="c.key"
              class="colorDot"
              :class="{ on: viewColor === c.key }"
              type="button"
              :style="{ '--dot': c.rgb } as any"
              @click="viewColor = c.key"
            />
          </div>
        </div>
        <template #footer>
          <div class="modalActions">
            <n-button @click="viewModalOpen = false">取消</n-button>
            <n-button v-if="activeViewId" type="primary" :loading="savingView" @click="updateActiveView">更新当前</n-button>
            <n-button v-else type="primary" :loading="savingView" @click="saveViewAsNew">保存</n-button>
            <n-button v-if="activeViewId" :loading="savingView" @click="saveViewAsNew">另存为</n-button>
          </div>
        </template>
      </n-card>
    </n-modal>
  </div>
</template>

<style scoped>
.boardPage {
  color: #0f172a;
}

.sub {
  margin-top: 8px;
  font-size: 13px;
}

.filters {
  margin-top: 0;
  display: grid;
  grid-template-columns: 300px 240px 190px 1fr auto;
  gap: 10px;
  align-items: center;
}

.fSel {
  border-radius: 14px;
}

.viewBox {
  display: flex;
  align-items: center;
  gap: 10px;
}

.viewDot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: rgba(var(--dot), 0.85);
  box-shadow: 0 0 0 3px rgba(var(--dot), 0.10);
}

.fView {
  width: 100%;
}

.fMode {
  border-radius: 14px;
}

.fSearch {
  border-radius: 14px;
}

.fActions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.ck {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  user-select: none;
}

.lightPanel {
  margin-top: 12px;
  padding: 12px 12px;
  border-radius: 18px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  box-shadow: 0 14px 44px rgba(2, 6, 23, 0.08);
}

.boardWrap {
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-gutter: stable;
}

.board {
  display: flex;
  gap: 12px;
  min-width: 0;
  width: 100%;
}

.col {
  --col-accent-rgb: var(--accent-rgb);
  flex: 1 1 0;
  min-width: 0;
  border-radius: 18px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.56);
  min-height: 520px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.col.todo {
  --col-accent-rgb: var(--accent-rgb);
}

.col.doing {
  --col-accent-rgb: var(--accent2-rgb);
}

.col.done {
  --col-accent-rgb: var(--accent-rgb);
}

.colHead {
  padding: 12px 12px 10px;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  border-bottom: 1px solid rgba(var(--col-accent-rgb), 0.12);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.62) 0%, rgba(255, 255, 255, 0.50) 100%);
}

.colTitle {
  font-weight: 820;
  letter-spacing: 0.4px;
  font-size: 12px;
  color: rgba(15, 23, 42, 0.82);
}

.colRight {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.pill {
  display: inline-flex;
  align-items: center;
}

.pill.count {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 850;
  border: 1px solid rgba(var(--accent-rgb), 0.16);
  background: rgba(255, 255, 255, 0.72);
  color: rgba(15, 23, 42, 0.72);
}

.iconBtn {
  width: 30px;
  height: 28px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.65);
  border: 1px solid rgba(15, 23, 42, 0.10);
  color: rgba(15, 23, 42, 0.78);
  font-weight: 900;
  line-height: 1;
  transition: transform 120ms ease, box-shadow 120ms ease, border-color 120ms ease;
}

.iconBtn:hover {
  transform: translateY(-1px);
  border-color: rgba(var(--accent-rgb), 0.22);
  box-shadow: 0 12px 26px rgba(2, 6, 23, 0.10);
}

.colBody {
  padding: 10px;
  display: grid;
  gap: 10px;
  align-content: start;
  flex: 1;
  overflow: auto;
  max-height: 70vh;
}

.list {
  display: grid;
  gap: 10px;
  align-content: start;
}

.compose {
  border-radius: 16px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.78);
  padding: 10px;
  display: grid;
  gap: 10px;
}

.composeActions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.btnGhost {
  height: 34px;
  padding: 0 12px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.04);
  color: rgba(15, 23, 42, 0.74);
  font-weight: 850;
}

.btnPrimary {
  height: 34px;
  padding: 0 12px;
  border-radius: 12px;
  background: rgba(var(--accent-rgb), 0.92);
  color: rgba(255, 255, 255, 0.96);
  font-weight: 900;
}

.btnPrimary:disabled,
.btnGhost:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btnGhost.danger {
  background: rgba(239, 68, 68, 0.06);
  color: rgba(185, 28, 28, 0.92);
}

.formRow {
  display: grid;
  grid-template-columns: 86px 1fr;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}

.label {
  font-size: 12px;
}

.modalActions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.colorRow {
  display: flex;
  align-items: center;
  gap: 10px;
}

.colorDot {
  width: 18px;
  height: 18px;
  border-radius: 999px;
  background: rgba(var(--dot), 0.18);
  border: 1px solid rgba(var(--dot), 0.26);
  box-shadow: inset 0 0 0 3px rgba(255, 255, 255, 0.8);
  transition: transform 120ms ease, box-shadow 120ms ease, border-color 120ms ease;
}

.colorDot:hover {
  transform: translateY(-1px);
  border-color: rgba(var(--dot), 0.38);
  box-shadow: inset 0 0 0 3px rgba(255, 255, 255, 0.82), 0 10px 22px rgba(2, 6, 23, 0.10);
}

.colorDot.on {
  border-color: rgba(var(--dot), 0.58);
  box-shadow: inset 0 0 0 3px rgba(255, 255, 255, 0.82), 0 0 0 4px rgba(var(--dot), 0.10);
}

.emptyCol {
  border-radius: 16px;
  padding: 18px 12px;
  display: grid;
  gap: 10px;
  justify-items: start;
  background: rgba(255, 255, 255, 0.45);
  border: 1px dashed rgba(15, 23, 42, 0.10);
}

.skeletonList {
  display: grid;
  gap: 10px;
}

.skeletonCard {
  height: 70px;
  border-radius: 16px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: linear-gradient(
    90deg,
    rgba(15, 23, 42, 0.04) 0%,
    rgba(15, 23, 42, 0.07) 35%,
    rgba(15, 23, 42, 0.04) 70%
  );
  background-size: 200% 100%;
  animation: shimmer 1.1s ease-in-out infinite;
}

@keyframes shimmer {
  0% {
    background-position: 0% 0%;
  }
  100% {
    background-position: -200% 0%;
  }
}

.dropLine {
  height: 6px;
  border-radius: 999px;
  background: rgba(var(--accent-rgb), 0.22);
  box-shadow: 0 0 0 2px rgba(var(--accent-rgb), 0.10);
}

.card {
  border-radius: 16px;
  border: 1px solid rgba(15, 23, 42, 0.07);
  background: rgba(255, 255, 255, 0.86);
  padding: 10px 10px;
  cursor: pointer;
  transition: transform 160ms ease, box-shadow 160ms ease, border-color 160ms ease;
  box-shadow: inset 0 1px 0 rgba(var(--accent-rgb), 0.05);
}

.card.sub {
  background: rgba(248, 250, 252, 0.92);
  border-color: rgba(var(--accent-rgb), 0.16);
  box-shadow: inset 3px 0 0 rgba(var(--accent-rgb), 0.26), inset 0 1px 0 rgba(var(--accent-rgb), 0.05);
}

.card:hover {
  transform: translateY(-1px);
  box-shadow: 0 14px 40px rgba(2, 6, 23, 0.10);
  border-color: rgba(var(--accent-rgb), 0.22);
}

.cardTop {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.grip {
  width: 14px;
  height: 18px;
  margin-top: 2px;
  border-radius: 10px;
  opacity: 0;
  background: repeating-linear-gradient(
    to bottom,
    rgba(15, 23, 42, 0.16) 0,
    rgba(15, 23, 42, 0.16) 2px,
    transparent 2px,
    transparent 6px
  );
  transition: opacity 140ms ease;
}

.card:hover .grip {
  opacity: 0.85;
}

.cardTitle {
  font-size: 13.5px;
  font-weight: 760;
  color: rgba(15, 23, 42, 0.92);
  letter-spacing: -0.1px;
  line-height: 1.4;
  word-break: break-word;
}

.meta {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chip {
  padding: 3px 8px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.10);
  background: rgba(255, 255, 255, 0.75);
  font-size: 12px;
  font-weight: 650;
}

.chip.prio {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.chip.prio::before {
  content: '';
  width: 5px;
  height: 5px;
  border-radius: 999px;
  background: currentColor;
  flex: none;
}
.chip.prio-high {
  padding: 3px 9px 3px 7px;
  border-color: rgba(var(--accent-rgb), 0.22);
  background: rgba(var(--accent-rgb), 0.10);
  color: rgba(var(--accent-rgb), 1);
  font-weight: 800;
  box-shadow: inset 0 0 0 1px rgba(var(--accent-rgb), 0.05);
}
.chip.prio-medium {
  padding: 3px 9px 3px 7px;
  border-color: rgba(15, 23, 42, 0.10);
  background: rgba(15, 23, 42, 0.04);
  color: rgba(71, 85, 105, 0.95);
  font-weight: 700;
}
.chip.prio-low {
  padding: 3px 9px 3px 7px;
  border-color: rgba(15, 23, 42, 0.08);
  background: transparent;
  color: rgba(100, 116, 139, 0.85);
  font-weight: 600;
}

.chip.parent {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: rgba(15, 23, 42, 0.72);
  background: rgba(15, 23, 42, 0.04);
}

.chip.prog {
  border-color: rgba(var(--accent-rgb), 0.22);
  background: rgba(var(--accent-rgb), 0.10);
  color: rgba(15, 23, 42, 0.78);
  font-weight: 750;
}

.cardMove-move {
  transition: transform 160ms ease;
}

@media (max-width: 980px) {
  .filters {
    grid-template-columns: 1fr;
  }
  .col {
    min-height: 360px;
  }
  .board {
    min-width: 980px;
  }
  .colBody {
    max-height: none;
  }
}

/* ─── 数据窗（融合统计） ─── */
.statBar {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 18px;
  border-radius: 18px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.72) 0%, rgba(255, 255, 255, 0.52) 100%);
  box-shadow: 0 10px 30px rgba(2, 6, 23, 0.05);
}

.kpis {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  row-gap: 8px;
}

.kpi {
  min-width: 84px;
  padding: 0 18px;
  border-left: 1px solid rgba(15, 23, 42, 0.06);
}
.kpi:first-child {
  border-left: none;
  padding-left: 0;
}

.kpiNum {
  font-size: 22px;
  font-weight: 800;
  line-height: 1.15;
  letter-spacing: -0.01em;
  color: rgba(15, 23, 42, 0.88);
  font-variant-numeric: tabular-nums;
}
.kpiNum.warn {
  color: rgba(220, 60, 60, 0.92);
}

.kpiLabel {
  margin-top: 2px;
  font-size: 11px;
  font-weight: 600;
  color: rgba(100, 116, 139, 0.9);
}

.rate {
  min-width: 150px;
  margin-left: 18px;
  padding-left: 18px;
  border-left: 1px solid rgba(15, 23, 42, 0.06);
}
.rateTop {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 6px;
}
.rateLabel {
  font-size: 11px;
  font-weight: 600;
  color: rgba(100, 116, 139, 0.9);
}
.rateVal {
  font-size: 12px;
  font-weight: 800;
  color: rgba(var(--accent-rgb), 0.95);
  font-variant-numeric: tabular-nums;
}
.rateTrack {
  height: 6px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
  overflow: hidden;
}
.rateFill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(var(--accent-rgb), 0.72), rgba(var(--accent-rgb), 1));
  transition: width 360ms ease;
}

.winToggles {
  display: flex;
  align-items: center;
  gap: 8px;
}
.winCap {
  font-size: 11px;
  font-weight: 600;
  color: rgba(100, 116, 139, 0.85);
  margin-right: 2px;
}
.winBtn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.10);
  background: rgba(255, 255, 255, 0.82);
  color: rgba(15, 23, 42, 0.62);
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
  transition: all 160ms ease;
}
.winBtn:hover {
  border-color: rgba(var(--accent-rgb), 0.28);
  color: rgba(15, 23, 42, 0.82);
}
.winBtn.on {
  border-color: rgba(var(--accent-rgb), 0.28);
  background: rgba(var(--accent-rgb), 0.10);
  color: rgba(var(--accent-rgb), 1);
}

.winGrid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 12px;
  align-items: start;
}

.winCard {
  border-radius: 18px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.58);
  box-shadow: 0 10px 30px rgba(2, 6, 23, 0.05);
  padding: 14px 16px 16px;
}

.winHead {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.winTitle {
  font-size: 13px;
  font-weight: 800;
  color: rgba(15, 23, 42, 0.86);
  border-left: 3px solid rgba(var(--accent-rgb), 0.85);
  padding-left: 9px;
  line-height: 1.2;
}
.winSub {
  font-size: 11px;
}
.winClose {
  margin-left: auto;
  width: 22px;
  height: 22px;
  border-radius: 8px;
  border: none;
  background: transparent;
  color: rgba(100, 116, 139, 0.8);
  font-size: 15px;
  line-height: 1;
  cursor: pointer;
  transition: all 160ms ease;
}
.winClose:hover {
  background: rgba(15, 23, 42, 0.06);
  color: rgba(15, 23, 42, 0.8);
}

.winEmpty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 26px 12px 22px;
  text-align: center;
}
.winEmpty.ok {
  flex-direction: row;
  justify-content: center;
  gap: 8px;
  padding: 30px 12px;
}
.winEmpty p {
  margin: 0;
  font-size: 12.5px;
  font-weight: 600;
  color: rgba(100, 116, 139, 0.9);
}
.okDot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: rgba(16, 163, 127, 0.85);
  box-shadow: 0 0 0 4px rgba(16, 163, 127, 0.10);
  margin-top: 1px;
}

.memList {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.memRow {
  display: grid;
  grid-template-columns: 26px 96px 1fr 56px;
  gap: 10px;
  align-items: center;
}
.memAvatar {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: rgba(15, 23, 42, 0.06);
  color: rgba(15, 23, 42, 0.62);
  font-size: 11px;
  font-weight: 700;
}
.memName {
  font-size: 12px;
  font-weight: 650;
  color: rgba(15, 23, 42, 0.82);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.memBar {
  height: 6px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
  overflow: hidden;
}
.memBar i {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(var(--accent-rgb), 0.62), rgba(var(--accent-rgb), 0.95));
  transition: width 360ms ease;
}
.memNum {
  font-size: 11.5px;
  font-weight: 750;
  color: rgba(100, 116, 139, 0.95);
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.riskList {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.riskItem {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 160ms ease;
}
.riskItem:hover {
  background: rgba(15, 23, 42, 0.04);
}
.riskDot {
  flex: none;
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: rgba(var(--accent-rgb), 0.6);
}
.riskDot.warn {
  background: rgba(220, 60, 60, 0.75);
}
.riskTitle {
  flex: 1;
  font-size: 12.5px;
  font-weight: 600;
  color: rgba(15, 23, 42, 0.82);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.riskDays {
  flex: none;
  font-size: 11px;
  padding: 2px 8px;
  border-color: rgba(220, 60, 60, 0.18);
  background: rgba(220, 60, 60, 0.06);
  color: rgba(185, 28, 28, 0.85);
}
.riskHigh {
  flex: none;
  font-size: 11px;
  padding: 2px 8px;
  border-color: rgba(var(--accent-rgb), 0.20);
  background: rgba(var(--accent-rgb), 0.07);
  color: rgba(var(--accent-rgb), 0.95);
}

@media (max-width: 900px) {
  .statBar {
    flex-direction: column;
    align-items: stretch;
  }
  .rate {
    margin-left: 0;
    padding-left: 0;
    border-left: none;
  }
  .winGrid {
    grid-template-columns: 1fr;
  }
}
</style>
