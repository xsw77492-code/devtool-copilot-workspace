<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NCard, NCheckbox, NDatePicker, NInput, NModal, NSelect, NSpin, useDialog, useMessage } from 'naive-ui'
import { dashboardApi, type DashboardOverviewResponse } from '../api/dashboard'
import { projectCollabApi, type ProjectActivityItem, type ProjectMemberItem, type ProjectMemberRole } from '../api/projectCollab'
import { taskApi, type Task, type TaskBatchStatusResult, type TaskBoardView, type TaskStatus, type TaskTemplate } from '../api/task'
import { milestoneApi, type Milestone } from '../api/milestone'
import { useAuthStore } from '../stores/auth'
import { useTaskStore } from '../stores/task'

const props = defineProps<{ projectId: number; archived?: boolean }>()
const emit = defineEmits<{ (e: 'open-task', taskId: number): void }>()

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const taskStore = useTaskStore()

const loading = computed(() => taskStore.loading)
const members = ref<ProjectMemberItem[]>([])
const myRole = ref<ProjectMemberRole | null>(null)
const participatedIds = ref<Set<number>>(new Set())

type FilterMode = 'all' | 'mine' | 'unassigned' | 'participated'

const mode = ref<FilterMode>('all')
const q = ref('')
const overdueOnly = ref(false)
const hierarchyView = ref(false)
const sortKey = ref<'recent' | 'due' | 'priority'>('recent')

const expandedParents = ref<Set<number>>(new Set())

const TREE_STATE_KEY = computed(() => `dtc_task_tree_state_v1_${props.projectId}`)

function readTreeState() {
  try {
    const raw = localStorage.getItem(TREE_STATE_KEY.value)
    if (!raw) return null
    const obj = JSON.parse(raw) as any
    const expanded = Array.isArray(obj?.expanded) ? obj.expanded.map((x: any) => Number(x)).filter((x: any) => Number.isFinite(x) && x > 0) : []
    const hv = obj?.hv === 1 || obj?.hv === '1' || obj?.hv === true
    return { expanded, hv }
  } catch {
    return null
  }
}

function writeTreeState() {
  try {
    localStorage.setItem(
      TREE_STATE_KEY.value,
      JSON.stringify({
        expanded: Array.from(expandedParents.value),
        hv: hierarchyView.value ? 1 : 0
      })
    )
  } catch {
  }
}

const milestones = ref<Milestone[]>([])
const milestoneOptions = computed(() => {
  const opts = milestones.value.map((m) => ({ label: m.name, value: m.id }))
  return [{ label: '无里程碑', value: 0 }, ...opts]
})

const retroLoading = ref(false)
const retro = ref<DashboardOverviewResponse | null>(null)

const retroNew = computed(() => Number(retro.value?.tasksCreatedThisWeek || 0))
const retroDone = computed(() => (retro.value?.throughputTrend || []).reduce((s, x) => s + Number(x.count || 0), 0))
const retroOverdue = computed(() => (retro.value?.riskTasks || []).length)
const retroSpark = computed(() => {
  const pts = retro.value?.throughputTrend || []
  const maxV = Math.max(1, ...pts.map((x) => Number(x.count || 0)))
  return pts.slice(0, 14).map((x) => {
    const v = Number(x.count || 0)
    const h = Math.max(2, Math.round((v / maxV) * 18))
    return { day: String(x.day || '').slice(5), v, h }
  })
})

const viewItems = ref<TaskBoardView[]>([])
const activeViewId = ref<number | null>(null)
const applyingView = ref(false)
const viewModalOpen = ref(false)
const viewName = ref('')
const viewColor = ref<string>('teal')
const savingView = ref(false)

const templates = ref<TaskTemplate[]>([])
const tplId = ref<number | null>(null)
const tplModalOpen = ref(false)
const tplName = ref('')
const tplPayload = ref<string>('')
const savingTpl = ref(false)

const newTitle = ref('')
const quickPriority = ref<string | null>(null)
const quickAssigneeId = ref<number | null>(null)
const quickDueAt = ref<number | null>(null)
const quickMilestoneId = ref<number | null>(null)
const creating = ref(false)
const advancedOpen = ref(false)

const activityOpen = ref(false)
const activityLoading = ref(false)
const activities = ref<ProjectActivityItem[]>([])
const activityDeletingId = ref<number | null>(null)
const activityClearing = ref(false)

const canManageActivity = computed(() => myRole.value === 'OWNER')

function actTitle(a: ProjectActivityItem) {
  const actor = a.actorUsername || 'System'
  const t = String(a.type || '')
  if (t === 'TASK_CREATED') return `${actor} 创建了任务`
  if (t === 'TASK_DONE') return `${actor} 完成了任务`
  if (t === 'AI_GENERATE_TASK') return `${actor} 通过 AI 生成了任务`
  if (t === 'TASK_COMMENT_CREATED') return `${actor} 评论了任务`
  if (t === 'MEMBER_JOINED') return `${actor} 加入了项目`
  if (t === 'MEMBER_REMOVED') return `${actor} 移除了成员`
  if (t === 'MEMBER_INVITED') return `${actor} 邀请了成员`
  if (t === 'MEMBER_INVITE_CANCELED') return `${actor} 取消了邀请`
  if (t === 'MEMBER_INVITE_REISSUED') return `${actor} 更新了邀请链接`
  if (t === 'MEMBER_ROLE_CHANGED') return `${actor} 调整了成员角色`
  if (t === 'MEMBER_DISABLED') return `${actor} 禁用了成员`
  if (t === 'MEMBER_ENABLED') return `${actor} 启用了成员`
  if (t === 'MEMBER_OWNER_TRANSFERRED') return `${actor} 转让了所有权`
  if (t === 'MEMBERS_EXPORT_CSV') return `${actor} 导出了成员列表`
  if (t === 'WORKSPACE_EXPORT_CSV') return `${actor} 导出了任务`
  if (t === 'WORKSPACE_WEEKLY_REPORT') return `${actor} 生成了周报`
  return `${actor} · ${t}`
}

function actDetail(a: ProjectActivityItem) {
  const raw = String(a.detail || '').trim()
  if (!raw) return ''
  let obj: any = null
  if (raw.startsWith('{') && raw.endsWith('}')) {
    try {
      obj = JSON.parse(raw)
    } catch {
      obj = null
    }
  }
  const email = obj && typeof obj.email === 'string' ? obj.email : null
  const role = obj && typeof obj.role === 'string' ? obj.role : null
  const inviteId = obj && (typeof obj.inviteId === 'number' || typeof obj.inviteId === 'string') ? obj.inviteId : null
  const userId = obj && (typeof obj.userId === 'number' || typeof obj.userId === 'string') ? obj.userId : null
  const newOwnerUserId =
    obj && (typeof obj.newOwnerUserId === 'number' || typeof obj.newOwnerUserId === 'string') ? obj.newOwnerUserId : null
  const start = obj && typeof obj.start === 'string' ? obj.start : null
  const end = obj && typeof obj.end === 'string' ? obj.end : null

  const t = String(a.type || '')
  if (t === 'MEMBER_INVITED') return `${email || ''}${role ? ` · ${role}` : ''}`.trim()
  if (t === 'MEMBER_INVITE_CANCELED') return `${email || ''}${inviteId ? ` · inviteId=${inviteId}` : ''}`.trim()
  if (t === 'MEMBER_INVITE_REISSUED') return `${email || ''}${inviteId ? ` · inviteId=${inviteId}` : ''}`.trim()
  if (t === 'MEMBER_ROLE_CHANGED') return `${userId ? `userId=${userId}` : ''}${role ? ` · ${role}` : ''}`.trim()
  if (t === 'MEMBER_DISABLED' || t === 'MEMBER_ENABLED') return userId ? `userId=${userId}` : ''
  if (t === 'MEMBER_OWNER_TRANSFERRED') return newOwnerUserId ? `newOwnerUserId=${newOwnerUserId}` : ''
  if (t === 'WORKSPACE_WEEKLY_REPORT') return start && end ? `${start} ~ ${end}` : ''
  return raw
}

const selected = ref<Set<number>>(new Set())
const bulkAssigneeId = ref<number | null>(null)
const bulkDueAt = ref<number | null>(null)
const bulkPriority = ref<string | null>(null)
const bulkStatus = ref<TaskStatus | null>(null)
const bulkClearDue = ref(false)
const bulkWorking = ref(false)

const draggingId = ref<number | null>(null)
const dropCol = ref<TaskStatus | null>(null)

const WIP_LIMIT = 8

const canEdit = computed(() => myRole.value !== 'VIEWER' && Number(props.archived || 0) !== 1)
const isViewer = computed(() => myRole.value === 'VIEWER' || Number(props.archived || 0) === 1)

const memberOptions = computed(() => {
  const opts = members.value
    .slice()
    .sort((a, b) => String(a.username || '').localeCompare(String(b.username || '')))
    .map((m) => ({ label: m.username, value: m.userId }))
  return [{ label: '未分配', value: 0 }, ...opts]
})

const priorityOptions = [
  { label: 'HIGH', value: 'HIGH' },
  { label: 'MEDIUM', value: 'MEDIUM' },
  { label: 'LOW', value: 'LOW' }
]

const statusOptions = [
  { label: 'TODO', value: 'TODO' },
  { label: 'DOING', value: 'DOING' },
  { label: 'DONE', value: 'DONE' }
]

