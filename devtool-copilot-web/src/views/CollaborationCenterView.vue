<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NDrawer, NDrawerContent, NInput, NModal, NSelect, NSpin, useDialog, useMessage } from 'naive-ui'
import {
  projectCollabApi,
  type TeamActivityItem,
  type TeamCenterResponse,
  type TeamContactItem,
  type TeamGroupItem,
  type TeamInviteItem
} from '../api/projectCollab'
import { taskApi, type TaskSearchItem, type WorkspaceMyWorkItem } from '../api/task'
import { groupTaskApi, type GroupTaskBoardResponse, type GroupTaskItem, type GroupTaskPriority, type GroupTaskStatus } from '../api/groupTask'
import { useRealtimeStore, type RealtimeServerMessage } from '../stores/realtime'

type WorkspaceTab = 'overview' | 'members' | 'groups' | 'activity'
type ActivityCategory = 'invite' | 'member' | 'role' | 'group' | 'task' | 'other'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const rt = useRealtimeStore()

const activeTab = ref<WorkspaceTab>('overview')
const selectedGroupId = ref(0)
const loading = ref(false)
const memberQuery = ref('')
const groupFilter = ref(0)
const roleFilter = ref('all')
const statusFilter = ref('all')
const activityFilter = ref<ActivityCategory | 'all'>('all')
const center = ref<TeamCenterResponse | null>(null)
const myWork = ref<WorkspaceMyWorkItem[]>([])
const myPendingInvites = ref<TeamInviteItem[]>([])

const groupDialogOpen = ref(false)
const groupDialogMode = ref<'create' | 'rename'>('create')
const groupName = ref('')
const groupSaving = ref(false)

const inviteDialogOpen = ref(false)
const inviteGroupId = ref<number | null>(null)
const inviteEmail = ref('')
const inviteSaving = ref(false)

const memberDrawerOpen = ref(false)
const drawerMember = ref<TeamContactItem | null>(null)
const drawerTasks = ref<TaskSearchItem[]>([])
const drawerTasksLoading = ref(false)

const groupTaskBoard = ref<GroupTaskBoardResponse | null>(null)
const groupTaskLoading = ref(false)
const groupTaskSaving = ref(false)
const groupTaskTitle = ref('')
const groupTaskDescription = ref('')
const groupTaskPriority = ref<GroupTaskPriority>('MEDIUM')
const groupTaskDueTime = ref('')

let refreshTimer: ReturnType<typeof setTimeout> | null = null

const TEAM_EVENT_TYPES = [
  'GROUP_CREATED',
  'GROUP_RENAMED',
  'GROUP_UPDATED',
  'GROUP_ARCHIVED',
  'MEMBER_INVITED',
  'MEMBER_JOINED',
  'MEMBER_REMOVED',
  'INVITE_REJECTED',
  'INVITE_CANCELED',
  'OWNER_TRANSFERRED',
  'MEMBER_LEFT',
  'GROUP_TASK_CREATED',
  'GROUP_TASK_UPDATED',
  'GROUP_TASK_ASSIGNED',
  'GROUP_TASK_CLAIMED',
  'GROUP_TASK_DONE',
  'GROUP_TASK_DELETED'
]

const navItems: Array<{ key: WorkspaceTab; label: string }> = [
  { key: 'overview', label: '概览' },
  { key: 'members', label: '成员' },
  { key: 'activity', label: '动态' }
]

const digest = computed(() => center.value?.digest || {
  memberCount: 0,
  onlineCount: 0,
  ownerCount: 0,
  disabledCount: 0,
  pendingInviteCount: 0,
  groupCount: 0
})
const contacts = computed(() => center.value?.contacts || [])
const groups = computed(() => center.value?.groups || [])
const invites = computed(() => center.value?.invites || [])
const activities = computed(() => center.value?.activities || [])
const selectedGroup = computed(() => groups.value.find((item) => item.groupId === selectedGroupId.value) || null)
const selectedGroupContacts = computed(() => {
  if (!selectedGroupId.value) return []
  return contacts.value.filter((item) => item.groupIds.includes(selectedGroupId.value))
})
const manageableGroups = computed(() => groups.value.filter(canManageGroup))
const inviteGroupOptions = computed(() => manageableGroups.value.map((item) => ({ label: item.groupName, value: item.groupId })))
const recentlyActive = computed(() => contacts.value.filter((item) => Number(item.online) === 1 && Number(item.disabled) !== 1))
const actionableInvites = computed(() => invites.value.filter((item) => item.status === 'PENDING'))
const groupActivities = computed(() => {
  const g = selectedGroup.value
  if (!g) return []
  return activities.value.filter((item) => item.groupId === g.groupId)
})
const pendingMine = computed(() => myPendingInvites.value.filter((item) => item.status === 'PENDING'))
const urgentWorkCount = computed(() => myWork.value.filter(isUrgentWork).length)
const groupTaskColumns = computed(() => {
  const board = groupTaskBoard.value
  return [
    { key: 'TODO' as GroupTaskStatus, label: '待开始', items: board?.todo || [] },
    { key: 'DOING' as GroupTaskStatus, label: '进行中', items: board?.doing || [] },
    { key: 'DONE' as GroupTaskStatus, label: '已完成', items: board?.done || [] }
  ]
})
const taskAssigneeOptions = computed(() => selectedGroupContacts.value.map((member) => ({ label: member.username, value: member.userId })))

const groupFilterOptions = computed(() => [
  { label: '全部群组', value: 0 },
  ...groups.value.map((item) => ({ label: item.groupName, value: item.groupId }))
])
const roleFilterOptions = [
  { label: '全部角色', value: 'all' },
  { label: '负责人', value: 'OWNER' },
  { label: '成员', value: 'MEMBER' }
]
const statusFilterOptions = [
  { label: '全部状态', value: 'all' },
  { label: '在线', value: 'online' },
  { label: '离线', value: 'offline' }
]
const activityFilterChips: Array<{ key: ActivityCategory | 'all'; label: string }> = [
  { key: 'all', label: '全部' },
  { key: 'invite', label: '邀请' },
  { key: 'member', label: '成员' },
  { key: 'role', label: '权限' },
  { key: 'group', label: '群组' },
  { key: 'task', label: '任务' }
]

const drawerManageGroups = computed(() => {
  if (!drawerMember.value) return []
  return manageableGroups.value.filter((g) => (drawerMember.value?.groupIds || []).includes(g.groupId))
})

const filteredContacts = computed(() => {
  let list = contacts.value
  const query = memberQuery.value.trim().toLowerCase()
  if (query) {
    list = list.filter((item) => `${item.username} ${item.email} ${item.groupNames.join(' ')}`.toLowerCase().includes(query))
  }
  if (groupFilter.value) {
    list = list.filter((item) => item.groupIds.includes(groupFilter.value))
  }
  if (roleFilter.value !== 'all') {
    list = list.filter((item) => (item.roles || []).includes(roleFilter.value))
  }
  if (statusFilter.value === 'online') {
    list = list.filter((item) => Number(item.online) === 1)
  } else if (statusFilter.value === 'offline') {
    list = list.filter((item) => Number(item.online) !== 1)
  }
  return list
})

const groupedActivities = computed(() => {
  let list = activities.value
  if (activityFilter.value !== 'all') {
    list = list.filter((item) => activityCategory(item) === activityFilter.value)
  }
  const groups: Array<{ label: string; items: TeamActivityItem[] }> = [
    { label: '今天', items: [] },
    { label: '昨天', items: [] },
    { label: '更早', items: [] }
  ]
  const now = new Date()
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const startOfYesterday = startOfToday - 86400000
  for (const item of list) {
    const ts = Date.parse(item.createTime)
    if (!Number.isFinite(ts)) {
      groups[2].items.push(item)
      continue
    }
    if (ts >= startOfToday) groups[0].items.push(item)
    else if (ts >= startOfYesterday) groups[1].items.push(item)
    else groups[2].items.push(item)
  }
  return groups
})

function isTab(value: unknown): value is WorkspaceTab {
  return value === 'overview' || value === 'members' || value === 'groups' || value === 'activity'
}

function normalizeGroupId(value: unknown) {
  const id = Number(value || 0)
  return Number.isFinite(id) && id > 0 ? id : 0
}

function canManageGroup(group: TeamGroupItem) {
  return group.myRole === 'OWNER' && !group.systemGroup
}

function roleLabel(role?: string | null) {
  if (role === 'OWNER') return '负责人'
  if (role === 'ADMIN') return '管理员'
  return '成员'
}

function activityCategory(item: TeamActivityItem): ActivityCategory {
  const t = String(item.type || '').toUpperCase()
  if (t.startsWith('MEMBER_INVITE') || t.startsWith('INVITE_')) return 'invite'
  if (t.startsWith('MEMBER_')) return 'member'
  if (t === 'OWNER_TRANSFERRED') return 'role'
  if (t.startsWith('GROUP_TASK_')) return 'task'
  if (t.startsWith('GROUP_')) return 'group'
  return 'other'
}

function activityKindLabel(cat: ActivityCategory) {
  if (cat === 'invite') return '邀请'
  if (cat === 'member') return '成员'
  if (cat === 'role') return '权限'
  if (cat === 'group') return '群组'
  if (cat === 'task') return '任务'
  return '动态'
}

