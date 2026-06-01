<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NCard, NCheckbox, NInput, NModal, NSelect, NSpin, useDialog, useMessage } from 'naive-ui'
import { taskApi, type Task, type TaskBoardView, type TaskStatus } from '../api/task'
import { useAuthStore } from '../stores/auth'
import { useProjectStore } from '../stores/project'
import { useRealtimeStore } from '../stores/realtime'

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
const boardWrapRef = ref<HTMLElement | null>(null)
const colBodyRef = reactive<Record<TaskStatus, HTMLElement | null>>({ TODO: null, DOING: null, DONE: null })

const projectOptions = computed(() => ps.visibleProjects.map((p) => ({ label: p.name || `项目 #${p.id}`, value: p.id })))

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
  { key: 'teal', rgb: '20, 184, 166' },
  { key: 'cyan', rgb: '14, 165, 233' },
  { key: 'amber', rgb: '245, 158, 11' },
  { key: 'rose', rgb: '244, 63, 94' },
  { key: 'emerald', rgb: '16, 185, 129' },
  { key: 'slate', rgb: '100, 116, 139' }
]

const activeView = computed(() => (activeViewId.value ? viewItems.value.find((x) => x.id === activeViewId.value) : null))
const activeViewColor = computed(() => String(activeView.value?.color || '').trim())

function myId() {
  return Number(auth.me?.id || 0)
}

function dueMeta(t: Task) {
  const ms = t?.dueTime ? Date.parse(t.dueTime) : NaN
  if (!Number.isFinite(ms)) return null
  const now = Date.now()
  if (ms >= now) return { level: 'future' as const, sort: ms }
  return { level: 'overdue' as const, sort: ms }
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

function openTask(t: Task) {
  if (!t || !t.id || !t.projectId) return
  router.push({ name: 'task-detail', params: { projectId: t.projectId, taskId: t.id } })
}

const dragging = ref<{ taskId: number; from: TaskStatus } | null>(null)
const over = ref<{ to: TaskStatus; index: number } | null>(null)

function onDragStart(t: Task) {
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
  quickCreate.status = status
  quickCreate.title = ''
}

function closeQuickCreate() {
  quickCreate.status = null
  quickCreate.title = ''
}

async function submitQuickCreate() {
  if (!projectId.value || !quickCreate.status) return
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
  viewName.value = String(activeView.value?.name || '').trim()
  viewColor.value = activeViewColor.value || 'teal'
  viewModalOpen.value = true
}

async function saveViewAsNew() {
  if (!projectId.value) return
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
  <div class="page lightPage">
    <div class="head">
      <div class="left">
        <h1 class="h1">看板</h1>
      </div>
      <div class="right">
        <n-button tertiary @click="router.push({ name: 'workspace' })">工作台</n-button>
        <n-button tertiary @click="load">刷新</n-button>
      </div>
    </div>

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
        <button class="btnGhost" type="button" @click="openSaveView">保存视图</button>
        <button v-if="activeViewId" class="btnGhost danger" type="button" @click="deleteActiveView">删除</button>
      </div>
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
                <button class="iconBtn" type="button" @click="openQuickCreate('TODO')">+</button>
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
                <button class="btnGhost" type="button" @click="openQuickCreate('TODO')">添加任务</button>
              </div>
              <transition-group v-else name="cardMove" tag="div" class="list">
                <template v-for="ri in renderItems('TODO')" :key="ri.key">
                  <div v-if="ri.kind === 'line'" class="dropLine" />
                  <div
                    v-else
                    class="card"
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
                      <span v-if="ri.task.priority" class="chip">{{ ri.task.priority }}</span>
                      <span v-if="ri.task.assignee" class="chip muted">{{ ri.task.assignee }}</span>
                      <span v-if="ri.task.dueTime" class="chip muted">Due {{ timeShort(ri.task.dueTime) }}</span>
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
                <button class="iconBtn" type="button" @click="openQuickCreate('DOING')">+</button>
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
                <button class="btnGhost" type="button" @click="openQuickCreate('DOING')">添加任务</button>
              </div>
              <transition-group v-else name="cardMove" tag="div" class="list">
                <template v-for="ri in renderItems('DOING')" :key="ri.key">
                  <div v-if="ri.kind === 'line'" class="dropLine" />
                  <div
                    v-else
                    class="card"
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
                      <span v-if="ri.task.priority" class="chip">{{ ri.task.priority }}</span>
                      <span v-if="ri.task.assignee" class="chip muted">{{ ri.task.assignee }}</span>
                      <span v-if="ri.task.dueTime" class="chip muted">Due {{ timeShort(ri.task.dueTime) }}</span>
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
                <button class="iconBtn" type="button" @click="openQuickCreate('DONE')">+</button>
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
                <button class="btnGhost" type="button" @click="openQuickCreate('DONE')">添加任务</button>
              </div>
              <transition-group v-else name="cardMove" tag="div" class="list">
                <template v-for="ri in renderItems('DONE')" :key="ri.key">
                  <div v-if="ri.kind === 'line'" class="dropLine" />
                  <div
                    v-else
                    class="card"
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
                      <span v-if="ri.task.priority" class="chip">{{ ri.task.priority }}</span>
                      <span v-if="ri.task.assignee" class="chip muted">{{ ri.task.assignee }}</span>
                      <span v-if="ri.task.dueTime" class="chip muted">Due {{ timeShort(ri.task.dueTime) }}</span>
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
.lightPage {
  background: #ffffff;
  color: #0f172a;
}

.sub {
  margin-top: 8px;
  font-size: 13px;
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

.filters {
  margin-top: 12px;
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
  min-width: 1040px;
}

.col {
  --accent-rgb: 20, 184, 166;
  border-radius: 18px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(15, 23, 42, 0.02);
  min-height: 520px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex: 0 0 340px;
}

.col.todo {
  --accent-rgb: 20, 184, 166;
  background: rgba(20, 184, 166, 0.045);
}

.col.doing {
  --accent-rgb: 6, 182, 212;
  background: rgba(6, 182, 212, 0.045);
}

.col.done {
  --accent-rgb: 16, 185, 129;
  background: rgba(16, 185, 129, 0.045);
}

.colHead {
  padding: 12px 12px 10px;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  border-bottom: 1px solid rgba(var(--accent-rgb), 0.12);
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
  background: rgba(20, 184, 166, 0.92);
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
</style>