function applyQuery() {
  const m = String(route.query.m || '')
  if (m === 'mine' || m === 'unassigned' || m === 'participated' || m === 'all') mode.value = m
  q.value = String(route.query.q || '')
  overdueOnly.value = String(route.query.overdue || '') === '1'
  if (route.query.hv === undefined) {
    if (String(route.query.sub || '') === '1') {
      hierarchyView.value = true
    }
    const st = readTreeState()
    if (String(route.query.sub || '') !== '1') hierarchyView.value = !!st?.hv
  } else {
    hierarchyView.value = String(route.query.hv || '') === '1'
  }
  const s = String(route.query.sort || '')
  if (s === 'due' || s === 'priority' || s === 'recent') sortKey.value = s
  const vid = Number(route.query.viewId || 0)
  activeViewId.value = vid && Number.isFinite(vid) ? vid : null
}

function syncQuery() {
  const query: any = { ...route.query }
  query.m = mode.value !== 'all' ? mode.value : undefined
  query.q = q.value.trim() ? q.value.trim() : undefined
  query.overdue = overdueOnly.value ? '1' : undefined
  query.hv = hierarchyView.value ? '1' : undefined
  query.sort = sortKey.value !== 'recent' ? sortKey.value : undefined
  query.viewId = activeViewId.value ? String(activeViewId.value) : undefined
  router.replace({ query })
}

watch(
  () => route.fullPath,
  () => {
    applyQuery()
  }
)

watch([mode, q, overdueOnly, hierarchyView, sortKey], () => {
  if (!applyingView.value) activeViewId.value = null
  syncQuery()
})

watch(
  () => [hierarchyView.value, expandedParents.value] as const,
  () => {
    writeTreeState()
  },
  { deep: true }
)

async function loadMembers() {
  try {
    const res = await projectCollabApi.members(props.projectId)
    members.value = res.members || []
    myRole.value = res.myRole || null
  } catch {
    members.value = []
    myRole.value = null
  }
}

async function loadParticipated() {
  try {
    const ids = await taskApi.participatedIds(props.projectId)
    participatedIds.value = new Set(ids || [])
  } catch {
    participatedIds.value = new Set()
  }
}

async function loadMilestones() {
  try {
    milestones.value = await milestoneApi.list(props.projectId, false)
  } catch {
    milestones.value = []
  }
}

async function loadViews() {
  try {
    viewItems.value = await taskApi.listViews(props.projectId)
  } catch {
    viewItems.value = []
  }
}

async function loadTemplates() {
  try {
    templates.value = await taskApi.listTemplates(props.projectId)
  } catch {
    templates.value = []
  }
  if (!templates.value.length) {
    const builtIn: TaskTemplate[] = [
      {
        id: -1,
        userId: 0,
        name: '需求（PRD）',
        payloadJson: JSON.stringify({
          priority: 'MEDIUM',
          tags: 'feature',
          description: '背景\\n- \\n\\n目标\\n- \\n\\n方案\\n- \\n\\n范围\\n- In: \\n- Out: ',
          acceptanceCriteria: '验收标准\\n- [ ] \\n- [ ] '
        })
      },
      {
        id: -2,
        userId: 0,
        name: 'Bug 修复',
        payloadJson: JSON.stringify({
          priority: 'HIGH',
          tags: 'bug',
          description: '现象\\n- \\n\\n复现步骤\\n1. \\n2. \\n\\n期望\\n- \\n\\n实际\\n- ',
          acceptanceCriteria: '验收标准\\n- [ ] 已修复\\n- [ ] 已回归\\n- [ ] 无副作用'
        })
      },
      {
        id: -3,
        userId: 0,
        name: '技术任务',
        payloadJson: JSON.stringify({
          priority: 'MEDIUM',
          tags: 'tech',
          description: '动机\\n- \\n\\n设计\\n- \\n\\n风险\\n- ',
          acceptanceCriteria: '验收标准\\n- [ ] 编译通过\\n- [ ] 覆盖关键路径\\n- [ ] 文档/说明完善'
        })
      }
    ]
    templates.value = builtIn
  }
}

function parseTplPayload(id: number | null) {
  if (!id) return null
  const t = templates.value.find((x) => x.id === id)
  if (!t) return null
  try {
    return JSON.parse(t.payloadJson) as any
  } catch {
    return null
  }
}

async function createTask() {
  if (!canEdit.value) {
    message.warning('只读权限，无法创建任务')
    return
  }
  const title = newTitle.value.trim()
  if (!title) return
  creating.value = true
  try {
    const tpl = parseTplPayload(tplId.value)
    await taskStore.create(props.projectId, title, {
      assigneeId: quickAssigneeId.value || undefined,
      dueAt: quickDueAt.value || undefined,
      milestoneId: quickMilestoneId.value && quickMilestoneId.value > 0 ? quickMilestoneId.value : undefined,
      priority: (tpl?.priority || quickPriority.value) ?? undefined,
      tags: tpl?.tags ?? undefined,
      description: tpl?.description ?? undefined,
      acceptanceCriteria: tpl?.acceptanceCriteria ?? undefined
    })
    newTitle.value = ''
    quickPriority.value = null
    quickAssigneeId.value = null
    quickDueAt.value = null
    quickMilestoneId.value = null
    if (advancedOpen.value) advancedOpen.value = false
  } catch (e: any) {
    message.error(e?.message || '创建任务失败')
  } finally {
    creating.value = false
  }
}

function myId() {
  return Number(auth.me?.id || 0)
}

function toMs(dt: string | undefined) {
  if (!dt) return null
  const ms = Date.parse(dt)
  return Number.isFinite(ms) ? ms : null
}

function dueMeta(t: Task) {
  const ms = toMs(t.dueTime)
  if (!ms) return null
  const now = Date.now()
  const dayMs = 24 * 60 * 60 * 1000
  const startToday = new Date()
  startToday.setHours(0, 0, 0, 0)
  const base = startToday.getTime()
  const diffDay = Math.floor((ms - base) / dayMs)
  const overdue = ms < now
  if (overdue) {
    const od = Math.max(1, Math.ceil((now - ms) / dayMs))
    return { text: `逾期 ${od} 天`, level: 'overdue' as const, sort: ms }
  }
  if (diffDay === 0) return { text: '今天', level: 'today' as const, sort: ms }
  if (diffDay === 1) return { text: '明天', level: 'tomorrow' as const, sort: ms }
  const d = new Date(ms)
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return { text: `${mm}-${dd}`, level: 'future' as const, sort: ms }
}

function priRank(p?: string) {
  const v = String(p || '').toUpperCase()
  if (v === 'HIGH') return 3
  if (v === 'MEDIUM') return 2
  if (v === 'LOW') return 1
  return 0
}

const filtered = computed(() => {
  const text = q.value.trim().toLowerCase()
  const uid = myId()
  let list = taskStore.tasks.slice()

  if (mode.value === 'mine') list = list.filter((t) => Number(t.assigneeId || 0) === uid)
  if (mode.value === 'unassigned') list = list.filter((t) => !t.assigneeId)
  if (mode.value === 'participated') list = list.filter((t) => participatedIds.value.has(t.id) || Number(t.assigneeId || 0) === uid)

  if (text) list = list.filter((t) => String(t.title || '').toLowerCase().includes(text))
  if (overdueOnly.value) list = list.filter((t) => (dueMeta(t)?.level || '') === 'overdue')
  if (!hierarchyView.value) list = list.filter((t) => !t.parentTaskId)

  if (sortKey.value === 'due') {
    list.sort((a, b) => {
      const am = dueMeta(a)?.sort || Number.MAX_SAFE_INTEGER
      const bm = dueMeta(b)?.sort || Number.MAX_SAFE_INTEGER
      if (am !== bm) return am - bm
      return b.id - a.id
    })
  } else if (sortKey.value === 'priority') {
    list.sort((a, b) => {
      const ar = priRank(a.priority)
      const br = priRank(b.priority)
      if (ar !== br) return br - ar
      return b.id - a.id
    })
  } else {
    list.sort((a, b) => b.id - a.id)
  }

  return list
})

const grouped = computed(() => {
  const todo: Task[] = []
  const doing: Task[] = []
  const done: Task[] = []
  for (const t of filtered.value) {
    if (t.status === 'DOING') doing.push(t)
    else if (t.status === 'DONE') done.push(t)
    else todo.push(t)
  }
  return { todo, doing, done }
})

const inlineSubtaskDraft = ref<Record<number, string>>({})
const inlineSubtaskCreatingParentId = ref<number | null>(null)

const manageOpen = ref(false)
const manageParentId = ref<number | null>(null)
const managePick = ref<Set<number>>(new Set())
const manageWorking = ref(false)
const manageResult = ref<TaskBatchStatusResult | null>(null)

function setInlineDraft(pid: number, v: string) {
  inlineSubtaskDraft.value = { ...inlineSubtaskDraft.value, [pid]: v }
}

function getInlineDraft(pid: number) {
  return String(inlineSubtaskDraft.value[pid] || '')
}

async function createInlineSubtask(parent: Task) {
  if (!canEdit.value) return
  const pid = Number(parent.id || 0)
  if (!pid) return
  const title = getInlineDraft(pid).trim()
  if (!title) {
    message.warning('请输入子任务标题')
    return
  }
  inlineSubtaskCreatingParentId.value = pid
  try {
    await taskApi.create(props.projectId, title, {
      parentTaskId: pid,
      milestoneId: (parent as any)?.milestoneId ?? undefined,
      source: 'UI'
    })
    setInlineDraft(pid, '')
    expandedParents.value = new Set([...Array.from(expandedParents.value), pid])
    await taskStore.loadByProject(props.projectId)
    message.success('已新增子任务')
  } catch (e: any) {
    message.error(e?.message || '创建失败')
  } finally {
    inlineSubtaskCreatingParentId.value = null
  }
}

const taskTitleById = computed(() => {
  const map = new Map<number, string>()
  for (const t of taskStore.tasks) {
    if (!t || !t.id) continue
    map.set(t.id, String(t.title || `#${t.id}`))
  }
  return map
})