function activityAction(item: TeamActivityItem) {
  const t = String(item.type || '').toUpperCase()
  if (t === 'GROUP_CREATED') return '创建了群组'
  if (t === 'GROUP_RENAMED') return '重命名了群组'
  if (t === 'GROUP_ARCHIVED') return '归档了群组'
  if (t === 'MEMBER_INVITED') return '邀请了新成员'
  if (t === 'MEMBER_JOINED') return '加入了群组'
  if (t === 'MEMBER_REMOVED') return '移除了成员'
  if (t === 'OWNER_TRANSFERRED') return '转移了群组负责人'
  if (t === 'INVITE_REJECTED') return '拒绝了邀请'
  if (t === 'INVITE_CANCELED') return '取消了邀请'
  if (t === 'MEMBER_LEFT') return '退出了群组'
  if (t === 'GROUP_TASK_CREATED') return '创建了任务'
  if (t === 'GROUP_TASK_UPDATED') return '更新了任务'
  if (t === 'GROUP_TASK_ASSIGNED') return '分配了任务'
  if (t === 'GROUP_TASK_CLAIMED') return '认领了任务'
  if (t === 'GROUP_TASK_DONE') return '完成了任务'
  if (t === 'GROUP_TASK_DELETED') return '删除了任务'
  return String(item.detail || '团队动态更新')
}

function initials(name?: string | null) {
  return String(name || 'U').trim().slice(0, 1).toUpperCase()
}

function avatarTone(value: string | number) {
  const tones = ['blue', 'indigo', 'slate', 'cyan', 'violet']
  const text = String(value || '')
  let total = 0
  for (let i = 0; i < text.length; i += 1) total += text.charCodeAt(i)
  return tones[total % tones.length]
}

function fmtTime(value?: string | null) {
  if (!value) return '刚刚'
  const timestamp = Date.parse(value)
  if (!Number.isFinite(timestamp)) return String(value)
  const diff = Math.max(0, Date.now() - timestamp)
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  return `${Math.floor(hours / 24)} 天前`
}

function workStatusLabel(status: WorkspaceMyWorkItem['status']) {
  if (status === 'DOING') return '进行中'
  if (status === 'DONE') return '已完成'
  return '待开始'
}

function isUrgentWork(item: WorkspaceMyWorkItem) {
  if (!item.dueTime || item.status === 'DONE') return false
  const due = Date.parse(item.dueTime)
  return Number.isFinite(due) && due < Date.now() + 24 * 60 * 60 * 1000
}

function workDueLabel(item: WorkspaceMyWorkItem) {
  if (!item.dueTime) return '未设截止时间'
  const due = Date.parse(item.dueTime)
  if (!Number.isFinite(due)) return item.dueTime
  const diff = due - Date.now()
  if (diff < 0) return '已逾期'
  if (diff < 24 * 60 * 60 * 1000) return '今天截止'
  return '后续安排'
}

function taskNextStatus(status: GroupTaskStatus): GroupTaskStatus {
  if (status === 'TODO') return 'DOING'
  if (status === 'DOING') return 'DONE'
  return 'TODO'
}

function taskNextLabel(status: GroupTaskStatus) {
  if (status === 'TODO') return '开始'
  if (status === 'DOING') return '完成'
  return '重开'
}

function taskPriorityLabel(priority: GroupTaskPriority) {
  if (priority === 'HIGH') return '高'
  if (priority === 'LOW') return '低'
  return '中'
}

function taskDueLabel(task: GroupTaskItem) {
  if (!task.dueTime) return '未设截止'
  const due = Date.parse(task.dueTime)
  if (!Number.isFinite(due)) return '已设截止'
  if (task.overdue) return '已逾期'
  return new Date(due).toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' }) + ' 截止'
}

function syncRoute() {
  const query = { ...route.query }
  if (activeTab.value === 'overview') delete query.tab
  else query.tab = activeTab.value
  if (activeTab.value === 'groups' && selectedGroupId.value) query.groupId = String(selectedGroupId.value)
  else delete query.groupId
  void router.replace({ query })
}

function selectTab(tab: WorkspaceTab) {
  activeTab.value = tab
  if (tab === 'groups' && !selectedGroupId.value && groups.value[0]) {
    selectedGroupId.value = groups.value[0].groupId
    return
  }
  syncRoute()
}

function selectGroup(groupId: number) {
  selectedGroupId.value = groupId
  activeTab.value = 'groups'
  syncRoute()
}

async function load() {
  loading.value = true
  try {
    const team = await projectCollabApi.teamCenter(activeTab.value === 'groups' ? selectedGroupId.value || undefined : undefined)
    center.value = team
    if (selectedGroupId.value && !groups.value.some((item) => item.groupId === selectedGroupId.value)) {
      selectedGroupId.value = 0
    }
    const [work, mine] = await Promise.all([
      taskApi.myWork(6).catch(() => [] as WorkspaceMyWorkItem[]),
      projectCollabApi.teamInvitesMine().catch(() => [] as TeamInviteItem[])
    ])
    myWork.value = work
    myPendingInvites.value = mine
    await loadGroupTasks(activeTab.value === 'groups' ? selectedGroupId.value : 0)
  } catch (error: any) {
    message.error(error?.message || '协作中心加载失败')
  } finally {
    loading.value = false
  }
}

async function loadGroupTasks(groupId = selectedGroupId.value) {
  if (!groupId) {
    groupTaskBoard.value = null
    return
  }
  groupTaskLoading.value = true
  try {
    groupTaskBoard.value = await groupTaskApi.board(groupId)
  } catch (error: any) {
    groupTaskBoard.value = null
    message.error(error?.message || '群组任务加载失败')
  } finally {
    groupTaskLoading.value = false
  }
}

function clearGroupTaskDraft() {
  groupTaskTitle.value = ''
  groupTaskDescription.value = ''
  groupTaskPriority.value = 'MEDIUM'
  groupTaskDueTime.value = ''
}

async function createGroupTask() {
  const groupId = selectedGroupId.value
  const title = groupTaskTitle.value.trim()
  if (!groupId || !title) {
    message.warning('先写下任务标题')
    return
  }
  groupTaskSaving.value = true
  try {
    await groupTaskApi.create(groupId, {
      title,
      description: groupTaskDescription.value.trim() || undefined,
      priority: groupTaskPriority.value,
      dueTime: groupTaskDueTime.value || null
    })
    clearGroupTaskDraft()
    await loadGroupTasks(groupId)
    await load()
    message.success('任务已创建')
  } catch (error: any) {
    message.error(error?.message || '任务创建失败')
  } finally {
    groupTaskSaving.value = false
  }
}

async function changeGroupTaskStatus(task: GroupTaskItem, status: GroupTaskStatus) {
  if (!selectedGroupId.value) return
  try {
    await groupTaskApi.changeStatus(selectedGroupId.value, task.id, status)
    await loadGroupTasks(selectedGroupId.value)
  } catch (error: any) {
    message.error(error?.message || '任务状态更新失败')
  }
}

async function assignGroupTask(task: GroupTaskItem, assigneeId: number | null) {
  if (!selectedGroupId.value || !assigneeId) return
  try {
    await groupTaskApi.assign(selectedGroupId.value, task.id, assigneeId)
    await loadGroupTasks(selectedGroupId.value)
    await load()
  } catch (error: any) {
    message.error(error?.message || '任务指派失败')
  }
}

async function claimGroupTask(task: GroupTaskItem) {
  if (!selectedGroupId.value) return
  try {
    await groupTaskApi.claim(selectedGroupId.value, task.id)
    await loadGroupTasks(selectedGroupId.value)
    await load()
  } catch (error: any) {
    message.error(error?.message || '任务认领失败')
  }
}

function deleteGroupTask(task: GroupTaskItem) {
  if (!selectedGroupId.value) return
  dialog.warning({
    title: '删除任务',
    content: `确定删除「${task.title}」吗？删除后无法恢复。`,
    positiveText: '删除任务',
    negativeText: '返回',
    onPositiveClick: async () => {
      try {
        await groupTaskApi.delete(selectedGroupId.value!, task.id)
        await loadGroupTasks(selectedGroupId.value!)
        await load()
        message.success('任务已删除')
      } catch (error: any) {
        message.error(error?.message || '任务删除失败')
      }
    }
  })
}

function reissueInvite(invite: TeamInviteItem) {
  if (!selectedGroup.value) return
  void projectCollabApi.reissueTeamGroupInvite(selectedGroup.value.groupId, invite.id)
    .then(async (response) => {
      await copyText(response.inviteLink)
      message.success('新邀请链接已复制')
      await load()
    })
    .catch((error: any) => message.error(error?.message || '重新发送失败'))
}

function leaveSelectedGroup() {
  const group = selectedGroup.value
  if (!group) return
  dialog.warning({
    title: '退出群组',
    content: `确定退出「${group.groupName}」吗？退出后将无法继续查看群组任务。`,
    positiveText: '退出群组',
    negativeText: '返回',
    onPositiveClick: async () => {
      try {
        await projectCollabApi.leaveTeamGroup(group.groupId)
        selectedGroupId.value = 0
        await load()
        message.success('已退出群组')
      } catch (error: any) {
        message.error(error?.message || '退出失败')
      }
    }
  })
}

function openCreateGroup() {
  groupDialogMode.value = 'create'
  groupName.value = ''
  groupDialogOpen.value = true
}

function openRenameGroup() {
  if (!selectedGroup.value) return
  groupDialogMode.value = 'rename'
  groupName.value = selectedGroup.value.groupName
  groupDialogOpen.value = true
}

async function saveGroup() {
  const name = groupName.value.trim()
  if (!name) {
    message.error('请输入群组名称')
    return
  }
  groupSaving.value = true
  try {
    if (groupDialogMode.value === 'create') {
      const groupId = await projectCollabApi.createTeamGroup(name)
      selectedGroupId.value = groupId
      activeTab.value = 'groups'
      message.success('群组已创建')
    } else if (selectedGroup.value) {
      await projectCollabApi.renameTeamGroup(selectedGroup.value.groupId, name)
      message.success('群组名称已更新')
    }
    groupDialogOpen.value = false
    await load()
  } catch (error: any) {
    message.error(error?.message || '群组操作失败')
  } finally {
    groupSaving.value = false
  }
}

function openInvite(groupId?: number) {
  const target = groupId || (selectedGroup.value && canManageGroup(selectedGroup.value) ? selectedGroup.value.groupId : manageableGroups.value[0]?.groupId)
  if (!target) {
    message.error('请先创建或选择你负责的群组')
    return
  }
  inviteGroupId.value = target
  inviteEmail.value = ''
  inviteDialogOpen.value = true
}

async function copyText(value: string) {
  try {
    await navigator.clipboard.writeText(value)
  } catch {
    const node = document.createElement('textarea')
    node.value = value
    node.style.position = 'fixed'
    node.style.left = '-9999px'
    document.body.appendChild(node)
    node.select()
    document.execCommand('copy')
    document.body.removeChild(node)
  }
}

async function sendInvite() {
  if (!inviteGroupId.value || !inviteEmail.value.trim()) {
    message.error('请选择群组并输入邮箱')
    return
  }
  inviteSaving.value = true
  try {
    const response = await projectCollabApi.inviteToTeamGroup(inviteGroupId.value, inviteEmail.value.trim())
    await copyText(response.inviteLink)
    inviteDialogOpen.value = false
    message.success('邀请链接已复制')
    await load()
  } catch (error: any) {
    message.error(error?.message || '发送邀请失败')
  } finally {
    inviteSaving.value = false
  }
}

function cancelInvite(invite: TeamInviteItem) {
  dialog.warning({
    title: '取消邀请',
    content: `确定取消发给 ${invite.email} 的邀请吗？`,
    positiveText: '取消邀请',
    negativeText: '返回',
    onPositiveClick: async () => {
      try {
        await projectCollabApi.cancelTeamGroupInvite(invite.groupId, invite.id)
        await load()
        message.success('邀请已取消')
      } catch (error: any) {
        message.error(error?.message || '取消失败')
      }
    }
  })
}

function removeMember(member: TeamContactItem, group?: TeamGroupItem) {
  const g = group || selectedGroup.value
  if (!g) return
  dialog.warning({
    title: '移除成员',
    content: `确定将 ${member.username} 移出「${g.groupName}」吗？`,
    positiveText: '移除',
    negativeText: '返回',
    onPositiveClick: async () => {
      try {
        await projectCollabApi.removeTeamGroupMember(g.groupId, member.userId)
        await load()
        if (memberDrawerOpen.value) drawerMember.value = null
        memberDrawerOpen.value = false
        message.success('成员已移除')
      } catch (error: any) {
        message.error(error?.message || '移除失败')
      }
    }
  })
}

function transferOwnership(member: TeamContactItem, group?: TeamGroupItem) {
  const g = group || selectedGroup.value
  if (!g) return
  dialog.warning({
    title: '转让群组负责人',
    content: `确定将「${g.groupName}」交由 ${member.username} 负责吗？`,
    positiveText: '确认转让',
    negativeText: '返回',
    onPositiveClick: async () => {
      try {
        await projectCollabApi.transferTeamGroupOwner(g.groupId, member.userId)
        await load()
        message.success('负责人已更新')
      } catch (error: any) {
        message.error(error?.message || '转让失败')
      }
    }
  })
}

function deleteGroup() {
  if (!selectedGroup.value) return
  dialog.warning({
    title: '归档群组',
    content: `归档「${selectedGroup.value.groupName}」后，成员和历史任务会保留，但群组将停止新增协作。`,
    positiveText: '归档群组',
    negativeText: '返回',
    onPositiveClick: async () => {
      try {
        await projectCollabApi.deleteTeamGroup(selectedGroup.value!.groupId)
        selectedGroupId.value = 0
        activeTab.value = 'groups'
        await load()
        message.success('群组已归档')
      } catch (error: any) {
        message.error(error?.message || '删除失败')
      }
    }
  })
}

function clearGroupActivities() {
  if (!selectedGroup.value || !canManageGroup(selectedGroup.value)) return
  dialog.warning({
    title: '清空动态',
    content: `确定清空「${selectedGroup.value.groupName}」的全部动态记录吗？`,
    positiveText: '清空',
    negativeText: '返回',
    onPositiveClick: async () => {
      try {
        await projectCollabApi.clearTeamActivities(selectedGroup.value!.groupId)
        await load()
        message.success('动态已清空')
      } catch (error: any) {
        message.error(error?.message || '清空失败')
      }
    }
  })
}

function deleteActivity(item: TeamActivityItem) {
  dialog.warning({
    title: '删除动态',
    content: '确定删除这条动态记录吗？',
    positiveText: '删除',
    negativeText: '返回',
    onPositiveClick: async () => {
      try {
        await projectCollabApi.deleteTeamActivity(item.groupId, item.id)
        await load()
        message.success('动态已删除')
      } catch (error: any) {
        message.error(error?.message || '删除失败')
      }
    }
  })
}

function canDeleteActivity(item: TeamActivityItem) {
  const g = groups.value.find((x) => x.groupId === item.groupId)
  return !!g && canManageGroup(g)
}

function openWork(item: WorkspaceMyWorkItem) {
  void router.push({ name: 'task-detail', params: { projectId: item.projectId, taskId: item.taskId } })
}

function openTask(item: TaskSearchItem) {
  void router.push({ name: 'task-detail', params: { projectId: item.projectId, taskId: item.id } })
}

async function openMember(member: TeamContactItem) {
  drawerMember.value = member
  drawerTasks.value = []
  memberDrawerOpen.value = true
  drawerTasksLoading.value = true
  try {
    const res = await taskApi.search({ assigneeId: member.userId, pageSize: 5 })
    drawerTasks.value = res.items || []
  } catch {
    drawerTasks.value = []
  } finally {
    drawerTasksLoading.value = false
  }
}

async function handleMineInvite(invite: TeamInviteItem, accept: boolean) {
  try {
    if (accept) {
      const groupId = await projectCollabApi.acceptTeamGroupInviteById(invite.id)
      message.success('已加入群组')
      selectedGroupId.value = groupId
      activeTab.value = 'groups'
    } else {
      await projectCollabApi.rejectTeamGroupInviteById(invite.id)
      message.success('已拒绝邀请')
    }
    await Promise.all([load(), Promise.resolve()])
    syncRoute()
  } catch (error: any) {
    message.error(error?.message || '操作失败')
  }
}

watch(
  () => route.query.tab,
  (value) => {
    if (isTab(value)) activeTab.value = value
  },
  { immediate: true }
)

watch(
  () => route.query.groupId,
  (value) => {
    const next = normalizeGroupId(value)
    if (next !== selectedGroupId.value) selectedGroupId.value = next
  },
  { immediate: true }
)

watch([activeTab, selectedGroupId], () => {
  syncRoute()
  void load()
})

let localActivitySeq = 0

function scheduleTeamRefresh() {
  if (refreshTimer) clearTimeout(refreshTimer)
  refreshTimer = setTimeout(() => {
    void load()
  }, 600)
}

function applyTeamEvent(msg: RealtimeServerMessage | null) {
  const t = String(msg?.type || '')
  if (!TEAM_EVENT_TYPES.includes(t)) return
  let payload: Record<string, unknown> = {}
  try {
    payload = msg?.payloadJson ? (JSON.parse(String(msg.payloadJson)) as Record<string, unknown>) : {}
  } catch {
    payload = {}
  }
  const groupId = Number(payload.groupId || 0)
  if (!groupId || !center.value) {
    scheduleTeamRefresh()
    return
  }
  // 动态本地插入（负 id 占位，防抖兜底会以服务端数据修正）
  const groupName = groups.value.find((g) => g.groupId === groupId)?.groupName || String(payload.groupName || '群组')
  const username = String(payload.username || '') || undefined
  center.value.activities = [
    {
      id: -10000 - (++localActivitySeq),
      groupId,
      groupName,
      actorUsername: username,
      type: t,
      detail: String(payload.detail || ''),
      createTime: new Date().toISOString()
    },
    ...(center.value.activities || [])
  ]
  // 群组数字增量
  const g = groups.value.find((x) => x.groupId === groupId)
  if (g) {
    if (t === 'MEMBER_JOINED') {
      g.memberCount += 1
      g.pendingInviteCount = Math.max(0, g.pendingInviteCount - 1)
    } else if (t === 'MEMBER_REMOVED') {
      g.memberCount = Math.max(0, g.memberCount - 1)
    } else if (t === 'MEMBER_INVITED') {
      g.pendingInviteCount += 1
    } else if (t === 'INVITE_REJECTED' || t === 'INVITE_CANCELED') {
      g.pendingInviteCount = Math.max(0, g.pendingInviteCount - 1)
    }
  }
  // 全局统计增量
  const d = center.value.digest
  if (d) {
    if (t === 'MEMBER_JOINED') d.memberCount += 1
    else if (t === 'MEMBER_REMOVED') d.memberCount = Math.max(0, d.memberCount - 1)
    else if (t === 'MEMBER_INVITED') d.pendingInviteCount += 1
    else if (t === 'INVITE_REJECTED' || t === 'INVITE_CANCELED') d.pendingInviteCount = Math.max(0, d.pendingInviteCount - 1)
  }
  scheduleTeamRefresh()
}