function parentTitle(pid: number) {
  return taskTitleById.value.get(pid) || `#${pid}`
}

const manageParent = computed(() => {
  const pid = Number(manageParentId.value || 0)
  if (!pid) return null
  return taskStore.tasks.find((x) => x.id === pid) || null
})

const manageChildren = computed(() => {
  const pid = Number(manageParentId.value || 0)
  if (!pid) return []
  const list = taskStore.tasks.filter((x) => Number(x.parentTaskId || 0) === pid)
  list.sort((a, b) => (statusRank(b.status) - statusRank(a.status)) || (b.id - a.id))
  return list
})

const manageDone = computed(() => manageChildren.value.filter((x) => String(x.status || '') === 'DONE').length)
const manageTotal = computed(() => manageChildren.value.length)
const managePct = computed(() => (manageTotal.value ? Math.round((manageDone.value / manageTotal.value) * 100) : 0))

function openParentManage(parent: Task) {
  if (!canEdit.value) return
  manageParentId.value = parent?.id || null
  manageOpen.value = true
  manageResult.value = null
  const s = new Set<number>()
  for (const c of manageChildren.value) {
    if (String(c.status || '') !== 'DONE') s.add(c.id)
  }
  managePick.value = s
}

function toggleManagePick(id: number, v: boolean) {
  const s = new Set(managePick.value)
  if (v) s.add(id)
  else s.delete(id)
  managePick.value = s
}

function pickAllIncomplete() {
  const s = new Set<number>()
  for (const c of manageChildren.value) {
    if (String(c.status || '') !== 'DONE') s.add(c.id)
  }
  managePick.value = s
}

function clearManagePick() {
  managePick.value = new Set()
}

async function doCompleteSubtaskIds(ids: number[], forceDone?: boolean) {
  manageWorking.value = true
  try {
    const res = await taskApi.batchUpdateStatusDetail(ids, 'DONE', { forceDone: forceDone ?? undefined })
    manageResult.value = res
    if (!res.failed?.length) {
      message.success(`已完成 ${res.ok} 项`)
    } else {
      message.warning(`已完成 ${res.ok} 项，${res.failed.length} 项未完成`)
    }

    const blocked = (res.failed || []).filter((x) => Number(x.code || 0) === 409)
    if (!forceDone && blocked.length) {
      dialog.warning({
        title: 'DONE 门槛拦截',
        content: `有 ${blocked.length} 个子任务未满足验收清单，是否强制完成？`,
        positiveText: '强制完成',
        negativeText: '取消',
        onPositiveClick: async () => {
          await doCompleteSubtaskIds(blocked.map((x) => x.taskId), true)
        }
      })
    }

    await taskStore.loadByProject(props.projectId)
    await loadParticipated()
    pickAllIncomplete()
  } catch (e: any) {
    message.error(e?.message || '批量完成失败')
  } finally {
    manageWorking.value = false
  }
}

async function completePickedSubtasks() {
  if (!canEdit.value) return
  const ids = Array.from(managePick.value).filter((x) => Number.isFinite(x) && x > 0)
  if (!ids.length) {
    message.warning('请选择要完成的子任务')
    return
  }
  await doCompleteSubtaskIds(ids, false)
}

function statusRank(s?: string) {
  const v = String(s || '').toUpperCase()
  if (v === 'DONE') return 2
  if (v === 'DOING') return 1
  return 0
}

const hierarchyNodes = computed(() => {
  if (!hierarchyView.value) return []
  const all = taskStore.tasks.slice()
  const text = q.value.trim().toLowerCase()
  const uid = myId()
  const filtering = !!text || overdueOnly.value || mode.value !== 'all'

  function matchOne(t: Task) {
    if (mode.value === 'mine' && Number(t.assigneeId || 0) !== uid) return false
    if (mode.value === 'unassigned' && !!t.assigneeId) return false
    if (mode.value === 'participated' && !(participatedIds.value.has(t.id) || Number(t.assigneeId || 0) === uid)) return false
    if (text && !String(t.title || '').toLowerCase().includes(text)) return false
    if (overdueOnly.value && (dueMeta(t)?.level || '') !== 'overdue') return false
    return true
  }

  const childrenByParent = new Map<number, Task[]>()
  for (const t of all) {
    const pid = Number(t.parentTaskId || 0)
    if (!pid) continue
    const list = childrenByParent.get(pid) || []
    list.push(t)
    childrenByParent.set(pid, list)
  }

  const includeParent = new Set<number>()
  const includeChild = new Set<number>()
  for (const t of all) {
    if (!matchOne(t)) continue
    const pid = Number(t.parentTaskId || 0)
    if (pid) {
      includeChild.add(t.id)
      includeParent.add(pid)
    } else {
      includeParent.add(t.id)
    }
  }

  const parents = all.filter((t) => !t.parentTaskId && includeParent.has(t.id))
  const nodes = parents.map((p) => {
    const allChildren = childrenByParent.get(p.id) || []
    const shownChildren = filtering ? allChildren.filter((c) => includeChild.has(c.id)) : allChildren.slice()
    shownChildren.sort((a, b) => {
      const ar = statusRank(a.status)
      const br = statusRank(b.status)
      if (ar !== br) return ar - br
      return b.id - a.id
    })
    const done = allChildren.filter((c) => String(c.status || '') === 'DONE').length
    const doing = allChildren.filter((c) => String(c.status || '') === 'DOING').length
    const total = allChildren.length
    let rollup: TaskStatus | null = null
    if (total) {
      if (done >= total) rollup = 'DONE'
      else if (doing > 0 || done > 0) rollup = 'DOING'
      else rollup = 'TODO'
    }
    const pct = total ? Math.round((done / total) * 100) : 0
    const mismatch = rollup ? String(p.status || '') !== rollup : false
    return { parent: p, children: shownChildren, done, doing, total, pct, rollup, mismatch }
  })

  const list = nodes.slice()
  if (sortKey.value === 'due') {
    list.sort((a, b) => {
      const am = dueMeta(a.parent)?.sort || Number.MAX_SAFE_INTEGER
      const bm = dueMeta(b.parent)?.sort || Number.MAX_SAFE_INTEGER
      if (am !== bm) return am - bm
      return b.parent.id - a.parent.id
    })
  } else if (sortKey.value === 'priority') {
    list.sort((a, b) => {
      const ar = priRank(a.parent.priority)
      const br = priRank(b.parent.priority)
      if (ar !== br) return br - ar
      return b.parent.id - a.parent.id
    })
  } else {
    list.sort((a, b) => b.parent.id - a.parent.id)
  }
  return list
})

watch(
  () => [hierarchyView.value, q.value, overdueOnly.value, mode.value] as const,
  () => {
    if (!hierarchyView.value) return
    const next = new Set(expandedParents.value)
    for (const n of hierarchyNodes.value) {
      if ((n.children || []).length) next.add(n.parent.id)
    }
    expandedParents.value = next
  },
  { immediate: true }
)

function isExpanded(pid: number) {
  return expandedParents.value.has(pid)
}

function toggleExpanded(pid: number) {
  const next = new Set(expandedParents.value)
  if (next.has(pid)) next.delete(pid)
  else next.add(pid)
  expandedParents.value = next
}

function expandAll() {
  const next = new Set<number>()
  for (const n of hierarchyNodes.value) {
    if (n.total) next.add(n.parent.id)
  }
  expandedParents.value = next
}

function collapseAll() {
  expandedParents.value = new Set()
}

const childStatsByParent = computed(() => {
  const map = new Map<number, { total: number; done: number; doing: number }>()
  for (const t of taskStore.tasks) {
    const pid = Number(t.parentTaskId || 0)
    if (!pid) continue
    const cur = map.get(pid) || { total: 0, done: 0, doing: 0 }
    cur.total += 1
    if (String(t.status || '') === 'DONE') cur.done += 1
    if (String(t.status || '') === 'DOING') cur.doing += 1
    map.set(pid, cur)
  }
  return map
})

function childStat(pid: number) {
  return childStatsByParent.value.get(pid) || { total: 0, done: 0, doing: 0 }
}

const doingOver = computed(() => grouped.value.doing.length > WIP_LIMIT)
const selectedList = computed(() => Array.from(selected.value))

function togglePick(id: number, v: boolean) {
  const s = new Set(selected.value)
  if (v) s.add(id)
  else s.delete(id)
  selected.value = s
}

function clearPick() {
  selected.value = new Set()
  bulkAssigneeId.value = null
  bulkDueAt.value = null
  bulkPriority.value = null
  bulkStatus.value = null
  bulkClearDue.value = false
}

async function applyBulk() {
  if (!canEdit.value) {
    message.warning('只读权限，无法批量操作')
    return
  }
  if (!selectedList.value.length) return
  bulkWorking.value = true
  try {
    if (bulkStatus.value) {
      if (bulkStatus.value === 'DOING' && grouped.value.doing.length >= WIP_LIMIT) {
        message.warning(`DOING 超过 WIP 阈值（${WIP_LIMIT}）`)
      } else {
        await taskApi.batchUpdateStatus(selectedList.value, bulkStatus.value)
      }
    }

    const clearAssignee = bulkAssigneeId.value === 0
    const clearDueTime = bulkClearDue.value
    const hasAny =
      bulkPriority.value !== null || bulkAssigneeId.value !== null || bulkDueAt.value !== null || clearAssignee || clearDueTime

    if (hasAny) {
      await taskApi.batchUpdateFields({
        taskIds: selectedList.value,
        priority: bulkPriority.value ?? undefined,
        assigneeId: bulkAssigneeId.value && bulkAssigneeId.value > 0 ? bulkAssigneeId.value : undefined,
        dueTime: bulkDueAt.value ? bulkDueAt.value : undefined,
        clearAssignee,
        clearDueTime
      })
    }

    clearPick()
    await taskStore.loadByProject(props.projectId)
    await loadParticipated()
  } catch (e: any) {
    message.error(e?.message || '批量操作失败')
  } finally {
    bulkWorking.value = false
  }
}