watch(
  () => rt.lastEvent,
  (msg) => applyTeamEvent(msg)
)

// 在线成员增量：团队在线快照驱动成员在线状态与概览"近期活跃"
watch(
  () => rt.teamPresence,
  (list) => {
    if (!center.value) return
    const onlineIds = new Set(list.map((m) => Number(m.userId)))
    for (const c of center.value.contacts) {
      c.online = onlineIds.has(Number(c.userId)) ? 1 : 0
    }
  },
  { deep: true }
)

watch(
  () => rt.connected,
  (v) => {
    if (v) rt.subscribeTeam()
  },
  { immediate: true }
)

onMounted(() => {
  void load()
})

onBeforeUnmount(() => {
  rt.unsubscribeTeam()
  if (refreshTimer) clearTimeout(refreshTimer)
})
</script>

<template>
  <div class="cc">
    <div class="ccGrid3">
      <!-- 第 1 列：导航 + 群组列表 -->
      <aside class="ccCol ccCol1">
        <nav class="ccNav" aria-label="协作导航">
          <button
            v-for="item in navItems"
            :key="item.key"
            type="button"
            class="ccNavItem"
            :class="{ active: activeTab === item.key && !selectedGroupId }"
            @click="selectTab(item.key)"
          >
            <svg v-if="item.key === 'overview'" class="ccNavIcon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M4.5 6.5A2 2 0 0 1 6.5 4.5h11a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2v-11Z" stroke="currentColor" stroke-width="1.6" />
              <path d="M8.5 9.5h7M8.5 13h4.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            </svg>
            <svg v-else-if="item.key === 'members'" class="ccNavIcon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M8.5 9.2a2.7 2.7 0 1 1 5.4 0 2.7 2.7 0 0 1-5.4 0Z" stroke="currentColor" stroke-width="1.6" />
              <path d="M4.8 17.8c.9-2.3 3-3.7 5.9-3.7 1.1 0 2 .2 2.9.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            </svg>
            <svg v-else class="ccNavIcon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M5 5.5h14M5 12h14M5 18.5h9" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            </svg>
            <span class="ccNavLabel">{{ item.label }}</span>
            <b v-if="item.key === 'overview'" class="ccNavCount">{{ pendingMine.length + urgentWorkCount }}</b>
            <b v-else-if="item.key === 'members'" class="ccNavCount">{{ digest.memberCount }}</b>
            <b v-else class="ccNavCount">{{ activities.length }}</b>
          </button>
        </nav>

        <div class="ccSideRule" />

        <div class="ccGroups">
          <div class="ccGroupsHead">
            <span class="ccGroupsTitle">团队群组</span>
            <button class="ccGroupsAdd" type="button" aria-label="新建群组" @click="openCreateGroup">
              <svg viewBox="0 0 16 16" width="12" height="12" fill="none" aria-hidden="true">
                <path d="M8 3v10M3 8h10" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
              </svg>
            </button>
          </div>
          <div v-if="groups.length" class="ccGroupList">
            <button
              v-for="group in groups"
              :key="group.groupId"
              type="button"
              class="ccGroupItem"
              :class="{ active: selectedGroupId === group.groupId }"
              @click="selectGroup(group.groupId)"
            >
              <span class="ccAvatar">{{ initials(group.groupName) }}</span>
              <span class="ccGroupCopy">
                <strong>{{ group.groupName }}</strong>
                <small>{{ group.memberCount }} 人</small>
              </span>
              <span v-if="group.pendingInviteCount" class="ccGroupBadge">{{ group.pendingInviteCount }}</span>
            </button>
          </div>
          <div v-else class="ccSideEmpty">暂无群组，点击 + 新建</div>
        </div>
      </aside>

      <!-- 主内容 -->
      <main class="ccMain">
        <n-spin :show="loading">
          <!-- 概览 -->
          <section v-if="activeTab === 'overview'" class="ccPane">
            <div class="ccStats ccCard">
              <div class="ccStat">
                <strong>{{ digest.memberCount }}</strong>
                <span>成员</span>
              </div>
              <div class="ccStat">
                <strong>{{ digest.groupCount }}</strong>
                <span>群组</span>
              </div>
              <div class="ccStat">
                <strong>{{ recentlyActive.length }}</strong>
                <span>在线</span>
              </div>
              <div class="ccStat" :class="{ warn: digest.pendingInviteCount > 0 }">
                <strong>{{ digest.pendingInviteCount }}</strong>
                <span>待处理</span>
              </div>
            </div>

            <section class="ccSec ccCard">
              <div class="ccSecHead">
                <h3 class="ccSecTitle">待处理</h3>
                <button class="ccTextLink" type="button" @click="router.push({ name: 'workspace' })">进入工作台</button>
              </div>
              <div v-if="pendingMine.length" class="ccList">
                <div v-for="invite in pendingMine.slice(0, 3)" :key="invite.id" class="ccListRow">
                  <span class="ccAvatar">{{ initials(invite.groupName) }}</span>
                  <span class="ccListCopy">
                    <strong>{{ invite.groupName }}</strong>
                    <small>待接受邀请</small>
                  </span>
                  <span class="ccListOps">
                    <button class="ccTextLink" type="button" @click="handleMineInvite(invite, false)">拒绝</button>
                    <button class="ccTextLink ccTextLinkPrimary" type="button" @click="handleMineInvite(invite, true)">接受</button>
                  </span>
                </div>
              </div>
              <div v-if="myWork.length" class="ccList">
                <button v-for="item in myWork.slice(0, pendingMine.length ? 2 : 4)" :key="item.taskId" class="ccListRow" type="button" @click="openWork(item)">
                  <span class="ccWorkDot" :class="item.status.toLowerCase()" />
                  <span class="ccListCopy">
                    <strong>{{ item.title }}</strong>
                    <small>{{ item.projectName || '项目任务' }}</small>
                  </span>
                  <span class="ccWorkStatus" :class="{ urgent: isUrgentWork(item) }">
                    {{ isUrgentWork(item) ? '临近截止' : workStatusLabel(item.status) }}
                  </span>
                </button>
              </div>
              <div v-if="!pendingMine.length && !myWork.length" class="ccEmpty">暂无待处理事项</div>
            </section>

            <section class="ccSec ccCard">
              <div class="ccSecHead">
                <h3 class="ccSecTitle">动态</h3>
                <button class="ccTextLink" type="button" @click="selectTab('activity')">查看全部</button>
              </div>
              <div v-if="activities.length" class="ccList">
                <div v-for="item in activities.slice(0, 6)" :key="item.id" class="ccListRow">
                  <span class="ccListCopy">
                    <strong>{{ item.actorUsername || '团队成员' }}</strong>
                    <span class="ccMuted">{{ activityAction(item) }}</span>
                  </span>
                  <time class="ccTime">{{ fmtTime(item.createTime) }}</time>
                </div>
              </div>
              <div v-else class="ccEmpty">暂无协作动态</div>
            </section>

            <div class="ccTwoCol ccCard">
              <section class="ccSec">
                <div class="ccSecHead">
                  <h3 class="ccSecTitle">群组</h3>
                  <button class="ccTextLink" type="button" @click="openCreateGroup">新建</button>
                </div>
                <div v-if="groups.length" class="ccList">
                  <button v-for="group in groups" :key="group.groupId" class="ccListRow" type="button" @click="selectGroup(group.groupId)">
                    <span class="ccAvatar">{{ initials(group.groupName) }}</span>
                    <span class="ccListCopy">
                      <strong>{{ group.groupName }}</strong>
                      <small>{{ group.memberCount }} 人 · {{ group.onlineCount }} 在线</small>
                    </span>
                    <svg class="ccChevron" viewBox="0 0 20 20" fill="none" aria-hidden="true">
                      <path d="m8 5 5 5-5 5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
                    </svg>
                  </button>
                </div>
                <div v-else class="ccEmpty">暂无群组</div>
              </section>

              <section class="ccSec">
                <div class="ccSecHead">
                  <h3 class="ccSecTitle">成员</h3>
                  <button class="ccTextLink" type="button" @click="selectTab('members')">查看全部</button>
                </div>
                <div class="ccList">
                  <button v-for="member in contacts.slice(0, 8)" :key="member.userId" class="ccListRow" type="button" @click="openMember(member)">
                    <span class="ccAvatar">{{ initials(member.username) }}</span>
                    <span class="ccListCopy">
                      <strong>{{ member.username }}</strong>
                      <small>{{ Number(member.online) === 1 ? '在线' : fmtTime(member.lastSeenAt) }}</small>
                    </span>
                    <i class="ccOnlineDot" :class="{ on: Number(member.online) === 1 }" />
                  </button>
                </div>
              </section>
            </div>
          </section>

          <!-- 成员通讯录 -->
          <section v-else-if="activeTab === 'members'" class="ccPane ccPaneFill">
            <section class="ccSec ccCard">
              <div class="ccSecHead">
                <h3 class="ccSecTitle">成员 <em>{{ filteredContacts.length }}</em></h3>
                <button class="ccBtn ccBtnPrimary" type="button" @click="openInvite()">
                  <svg class="ccBtnIcon" viewBox="0 0 20 20" fill="none" aria-hidden="true">
                    <path d="M10 4.5v11M4.5 10h11" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
                  </svg>
                  邀请成员
                </button>
              </div>
              <div class="ccFilters">
                <n-input v-model:value="memberQuery" clearable placeholder="搜索姓名、邮箱或群组" class="ccSearch" />
                <n-select v-model:value="groupFilter" :options="groupFilterOptions" class="ccFilter" />
                <n-select v-model:value="roleFilter" :options="roleFilterOptions" class="ccFilter" />
                <n-select v-model:value="statusFilter" :options="statusFilterOptions" class="ccFilter" />
              </div>
              <div class="ccTable">
                <div class="ccTableRow ccTableHead">
                  <span>成员</span>
                  <span>角色</span>
                  <span>群组</span>
                  <span>状态</span>
                  <span></span>
                </div>
                <div v-for="member in filteredContacts" :key="member.userId" class="ccTableRow">
                  <button class="ccIdentity" type="button" @click="openMember(member)">
                    <span class="ccAvatar">{{ initials(member.username) }}</span>
                    <span class="ccIdentityCopy">
                      <strong>{{ member.username }}</strong>
                      <small>{{ member.email || '未填写邮箱' }}</small>
                    </span>
                  </button>
                  <span class="ccRole">{{ roleLabel((member.roles || [])[0]) }}</span>
                  <span class="ccGroupsText">{{ member.groupNames.join(' · ') || '未分组' }}</span>
                  <span class="ccState" :class="{ on: Number(member.online) === 1 }">
                    <i class="ccOnlineDot" :class="{ on: Number(member.online) === 1 }" />
                    {{ Number(member.online) === 1 ? '在线' : fmtTime(member.lastSeenAt) }}
                  </span>
                  <span class="ccTableOps">
                    <button class="ccTextBtn" type="button" @click="openMember(member)">查看</button>
                  </span>
                </div>
                <div v-if="!filteredContacts.length" class="ccEmpty ccEmptyTall">没有匹配的成员</div>
              </div>
            </section>
          </section>

          <!-- 群组空间 -->
          <section v-else-if="activeTab === 'groups'" class="ccPane">
            <template v-if="selectedGroup">
              <div class="ccGroupHead ccCard">
                <div class="ccGroupHeadIdentity">
                  <span class="ccAvatar ccAvatarLg">{{ initials(selectedGroup.groupName) }}</span>
                  <div class="ccGroupHeadCopy">
                    <h2>{{ selectedGroup.groupName }}</h2>
                  </div>
                </div>
                <div class="ccGroupHeadActions">
                  <button v-if="canManageGroup(selectedGroup)" class="ccBtn ccBtnPrimary" type="button" @click="openInvite(selectedGroup.groupId)">邀请成员</button>
                  <button v-if="canManageGroup(selectedGroup)" class="ccBtn ccBtnGhost" type="button" @click="openRenameGroup">重命名</button>
                  <button v-else-if="!selectedGroup.systemGroup" class="ccBtn ccBtnGhost" type="button" @click="leaveSelectedGroup">退出群组</button>
                </div>
              </div>

              <!-- 任务看板 -->
              <section class="ccSec ccCard">
                <div class="ccSecHead">
                  <h3 class="ccSecTitle">任务 <em>{{ groupTaskBoard?.total || 0 }} 条 · 完成 {{ groupTaskBoard?.completionRate || 0 }}%</em></h3>
                  <span v-if="groupTaskBoard?.overdueCount" class="ccOverdue">{{ groupTaskBoard.overdueCount }} 条逾期</span>
                </div>

                <form class="ccComposer" @submit.prevent="createGroupTask">
                  <n-input v-model:value="groupTaskTitle" size="small" placeholder="添加一条团队任务…" class="ccComposerTitle" />
                  <n-select v-model:value="groupTaskPriority" size="small" :options="[{ label: '高优先级', value: 'HIGH' }, { label: '中优先级', value: 'MEDIUM' }, { label: '低优先级', value: 'LOW' }]" class="ccComposerPriority" />
                  <input v-model="groupTaskDueTime" class="ccComposerDue" type="datetime-local" aria-label="截止时间" />
                  <button class="ccBtn ccBtnPrimary ccBtnSmall" type="submit" :disabled="groupTaskSaving">创建任务</button>
                </form>
                <n-input v-model:value="groupTaskDescription" class="ccComposerDesc" size="small" placeholder="补充说明（可选）" />

                <n-spin :show="groupTaskLoading">
                  <div class="ccBoard">
                    <section v-for="column in groupTaskColumns" :key="column.key" class="ccColumn">
                      <div class="ccColumnHead">
                        <span class="ccColumnTitle"><i :class="`ccColumnDot ${column.key.toLowerCase()}`" />{{ column.label }}</span>
                        <b class="ccColumnCount">{{ column.items.length }}</b>
                      </div>
                      <div v-if="column.items.length" class="ccColumnList">
                        <article v-for="task in column.items" :key="task.id" class="ccTask" :class="{ overdue: task.overdue }">
                          <div class="ccTaskTop">
                            <strong>{{ task.title }}</strong>
                            <span class="ccPriority" :class="task.priority.toLowerCase()">{{ taskPriorityLabel(task.priority) }}</span>
                          </div>
                          <p v-if="task.description" class="ccTaskDesc">{{ task.description }}</p>
                          <div class="ccTaskMeta">
                            <span class="ccTaskAssignee" :class="{ unassigned: !task.assigneeId }">
                              {{ task.assigneeUsername || '未指派' }}
                            </span>
                            <span :class="{ overdue: task.overdue }">{{ taskDueLabel(task) }}</span>
                          </div>
                          <div class="ccTaskOps">
                            <n-select
                              v-if="canManageGroup(selectedGroup)"
                              size="tiny"
                              :value="task.assigneeId"
                              :options="taskAssigneeOptions"
                              placeholder="指派"
                              class="ccTaskAssign"
                              @update:value="(value) => assignGroupTask(task, value)"
                            />
                            <button v-if="!task.assigneeId" class="ccTaskBtn" type="button" @click="claimGroupTask(task)">认领</button>
                            <button class="ccTaskBtn" type="button" @click="changeGroupTaskStatus(task, taskNextStatus(task.status))">{{ taskNextLabel(task.status) }}</button>
                            <button class="ccTaskBtn ccTaskBtnDanger" type="button" @click="deleteGroupTask(task)">删除</button>
                          </div>
                        </article>
                      </div>
                      <div v-else class="ccColumnEmpty">暂无任务</div>
                    </section>
                  </div>
                </n-spin>
              </section>

              <div class="ccTwoCol ccCard">
                <section class="ccSec">
                  <div class="ccSecHead">
                    <h3 class="ccSecTitle">成员 <em>{{ selectedGroupContacts.length }}</em></h3>
                  </div>
                  <div v-if="selectedGroupContacts.length" class="ccTable ccTableCompact">
                    <div v-for="member in selectedGroupContacts" :key="member.userId" class="ccTableRow">
                      <button class="ccIdentity" type="button" @click="openMember(member)">
                        <span class="ccAvatar">{{ initials(member.username) }}</span>
                        <span class="ccIdentityCopy">
                          <strong>{{ member.username }}</strong>
                          <small>{{ member.email || '未填写邮箱' }}</small>
                        </span>
                      </button>
                      <span class="ccRole">{{ roleLabel((member.roles || [])[0]) }}</span>
                      <span v-if="canManageGroup(selectedGroup) && member.userId !== selectedGroup.ownerUserId" class="ccTableOps">
                        <button class="ccTextBtn" type="button" @click="transferOwnership(member)">设负责人</button>
                        <button class="ccTextBtn ccTextBtnDanger" type="button" @click="removeMember(member)">移除</button>
                      </span>
                    </div>
                  </div>
                  <div v-else class="ccEmpty">暂无成员</div>
                </section>

                <section class="ccSec">
                  <div class="ccSecHead">
                    <h3 class="ccSecTitle">待接受邀请 <em>{{ actionableInvites.length }}</em></h3>
                  </div>
                  <div v-if="actionableInvites.length" class="ccList">
                    <div v-for="invite in actionableInvites" :key="invite.id" class="ccListRow">
                      <span class="ccListCopy">
                        <strong>{{ invite.email }}</strong>
                        <small>{{ fmtTime(invite.createTime) }} 发出</small>
                      </span>
                      <span v-if="canManageGroup(selectedGroup)" class="ccListOps">
                        <button class="ccTextLink" type="button" @click="reissueInvite(invite)">重发</button>
                        <button class="ccTextLink ccTextLinkDanger" type="button" @click="cancelInvite(invite)">取消</button>
                      </span>
                    </div>
                  </div>
                  <div v-else class="ccEmpty">暂无待接受邀请</div>
                </section>
              </div>

              <section class="ccSec ccCard">
                <div class="ccSecHead">
                  <h3 class="ccSecTitle">群组动态</h3>
                  <div class="ccCardOps">
                    <button v-if="canManageGroup(selectedGroup)" class="ccTextLink ccTextLinkDanger" type="button" @click="clearGroupActivities">清空</button>
                    <button class="ccTextLink" type="button" @click="selectTab('activity')">全部动态</button>
                  </div>
                </div>
                <div v-if="groupActivities.length" class="ccList">
                  <div v-for="item in groupActivities.slice(0, 8)" :key="item.id" class="ccListRow">
                    <span class="ccListCopy">
                      <strong>{{ item.actorUsername || '团队成员' }}</strong>
                      <span class="ccMuted">{{ activityAction(item) }}</span>
                    </span>
                    <time class="ccTime">{{ fmtTime(item.createTime) }}</time>
                  </div>
                </div>
                <div v-else class="ccEmpty">暂无群内动态</div>
              </section>

              <div v-if="canManageGroup(selectedGroup)" class="ccDangerZone ccCard">
                <span class="ccDangerCopy">
                  <strong>归档群组</strong>
                </span>
                <button type="button" class="ccBtn ccBtnDangerGhost" @click="deleteGroup">归档群组</button>
              </div>
            </template>

            <div v-else class="ccEmptyState">
              <strong>选择一个群组</strong>
              <button class="ccBtn ccBtnPrimary" type="button" @click="openCreateGroup">新建群组</button>
            </div>
          </section>

          <!-- 协作动态 -->
          <section v-else class="ccPane ccPaneFill">
            <section class="ccSec ccCard">
              <div class="ccSecHead">
                <h3 class="ccSecTitle">动态</h3>
              </div>
              <div class="ccChips">
                <button
                  v-for="chip in activityFilterChips"
                  :key="chip.key"
                  type="button"
                  class="ccChip"
                  :class="{ active: activityFilter === chip.key }"
                  @click="activityFilter = chip.key"
                >
                  {{ chip.label }}
                </button>
              </div>
              <div v-if="activities.length" class="ccTimeline">
                <template v-for="group in groupedActivities" :key="group.label">
                  <div v-if="group.items.length" class="ccTimelineDay">
                    <div class="ccDayLabel">{{ group.label }}</div>
                    <div v-for="item in group.items" :key="item.id" class="ccTimelineRow">
                      <span class="ccTimelineDot" />
                      <span class="ccTimelineCopy">
                        <strong>{{ item.actorUsername || '团队成员' }}</strong>
                        <span>{{ activityAction(item) }}</span>
                        <small>{{ item.groupName }}</small>
                      </span>
                      <time class="ccTimelineTime">{{ fmtTime(item.createTime) }}</time>
                      <button v-if="canDeleteActivity(item)" class="ccTextBtn ccTextBtnDanger" type="button" @click="deleteActivity(item)">删除</button>
                    </div>
                  </div>
                </template>
              </div>
              <div v-else class="ccEmpty ccEmptyTall">暂无协作动态</div>
            </section>
          </section>
        </n-spin>
      </main>
    </div>

    <!-- 成员资料抽屉 -->
    <n-drawer v-model:show="memberDrawerOpen" :width="400" placement="right">
      <n-drawer-content :title="drawerMember?.username || '成员资料'">
        <div v-if="drawerMember" class="ccDrawer">
          <div class="ccDrawerIdentity">
            <span class="ccAvatar ccAvatarXl">{{ initials(drawerMember.username) }}</span>
            <div class="ccDrawerCopy">
              <strong>{{ drawerMember.username }}</strong>
              <small>{{ drawerMember.email || '未填写邮箱' }}</small>
              <span class="ccState" :class="{ on: Number(drawerMember.online) === 1 }">
                <i class="ccOnlineDot" :class="{ on: Number(drawerMember.online) === 1 }" />
                {{ Number(drawerMember.online) === 1 ? '在线' : fmtTime(drawerMember.lastSeenAt) }}
              </span>
            </div>
          </div>

          <div class="ccDrawerSection">
            <div class="ccDrawerTitle">所属群组</div>
            <div v-if="drawerMember.groupNames.length" class="ccChips">
              <button v-for="(name, index) in drawerMember.groupNames" :key="name" type="button" class="ccChip" @click="selectGroup(drawerMember.groupIds[index])">
                {{ name }}
              </button>
            </div>
            <span v-else class="ccMuted">未加入任何群组</span>
          </div>

          <div class="ccDrawerSection">
            <div class="ccDrawerTitle">在办任务</div>
            <n-spin :show="drawerTasksLoading">
              <div v-if="drawerTasks.length" class="ccFocusList">
                <button v-for="task in drawerTasks" :key="task.id" type="button" class="ccFocusRow ccFocusRowTask" @click="openTask(task)">
                  <span class="ccWorkDot" :class="task.status.toLowerCase()" />
                  <span class="ccFocusCopy">
                    <strong>{{ task.title }}</strong>
                    <small>{{ task.projectName }}</small>
                  </span>
                  <span class="ccWorkStatus" :class="task.status.toLowerCase()">{{ workStatusLabel(task.status) }}</span>
                </button>
              </div>
              <div v-else-if="!drawerTasksLoading" class="ccEmpty">暂无进行中的任务</div>
            </n-spin>
          </div>

          <div v-if="drawerManageGroups.length" class="ccDrawerSection">
            <div class="ccDrawerTitle">管理（我负责的群组）</div>
            <div v-for="g in drawerManageGroups" :key="g.groupId" class="ccDrawerManage">
              <span class="ccDrawerManageCopy">
                <strong>{{ g.groupName }}</strong>
                <small>{{ roleLabel(g.myRole) }}</small>
              </span>
              <span class="ccFocusActions">
                <button class="ccTextBtn" type="button" @click="transferOwnership(drawerMember, g)">设负责人</button>
                <button class="ccTextBtn ccTextBtnDanger" type="button" @click="removeMember(drawerMember, g)">移除</button>
              </span>
            </div>
          </div>
        </div>
      </n-drawer-content>
    </n-drawer>

    <!-- 新建 / 重命名群组 -->
    <n-modal v-model:show="groupDialogOpen" :mask-closable="!groupSaving">
      <div class="ccDialog" role="dialog" aria-modal="true">
        <header class="ccDialogHead">
          <h2>{{ groupDialogMode === 'create' ? '新建群组' : '重命名群组' }}</h2>
          <button class="ccDialogClose" type="button" aria-label="关闭" @click="groupDialogOpen = false">
            <svg viewBox="0 0 16 16" width="14" height="14" fill="none" aria-hidden="true">
              <path d="m4 4 8 8M12 4l-8 8" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
            </svg>
          </button>
        </header>
        <div class="ccDialogBody">
          <label class="ccField">
            <span>群组名称</span>
            <n-input v-model:value="groupName" maxlength="64" placeholder="输入群组名称" @keyup.enter="saveGroup" />
          </label>
        </div>
        <footer class="ccDialogFoot">
          <button class="ccBtn ccBtnGhost" type="button" :disabled="groupSaving" @click="groupDialogOpen = false">取消</button>
          <button class="ccBtn ccBtnPrimary" type="button" :disabled="groupSaving" @click="saveGroup">{{ groupDialogMode === 'create' ? '创建群组' : '保存更改' }}</button>
        </footer>
      </div>
    </n-modal>

    <!-- 邀请成员 -->
    <n-modal v-model:show="inviteDialogOpen" :mask-closable="!inviteSaving">
      <div class="ccDialog" role="dialog" aria-modal="true">
        <header class="ccDialogHead">
          <h2>邀请成员</h2>
          <button class="ccDialogClose" type="button" aria-label="关闭" @click="inviteDialogOpen = false">
            <svg viewBox="0 0 16 16" width="14" height="14" fill="none" aria-hidden="true">
              <path d="m4 4 8 8M12 4l-8 8" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
            </svg>
          </button>
        </header>
        <div class="ccDialogBody">
          <label class="ccField">
            <span>加入群组</span>
            <n-select v-model:value="inviteGroupId" :options="inviteGroupOptions" placeholder="选择群组" />
          </label>
          <label class="ccField">
            <span>成员邮箱</span>
            <n-input v-model:value="inviteEmail" placeholder="输入邮箱地址" @keyup.enter="sendInvite" />
          </label>
        </div>
        <footer class="ccDialogFoot">
          <button class="ccBtn ccBtnGhost" type="button" @click="inviteDialogOpen = false">取消</button>
          <button class="ccBtn ccBtnPrimary" type="button" :disabled="inviteSaving" @click="sendInvite">发送邀请</button>
        </footer>
      </div>
    </n-modal>
  </div>