async function updateOne(t: Task, patch: { assigneeId?: number; dueAt?: number; priority?: string | null }) {
  if (!canEdit.value) {
    message.warning('只读权限，无法编辑任务')
    return
  }
  try {
    await taskApi.updateDetail(t.id, {
      assigneeId: patch.assigneeId,
      dueAt: patch.dueAt,
      priority: patch.priority ?? undefined,
      baseUpdatedAt: t.updatedAt || null
    })
    await taskStore.loadByProject(props.projectId)
    await loadParticipated()
  } catch (e: any) {
    message.error(e?.message || '更新失败')
    await taskStore.loadByProject(props.projectId)
  }
}

async function move(t: Task, to: TaskStatus) {
  if (!canEdit.value) {
    message.warning('只读权限，无法移动任务')
    return
  }
  if (to === 'DOING' && grouped.value.doing.length >= WIP_LIMIT) {
    message.warning(`DOING 超过 WIP 阈值（${WIP_LIMIT}）`)
    return
  }
  try {
    await taskStore.updateStatus(t.id, to, t.updatedAt || null)
  } catch (e: any) {
    message.error(e?.message || '更新失败')
    await taskStore.loadByProject(props.projectId)
  }
}

function onDragStart(t: Task, ev: DragEvent) {
  if (!canEdit.value) return
  draggingId.value = t.id
  try {
    ev.dataTransfer?.setData('text/plain', String(t.id))
  } catch {
  }
  ev.dataTransfer?.setDragImage?.(new Image(), 0, 0)
}

function onDragEnd() {
  draggingId.value = null
  dropCol.value = null
}

function allowDrop(status: TaskStatus, ev: DragEvent) {
  if (!canEdit.value) return
  ev.preventDefault()
  dropCol.value = status
}

async function onDrop(status: TaskStatus, ev: DragEvent) {
  if (!canEdit.value) return
  ev.preventDefault()
  const raw = ev.dataTransfer?.getData('text/plain') || ''
  const id = Number(raw || draggingId.value || 0)
  dropCol.value = null
  draggingId.value = null
  if (!id || !Number.isFinite(id)) return
  const t = taskStore.tasks.find((x) => x.id === id)
  if (!t) return
  if (t.status === status) return
  await move(t, status)
}

const viewSelectOptions = computed(() => {
  return viewItems.value.map((v) => ({ label: v.name, value: v.id }))
})

const viewColors = [
  { key: 'teal', label: 'Teal', rgb: '15, 23, 42' },
  { key: 'cyan', label: 'Sky', rgb: '51, 65, 85' },
  { key: 'amber', label: 'Amber', rgb: '100, 116, 139' },
  { key: 'rose', label: 'Rose', rgb: '148, 163, 184' },
  { key: 'emerald', label: 'Emerald', rgb: '203, 213, 225' },
  { key: 'slate', label: 'Slate', rgb: '15, 23, 42' }
]

const activeView = computed(() => (activeViewId.value ? viewItems.value.find((x) => x.id === activeViewId.value) : null))
const activeViewColor = computed(() => String(activeView.value?.color || '').trim())
const activeViewDotRgb = computed(() => {
  const key = activeViewColor.value || viewColor.value
  const c = viewColors.find((x) => x.key === key)
  return c ? c.rgb : viewColors[0].rgb
})

function currentFiltersJson() {
  return JSON.stringify({
    m: mode.value,
    q: q.value.trim(),
    overdue: overdueOnly.value ? 1 : 0,
    hv: hierarchyView.value ? 1 : 0,
    sort: sortKey.value
  })
}

async function activateView(id: number | null) {
  activeViewId.value = id
  if (!id) {
    syncQuery()
    return
  }
  const v = viewItems.value.find((x) => x.id === id)
  if (!v) return
  applyingView.value = true
  try {
    const obj = JSON.parse(v.filtersJson) as any
    mode.value = obj.m || 'all'
    q.value = obj.q || ''
    overdueOnly.value = String(obj.overdue || 0) === '1' || obj.overdue === 1
    hierarchyView.value = String(obj.hv || 0) === '1' || obj.hv === 1
    sortKey.value = obj.sort || 'recent'
  } catch {
  }
  applyingView.value = false
  syncQuery()
}

function openSaveView() {
  viewName.value = String(activeView.value?.name || '').trim()
  viewColor.value = activeViewColor.value || 'teal'
  viewModalOpen.value = true
}