</template>

<style scoped>
.cc {
  height: 100%;
  min-width: 0;
  width: 100%;
  box-sizing: border-box;
  display: flex;
}

/* ---- 顶部操作条：只放操作按钮 ---- */
/* ---- 按钮 ---- */
.ccBtn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 32px;
  padding: 0 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  transition: background 0.15s ease, border-color 0.15s ease;
  white-space: nowrap;
}
.ccBtnIcon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}
.ccBtnPrimary {
  background: #1f2329;
  color: #fff;
}
.ccBtnPrimary:hover {
  background: #000;
}
.ccBtnGhost {
  background: #fff;
  color: #374151;
  border-color: #e5e7eb;
}
.ccBtnGhost:hover {
  background: #f9fafb;
  border-color: #d1d5db;
}
.ccBtnDangerGhost {
  background: #fff;
  color: #b91c1c;
  border-color: #fecaca;
}
.ccBtnDangerGhost:hover {
  background: #fef2f2;
  border-color: #f87171;
}
.ccBtnSmall {
  height: 30px;
  padding: 0 12px;
  font-size: 12.5px;
}
.ccBtn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* ---- 布局：三列中的第 2/3 列（外层左栏为第 1 列） ---- */
.ccGrid3 {
  flex: 1;
  display: flex;
  min-width: 0;
  min-height: 0;
}

/* ---- 第 2 列：页内左栏，白底与全局左栏视觉连续 ---- */
.ccCol1 {
  flex-shrink: 0;
  width: 220px;
  background: #fff;
  border-right: 1px solid #e7e9ed;
  overflow: hidden;
  padding: 16px 10px 24px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.ccNav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ccNavItem {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 10px;
  border: 0;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  text-align: left;
  transition: background 0.15s ease;
}
.ccNavItem:hover {
  background: #f3f4f6;
}
.ccNavItem.active {
  background: #f1f5f9;
  color: #111827;
  font-weight: 600;
}
.ccNavIcon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}
.ccNavLabel {
  flex: 1;
}
.ccNavCount {
  font-size: 11px;
  font-weight: 600;
  color: #6b7280;
  background: #f3f4f6;
  min-width: 18px;
  height: 18px;
  border-radius: 9px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
}
.ccNavItem.active .ccNavCount {
  background: #1f2329;
  color: #fff;
}
.ccSideRule {
  height: 1px;
  background: #f0f1f3;
  margin: 10px 6px;
}

/* ---- 群组列表 ---- */
.ccGroupsHead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 10px 6px;
}
.ccGroupsTitle {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  letter-spacing: 0.03em;
}
.ccGroupsAdd {
  width: 22px;
  height: 22px;
  border: 0;
  background: transparent;
  color: #6b7280;
  border-radius: 5px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.ccGroupsAdd:hover {
  background: #f3f4f6;
  color: #111827;
}
.ccGroups {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.ccGroupList {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
  scrollbar-gutter: stable;
}
.ccGroupItem {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 56px;
  padding: 6px 8px;
  border: 0;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s ease;
}
.ccGroupItem:hover {
  background: #f3f4f6;
}
.ccGroupItem.active {
  background: #f1f5f9;
}
.ccGroupCopy {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.ccGroupCopy strong {
  font-size: 13px;
  color: #1f2329;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ccGroupCopy small {
  font-size: 11px;
  color: #9ca3af;
}
.ccGroupBadge {
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  background: #1f2329;
  min-width: 18px;
  height: 18px;
  border-radius: 9px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
  flex-shrink: 0;
}
.ccSideEmpty {
  padding: 8px 10px;
  font-size: 12px;
  color: #9ca3af;
}

/* ---- 头像：统一克制灰蓝 ---- */
.ccAvatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #eef1f5;
  color: #3f4b5c;
  font-size: 12px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.ccAvatarLg {
  width: 44px;
  height: 44px;
  font-size: 17px;
  background: #1f2329;
  color: #fff;
}
.ccAvatarXl {
  width: 56px;
  height: 56px;
  font-size: 20px;
  background: #1f2329;
  color: #fff;
}

/* ---- 第 3 列：具体内容，浅灰底独立滚动 ---- */
.ccMain {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
  background: var(--bg0);
  overflow-y: auto;
  padding: 20px 26px 48px;
  scrollbar-gutter: stable;
}
/* n-spin 包裹层要跟着撑满，否则 .ccPane 的 flex:1 断链 */
.ccMain :deep(.n-spin-container),
.ccMain :deep(.n-spin-content) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.ccPane {
  display: flex;
  flex-direction: column;
  gap: 14px;
  flex: 1;
  min-height: 0;
}
/* 单卡页面（成员/动态）：让唯一的一张卡拉伸填满中栏高度，避免数据少时下方大片空白 */
.ccPaneFill {
  flex: 1;
  min-height: 0;
}
.ccPaneFill > .ccSec.ccCard {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.ccPaneFill .ccTable,
.ccPaneFill .ccTimeline {
  flex: 1;
  min-height: 0;
}
.ccPaneFill .ccEmpty.ccEmptyTall {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ---- 卡片：右栏所有区段的统一白卡容器 ---- */
.ccCard {
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  padding: 16px 20px;
}

/* ---- 统计：卡片内 4 列大字 ---- */
.ccStats {
  display: flex;
  align-items: stretch;
  padding: 0;
}
.ccStat {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 0 12px;
  border-right: 1px solid #eef0f3;
}
.ccStat:first-child {
  padding-left: 0;
}
.ccStat:last-child {
  padding-right: 0;
  border-right: 0;
}
.ccStat strong {
  font-size: 26px;
  font-weight: 650;
  color: #111827;
  line-height: 1.2;
  letter-spacing: -0.01em;
}
.ccStat span {
  font-size: 12.5px;
  color: #6b7280;
}
.ccStat.warn strong {
  color: #b91c1c;
}

/* ---- 段落：卡片内的区段 ---- */
.ccSec {
  padding: 2px 0;
  border-top: 1px solid #eef0f3;
}
.ccPane > .ccSec:first-child {
  border-top: 0;
}
.ccSec.ccCard {
  border-top: 0;
  padding: 16px 20px;
}
.ccStats.ccCard {
  padding: 16px 20px;
}
.ccSecHead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 2px 0 10px;
}
.ccSecTitle {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
  display: flex;
  align-items: center;
  gap: 8px;
  line-height: 1.4;
}
.ccSecTitle em {
  font-style: normal;
  font-size: 12.5px;
  font-weight: 500;
  color: #9ca3af;
}
.ccTwoCol {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 44px;
  border-top: 1px solid #eef0f3;
}
.ccTwoCol > .ccSec {
  border-top: 0;
}
.ccTwoCol.ccCard {
  border-top: 0;
}

/* ---- 列表：分隔线行 ---- */
.ccList {
  display: flex;
  flex-direction: column;
}
.ccListRow {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 4px;
  border-top: 1px solid #f3f4f6;
  font-size: 13px;
}
button.ccListRow {
  width: 100%;
  border: 0;
  border-top: 1px solid #f3f4f6;
  background: transparent;
  cursor: pointer;
  text-align: left;
}
.ccListRow:first-child,
button.ccListRow:first-child {
  border-top: 0;
}
button.ccListRow:hover {
  background: #f8f9fb;
  border-radius: 6px;
}
.ccListCopy {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ccListCopy strong {
  font-size: 13px;
  font-weight: 600;
  color: #1f2329;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ccListCopy small {
  font-size: 12px;
  color: #9ca3af;
}
.ccListOps {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}
.ccMuted {
  color: #6b7280;
  font-size: 12.5px;
}
.ccTime {
  font-size: 12px;
  color: #9ca3af;
  flex-shrink: 0;
}
.ccEmpty {
  padding: 22px 4px;
  text-align: center;
  font-size: 13px;
  color: #9ca3af;
}
.ccEmptyTall {
  padding: 48px 20px;
}
.ccTextLink {
  border: 0;
  background: transparent;
  font-size: 12.5px;
  color: #6b7280;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 6px;
  font-weight: 500;
}
.ccTextLink:hover {
  color: #111827;
  background: #f3f4f6;
}
.ccTextLinkPrimary {
  color: #1f2329;
  font-weight: 600;
}
.ccTextLinkDanger {
  color: #b91c1c;
}
.ccTextLinkDanger:hover {
  color: #991b1b;
  background: #fef2f2;
}
.ccCardOps {
  display: flex;
  gap: 4px;
}

/* ---- 待处理 / 焦点列表 ---- */
.ccFocusList {
  display: flex;
  flex-direction: column;
}
.ccFocusRow {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 8px;
  border-radius: 6px;
}
.ccFocusRow:hover {
  background: #f9fafb;
}
.ccFocusRowTask {
  width: 100%;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
}
.ccFocusCopy {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ccFocusCopy strong {
  font-size: 13px;
  color: #1f2329;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ccFocusCopy small {
  font-size: 12px;
  color: #9ca3af;
}
.ccFocusActions {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
}
.ccTextBtn {
  border: 0;
  background: transparent;
  font-size: 12.5px;
  color: #374151;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  font-weight: 500;
  transition: background 0.15s ease, color 0.15s ease;
}
.ccTextBtn:hover {
  background: #f3f4f6;
  color: #111827;
}
.ccTextBtnPrimary {
  color: #1f2329;
  font-weight: 600;
}
.ccTextBtnDanger {
  color: #b91c1c;
}
.ccTextBtnDanger:hover {
  background: #fef2f2;
  color: #991b1b;
}

/* ---- 工作项 dot / 状态 ---- */
.ccWorkDot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
  background: #d1d5db;
}
.ccWorkDot.todo {
  background: #d1d5db;
}
.ccWorkDot.doing {
  background: #2563eb;
}
.ccWorkDot.done {
  background: #16a34a;
}
.ccWorkStatus {
  font-size: 12px;
  color: #6b7280;
  flex-shrink: 0;
}
.ccWorkStatus.urgent {
  color: #b91c1c;
  font-weight: 600;
}
.ccWorkStatus.done {
  color: #16a34a;
}

.ccChevron {
  width: 16px;
  height: 16px;
  color: #d1d5db;
  flex-shrink: 0;
}

/* ---- 在线状态点 ---- */
.ccOnlineDot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #d1d5db;
  flex-shrink: 0;
}
.ccOnlineDot.on {
  background: #10b981;
}

/* ---- 成员表格 ---- */
.ccFilters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.ccSearch {
  width: 200px;
}
.ccFilter {
  width: 120px;
}
.ccTable {
  display: flex;
  flex-direction: column;
}
.ccTableRow {
  display: grid;
  grid-template-columns: 1fr 90px 1.2fr 110px 60px;
  gap: 12px;
  align-items: center;
  padding: 12px 8px;
  border-top: 1px solid #f3f4f6;
}
.ccTableHead {
  border-top: 0;
  padding: 0 8px 10px;
}
.ccTableHead span {
  font-size: 12px;
  color: #9ca3af;
  font-weight: 600;
}
.ccIdentity {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
  padding: 0;
}
.ccIdentityCopy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.ccIdentityCopy strong {
  font-size: 13px;
  color: #1f2329;
  font-weight: 600;
}
.ccIdentityCopy small {
  font-size: 12px;
  color: #9ca3af;
}
.ccRole {
  font-size: 12.5px;
  color: #374151;
  background: #f3f4f6;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: fit-content;
  padding: 2px 10px;
  border-radius: 4px;
}
.ccGroupsText {
  font-size: 12.5px;
  color: #6b7280;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ccState {
  font-size: 12.5px;
  color: #9ca3af;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.ccState.on {
  color: #059669;
}
.ccTableOps {
  display: flex;
  justify-content: flex-end;
  gap: 2px;
}
.ccTableCompact .ccTableRow {
  grid-template-columns: 1fr 90px 130px;
}

/* ---- 群组头 ---- */
.ccGroupHead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.ccGroupHeadIdentity {
  display: flex;
  align-items: center;
  gap: 14px;
}
.ccGroupHeadCopy h2 {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 650;
  color: #111827;
}
.ccGroupHeadCopy p {
  margin: 0;
  font-size: 12.5px;
  color: #9ca3af;
}
.ccGroupHeadActions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* ---- 任务输入 ---- */
.ccComposer {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
.ccComposerTitle {
  flex: 1;
}
.ccComposerPriority {
  width: 130px;
}
.ccComposerDue {
  height: 30px;
  padding: 0 8px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 12.5px;
  color: #374151;
  background: #fff;
}
.ccComposerDesc {
  margin-bottom: 16px;
}

/* ---- 任务看板 ---- */
.ccBoard {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.ccColumn {
  background: #fafbfc;
  border-radius: 6px;
  padding: 10px;
}
.ccColumnHead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  padding: 0 2px;
}
.ccColumnTitle {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.ccColumnDot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
}
.ccColumnDot.todo {
  background: #d1d5db;
}
.ccColumnDot.doing {
  background: #2563eb;
}
.ccColumnDot.done {
  background: #16a34a;
}
.ccColumnCount {
  font-size: 11px;
  font-weight: 600;
  color: #6b7280;
  background: #fff;
  border: 1px solid #f0f1f3;
  min-width: 18px;
  height: 18px;
  border-radius: 9px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
}
.ccColumnList {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ccTask {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.ccTask:hover {
  border-color: #d1d5db;
  box-shadow: 0 1px 3px rgba(17, 24, 39, 0.05);
}
.ccTask.overdue {
  border-color: #fecaca;
}
.ccTaskTop {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: flex-start;
  margin-bottom: 6px;
}
.ccTaskTop strong {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
  line-height: 1.4;
}
.ccPriority {
  font-size: 11px;
  font-weight: 600;
  padding: 1px 7px;
  border-radius: 4px;
  flex-shrink: 0;
}
.ccPriority.high {
  color: #b91c1c;
  background: #fef2f2;
}
.ccPriority.medium {
  color: #6b7280;
  background: #f3f4f6;
}
.ccPriority.low {
  color: #9ca3af;
  background: #f3f4f6;
}
.ccTaskDesc {
  margin: 0 0 8px;
  font-size: 12.5px;
  color: #6b7280;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.ccTaskMeta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #9ca3af;
  margin-bottom: 10px;
}
.ccTaskAssignee {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #374151;
}
.ccTaskAssignee.unassigned {
  color: #9ca3af;
}
.ccTaskMeta .overdue {
  color: #b91c1c;
  font-weight: 600;
}
.ccTaskOps {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
}
.ccTaskAssign {
  width: 92px;
}
.ccTaskBtn {
  border: 1px solid #e5e7eb;
  background: #fff;
  font-size: 12px;
  color: #374151;
  padding: 3px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.ccTaskBtn:hover {
  background: #f9fafb;
  border-color: #d1d5db;
}
.ccTaskBtnDanger {
  color: #b91c1c;
}
.ccTaskBtnDanger:hover {
  background: #fef2f2;
  border-color: #fecaca;
}
.ccColumnEmpty {
  padding: 24px 8px;
  text-align: center;
  font-size: 12.5px;
  color: #c7ccd4;
}

/* ---- 逾期徽章 ---- */
.ccOverdue {
  font-size: 12px;
  font-weight: 600;
  color: #b91c1c;
  background: #fef2f2;
  padding: 2px 10px;
  border-radius: 5px;
}

/* ---- 危险区 ---- */
.ccDangerZone {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-top: 1px solid #eef0f3;
  padding: 14px 0;
}
.ccDangerZone.ccCard {
  border-top: 0;
  padding: 16px 20px;
}
.ccDangerCopy {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ccDangerCopy strong {
  font-size: 13px;
  color: #b91c1c;
}
.ccDangerCopy small {
  font-size: 12px;
  color: #9ca3af;
}

/* ---- 筛选 chips ---- */
.ccChips {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.ccSec.ccCard > .ccChips {
  margin-bottom: 12px;
}
.ccChip {
  border: 1px solid #e5e7eb;
  background: #fff;
  font-size: 12.5px;
  color: #6b7280;
  padding: 4px 12px;
  border-radius: 20px;
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease, background 0.15s ease;
}
.ccChip:hover {
  color: #111827;
  border-color: #d1d5db;
}
.ccChip.active {
  background: #1f2329;
  border-color: #1f2329;
  color: #fff;
}

/* ---- 时间线 ---- */
.ccTimeline {
  display: flex;
  flex-direction: column;
}
.ccTimelineDay {
  margin-bottom: 18px;
}
.ccDayLabel {
  font-size: 12px;
  font-weight: 600;
  color: #9ca3af;
  margin-bottom: 8px;
}
.ccTimelineRow {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 8px;
  border-radius: 6px;
}
.ccTimelineRow:hover {
  background: #f9fafb;
}
.ccTimelineDot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #d1d5db;
  flex-shrink: 0;
}
.ccTimelineCopy {
  flex: 1;
  display: flex;
  align-items: baseline;
  gap: 5px;
  font-size: 13px;
  color: #6b7280;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ccTimelineCopy strong {
  color: #1f2329;
  font-weight: 600;
}
.ccTimelineCopy small {
  font-size: 12px;
  color: #9ca3af;
}
.ccTimelineTime {
  font-size: 12px;
  color: #9ca3af;
  flex-shrink: 0;
}

/* ---- 空态（群组选择） ---- */
.ccEmptyState {
  padding: 64px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.ccEmptyState strong {
  font-size: 15px;
  color: #1f2329;
  font-weight: 600;
}
.ccEmptyState span {
  font-size: 13px;
  color: #9ca3af;
}
.ccEmptyState .ccBtn {
  margin-top: 12px;
}

/* ---- 抽屉 ---- */
.ccDrawerIdentity {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 8px 0 20px;
}
.ccDrawerCopy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ccDrawerCopy strong {
  font-size: 16px;
  color: #111827;
  font-weight: 650;
}
.ccDrawerCopy small {
  font-size: 13px;
  color: #6b7280;
}
.ccDrawerSection {
  border-top: 1px solid #f3f4f6;
  padding: 16px 0;
}
.ccDrawerTitle {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 10px;
}
.ccMuted {
  font-size: 13px;
  color: #9ca3af;
}
.ccDrawerManage {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 4px;
}
.ccDrawerManageCopy {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ccDrawerManageCopy strong {
  font-size: 13px;
  color: #1f2329;
  font-weight: 600;
}
.ccDrawerManageCopy small {
  font-size: 12px;
  color: #9ca3af;
}

/* ---- 弹窗 ---- */
.ccDialog {
  background: #fff;
  border-radius: 12px;
  width: 420px;
  max-width: calc(100vw - 32px);
  box-shadow: 0 12px 32px rgba(17, 24, 39, 0.12);
  overflow: hidden;
}
.ccDialogHead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #f3f4f6;
}
.ccDialogHead h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 650;
  color: #111827;
}
.ccDialogClose {
  width: 26px;
  height: 26px;
  border: 0;
  background: transparent;
  color: #9ca3af;
  border-radius: 6px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.ccDialogClose:hover {
  background: #f3f4f6;
  color: #111827;
}
.ccDialogBody {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.ccField {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ccField span {
  font-size: 12.5px;
  font-weight: 600;
  color: #374151;
}
.ccDialogFoot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 14px 20px;
  border-top: 1px solid #f3f4f6;
}

/* ---- 响应式 ---- */
@media (max-width: 1100px) {
  .ccBody {
    grid-template-columns: 1fr;
  }
  .ccSide {
    position: static;
  }
  .ccTwoCol {
    grid-template-columns: 1fr;
    gap: 0;
  }
  .ccBoard {
    grid-template-columns: 1fr;
  }
  .ccTableRow {
    grid-template-columns: 1fr 90px 110px;
  }
}

</style>