async function saveViewAsNew() {
  const name = viewName.value.trim()
  if (!name) {
    message.warning('请输入视图名称')
    return
  }
  savingView.value = true
  try {
    const v = await taskApi.createView({ projectId: props.projectId, name, color: viewColor.value, filtersJson: currentFiltersJson() })
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

function openTplEdit(t?: TaskTemplate) {
  if (!t || t.id < 0) {
    tplName.value = ''
    tplPayload.value = JSON.stringify(
      { priority: 'MEDIUM', tags: '', description: '', acceptanceCriteria: '' },
      null,
      2
    )
  } else {
    tplName.value = t.name
    tplPayload.value = t.payloadJson
  }
  tplModalOpen.value = true
}

async function saveTpl() {
  const name = tplName.value.trim()
  const payload = tplPayload.value.trim()
  if (!name) {
    message.warning('请输入模板名称')
    return
  }
  if (!payload) {
    message.warning('请输入模板内容')
    return
  }
  savingTpl.value = true
  try {
    const existing = templates.value.find((x) => x.name === name && x.id > 0)
    if (existing) {
      await taskApi.updateTemplate(existing.id, { payloadJson: payload })
    } else {
      await taskApi.createTemplate({ projectId: props.projectId, name, payloadJson: payload })
    }
    tplModalOpen.value = false
    await loadTemplates()
    message.success('已保存模板')
  } catch (e: any) {
    message.error(e?.message || '保存失败')
  } finally {
    savingTpl.value = false
  }
}

async function deleteTpl(t: TaskTemplate) {
  if (!t || t.id < 0) return
  try {
    await taskApi.deleteTemplate(t.id)
    await loadTemplates()
    message.success('已删除模板')
  } catch (e: any) {
    message.error(e?.message || '删除失败')
  }
}

function cardClass(t: Task) {
  const p = String(t.priority || '').toUpperCase()
  const sub = t.parentTaskId ? ' subTask' : ''
  if (p === 'HIGH') return `taskCard priHigh${sub}`
  if (p === 'MEDIUM') return `taskCard priMed${sub}`
  return `taskCard priLow${sub}`
}

function dueClass(level?: string) {
  if (level === 'overdue') return 'dueBadge overdue'
  if (level === 'today') return 'dueBadge today'
  if (level === 'tomorrow') return 'dueBadge tomorrow'
  return 'dueBadge future'
}

function assigneeLabel(t: Task) {
  if (!t.assigneeId) return '未分配'
  const m = members.value.find((x) => x.userId === t.assigneeId)
  return m?.username || t.assignee || `#${t.assigneeId}`
}

function assigneeInitial(t: Task) {
  const s = assigneeLabel(t)
  return (s || 'U').slice(0, 1).toUpperCase()
}

function isPicked(id: number) {
  return selected.value.has(id)
}

function isoDate(d: Date) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${dd}`
}

function weekRange() {
  const now = new Date()
  const day = (now.getDay() + 6) % 7
  const start = new Date(now)
  start.setHours(0, 0, 0, 0)
  start.setDate(start.getDate() - day)
  const end = new Date(start)
  end.setDate(end.getDate() + 6)
  return { startDate: isoDate(start), endDate: isoDate(end) }
}

async function loadRetro() {
  retroLoading.value = true
  try {
    const r = weekRange()
    retro.value = await dashboardApi.overview({ projectId: props.projectId, startDate: r.startDate, endDate: r.endDate })
  } catch {
    retro.value = null
  } finally {
    retroLoading.value = false
  }
}

async function loadActivities() {
  activityLoading.value = true
  try {
    activities.value = await projectCollabApi.activities(props.projectId, 80)
  } catch {
    activities.value = []
  } finally {
    activityLoading.value = false
  }
}

function deleteActivity(a: ProjectActivityItem) {
  if (!canManageActivity.value) {
    message.error('仅项目 OWNER 可删除')
    return
  }
  if (!a?.id) return
  dialog.warning({
    title: '删除动态',
    content: '删除后不可恢复。',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        activityDeletingId.value = a.id
        await projectCollabApi.deleteActivity(props.projectId, a.id)
        activities.value = activities.value.filter((x) => x.id !== a.id)
        message.success('已删除')
      } catch (e: any) {
        message.error(e?.message || '删除失败')
      } finally {
        activityDeletingId.value = null
      }
    }
  })
}

function clearActivityAll() {
  if (!canManageActivity.value) {
    message.error('仅项目 OWNER 可清空')
    return
  }
  dialog.warning({
    title: '清空动态',
    content: '将删除该项目的全部动态记录，删除后不可恢复。',
    positiveText: '清空',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        activityClearing.value = true
        await projectCollabApi.clearActivities(props.projectId)
        activities.value = []
        message.success('已清空')
      } catch (e: any) {
        message.error(e?.message || '清空失败')
      } finally {
        activityClearing.value = false
      }
    }
  })
}

async function openActivity() {
  activityOpen.value = true
  await loadActivities()
}

function downloadText(filename: string, content: string, mime = 'text/plain') {
  const blob = new Blob([content], { type: `${mime};charset=utf-8` })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

async function exportCsv() {
  try {
    const res = await taskApi.workspaceExport({
      projectId: props.projectId,
      mode: mode.value,
      q: q.value.trim() ? q.value.trim() : undefined,
      overdueOnly: overdueOnly.value ? true : undefined,
      sort: sortKey.value
    })
    downloadText(res.filename || `tasks_project_${props.projectId}.csv`, res.content || '', 'text/csv')
    message.success('已导出')
  } catch (e: any) {
    message.error(e?.message || '导出失败')
  }
}

async function copyWeekly() {
  try {
    const res = await taskApi.weeklyReport({ projectId: props.projectId })
    const t = res.content || ''
    try {
      await navigator.clipboard.writeText(t)
      message.success('周报已复制')
      return
    } catch {
    }
    const el = document.createElement('textarea')
    el.value = t
    el.style.position = 'fixed'
    el.style.left = '-9999px'
    document.body.appendChild(el)
    el.focus()
    el.select()
    document.execCommand('copy')
    document.body.removeChild(el)
    message.success('周报已复制')
  } catch (e: any) {
    message.error(e?.message || '复制失败')
  }
}

onMounted(async () => {
  const st = readTreeState()
  if (st?.expanded?.length) expandedParents.value = new Set(st.expanded)
  applyQuery()
  await Promise.all([
    taskStore.loadByProject(props.projectId),
    loadMembers(),
    loadParticipated(),
    loadViews(),
    loadTemplates(),
    loadRetro(),
    loadMilestones()
  ])
})

watch(
  () => props.projectId,
  async (pid) => {
    clearPick()
    activeViewId.value = null
    const st = readTreeState()
    expandedParents.value = st?.expanded?.length ? new Set(st.expanded) : new Set()
    await Promise.all([taskStore.loadByProject(pid), loadMembers(), loadParticipated(), loadViews(), loadTemplates(), loadRetro(), loadMilestones()])
  }
)
</script>

<template>
  <section class="taskBoard">
    <div class="block-head headRow">
      <div class="headLeft">
        <div class="headTitle">任务看板</div>
        <div class="muted headSub">{{ hierarchyView ? '层级视图' : '看板视图' }}</div>
      </div>
      <div class="headRight">
        <div class="headActions">
          <span v-if="isViewer" class="rolePill">只读</span>
          <span class="countPill">共 {{ filtered.length }}</span>
          <n-button size="small" tertiary @click="copyWeekly">周报</n-button>
          <n-button size="small" tertiary @click="exportCsv">导出 CSV</n-button>
          <n-button size="small" tertiary @click="openActivity">动态</n-button>
        </div>
      </div>
    </div>

    <div class="retroStrip">
      <div class="retroTop">
        <div class="retroTopLeft">
          <div class="retroTitle">本周复盘</div>
          <div class="muted retroHint">新增 · 完成 · 逾期（本周）</div>
        </div>
        <div class="retroTopRight">
          <n-button size="small" tertiary :loading="retroLoading" @click="loadRetro">刷新</n-button>
        </div>
      </div>

      <div class="retroBottom">
        <div class="retroKpis">
          <div class="retroStat">
            <div class="muted rsLabel">新增</div>
            <div class="rsVal">{{ retroNew }}</div>
          </div>
          <div class="retroStat">
            <div class="muted rsLabel">完成</div>
            <div class="rsVal">{{ retroDone }}</div>
          </div>
          <div class="retroStat">
            <div class="muted rsLabel">逾期</div>
            <div class="rsVal">{{ retroOverdue }}</div>
          </div>
        </div>

        <div class="retroSpark">
          <div class="spark" aria-hidden="true">
            <div v-for="(p, idx) in retroSpark" :key="idx" class="spBar" :style="{ height: `${p.h}px` }" />
          </div>
          <div class="muted sparkHint">完成趋势</div>
        </div>
      </div>
    </div>

    <div class="toolbar">
      <div class="tLeft">
        <div class="seg">
          <button class="segBtn" :class="{ on: mode === 'all' }" @click="mode = 'all'">全部</button>
          <button class="segBtn" :class="{ on: mode === 'mine' }" @click="mode = 'mine'">我负责</button>
          <button class="segBtn" :class="{ on: mode === 'unassigned' }" @click="mode = 'unassigned'">未分配</button>
          <button class="segBtn" :class="{ on: mode === 'participated' }" @click="mode = 'participated'">我参与</button>
        </div>
        <n-input v-model:value="q" size="small" placeholder="搜索任务…" class="search" />
        <label class="ck muted">
          <n-checkbox v-model:checked="overdueOnly" />逾期
        </label>
        <label class="ck muted">
          <n-checkbox v-model:checked="hierarchyView" />层级
        </label>
        <n-select v-model:value="sortKey" size="small" :options="[
          { label: '最近', value: 'recent' },
          { label: '按截止', value: 'due' },
          { label: '按优先级', value: 'priority' }
        ]" class="sort" />
      </div>

      <div class="tRight">
        <div v-if="hierarchyView" class="treeActions">
          <n-button size="small" tertiary @click="expandAll">展开</n-button>
          <n-button size="small" tertiary @click="collapseAll">收起</n-button>
        </div>
        <div v-if="activeViewId" class="viewDot" :style="{ '--dot': activeViewDotRgb } as any" />
        <n-select
          v-model:value="activeViewId"
          size="small"
          clearable
          placeholder="视图"
          :options="viewSelectOptions"
          class="viewSel"
          @update:value="activateView"
        />
        <n-button size="small" tertiary @click="openSaveView">保存</n-button>
        <n-button v-if="activeViewId" size="small" tertiary class="dangerT" @click="deleteActiveView">删除</n-button>
        <n-button size="small" tertiary @click="openTplEdit()">模板</n-button>
      </div>
    </div>

    <div class="createBar">
      <n-input
        v-model:value="newTitle"
        :disabled="!canEdit"
        placeholder="添加任务…"
        class="newInput"
        @keyup.enter="createTask"
      />
      <div class="createActions">
        <n-button size="small" tertiary :disabled="!canEdit" @click="advancedOpen = !advancedOpen">{{ advancedOpen ? '收起' : '更多' }}</n-button>
        <button class="btnPrimary" :disabled="creating || !canEdit" @click="createTask">
          <span>创建</span>
          <span v-if="creating" class="ml-2 inline-block h-4 w-4 rounded-full border-2 border-white/30 border-t-white animate-spin" />
        </button>
      </div>
    </div>

    <div v-if="advancedOpen" class="createMore">
      <n-select
        v-model:value="tplId"
        :disabled="!canEdit"
        :options="templates.map((t) => ({ label: t.name, value: t.id }))"
        placeholder="模板"
        class="moreSel"
      />
      <n-select
        v-model:value="quickMilestoneId"
        :disabled="!canEdit"
        :options="milestoneOptions"
        placeholder="Milestone"
        class="moreSel"
      />
      <n-select v-model:value="quickAssigneeId" :disabled="!canEdit" :options="memberOptions" placeholder="负责人" class="moreSel" />
      <n-select v-model:value="quickPriority" :disabled="!canEdit" :options="priorityOptions" placeholder="优先级" class="moreSel" />
      <n-date-picker v-model:value="quickDueAt" :disabled="!canEdit" type="date" clearable placeholder="截止" class="moreDate" />
    </div>

    <div v-if="canEdit && selectedList.length" class="bulkBar">
      <div class="bulkLeft">
        <div class="bulkTitle">已选 {{ selectedList.length }}</div>
        <n-select v-model:value="bulkStatus" :options="statusOptions" placeholder="批量状态" size="small" class="bulkSel" />
        <n-select v-model:value="bulkAssigneeId" :options="memberOptions" placeholder="批量负责人" size="small" class="bulkSel" />
        <n-select v-model:value="bulkPriority" :options="priorityOptions" placeholder="批量优先级" size="small" class="bulkSel" />
        <n-date-picker
          v-model:value="bulkDueAt"
          type="date"
          clearable
          placeholder="批量截止"
          size="small"
          class="bulkDate"
        />
        <n-button size="small" tertiary :class="{ dangerT: bulkClearDue }" @click="bulkClearDue = !bulkClearDue">
          {{ bulkClearDue ? '将清除截止' : '清除截止' }}
        </n-button>
      </div>
      <div class="bulkRight">
        <n-button size="small" :loading="bulkWorking" @click="applyBulk">应用</n-button>
        <n-button size="small" tertiary @click="clearPick">清空</n-button>
      </div>
    </div>

    <n-spin :show="loading">
      <div v-if="hierarchyView" class="tree">
        <div v-if="!hierarchyNodes.length" class="muted empty">暂无任务</div>
        <div v-for="n in hierarchyNodes" :key="n.parent.id" class="treeGroup">
          <div :class="[cardClass(n.parent), 'treeParent', { picked: isPicked(n.parent.id) }]">
            <div class="treeTop">
              <div class="treeTopLeft">
                <button
                  v-if="n.total"
                  class="twist"
                  type="button"
                  :aria-label="isExpanded(n.parent.id) ? 'collapse' : 'expand'"
                  @click="toggleExpanded(n.parent.id)"
                >
                  {{ isExpanded(n.parent.id) ? '▾' : '▸' }}
                </button>
                <label v-if="canEdit" class="pick">
                  <n-checkbox :checked="isPicked(n.parent.id)" @update:checked="(v) => togglePick(n.parent.id, v)" />
                </label>
                <div class="title" @click="emit('open-task', n.parent.id)">{{ n.parent.title }}</div>
              </div>
              <div class="treeTopRight">
                <button v-if="canEdit && n.total" type="button" class="treeBtn" @click.stop="openParentManage(n.parent)">管理</button>
                <span v-if="n.total" class="subCount">{{ n.done }}/{{ n.total }}</span>
                <span v-if="n.rollup && n.mismatch" class="st roll" :class="String(n.rollup || '').toLowerCase()">{{ n.rollup }}</span>
                <span class="st" :class="String(n.parent.status || '').toLowerCase()">{{ n.parent.status }}</span>
              </div>
            </div>
            <div v-if="n.total" class="treeProg" aria-hidden="true">
              <div class="treeProgBar" :style="{ width: `${n.pct}%` }" />
            </div>
            <div class="cMeta">
              <span class="avatar" :title="assigneeLabel(n.parent)">{{ assigneeInitial(n.parent) }}</span>
              <span class="pri">{{ (n.parent.priority || 'LOW').toString().toUpperCase() }}</span>
              <span v-if="dueMeta(n.parent)" :class="dueClass(dueMeta(n.parent)?.level)">{{ dueMeta(n.parent)?.text }}</span>
            </div>
          </div>

          <div v-if="n.children.length && isExpanded(n.parent.id)" class="treeChildren">
            <div v-for="c in n.children" :key="c.id" class="treeChild" :class="{ picked: isPicked(c.id) }">
              <div class="treeChildLeft">
                <span class="indent" aria-hidden="true" />
                <label v-if="canEdit" class="pick">
                  <n-checkbox :checked="isPicked(c.id)" @update:checked="(v) => togglePick(c.id, v)" />
                </label>
                <div class="title childTitle" @click="emit('open-task', c.id)">{{ c.title }}</div>
              </div>
              <div class="treeChildRight">
                <span class="avatar sm" :title="assigneeLabel(c)">{{ assigneeInitial(c) }}</span>
                <span class="priSm">{{ (c.priority || 'LOW').toString().toUpperCase() }}</span>
                <span v-if="dueMeta(c)" :class="['dueSm', dueClass(dueMeta(c)?.level)]">{{ dueMeta(c)?.text }}</span>
                <span class="st sm" :class="String(c.status || '').toLowerCase()">{{ c.status }}</span>
              </div>
            </div>
          </div>
          <div v-if="canEdit && isExpanded(n.parent.id)" class="inlineCreate">
            <div class="indentGhost" aria-hidden="true" />
            <n-input
              size="small"
              :value="getInlineDraft(n.parent.id)"
              placeholder="添加子任务…"
              class="inlineInput"
              @update:value="(v) => setInlineDraft(n.parent.id, v)"
              @keyup.enter="createInlineSubtask(n.parent)"
            />
            <n-button
              size="small"
              type="primary"
              :loading="inlineSubtaskCreatingParentId === n.parent.id"
              @click="createInlineSubtask(n.parent)"
            >
              添加
            </n-button>
          </div>
        </div>
      </div>

      <div v-else class="board">
        <div class="col" :class="{ over: dropCol === 'TODO' }" @dragover="allowDrop('TODO', $event)" @drop="onDrop('TODO', $event)">
          <div class="colHead">
            <div class="colTitle">TODO</div>
            <div class="muted meta">{{ grouped.todo.length }}</div>
          </div>
          <div class="colBody">
            <div
              v-for="t in grouped.todo"
              :key="t.id"
              :class="[cardClass(t), { picked: isPicked(t.id) }]"
              :draggable="canEdit"
              @dragstart="onDragStart(t, $event)"
              @dragend="onDragEnd"
            >
              <div class="cTop">
                <label v-if="canEdit" class="pick">
                  <n-checkbox :checked="isPicked(t.id)" @update:checked="(v) => togglePick(t.id, v)" />
                </label>
                <div class="title" @click="emit('open-task', t.id)">{{ t.title }}</div>
              </div>
              <div class="cMeta">
                <span class="avatar" :title="assigneeLabel(t)">{{ assigneeInitial(t) }}</span>
                <span class="pri">{{ (t.priority || 'LOW').toString().toUpperCase() }}</span>
                <span v-if="dueMeta(t)" :class="dueClass(dueMeta(t)?.level)">{{ dueMeta(t)?.text }}</span>
                <span v-if="t.parentTaskId" class="parentPill" :title="parentTitle(Number(t.parentTaskId))">↳ {{ parentTitle(Number(t.parentTaskId)) }}</span>
                <span v-if="!t.parentTaskId && childStat(t.id).total" class="subPill">{{ childStat(t.id).done }}/{{ childStat(t.id).total }}</span>
              </div>
              <div v-if="canEdit" class="cAct">
                <n-select
                  :value="t.assigneeId ? t.assigneeId : 0"
                  size="tiny"
                  :options="memberOptions"
                  class="miniSel"
                  @update:value="(v) => updateOne(t, { assigneeId: Number(v || 0) })"
                />
                <n-select
                  :value="(t.priority || 'LOW').toString().toUpperCase()"
                  size="tiny"
                  :options="priorityOptions"
                  class="miniSel"
                  @update:value="(v) => updateOne(t, { priority: String(v) })"
                />
                <n-date-picker
                  :value="dueMeta(t)?.sort || null"
                  type="date"
                  size="small"
                  clearable
                  class="miniDate"
                  @update:value="(v) => updateOne(t, { dueAt: v === null ? 0 : v })"
                />
                <n-button text size="tiny" @click="move(t, 'DOING')">→</n-button>
              </div>
            </div>
            <div v-if="!grouped.todo.length" class="muted empty">暂无待办</div>
          </div>
        </div>

        <div class="col" :class="{ over: dropCol === 'DOING' }" @dragover="allowDrop('DOING', $event)" @drop="onDrop('DOING', $event)">
          <div class="colHead">
            <div class="colTitle">
              DOING
              <span v-if="doingOver" class="wip">WIP {{ grouped.doing.length }}/{{ WIP_LIMIT }}</span>
            </div>
            <div class="muted meta">{{ grouped.doing.length }}</div>
          </div>
          <div class="colBody">
            <div
              v-for="t in grouped.doing"
              :key="t.id"
              :class="[cardClass(t), { picked: isPicked(t.id) }]"
              :draggable="canEdit"
              @dragstart="onDragStart(t, $event)"
              @dragend="onDragEnd"
            >
              <div class="cTop">
                <label v-if="canEdit" class="pick">
                  <n-checkbox :checked="isPicked(t.id)" @update:checked="(v) => togglePick(t.id, v)" />
                </label>
                <div class="title" @click="emit('open-task', t.id)">{{ t.title }}</div>
              </div>
              <div class="cMeta">
                <span class="avatar" :title="assigneeLabel(t)">{{ assigneeInitial(t) }}</span>
                <span class="pri">{{ (t.priority || 'LOW').toString().toUpperCase() }}</span>
                <span v-if="dueMeta(t)" :class="dueClass(dueMeta(t)?.level)">{{ dueMeta(t)?.text }}</span>
                <span v-if="t.parentTaskId" class="parentPill" :title="parentTitle(Number(t.parentTaskId))">↳ {{ parentTitle(Number(t.parentTaskId)) }}</span>
                <span v-if="!t.parentTaskId && childStat(t.id).total" class="subPill">{{ childStat(t.id).done }}/{{ childStat(t.id).total }}</span>
              </div>
              <div v-if="canEdit" class="cAct">
                <n-select
                  :value="t.assigneeId ? t.assigneeId : 0"
                  size="tiny"
                  :options="memberOptions"
                  class="miniSel"
                  @update:value="(v) => updateOne(t, { assigneeId: Number(v || 0) })"
                />
                <n-select
                  :value="(t.priority || 'LOW').toString().toUpperCase()"
                  size="tiny"
                  :options="priorityOptions"
                  class="miniSel"
                  @update:value="(v) => updateOne(t, { priority: String(v) })"
                />
                <n-date-picker
                  :value="dueMeta(t)?.sort || null"
                  type="date"
                  size="small"
                  clearable
                  class="miniDate"
                  @update:value="(v) => updateOne(t, { dueAt: v === null ? 0 : v })"
                />
                <n-button text size="tiny" @click="move(t, 'DONE')">→</n-button>
                <n-button text size="tiny" @click="move(t, 'TODO')">←</n-button>
              </div>
            </div>
            <div v-if="!grouped.doing.length" class="muted empty">No DOING</div>
          </div>
        </div>

        <div class="col" :class="{ over: dropCol === 'DONE' }" @dragover="allowDrop('DONE', $event)" @drop="onDrop('DONE', $event)">
          <div class="colHead">
            <div class="colTitle">DONE</div>
            <div class="muted meta">{{ grouped.done.length }}</div>
          </div>
          <div class="colBody">
            <div
              v-for="t in grouped.done"
              :key="t.id"
              :class="[cardClass(t), { picked: isPicked(t.id) }]"
              :draggable="canEdit"
              @dragstart="onDragStart(t, $event)"
              @dragend="onDragEnd"
            >
              <div class="cTop">
                <label v-if="canEdit" class="pick">
                  <n-checkbox :checked="isPicked(t.id)" @update:checked="(v) => togglePick(t.id, v)" />
                </label>
                <div class="title" @click="emit('open-task', t.id)">{{ t.title }}</div>
              </div>
              <div class="cMeta">
                <span class="avatar" :title="assigneeLabel(t)">{{ assigneeInitial(t) }}</span>
                <span class="pri">{{ (t.priority || 'LOW').toString().toUpperCase() }}</span>
                <span v-if="dueMeta(t)" :class="dueClass(dueMeta(t)?.level)">{{ dueMeta(t)?.text }}</span>
                <span v-if="t.parentTaskId" class="parentPill" :title="parentTitle(Number(t.parentTaskId))">↳ {{ parentTitle(Number(t.parentTaskId)) }}</span>
                <span v-if="!t.parentTaskId && childStat(t.id).total" class="subPill">{{ childStat(t.id).done }}/{{ childStat(t.id).total }}</span>
              </div>
              <div v-if="canEdit" class="cAct">
                <n-button text size="tiny" @click="move(t, 'DOING')">←</n-button>
              </div>
            </div>
            <div v-if="!grouped.done.length" class="muted empty">暂无已完成</div>
          </div>
        </div>
      </div>
    </n-spin>
  </section>

  <n-modal v-model:show="manageOpen" :mask-closable="true">
    <n-card style="width: 860px" :title="manageParent ? `子任务管理 · ${manageParent.title}` : '子任务管理'" :bordered="false">
      <div class="manageTop">
        <div class="manageKpis">
          <div class="manageStat">
            <div class="muted msLabel">完成</div>
            <div class="msVal">{{ manageDone }}/{{ manageTotal }}</div>
          </div>
          <div class="manageStat">
            <div class="muted msLabel">进度</div>
            <div class="msVal">{{ managePct }}%</div>
          </div>
        </div>
        <div class="manageProg" aria-hidden="true">
          <div class="manageProgBar" :style="{ width: `${managePct}%` }" />
        </div>
      </div>

      <n-spin :show="manageWorking">
        <div class="manageList">
          <div v-if="!manageChildren.length" class="muted empty">暂无子任务</div>
          <div v-for="c in manageChildren" :key="c.id" class="manageRow" :class="{ done: String(c.status || '') === 'DONE' }">
            <label v-if="canEdit" class="pick">
              <n-checkbox :checked="managePick.has(c.id)" @update:checked="(v) => toggleManagePick(c.id, v)" />
            </label>
            <div class="manageMain" @click="emit('open-task', c.id)">
              <div class="manageTitle">{{ c.title }}</div>
              <div class="muted manageMeta">
                #{{ c.id }}
                <span v-if="dueMeta(c)"> · {{ dueMeta(c)?.text }}</span>
                <span> · {{ assigneeLabel(c) }}</span>
              </div>
            </div>
            <div class="manageRight">
              <span class="st sm" :class="String(c.status || '').toLowerCase()">{{ c.status }}</span>
            </div>
          </div>
        </div>
      </n-spin>

      <div v-if="manageResult && manageResult.failed && manageResult.failed.length" class="manageErr">
        <div class="muted manageErrTitle">未完成原因（最多显示 6 条）</div>
        <div class="manageErrList">
          <div v-for="f in manageResult.failed.slice(0, 6)" :key="f.taskId" class="manageErrRow">
            <span class="errId">#{{ f.taskId }}</span>
            <span class="errMsg">{{ f.message }}</span>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="modalActions manageActions">
          <n-button tertiary @click="pickAllIncomplete">全选未完成</n-button>
          <n-button tertiary @click="clearManagePick">清空选择</n-button>
          <div class="spacer" />
          <n-button @click="manageOpen = false">关闭</n-button>
          <n-button type="primary" :loading="manageWorking" @click="completePickedSubtasks">完成选中（{{ managePick.size }}）</n-button>
        </div>
      </template>
    </n-card>
  </n-modal>

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

  <n-modal v-model:show="tplModalOpen" :mask-closable="false">
    <n-card style="width: 720px" title="任务模板" :bordered="false">
      <div class="tplGrid">
        <div class="tplList">
          <div class="muted tip">项目模板（同名会覆盖）</div>
          <button v-for="t in templates" :key="t.id" class="tplRow" @click="openTplEdit(t)">
            <div class="tplName">{{ t.name }}</div>
            <div class="tplActions">
              <n-button v-if="t.id > 0" size="tiny" tertiary class="dangerT" @click.stop="deleteTpl(t)">删除</n-button>
            </div>
          </button>
        </div>
        <div class="tplEdit">
          <div class="formRow">
            <div class="muted label">名称</div>
            <n-input v-model:value="tplName" placeholder="例如：API 需求" />
          </div>
          <div class="formRow">
            <div class="muted label">JSON</div>
            <n-input v-model:value="tplPayload" type="textarea" :autosize="{ minRows: 10, maxRows: 16 }" />
          </div>
        </div>
      </div>
      <template #footer>
        <div class="modalActions">
          <n-button @click="tplModalOpen = false">关闭</n-button>
          <n-button type="primary" :loading="savingTpl" @click="saveTpl">保存</n-button>
        </div>
      </template>
    </n-card>
  </n-modal>

  <n-modal v-model:show="activityOpen" :mask-closable="true">
    <n-card style="width: 820px" title="项目动态" :bordered="false">
      <n-spin :show="activityLoading">
        <div class="actList">
          <div v-for="a in activities" :key="a.id" class="actRow">
            <div class="actMain">
              <div class="actTitle">
                <span class="actUser">{{ actTitle(a) }}</span>
              </div>
              <div v-if="actDetail(a)" class="muted actDetail">{{ actDetail(a) }}</div>
            </div>
            <div class="muted actTime">{{ a.createTime }}</div>
            <div v-if="canManageActivity" class="actActions">
              <n-button size="tiny" quaternary :loading="activityDeletingId === a.id" @click="deleteActivity(a)">删除</n-button>
            </div>
          </div>
          <div v-if="!activities.length && !activityLoading" class="muted actEmpty">暂无动态</div>
        </div>
      </n-spin>
      <template #footer>
        <div class="modalActions">
          <n-button @click="activityOpen = false">关闭</n-button>
          <n-button tertiary :loading="activityLoading" @click="loadActivities">刷新</n-button>
          <n-button v-if="canManageActivity" secondary :loading="activityClearing" @click="clearActivityAll">清空</n-button>
        </div>
      </template>
    </n-card>
  </n-modal>
</template>

<style scoped>
.taskBoard {
  padding: 0;
}

.headRow {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 6px 2px 12px;
  border-bottom: 1px solid var(--line);
  position: relative;
}

.headLeft {
  display: grid;
  gap: 2px;
  align-items: center;
  text-align: center;
}

.headTitle {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.2px;
  line-height: 1.2;
  color: var(--ink);
}

.headSub {
  font-size: 12px;
  color: var(--ink-3);
}

.headRight {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
}

.headActions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.rolePill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 26px;
  padding: 0 10px;
  border-radius: var(--radius-sm);
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: -0.2px;
}

.countPill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 26px;
  padding: 0 10px;
  border-radius: var(--radius-sm);
  background: var(--surface-3);
  color: var(--ink-3);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: -0.2px;
}

.retroStrip {
  padding: 14px 16px;
  border-radius: var(--radius-md);
  background: var(--surface-2);
  border: 1px solid var(--line);
  margin-top: 12px;
  margin-bottom: 12px;
}

.retroTop {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.retroTopLeft {
  display: grid;
  gap: 3px;
}

.retroTitle {
  font-weight: 700;
  letter-spacing: -0.2px;
  font-size: 13px;
  color: var(--ink);
}

.retroHint {
  font-size: 12px;
  color: var(--ink-3);
}

.retroBottom {
  margin-top: 12px;
  display: grid;
  grid-template-columns: 1fr 0.9fr;
  gap: 12px;
}

.retroKpis {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.retroStat {
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--surface);
  border: 1px solid var(--line);
}

.rsLabel {
  font-size: 12px;
  color: var(--ink-3);
}

.rsVal {
  margin-top: 4px;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.4px;
  color: var(--ink);
  line-height: 1.1;
}

.retroSpark {
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  background: var(--surface);
  border: 1px solid var(--line);
}

.spark {
  height: 24px;
  display: flex;
  align-items: flex-end;
  gap: 3px;
}

.spBar {
  width: 5px;
  border-radius: 2px;
  background: var(--brand);
  opacity: 0.85;
}

.sparkHint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--ink-3);
}

.createBar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
}

.newInput {
  flex: 1;
}

.createActions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.createMore {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  align-items: center;
}

.moreSel {
  width: 100%;
}

.moreDate {
  width: 100%;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
  margin-bottom: 12px;
}

.tLeft {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  flex: 1 1 640px;
  min-width: 420px;
}

.tRight {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
  margin-left: auto;
}

.treeActions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.viewDot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: rgba(var(--dot), 0.85);
}

/* 文字 tab 组 */
.seg {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  background: transparent;
  box-shadow: none;
  overflow: visible;
}

.segBtn {
  height: 28px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: -0.2px;
  border: 0;
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: color 120ms ease, background 120ms ease;
}

.segBtn:hover {
  color: var(--ink);
  background: var(--surface-3);
}

.segBtn.on {
  background: var(--brand-soft);
  color: var(--brand);
  font-weight: 650;
}

.search {
  width: 220px;
}

.sort {
  width: 120px;
}

.viewSel {
  width: 220px;
  min-width: 200px;
  flex: 0 0 220px;
}

.ck {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ink-3);
}

.dangerT {
  color: var(--danger-ink);
}

.btnPrimary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  padding: 0 14px;
  border-radius: var(--radius-sm);
  background: var(--brand);
  color: rgba(255, 255, 255, 0.95);
  border: 1px solid transparent;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: -0.2px;
  cursor: pointer;
  transition: background 120ms ease;
}

.btnPrimary:hover {
  background: var(--brand-hover);
}

.btnPrimary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.bulkBar {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--surface);
  border: 1px solid var(--line);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.bulkLeft {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.bulkTitle {
  font-size: 12px;
  font-weight: 650;
  color: var(--ink);
}

.bulkSel {
  width: 120px;
}

.bulkDate {
  width: 160px;
}

.board {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  align-items: start;
}

.tree {
  margin-top: 14px;
  display: grid;
  gap: 12px;
}

.treeGroup {
  display: grid;
  gap: 8px;
}

.treeParent {
  padding: 10px 12px;
}

.treeTop {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.treeTopLeft {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.treeTopRight {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.treeBtn {
  height: 24px;
  padding: 0 8px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: -0.2px;
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--ink-2);
  cursor: pointer;
  transition: border-color 120ms ease, color 120ms ease;
}

.treeBtn:hover {
  border-color: var(--line-strong);
  color: var(--brand);
}

.twist {
  width: 20px;
  height: 20px;
  border-radius: var(--radius-sm);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--surface-3);
  color: var(--ink-3);
  border: 1px solid var(--line);
  cursor: pointer;
  transition: background 120ms ease, color 120ms ease;
}

.twist:hover {
  background: var(--brand-soft);
  color: var(--brand);
}

.subCount {
  font-size: 12px;
  font-weight: 600;
  color: var(--ink-3);
}

.st {
  font-size: 11px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--line);
  background: var(--surface-2);
  color: var(--ink-3);
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.st.roll {
  border-color: rgba(185, 28, 28, 0.24);
  background: rgba(185, 28, 28, 0.06);
  color: var(--danger-ink);
}

.st.todo,
.st.doing,
.st.done {
  color: var(--ink-2);
}

.st.sm {
  padding: 1px 5px;
  font-size: 10px;
}

.treeProg {
  margin-top: 10px;
  height: 4px;
  border-radius: 2px;
  background: var(--surface-3);
  overflow: hidden;
}

.treeProgBar {
  height: 100%;
  border-radius: 2px;
  background: var(--brand);
}

.treeChildren {
  display: grid;
  gap: 8px;
  padding-left: 14px;
}

.treeChild {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  border-radius: var(--radius-md);
  background: var(--surface);
  border: 1px solid var(--line);
  transition: border-color 120ms ease;
}

.treeChild:hover {
  border-color: var(--line-strong);
}

.treeChildLeft {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.indent {
  width: 10px;
  height: 10px;
  border-left: 2px solid var(--line-strong);
  border-bottom: 2px solid var(--line-strong);
  border-radius: 0 0 0 6px;
  margin-right: 2px;
}

.treeChildRight {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.childTitle {
  font-weight: 600;
}

.avatar.sm {
  width: 18px;
  height: 18px;
  font-size: 10px;
}

.dueSm,
.priSm {
  font-size: 11px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  background: var(--surface-3);
  color: var(--ink-3);
}

.inlineCreate {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-left: 14px;
}

.indentGhost {
  width: 10px;
  height: 10px;
  opacity: 0;
  margin-right: 2px;
}

.inlineInput {
  flex: 1;
}

.subPill,
.parentPill {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  background: var(--surface-3);
  color: var(--ink-3);
}

.col {
  border-radius: var(--radius-md);
  background: var(--surface-2);
  border: 1px solid var(--line);
  overflow: hidden;
  min-height: 220px;
}

.col.over {
  border-color: var(--brand);
  background: var(--brand-soft);
}

.colHead {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid var(--line);
}

.colTitle {
  font-weight: 650;
  letter-spacing: -0.2px;
  font-size: 13px;
  color: var(--ink);
}

.wip {
  margin-left: 8px;
  font-size: 11px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  background: var(--surface-3);
  color: var(--ink-3);
}

.colBody {
  padding: 10px;
  display: grid;
  gap: 8px;
}

.taskCard {
  border-radius: var(--radius-md);
  background: var(--surface);
  padding: 10px;
  border: 1px solid var(--line);
  transition: border-color 120ms ease, background 120ms ease;
}

.taskCard.subTask {
  background: var(--surface-2);
}

.taskCard:hover {
  border-color: var(--line-strong);
}

.taskCard.picked {
  border-color: var(--brand);
  background: var(--brand-soft);
}

/* 优先级：2px 左边框灰度线（唯一信息载体） */
.priHigh {
  border-left: 2px solid #374151;
}

.priMed {
  border-left: 2px solid #9ca3af;
}

.priLow {
  border-left: 2px solid #e5e7eb;
}

.cTop {
  display: grid;
  grid-template-columns: 18px 1fr;
  gap: 8px;
  align-items: start;
}

.title {
  font-weight: 600;
  letter-spacing: -0.2px;
  line-height: 1.3;
  cursor: pointer;
  color: var(--ink);
}

.cMeta {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.avatar {
  width: 20px;
  height: 20px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--surface-3);
  border: 1px solid var(--line);
  color: var(--ink-3);
  font-weight: 600;
  font-size: 11px;
  flex-shrink: 0;
}

.pri {
  font-size: 11px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  background: var(--surface-3);
  color: var(--ink-3);
}

.dueBadge {
  font-size: 11px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  background: var(--surface-3);
  color: var(--ink-3);
}

.dueBadge.today,
.dueBadge.tomorrow {
  background: var(--brand-soft);
  color: var(--brand);
}

.dueBadge.overdue {
  background: rgba(185, 28, 28, 0.08);
  color: var(--danger-ink);
}

.cAct {
  margin-top: 8px;
  display: grid;
  grid-template-columns: 1fr 1fr 1fr auto;
  gap: 8px;
  align-items: center;
  opacity: 0;
  max-height: 0;
  overflow: hidden;
  pointer-events: none;
  transition: opacity 160ms ease, max-height 160ms ease;
}

.taskCard:hover .cAct,
.taskCard.picked .cAct {
  opacity: 1;
  max-height: 120px;
  pointer-events: auto;
}

.miniSel {
  width: 100%;
}

.miniDate {
  width: 100%;
}

.empty {
  padding: 6px 4px;
  font-size: 12px;
  color: var(--ink-3);
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
  color: var(--ink-3);
}

.modalActions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.manageTop {
  display: grid;
  gap: 10px;
  margin-bottom: 10px;
}

.manageKpis {
  display: flex;
  gap: 8px;
}

.manageStat {
  flex: 0 0 auto;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  background: var(--surface-3);
}

.msLabel {
  font-size: 12px;
  color: var(--ink-3);
}

.msVal {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.3px;
  color: var(--ink);
}

.manageProg {
  height: 4px;
  border-radius: 2px;
  background: var(--surface-3);
  overflow: hidden;
}

.manageProgBar {
  height: 100%;
  border-radius: 2px;
  background: var(--brand);
}

.manageList {
  display: grid;
  gap: 8px;
  max-height: 420px;
  overflow: auto;
  padding-right: 2px;
}

.manageRow {
  display: grid;
  grid-template-columns: 22px 1fr auto;
  gap: 10px;
  align-items: center;
  padding: 8px 10px;
  border-radius: var(--radius-md);
  background: var(--surface);
  border: 1px solid var(--line);
}

.manageRow.done {
  opacity: 0.6;
}

.manageMain {
  min-width: 0;
  cursor: pointer;
}

.manageTitle {
  font-weight: 600;
  letter-spacing: -0.2px;
  line-height: 1.3;
  color: var(--ink);
}

.manageMeta {
  margin-top: 3px;
  font-size: 12px;
  color: var(--ink-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.manageRight {
  flex-shrink: 0;
}

.manageErr {
  margin-top: 10px;
  padding: 10px;
  border-radius: var(--radius-md);
  background: rgba(185, 28, 28, 0.06);
  border: 1px solid rgba(185, 28, 28, 0.16);
}

.manageErrTitle {
  font-size: 12px;
  color: var(--danger-ink);
  margin-bottom: 6px;
}

.manageErrList {
  display: grid;
  gap: 6px;
}

.manageErrRow {
  display: flex;
  gap: 8px;
  align-items: baseline;
}

.errId {
  font-size: 12px;
  font-weight: 600;
  color: var(--ink-2);
}

.errMsg {
  font-size: 12px;
  color: var(--ink-2);
}

.spacer {
  flex: 1;
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
}

.colorDot.on {
  border-color: rgba(var(--dot), 0.58);
  box-shadow: inset 0 0 0 3px rgba(255, 255, 255, 0.82), 0 0 0 4px rgba(var(--dot), 0.10);
}

.actList {
  display: grid;
  gap: 8px;
  padding: 4px 2px;
}

.actRow {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--surface);
  border: 1px solid var(--line);
}

.actMain {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.actTitle {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.actUser {
  font-weight: 650;
  letter-spacing: -0.2px;
  color: var(--ink);
  white-space: nowrap;
}

.actType {
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.actDetail {
  font-size: 12px;
  line-height: 1.5;
  word-break: break-word;
  color: var(--ink-3);
}

.actTime {
  font-size: 12px;
  color: var(--ink-3);
  white-space: nowrap;
}

.actActions {
  opacity: 0;
  transition: opacity 140ms ease;
}

.actRow:hover .actActions {
  opacity: 1;
}

.actEmpty {
  padding: 8px 6px;
  font-size: 12px;
  color: var(--ink-3);
}

.tplGrid {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 12px;
}

.tplList {
  border-right: 1px solid var(--line);
  padding-right: 12px;
  display: grid;
  gap: 8px;
  align-content: start;
}

.tplRow {
  text-align: left;
  border-radius: var(--radius-sm);
  border: 1px solid var(--line);
  background: var(--surface);
  padding: 8px 10px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  transition: border-color 120ms ease;
}

.tplRow:hover {
  border-color: var(--brand);
}

.tplName {
  font-weight: 600;
  letter-spacing: -0.2px;
  font-size: 13px;
  color: var(--ink);
}

.tplEdit {
  display: grid;
  align-content: start;
}

.tip {
  font-size: 12px;
  color: var(--ink-3);
  margin-bottom: 4px;
}

@media (max-width: 1180px) {
  .board {
    grid-template-columns: 1fr;
  }
  .createMore {
    grid-template-columns: 1fr 1fr;
  }
  .retroBottom {
    grid-template-columns: 1fr;
  }
  .retroKpis {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .cAct {
    opacity: 1;
    max-height: none;
    pointer-events: auto;
  }
}
</style>
