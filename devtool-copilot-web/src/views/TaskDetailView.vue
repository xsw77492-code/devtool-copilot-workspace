<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NCard, NCheckbox, NDatePicker, NDropdown, NInput, NModal, NSelect, NSpin, useDialog, useMessage } from 'naive-ui'
import {
  taskApi,
  type DeliverableType,
  type Task,
  type TaskChecklistItem,
  type TaskComment,
  type TaskDeliverable,
  type TaskStatus,
  type TaskTimelineItem
} from '../api/task'
import { attachmentApi, type AttachmentItem } from '../api/attachment'
import { milestoneApi, type Milestone } from '../api/milestone'
import { projectCollabApi, type ProjectMemberRole } from '../api/projectCollab'
import { useRealtimeStore } from '../stores/realtime'
import { useAuthStore } from '../stores/auth'
import PresenceBar from '../components/PresenceBar.vue'
import MarkdownView from '../components/MarkdownView.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const rt = useRealtimeStore()
const auth = useAuthStore()

const projectId = computed(() => {
  const pid = Number(route.params.projectId)
  return Number.isFinite(pid) ? pid : 0
})
const taskId = computed(() => Number(route.params.taskId))

const loading = ref(false)
const saving = ref(false)
const addingNote = ref(false)

const task = ref<Task | null>(null)
const projectIdSafe = computed(() => {
  const pid = Number(projectId.value || 0)
  if (pid > 0) return pid
  const fallback = Number((task.value as any)?.projectId || 0)
  return Number.isFinite(fallback) && fallback > 0 ? fallback : 0
})
const timeline = ref<TaskTimelineItem[]>([])
const baseUpdatedAt = ref<string | null>(null)

const commentsLoading = ref(false)
const comments = ref<TaskComment[]>([])
const commentDraft = ref('')
const sendingComment = ref(false)
const replyTo = ref<TaskComment | null>(null)
const highlightCommentId = ref<number | null>(null)
const lastFocusedCommentId = ref<number | null>(null)
const myRole = ref<ProjectMemberRole | null>(null)
const memberOptions = ref<{ label: string; value: number }[]>([])
const following = ref(false)
const followLoading = ref(false)

const attachmentsLoading = ref(false)
const attachments = ref<AttachmentItem[]>([])
const uploadingAttachment = ref(false)
const attachTargetCommentId = ref<number | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

const deliverablesLoading = ref(false)
const deliverables = ref<TaskDeliverable[]>([])
const checklistLoading = ref(false)
const checklist = ref<TaskChecklistItem[]>([])

const checklistDraft = ref('')
const addingChecklist = ref(false)

const subtasksLoading = ref(false)
const subtasks = ref<Task[]>([])
const parentTaskLoading = ref(false)
const parentTask = ref<Task | null>(null)
const subtaskOpen = ref(false)
const subtaskTitle = ref('')
const creatingSubtask = ref(false)

const deliverableModalOpen = ref(false)
const deliverableSaving = ref(false)
const editingDeliverableId = ref<number | null>(null)
const deliverableType = ref<DeliverableType>('LINK')
const deliverableTitle = ref('')
const deliverableUrl = ref('')
const deliverableContent = ref('')
const deliverableDocMode = ref<'EDIT' | 'PREVIEW'>('EDIT')

const deliverableDraggingId = ref<number | null>(null)
const deliverableDrop = ref<{ id: number | null; pos: 'before' | 'after' } | null>(null)
const deliverableMoving = ref(false)

const editTitle = ref('')
const editDescription = ref('')
const editAcceptance = ref('')
const editPriority = ref<string | null>(null)
const editTags = ref('')
const editAssignee = ref('')
const editAssigneeId = ref<number | null>(null)
const editDueAt = ref<number | null>(null)
const editStatus = ref<TaskStatus>('TODO')
const editMilestoneId = ref<number | null>(null)

const milestones = ref<Milestone[]>([])
const milestoneOptions = computed(() => {
  const opts = milestones.value.map((m) => ({ label: m.name, value: m.id }))
  return [{ label: 'Milestone: None', value: 0 }, ...opts]
})

const note = ref('')

const canEdit = computed(() => myRole.value !== 'VIEWER')

const moreOptions = computed(() => [
  { key: 'follow', label: following.value ? '取消关注' : '关注' },
  { key: 'delete', label: '删除任务', disabled: !canEdit.value }
])

const myUserId = computed(() => Number((auth.me as any)?.id || 0))

const isDirty = computed(() => {
  if (!task.value) return false
  const due0 = task.value.dueTime ? Date.parse(task.value.dueTime) : null
  const due1 = editDueAt.value ?? null
  const p0 = (task.value.priority as any) ?? null
  const p1 = editPriority.value ?? null
  const m0 = Number(task.value.milestoneId ?? 0)
  const m1 = Number(editMilestoneId.value ?? 0)
  return (
    String(task.value.title || '').trim() !== String(editTitle.value || '').trim() ||
    String(task.value.description || '') !== String(editDescription.value || '') ||
    String(task.value.acceptanceCriteria || '') !== String(editAcceptance.value || '') ||
    String(task.value.tags || '') !== String(editTags.value || '') ||
    Number(task.value.assigneeId ?? 0) !== Number(editAssigneeId.value ?? 0) ||
    Number(due0 ?? 0) !== Number(due1 ?? 0) ||
    String(p0 ?? '') !== String(p1 ?? '') ||
    m0 !== m1 ||
    String(task.value.status || '') !== String(editStatus.value || '')
  )
})

const taskViewers = computed(() => {
  return (rt.presenceMembers || []).filter((m) => m.online !== false && m.viewType === 'TASK' && Number(m.viewId) === taskId.value)
})

const taskEditors = computed(() => {
  return taskViewers.value.filter((m) => m.editing === true)
})

let editingTimer: any = null
let lastRemotePromptAt = 0
let autoRefreshTimer: any = null
function markEditing() {
  if (!canEdit.value) return
  if (loading.value) return
  rt.setEditing(true)
  if (editingTimer) clearTimeout(editingTimer)
  editingTimer = setTimeout(() => {
    rt.setEditing(false)
  }, 6000)
}

const priorityOptions = [
  { label: 'LOW', value: 'LOW' },
  { label: 'MEDIUM', value: 'MEDIUM' },
  { label: 'HIGH', value: 'HIGH' }
]

const statusOptions = [
  { label: 'TODO', value: 'TODO' },
  { label: 'DOING', value: 'DOING' },
  { label: 'DONE', value: 'DONE' }
]

function fmt(ts?: number) {
  if (!ts) return ''
  const d = new Date(ts)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${dd} ${hh}:${mm}`
}

function onMoreSelect(key: string) {
  if (key === 'follow') toggleFollow()
  else if (key === 'delete') removeTask()
}

async function load() {
  if (!Number.isFinite(taskId.value)) return
  loading.value = true
  try {
    const t = await taskApi.get(taskId.value)
    task.value = t
    if (projectId.value <= 0 && Number((t as any)?.projectId || 0) > 0) {
      router.replace({ name: 'task-detail', params: { projectId: Number((t as any).projectId), taskId: t.id } })
    }
    baseUpdatedAt.value = t.updatedAt || null
    editTitle.value = t.title || ''
    editDescription.value = t.description || ''
    editAcceptance.value = t.acceptanceCriteria || ''
    editPriority.value = (t.priority as string | undefined) || null
    editTags.value = t.tags || ''
    editAssignee.value = t.assignee || ''
    editAssigneeId.value = (t.assigneeId as number | null | undefined) ?? null
    editDueAt.value = t.dueTime ? Date.parse(t.dueTime) : null
    editStatus.value = t.status
    editMilestoneId.value = (t.milestoneId as any) ?? null
    ;(async () => {
      try {
        timeline.value = await taskApi.timeline(taskId.value)
      } catch {
        timeline.value = []
      }
    })()
    void loadComments()
    void loadAttachments()
    void loadDeliverables()
    void loadChecklist()
    void loadParentTask()
    void loadSubtasks()

    ;(async () => {
      try {
        const pid = projectIdSafe.value
        if (!pid) throw new Error('bad pid')
        const members = await projectCollabApi.members(pid)
        myRole.value = members.myRole
        memberOptions.value = members.members.map((m) => ({ label: m.username, value: m.userId }))
      } catch {
        myRole.value = null
        memberOptions.value = []
      }
    })()

    ;(async () => {
      try {
        const pid = projectIdSafe.value
        if (!pid) throw new Error('bad pid')
        milestones.value = await milestoneApi.list(pid, false)
      } catch {
        milestones.value = []
      }
    })()

    ;(async () => {
      try {
        const pid = projectIdSafe.value
        if (!pid) throw new Error('bad pid')
        following.value = await taskApi.isFollowed(pid, taskId.value)
      } catch {
        following.value = false
      }
    })()
  } catch (e: any) {
    const msg = String(e?.message || '')
    if (msg.includes('成员已被禁用')) {
      message.error('你已被该项目禁用')
      router.replace({ name: 'workspace' })
      return
    }
    message.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadAttachments() {
  if (!Number.isFinite(taskId.value)) return
  attachmentsLoading.value = true
  try {
    attachments.value = await attachmentApi.listByTask(taskId.value)
  } catch {
    attachments.value = []
  } finally {
    attachmentsLoading.value = false
  }
}

async function loadDeliverables() {
  if (!Number.isFinite(taskId.value)) return
  deliverablesLoading.value = true
  try {
    deliverables.value = await taskApi.deliverables(taskId.value)
  } catch {
    deliverables.value = []
  } finally {
    deliverablesLoading.value = false
  }
}

async function loadChecklist() {
  if (!Number.isFinite(taskId.value)) return
  checklistLoading.value = true
  try {
    checklist.value = await taskApi.checklist(taskId.value)
  } catch {
    checklist.value = []
  } finally {
    checklistLoading.value = false
  }
}

async function loadParentTask() {
  const pid = Number(task.value?.parentTaskId || 0)
  if (!pid) {
    parentTask.value = null
    return
  }
  parentTaskLoading.value = true
  try {
    parentTask.value = await taskApi.get(pid)
  } catch {
    parentTask.value = null
  } finally {
    parentTaskLoading.value = false
  }
}

async function loadSubtasks() {
  if (!Number.isFinite(taskId.value)) return
  const isSub = Number(task.value?.parentTaskId || 0) > 0
  if (isSub) {
    subtasks.value = []
    return
  }
  subtasksLoading.value = true
  try {
    subtasks.value = await taskApi.subtasks(taskId.value)
  } catch {
    subtasks.value = []
  } finally {
    subtasksLoading.value = false
  }
}

function goTask(id: number) {
  if (!id) return
  router.push({ name: 'task-detail', params: { projectId: projectId.value, taskId: id } })
}

function openSubtaskCreate() {
  subtaskTitle.value = ''
  subtaskOpen.value = true
}

async function createSubtask() {
  const title = subtaskTitle.value.trim()
  if (!title) {
    message.warning('请输入子任务标题')
    return
  }
  creatingSubtask.value = true
  try {
    await taskApi.create(projectId.value, title, {
      parentTaskId: taskId.value,
      milestoneId: (task.value as any)?.milestoneId ?? undefined,
      source: 'UI'
    })
    subtaskOpen.value = false
    await loadSubtasks()
    timeline.value = await taskApi.timeline(taskId.value)
    message.success('已新增子任务')
  } catch (e: any) {
    message.error(e?.message || '创建失败')
  } finally {
    creatingSubtask.value = false
  }
}

const checklistDoneCount = computed(() => checklist.value.filter((x) => Number(x.isDone || 0) === 1).length)
const deliverableDoneCount = computed(() => deliverables.value.filter((x) => String(x.status || '').toUpperCase() === 'DONE').length)

function openDeliverableCreate() {
  editingDeliverableId.value = null
  deliverableType.value = 'LINK'
  deliverableTitle.value = ''
  deliverableUrl.value = ''
  deliverableContent.value = ''
  deliverableDocMode.value = 'EDIT'
  deliverableModalOpen.value = true
}

function openDeliverableEdit(d: TaskDeliverable) {
  editingDeliverableId.value = d.id
  deliverableType.value = (String(d.type || 'LINK').toUpperCase() as any) || 'LINK'
  deliverableTitle.value = d.title || ''
  deliverableUrl.value = String(d.url || '')
  deliverableContent.value = String(d.content || '')
  deliverableDocMode.value = String(d.type || '').toUpperCase() === 'DOC' ? 'PREVIEW' : 'EDIT'
  deliverableModalOpen.value = true
}

async function saveDeliverable() {
  const title = deliverableTitle.value.trim()
  if (!title) {
    message.warning('请填写标题')
    return
  }
  deliverableSaving.value = true
  try {
    const type = String(deliverableType.value || 'LINK').toUpperCase()
    const url = deliverableUrl.value.trim()
    const content = deliverableContent.value
    const id = editingDeliverableId.value
    if (id) {
      await taskApi.updateDeliverable(id, { title, url: url || null, content: content || null })
      message.success('已保存')
    } else {
      await taskApi.createDeliverable(taskId.value, { type, title, url: url || null, content: content || null })
      message.success('已新增')
    }
    deliverableModalOpen.value = false
    await loadDeliverables()
    timeline.value = await taskApi.timeline(taskId.value)
  } catch (e: any) {
    message.error(e?.message || '保存失败')
  } finally {
    deliverableSaving.value = false
  }
}

async function toggleDeliverableDone(d: TaskDeliverable) {
  const next = String(d.status || '').toUpperCase() === 'DONE' ? 'PENDING' : 'DONE'
  try {
    await taskApi.updateDeliverable(d.id, { status: next })
    await loadDeliverables()
    timeline.value = await taskApi.timeline(taskId.value)
  } catch (e: any) {
    message.error(e?.message || '操作失败')
  }
}

async function removeDeliverable(d: TaskDeliverable) {
  dialog.warning({
    title: '删除交付物',
    content: `确认删除「${d.title}」？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await taskApi.deleteDeliverable(d.id)
        message.success('已删除')
        await loadDeliverables()
        timeline.value = await taskApi.timeline(taskId.value)
      } catch (e: any) {
        message.error(e?.message || '删除失败')
      }
    }
  })
}

function onDeliverableDragStart(id: number) {
  if (!canEdit.value) return
  deliverableDraggingId.value = id
}

function onDeliverableDragEnd() {
  deliverableDraggingId.value = null
  deliverableDrop.value = null
}

function onDeliverableDragOver(e: DragEvent, id: number) {
  if (!deliverableDraggingId.value) return
  const el = e.currentTarget as HTMLElement | null
  if (!el) return
  const r = el.getBoundingClientRect()
  const pos = e.clientY < r.top + r.height / 2 ? 'before' : 'after'
  deliverableDrop.value = { id, pos }
}

function onDeliverableListDragOver() {
  if (!deliverableDraggingId.value) return
  deliverableDrop.value = { id: null, pos: 'after' }
}

async function onDeliverableDrop(targetId: number | null) {
  const dragId = deliverableDraggingId.value
  if (!dragId || deliverableMoving.value) return
  const ids = deliverables.value.map((x) => x.id).filter((x) => Number(x || 0) > 0) as number[]
  const order = ids.filter((x) => x !== dragId)
  let idx = order.length
  if (targetId) {
    const t = order.indexOf(targetId)
    if (t >= 0) {
      const pos = deliverableDrop.value?.id === targetId ? deliverableDrop.value?.pos : 'after'
      idx = pos === 'before' ? t : t + 1
    }
  }
  order.splice(idx, 0, dragId)
  const at = order.indexOf(dragId)
  const beforeId = at > 0 ? order[at - 1] : null
  const afterId = at < order.length - 1 ? order[at + 1] : null

  deliverableMoving.value = true
  try {
    await taskApi.moveDeliverable(dragId, { beforeId, afterId })
    await loadDeliverables()
    timeline.value = await taskApi.timeline(taskId.value)
  } catch (e: any) {
    message.error(e?.message || '排序失败')
  } finally {
    deliverableMoving.value = false
    onDeliverableDragEnd()
  }
}

async function addChecklistItem() {
  const content = checklistDraft.value.trim()
  if (!content) return
  addingChecklist.value = true
  try {
    await taskApi.createChecklistItem(taskId.value, content)
    checklistDraft.value = ''
    await loadChecklist()
    timeline.value = await taskApi.timeline(taskId.value)
  } catch (e: any) {
    message.error(e?.message || '添加失败')
  } finally {
    addingChecklist.value = false
  }
}

async function toggleChecklistDone(i: TaskChecklistItem) {
  try {
    await taskApi.updateChecklistItem(i.id, { done: Number(i.isDone || 0) !== 1 })
    await loadChecklist()
    timeline.value = await taskApi.timeline(taskId.value)
  } catch (e: any) {
    message.error(e?.message || '操作失败')
  }
}

async function removeChecklistItem(i: TaskChecklistItem) {
  dialog.warning({
    title: '删除清单项',
    content: `确认删除「${i.content}」？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await taskApi.deleteChecklistItem(i.id)
        message.success('已删除')
        await loadChecklist()
        timeline.value = await taskApi.timeline(taskId.value)
      } catch (e: any) {
        message.error(e?.message || '删除失败')
      }
    }
  })
}

async function toggleFollow() {
  if (followLoading.value) return
  followLoading.value = true
  try {
    if (following.value) {
      await taskApi.unfollow(projectId.value, taskId.value)
      following.value = false
      message.success('已取消关注')
    } else {
      await taskApi.follow(projectId.value, taskId.value)
      following.value = true
      message.success('已关注')
    }
  } catch (e: any) {
    message.error(e?.message || '操作失败')
  } finally {
    followLoading.value = false
  }
}

async function loadComments() {
  if (!Number.isFinite(taskId.value)) return
  commentsLoading.value = true
  try {
    comments.value = await taskApi.comments(taskId.value)
  } catch {
    comments.value = []
  } finally {
    commentsLoading.value = false
    await focusCommentIfNeeded()
  }
}

const focusCommentId = computed(() => {
  const v = Number(route.query.commentId || 0)
  if (!v || !Number.isFinite(v)) return null
  return v
})

let focusTimer: any = null
async function focusCommentIfNeeded() {
  const id = focusCommentId.value
  if (!id) return
  if (lastFocusedCommentId.value === id) return
  lastFocusedCommentId.value = id
  await nextTick()
  const el = document.getElementById(`c-${id}`)
  if (!el) return
  el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  highlightCommentId.value = id
  if (focusTimer) clearTimeout(focusTimer)
  focusTimer = setTimeout(() => {
    if (highlightCommentId.value === id) highlightCommentId.value = null
  }, 3500)
}

async function submitComment() {
  const content = commentDraft.value.trim()
  if (!content) return
  sendingComment.value = true
  try {
    await taskApi.addComment(taskId.value, { content, replyToId: replyTo.value?.id ?? null })
    commentDraft.value = ''
    replyTo.value = null
    await loadComments()
    await loadAttachments()
  } catch (e: any) {
    message.error(e?.message || '发送失败')
  } finally {
    sendingComment.value = false
  }
}

const taskAttachments = computed(() => attachments.value.filter((a) => !a.commentId))

const commentAttachmentsMap = computed(() => {
  const m: Record<number, AttachmentItem[]> = {}
  for (const a of attachments.value) {
    const cid = a.commentId ? Number(a.commentId) : 0
    if (!cid) continue
    if (!m[cid]) m[cid] = []
    m[cid].push(a)
  }
  return m
})

function fmtSize(n?: number) {
  const v = Number(n || 0)
  if (!v) return '0B'
  const kb = 1024
  const mb = kb * 1024
  const gb = mb * 1024
  if (v >= gb) return `${(v / gb).toFixed(2)}GB`
  if (v >= mb) return `${(v / mb).toFixed(2)}MB`
  if (v >= kb) return `${(v / kb).toFixed(2)}KB`
  return `${v}B`
}

function getExt(name?: string | null) {
  const raw = String(name || '')
  const idx = raw.lastIndexOf('.')
  if (idx < 0 || idx >= raw.length - 1) return ''
  return raw.slice(idx + 1).trim().toLowerCase()
}

function detectPreviewKind(a: AttachmentItem, contentType?: string) {
  const ct = String(contentType || a.contentType || '').toLowerCase()
  const ext = getExt(a.originalName)
  if (ct.startsWith('image/') || ['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg'].includes(ext)) return 'image' as const
  if (ct === 'application/pdf' || ext === 'pdf') return 'pdf' as const
  if (
    ct.startsWith('text/') ||
    ct.includes('json') ||
    ct.includes('xml') ||
    ['txt', 'md', 'log', 'json', 'xml', 'yml', 'yaml', 'csv', 'java', 'ts', 'tsx', 'js', 'vue', 'sql'].includes(ext)
  ) {
    return 'text' as const
  }
  return 'unsupported' as const
}

function canPreviewAttachment(a: AttachmentItem) {
  return detectPreviewKind(a) !== 'unsupported'
}

function previewBadge(a: AttachmentItem) {
  const k = detectPreviewKind(a)
  if (k === 'image') return '图片'
  if (k === 'pdf') return 'PDF'
  if (k === 'text') return '文本'
  return '文件'
}

function openFilePicker(commentId?: number | null) {
  if (!canEdit.value) return
  if (uploadingAttachment.value) return
  attachTargetCommentId.value = commentId ? Number(commentId) : null
  fileInputRef.value?.click()
}

async function onPickFile(e: Event) {
  const el = e.target as HTMLInputElement
  const file = el.files && el.files.length ? el.files[0] : null
  el.value = ''
  if (!file) return
  uploadingAttachment.value = true
  try {
    if (attachTargetCommentId.value) {
      await attachmentApi.uploadToComment(attachTargetCommentId.value, file)
      message.success('附件已上传')
    } else {
      await attachmentApi.uploadToTask(taskId.value, file)
      message.success('附件已上传')
    }
    await loadAttachments()
  } catch (err: any) {
    message.error(err?.message || '上传失败')
  } finally {
    uploadingAttachment.value = false
    attachTargetCommentId.value = null
  }
}

async function downloadAttachment(a: AttachmentItem) {
  try {
    const res = await attachmentApi.download(a.id)
    const url = URL.createObjectURL(res.blob)
    const link = document.createElement('a')
    link.href = url
    link.download = res.filename
    link.click()
    URL.revokeObjectURL(url)
  } catch (e: any) {
    message.error(e?.message || '下载失败')
  }
}

async function previewAttachment(a: AttachmentItem) {
  const previewTab = window.open('', '_blank')
  if (!previewTab) {
    message.error('浏览器拦截了新窗口，请允许弹窗后重试')
    return
  }
  previewTab.document.title = a.originalName || '附件预览'
  previewTab.document.body.innerHTML =
    '<div style="margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#f8fafc;color:#0f172a;font:500 14px/1.6 -apple-system,BlinkMacSystemFont,Segoe UI,PingFang SC,Microsoft YaHei,sans-serif;">正在打开附件预览...</div>'
  try {
    const res = await attachmentApi.preview(a.id)
    const blobUrl = URL.createObjectURL(res.blob)
    previewTab.location.href = blobUrl
  } catch (e: any) {
    previewTab.close()
    message.error(e?.message || '预览失败')
  }
}

async function removeAttachment(a: AttachmentItem) {
  if (!canEdit.value) return
  dialog.warning({
    title: '删除附件',
    content: `确认删除「${a.originalName}」？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await attachmentApi.remove(a.id)
        message.success('已删除')
        await loadAttachments()
      } catch (e: any) {
        message.error(e?.message || '删除失败')
      }
    }
  })
}

onMounted(() => {
  if (projectId.value > 0) rt.subscribe(projectId.value, 'TASK', taskId.value)
  load()
  if (!autoRefreshTimer) {
    autoRefreshTimer = setInterval(() => {
      if (document.visibilityState !== 'visible') return
      if (loading.value || saving.value) return
      if (isDirty.value) return
      void load()
    }, 45000)
  }
})

watch(projectId, (id) => {
  const pid = Number(id || 0)
  if (!pid || !Number.isFinite(pid)) return
  rt.subscribe(pid, 'TASK', taskId.value)
  load()
})

watch(taskId, (id) => {
  rt.setView('TASK', Number(id))
  load()
})

watch(focusCommentId, () => {
  focusCommentIfNeeded()
})

onUnmounted(() => {
  rt.setEditing(false)
  rt.subscribe(null)
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
})

watch([editTitle, editDescription, editAcceptance, editPriority, editMilestoneId, editTags, editAssigneeId, editDueAt, editStatus], () => markEditing())

watch(
  () => rt.seq,
  async () => {
    const ev = rt.lastEvent
    const pid = Number(ev?.projectId || 0)
    if (!pid || pid !== projectIdSafe.value) return
    const t = String(ev?.type || '')
    if (
      t !== 'TASK_UPDATED' &&
      t !== 'TASK_STATUS_UPDATED' &&
      t !== 'TASK_MOVED' &&
      t !== 'TASK_DELETED' &&
      t !== 'TASK_COMMENT_CREATED' &&
      t !== 'TASK_CHECKLIST_CREATED' &&
      t !== 'TASK_CHECKLIST_UPDATED' &&
      t !== 'TASK_CHECKLIST_DELETED' &&
      t !== 'TASK_DELIVERABLE_CREATED' &&
      t !== 'TASK_DELIVERABLE_UPDATED' &&
      t !== 'TASK_DELIVERABLE_MOVED' &&
      t !== 'TASK_DELIVERABLE_DELETED' &&
      t !== 'AI_APPLY_DONE'
    )
      return
    const raw = String(ev?.payloadJson || '')
    if (!raw) return
    try {
      const p = JSON.parse(raw)
      if (t === 'AI_APPLY_DONE') {
        const ids = Array.isArray(p?.taskIds) ? (p.taskIds as any[]).map((x) => Number(x || 0)) : []
        if (!ids.includes(taskId.value)) return
        await load()
        return
      }
      if (Number(p?.taskId) !== taskId.value) return
      if (t === 'TASK_DELETED') {
        message.warning('任务已被删除')
        const nextPid = projectIdSafe.value
        router.replace(nextPid ? { name: 'project-detail', params: { id: nextPid } } : { name: 'workspace' })
        return
      }
      if (t === 'TASK_COMMENT_CREATED') {
        await loadComments()
        return
      }
      if (t === 'TASK_MOVED') {
        await load()
        return
      }
      if (t.startsWith('TASK_CHECKLIST_')) {
        await loadChecklist()
        return
      }
      if (t.startsWith('TASK_DELIVERABLE_')) {
        await loadDeliverables()
        return
      }
      if (Number(ev?.actorUserId || 0) === myUserId.value) {
        await load()
        return
      }
      if (!isDirty.value) {
        await load()
        return
      }
      const now = Date.now()
      if (now - lastRemotePromptAt < 4000) return
      lastRemotePromptAt = now
      dialog.warning({
        title: '检测到协作更新',
        content: '对方刚更新了这个任务。你当前还有未保存的修改，是否刷新？',
        positiveText: '刷新',
        negativeText: '继续编辑',
        onPositiveClick: async () => {
          await load()
        }
      })
    } catch {
    }
  }
)

async function save() {
  if (!task.value) return
  if (!canEdit.value) {
    message.warning('当前角色为 VIEWER，仅可查看')
    return
  }
  const title = editTitle.value.trim()
  if (!title) {
    message.warning('标题不能为空')
    return
  }
  saving.value = true
  try {
    await taskApi.updateDetail(task.value.id, {
      title,
      description: editDescription.value,
      acceptanceCriteria: editAcceptance.value,
      priority: editPriority.value || undefined,
      milestoneId: editMilestoneId.value ?? undefined,
      tags: editTags.value,
      assignee: editAssignee.value,
      assigneeId: editAssigneeId.value,
      dueAt: editDueAt.value,
      baseUpdatedAt: baseUpdatedAt.value
    })
    if (editStatus.value !== task.value.status) {
      try {
        await taskApi.updateStatus(task.value.id, editStatus.value)
      } catch (e: any) {
        const msg = String(e?.message || '')
        if (String(editStatus.value) === 'DONE' && msg.includes('验收清单未全部完成')) {
          dialog.warning({
            title: '验收清单未完成',
            content: msg,
            positiveText: '强制完成',
            negativeText: '取消',
            onPositiveClick: async () => {
              await taskApi.updateStatus(task.value!.id, 'DONE', { forceDone: true })
            },
            onNegativeClick: () => {
              editStatus.value = task.value!.status
            }
          })
        } else {
          throw e
        }
      }
    }
    await load()
    message.success('已保存')
  } catch (e: any) {
    const msg = String(e?.message || '保存失败')
    if (msg.includes('任务已被其他人更新')) {
      dialog.warning({
        title: '保存冲突',
        content: '任务已被其他人更新。你可以先刷新再保存，或直接覆盖保存。',
        positiveText: '刷新',
        negativeText: '覆盖保存',
        onPositiveClick: async () => {
          await load()
        },
        onNegativeClick: async () => {
          saving.value = true
          try {
            await taskApi.updateDetail(task.value!.id, {
              title,
              description: editDescription.value,
              acceptanceCriteria: editAcceptance.value,
              priority: editPriority.value || undefined,
              milestoneId: editMilestoneId.value ?? undefined,
              tags: editTags.value,
              assignee: editAssignee.value,
              assigneeId: editAssigneeId.value,
              dueAt: editDueAt.value,
              baseUpdatedAt: null
            })
            if (editStatus.value !== task.value!.status) {
              try {
                await taskApi.updateStatus(task.value!.id, editStatus.value)
              } catch (e3: any) {
                const msg3 = String(e3?.message || '')
                if (String(editStatus.value) === 'DONE' && msg3.includes('验收清单未全部完成')) {
                  dialog.warning({
                    title: '验收清单未完成',
                    content: msg3,
                    positiveText: '强制完成',
                    negativeText: '取消',
                    onPositiveClick: async () => {
                      await taskApi.updateStatus(task.value!.id, 'DONE', { forceDone: true })
                    },
                    onNegativeClick: () => {
                      editStatus.value = task.value!.status
                    }
                  })
                } else {
                  throw e3
                }
              }
            }
            await load()
            message.success('已覆盖保存')
          } catch (e2: any) {
            message.error(e2?.message || '覆盖保存失败')
          } finally {
            saving.value = false
          }
        }
      })
      return
    }
    message.error(msg)
  } finally {
    saving.value = false
  }
}

async function addNote() {
  if (!task.value) return
  if (!canEdit.value) {
    message.warning('当前角色为 VIEWER，不能添加备注')
    return
  }
  const content = note.value.trim()
  if (!content) return
  addingNote.value = true
  try {
    await taskApi.addNote(task.value.id, content)
    note.value = ''
    timeline.value = await taskApi.timeline(task.value.id)
  } catch (e: any) {
    message.error(e?.message || '添加失败')
  } finally {
    addingNote.value = false
  }
}

async function removeTask() {
  if (!task.value) return
  if (!canEdit.value) {
    message.warning('当前角色为 VIEWER，不能删除任务')
    return
  }
  const id = task.value.id
  dialog.warning({
    title: '删除任务',
    content: `确认删除任务 #${id}「${task.value.title || ''}」？删除后不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await taskApi.delete(id)
        message.success('已删除任务')
        const nextPid = projectIdSafe.value
        router.replace(nextPid ? { name: 'project-detail', params: { id: nextPid } } : { name: 'workspace' })
      } catch (e: any) {
        message.error(e?.message || '删除失败')
      }
    }
  })
}
</script>

<template>
  <div class="page detail-page px-5 py-5">
    <div class="top">
      <div class="left">
        <div class="h1">{{ editTitle || `Task #${taskId}` }}</div>
      </div>
      <div class="right">
        <presence-bar :project-id="projectIdSafe" />
        <div v-if="taskViewers.length" class="liveHint">
          <span class="pill watch">在看 {{ taskViewers.length }}</span>
          <span v-if="taskEditors.length" class="pill edit">编辑中 {{ taskEditors.length }}</span>
        </div>
        <button class="btnGhost" @click="router.push(projectIdSafe ? { name: 'project-detail', params: { id: projectIdSafe } } : { name: 'workspace' })">返回</button>
        <n-dropdown :options="moreOptions" placement="bottom-end" @select="onMoreSelect">
          <button class="btnGhost" type="button" :disabled="followLoading">更多</button>
        </n-dropdown>
        <button class="btnPrimary" :disabled="saving || !canEdit" @click="save">
          <span>保存</span>
          <span v-if="saving" class="ml-2 inline-block h-4 w-4 rounded-full border-2 border-white/30 border-t-white animate-spin" />
        </button>
      </div>
    </div>

    <div v-if="loading || !task" class="panel loading">Loading…</div>

    <div v-else class="grid">
      <n-card class="panel block" :bordered="false">
        <div class="block-head">
          <div class="h2">基本信息</div>
          <div class="muted meta">#{{ task.id }}</div>
        </div>

        <div class="form">
          <div class="row2">
            <div>
              <div class="muted label">标题</div>
              <n-input v-model:value="editTitle" :disabled="!canEdit" placeholder="输入任务标题" />
            </div>
            <div>
              <div class="muted label">状态</div>
              <n-select v-model:value="editStatus" :options="statusOptions" :disabled="!canEdit" />
            </div>
          </div>

          <div class="row2">
            <div>
              <div class="muted label">优先级</div>
              <n-select
                v-model:value="editPriority"
                clearable
                :options="priorityOptions"
                :disabled="!canEdit"
                placeholder="选择优先级"
              />
            </div>
            <div>
              <div class="muted label">截止时间</div>
              <n-date-picker v-model:value="editDueAt" type="datetime" clearable :disabled="!canEdit" />
            </div>
          </div>

          <div class="row2">
            <div>
              <div class="muted label">负责人</div>
              <n-select
                v-model:value="editAssigneeId"
                :options="memberOptions"
                placeholder="选择负责人"
                :disabled="!canEdit"
              />
            </div>
            <div>
              <div class="muted label">标签</div>
              <n-input v-model:value="editTags" :disabled="!canEdit" placeholder="用逗号分隔：frontend,bug,urgent" />
            </div>
          </div>

          <div class="row2">
            <div>
              <div class="muted label">Milestone</div>
              <n-select
                v-model:value="editMilestoneId"
                clearable
                :options="milestoneOptions"
                :disabled="!canEdit"
                placeholder="选择里程碑"
              />
            </div>
            <div />
          </div>

          <div>
            <div class="muted label">描述</div>
            <n-input
              v-model:value="editDescription"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 10 }"
              :disabled="!canEdit"
              placeholder="补充背景、范围、约束与上下文"
            />
          </div>

          <div>
            <div class="muted label">验收标准</div>
            <n-input
              v-model:value="editAcceptance"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 10 }"
              :disabled="!canEdit"
              placeholder="给出可验证的验收点（可操作、可检查）"
            />
          </div>
        </div>
      </n-card>

      <n-card class="panel block" :bordered="false">
        <div class="block-head">
          <div class="h2">动态</div>
          <div class="muted meta">{{ timeline.length }}</div>
        </div>

        <div class="noteBox">
          <n-input v-model:value="note" :disabled="!canEdit" placeholder="写一条备注…" @keyup.enter="addNote" />
          <button class="btnPrimary" :disabled="addingNote || !canEdit" @click="addNote">
            <span>添加</span>
            <span v-if="addingNote" class="ml-2 inline-block h-4 w-4 rounded-full border-2 border-white/30 border-t-white animate-spin" />
          </button>
        </div>

        <div class="timeline timelineScroll">
          <div v-for="e in timeline" :key="e.id" class="titem">
            <div class="tmain">
              <div class="tt">
                <div class="tlabel">{{ e.title || e.type }}</div>
                <div class="muted ttime">{{ fmt(e.createdAt) }}</div>
              </div>
              <div class="tdetail">{{ e.detail }}</div>
            </div>
          </div>
          <div v-if="!timeline.length" class="muted empty">暂无动态</div>
        </div>
      </n-card>

      <n-card class="panel block span2" :bordered="false">
        <div class="block-head">
          <div>
            <div class="h2">交付物</div>
            <div class="muted meta">清单 {{ checklistDoneCount }}/{{ checklist.length }} · 交付物 {{ deliverableDoneCount }}/{{ deliverables.length }}</div>
          </div>
          <div class="deliverHeadActions">
            <button class="btnPrimary" :disabled="!canEdit" @click="openDeliverableCreate">新增交付物</button>
          </div>
        </div>

        <div class="deliverGrid">
          <section class="deliverCol">
            <div class="deliverSubHead">
              <div class="subTitle">验收清单</div>
              <div class="muted subMeta">{{ checklistDoneCount }}/{{ checklist.length }}</div>
            </div>

            <div class="checkComposer">
              <n-input
                v-model:value="checklistDraft"
                :disabled="!canEdit"
                placeholder="新增一条可检查的验收点…"
                @keyup.enter="addChecklistItem"
              />
              <button class="btnGhost" :disabled="addingChecklist || !canEdit" @click="addChecklistItem">
                <span>添加</span>
                <span v-if="addingChecklist" class="ml-2 inline-block h-4 w-4 rounded-full border-2 border-black/15 border-t-black/55 animate-spin" />
              </button>
            </div>

            <n-spin :show="checklistLoading">
              <div v-if="!checklist.length && !checklistLoading" class="muted empty">暂无清单</div>
              <div v-else class="checkList">
                <div v-for="i in checklist" :key="i.id" class="checkRow" :class="{ done: Number(i.isDone || 0) === 1 }">
                  <div class="checkLeft">
                    <n-checkbox :checked="Number(i.isDone || 0) === 1" :disabled="!canEdit" @update:checked="() => toggleChecklistDone(i)" />
                    <div class="checkMain">
                      <div class="checkText">{{ i.content }}</div>
                      <div class="muted checkMeta">
                        <span v-if="i.username">{{ i.username }}</span>
                        <span v-if="i.createTime"> · {{ fmt(Date.parse(i.createTime)) }}</span>
                      </div>
                    </div>
                  </div>
                  <div class="checkActions">
                    <button class="btnLink danger" :disabled="!canEdit" @click="removeChecklistItem(i)">删除</button>
                  </div>
                </div>
              </div>
            </n-spin>
          </section>

          <section class="deliverCol">
            <div class="deliverSubHead">
              <div class="subTitle">交付物列表</div>
              <div class="muted subMeta">{{ deliverableDoneCount }}/{{ deliverables.length }}</div>
            </div>

            <n-spin :show="deliverablesLoading">
              <div v-if="!deliverables.length && !deliverablesLoading" class="muted empty">暂无交付物</div>
              <div v-else class="deliverList" @dragover.prevent="onDeliverableListDragOver" @drop.prevent="onDeliverableDrop(null)">
                <div
                  v-for="d in deliverables"
                  :key="d.id"
                  class="deliverRow"
                  :class="{
                    done: String(d.status || '').toUpperCase() === 'DONE',
                    dragOver: deliverableDrop?.id === d.id
                  }"
                  :draggable="canEdit"
                  @dragstart.stop="onDeliverableDragStart(d.id)"
                  @dragend.stop="onDeliverableDragEnd"
                  @dragover.prevent="(e) => onDeliverableDragOver(e, d.id)"
                  @drop.prevent="onDeliverableDrop(d.id)"
                >
                  <div class="dragGrip" aria-hidden="true"></div>
                  <div class="deliverMain">
                    <div class="deliverTop">
                      <div class="deliverTitle">{{ d.title }}</div>
                      <div class="deliverBadges">
                        <span class="badge">{{ String(d.type || '').toUpperCase() }}</span>
                        <span class="badge status" :class="{ ok: String(d.status || '').toUpperCase() === 'DONE' }">
                          {{ String(d.status || 'PENDING').toUpperCase() === 'DONE' ? 'DONE' : 'PENDING' }}
                        </span>
                      </div>
                    </div>
                    <div v-if="d.url" class="muted deliverMeta">
                      <a class="deliverLink" :href="d.url || undefined" target="_blank" rel="noreferrer">{{ d.url }}</a>
                    </div>
                    <div v-else-if="d.content" class="muted deliverMeta">{{ String(d.content).slice(0, 120) }}<span v-if="String(d.content).length > 120">…</span></div>
                    <div class="muted deliverMeta">
                      <span v-if="d.username">{{ d.username }}</span>
                      <span v-if="d.createTime"> · {{ fmt(Date.parse(d.createTime)) }}</span>
                    </div>
                  </div>
                  <div class="deliverActions">
                    <button class="btnLink" @click="openDeliverableEdit(d)">{{ String(d.type || '').toUpperCase() === 'DOC' ? '查看' : '编辑' }}</button>
                    <button class="btnLink" :disabled="!canEdit" @click="toggleDeliverableDone(d)">
                      {{ String(d.status || '').toUpperCase() === 'DONE' ? '恢复' : '完成' }}
                    </button>
                    <button class="btnLink danger" :disabled="!canEdit" @click="removeDeliverable(d)">删除</button>
                  </div>
                </div>
              </div>
            </n-spin>
          </section>
        </div>
      </n-card>

      <n-card class="panel block span2" :bordered="false">
        <div class="block-head">
          <div class="h2">{{ Number(task?.parentTaskId || 0) > 0 ? 'Epic' : 'Subtasks' }}</div>
          <div class="muted meta">{{ Number(task?.parentTaskId || 0) > 0 ? '' : subtasks.length }}</div>
          <div v-if="Number(task?.parentTaskId || 0) === 0" class="ml-auto">
            <button class="btnTealSm" :disabled="!canEdit" @click="openSubtaskCreate">新增子任务</button>
          </div>
        </div>

        <n-spin :show="subtasksLoading || parentTaskLoading">
          <div v-if="Number(task?.parentTaskId || 0) > 0" class="subtaskList">
            <button v-if="parentTask" class="subtaskRow" @click="goTask(parentTask.id)">
              <div class="subtaskMain">
                <div class="subtaskTitle">{{ parentTask.title }}</div>
                <span class="subtaskBadge">{{ parentTask.status }}</span>
              </div>
              <div class="muted subtaskMeta">#{{ parentTask.id }}</div>
            </button>
            <div v-else class="muted empty">父任务不存在或无权限</div>
          </div>
          <div v-else class="subtaskList">
            <div v-if="!subtasks.length && !subtasksLoading" class="muted empty">暂无子任务</div>
            <button v-for="st in subtasks" :key="st.id" class="subtaskRow" @click="goTask(st.id)">
              <div class="subtaskMain">
                <div class="subtaskTitle">{{ st.title }}</div>
                <span class="subtaskBadge">{{ st.status }}</span>
              </div>
              <div class="muted subtaskMeta">#{{ st.id }}</div>
            </button>
          </div>
        </n-spin>
      </n-card>

      <n-card class="panel block span2" :bordered="false">
        <div class="block-head">
          <div class="h2">讨论</div>
          <div class="muted meta">{{ comments.length }}</div>
        </div>

        <div class="attachBox">
          <div class="attachHead">
            <div class="attachTitleWrap">
              <div class="muted label">附件</div>
            </div>
            <div class="attachHeadRight">
              <button class="btnGhost" :disabled="!canEdit || uploadingAttachment" @click="openFilePicker(null)">
                上传附件
              </button>
            </div>
          </div>
          <n-spin :show="attachmentsLoading">
            <div v-if="!taskAttachments.length && !attachmentsLoading" class="muted empty">暂无附件</div>
            <div v-else class="fileList">
              <div v-for="a in taskAttachments" :key="a.id" class="fileRow">
                <div class="fileMain">
                  <div class="fileTop">
                    <div class="fileName">{{ a.originalName }}</div>
                    <span class="fileType">{{ previewBadge(a) }}</span>
                  </div>
                  <div class="muted fileMeta">
                    {{ fmtSize(a.sizeBytes) }}<span v-if="a.createTime"> · {{ fmt(Date.parse(a.createTime)) }}</span>
                  </div>
                </div>
                <div class="fileActions">
                  <button v-if="canPreviewAttachment(a)" class="btnLink preview" @click="previewAttachment(a)">预览</button>
                  <button class="btnLink" @click="downloadAttachment(a)">下载</button>
                  <button class="btnLink danger" :disabled="!canEdit" @click="removeAttachment(a)">删除</button>
                </div>
              </div>
            </div>
          </n-spin>
        </div>

        <div class="composer">
          <div v-if="replyTo" class="replyBanner">
            <div class="muted">回复 {{ replyTo.username }}</div>
            <button class="btnLink" @click="replyTo = null">取消</button>
          </div>
          <n-input
            v-model:value="commentDraft"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 6 }"
            :disabled="!canEdit"
            placeholder="写一条评论…（支持 @用户名）"
          />
          <div class="composerActions">
            <button class="btnPrimary" :disabled="sendingComment || !canEdit" @click="submitComment">
              <span>发送</span>
              <span v-if="sendingComment" class="ml-2 inline-block h-4 w-4 rounded-full border-2 border-white/30 border-t-white animate-spin" />
            </button>
          </div>
        </div>

        <n-spin :show="commentsLoading">
          <div class="commentList">
            <div
              v-for="c in comments"
              :key="c.id"
              class="cRow"
              :id="`c-${c.id}`"
              :class="{ reply: !!c.replyToId, focus: c.id === highlightCommentId }"
            >
              <div class="cAvatar" aria-hidden="true">{{ (c.username || 'U').slice(0, 1).toUpperCase() }}</div>
              <div class="cMain">
                <div class="cHead">
                  <div class="cName">{{ c.username }}</div>
                  <div class="muted cTime">{{ fmt(c.createdAt) }}</div>
                </div>
                <div class="cContent">{{ c.content }}</div>
                <div v-if="commentAttachmentsMap[c.id]?.length" class="cFiles">
                  <div v-for="a in commentAttachmentsMap[c.id]" :key="a.id" class="cFile">
                    <div class="cFileMain">
                      <div class="cFileTop">
                        <div class="cFileName">{{ a.originalName }}</div>
                        <span class="fileType small">{{ previewBadge(a) }}</span>
                      </div>
                      <div class="muted cFileMeta">{{ fmtSize(a.sizeBytes) }}</div>
                    </div>
                    <div class="cFileActions">
                      <button v-if="canPreviewAttachment(a)" class="btnLink preview" @click="previewAttachment(a)">预览</button>
                      <button class="btnLink" @click="downloadAttachment(a)">下载</button>
                      <button class="btnLink danger" :disabled="!canEdit" @click="removeAttachment(a)">删除</button>
                    </div>
                  </div>
                </div>
                <div class="cActions">
                  <button class="btnLink" :disabled="!canEdit" @click="replyTo = c">回复</button>
                  <button class="btnLink" :disabled="!canEdit || uploadingAttachment" @click="openFilePicker(c.id)">
                    附件
                  </button>
                </div>
              </div>
            </div>
            <div v-if="!comments.length && !commentsLoading" class="muted empty">暂无评论</div>
          </div>
        </n-spin>
      </n-card>
    </div>

    <n-modal v-model:show="subtaskOpen" :mask-closable="false">
      <n-card style="width: 520px" :bordered="false" title="新增子任务">
        <div class="form">
          <div class="row">
            <div class="muted label">标题</div>
            <n-input v-model:value="subtaskTitle" placeholder="例如：API 鉴权 / 前端联调 / 回归测试" />
          </div>
        </div>
        <template #footer>
          <div class="modal-actions">
            <button class="btnGhost" :disabled="creatingSubtask" @click="subtaskOpen = false">取消</button>
            <button class="btnTealSm" :disabled="creatingSubtask" @click="createSubtask">
              <span>创建</span>
              <span v-if="creatingSubtask" class="ml-2 inline-block h-3.5 w-3.5 rounded-full border-2 border-white/30 border-t-white animate-spin" />
            </button>
          </div>
        </template>
      </n-card>
    </n-modal>

    <n-modal v-model:show="deliverableModalOpen" :mask-closable="false">
      <n-card style="width: 560px" :bordered="false" title="交付物">
        <div class="deliverForm">
          <div class="row2">
            <div>
              <div class="muted label">类型</div>
              <n-select
                v-model:value="deliverableType"
                :options="[
                  { label: 'LINK', value: 'LINK' },
                  { label: 'DOC', value: 'DOC' },
                  { label: 'PR', value: 'PR' }
                ]"
                :disabled="!canEdit"
              />
            </div>
            <div>
              <div class="muted label">标题</div>
              <n-input v-model:value="deliverableTitle" :disabled="!canEdit" placeholder="例如：PR 已合并 / 接口联调完成" />
            </div>
          </div>

          <div v-if="deliverableType !== 'DOC'">
            <div class="muted label">链接</div>
            <n-input v-model:value="deliverableUrl" :disabled="!canEdit" placeholder="http(s)://..." />
          </div>

          <div v-else>
            <div class="docHead">
              <div class="muted label">内容（支持 Markdown）</div>
              <div class="docTabs">
                <button
                  class="docTab"
                  :class="{ active: deliverableDocMode === 'EDIT' }"
                  type="button"
                  @click="deliverableDocMode = 'EDIT'"
                >
                  编辑
                </button>
                <button
                  class="docTab"
                  :class="{ active: deliverableDocMode === 'PREVIEW' }"
                  type="button"
                  @click="deliverableDocMode = 'PREVIEW'"
                >
                  预览
                </button>
              </div>
            </div>
            <div v-if="deliverableDocMode === 'EDIT'">
              <n-input
                v-model:value="deliverableContent"
                :disabled="!canEdit"
                type="textarea"
                :autosize="{ minRows: 6, maxRows: 12 }"
                placeholder="写下方案、交付说明或验收记录…"
              />
            </div>
            <div v-else class="docPreview">
              <markdown-view :content="deliverableContent || ''" />
            </div>
          </div>
        </div>

        <template #footer>
          <div class="modal-actions">
            <button class="btnGhost" :disabled="deliverableSaving" @click="deliverableModalOpen = false">取消</button>
            <button class="btnPrimary" :disabled="deliverableSaving || !canEdit" @click="saveDeliverable">
              <span>保存</span>
              <span v-if="deliverableSaving" class="ml-2 inline-block h-4 w-4 rounded-full border-2 border-white/30 border-t-white animate-spin" />
            </button>
          </div>
        </template>
      </n-card>
    </n-modal>

    <input ref="fileInputRef" type="file" class="hidden" @change="onPickFile" />
  </div>
</template>

<style scoped>
.detail-page {
  max-width: 1080px;
}

.top {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 16px;
}

.right {
  display: flex;
  gap: 10px;
  align-items: center;
}

.liveHint {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: -0.2px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.76);
  color: rgba(15, 23, 42, 0.78);
}

.pill.watch {
  border-color: rgba(20, 184, 166, 0.18);
  background: rgba(20, 184, 166, 0.10);
  color: rgba(13, 148, 136, 0.95);
}

.pill.edit {
  border-color: rgba(6, 182, 212, 0.18);
  background: rgba(6, 182, 212, 0.10);
  color: rgba(8, 145, 178, 0.98);
}

.panel {
  background: rgba(255, 255, 255, 0.82);
  border: 0;
  box-shadow: 0 16px 44px rgba(2, 6, 23, 0.08);
  backdrop-filter: blur(12px);
}

.subtaskList {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.subtaskRow {
  width: 100%;
  text-align: left;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(241, 245, 249, 0.65);
  box-shadow: 0 10px 28px rgba(2, 6, 23, 0.06);
  transition: transform 120ms ease, box-shadow 120ms ease;
}

.subtaskRow:hover {
  transform: translateY(-1px);
  box-shadow: 0 14px 34px rgba(2, 6, 23, 0.09);
}

.subtaskMain {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.subtaskTitle {
  font-weight: 650;
  letter-spacing: -0.2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.subtaskBadge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(226, 232, 240, 0.85);
  color: rgba(15, 23, 42, 0.72);
  flex-shrink: 0;
}

.subtaskMeta {
  font-size: 12px;
  flex-shrink: 0;
}

.cRow.focus {
  border-color: rgba(20, 184, 166, 0.38);
  box-shadow: 0 18px 60px rgba(2, 6, 23, 0.10);
  background: radial-gradient(900px 220px at 20% 0%, rgba(6, 182, 212, 0.10), transparent 60%), rgba(255, 255, 255, 0.95);
}

.btnPrimary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 9px 12px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--accent), var(--accent2));
  color: #ffffff;
  border: 1px solid rgba(15, 23, 42, 0.12);
  font-size: 13px;
  font-weight: 800;
  letter-spacing: -0.2px;
  white-space: nowrap;
  transition: transform 140ms ease, box-shadow 140ms ease, background 140ms ease;
}

.btnPrimary:hover {
  background: linear-gradient(135deg, var(--accent2), var(--accent));
  transform: translateY(-1px);
  box-shadow: 0 16px 50px rgba(20, 184, 166, 0.20);
}

.btnPrimary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.btnGhost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 9px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.75);
  color: rgba(15, 23, 42, 0.88);
  border: 1px solid rgba(15, 23, 42, 0.12);
  font-size: 13px;
  font-weight: 800;
  letter-spacing: -0.2px;
  white-space: nowrap;
  transition: transform 140ms ease, box-shadow 140ms ease, background 140ms ease, border-color 140ms ease;
}

.btnTealSm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 9px 12px;
  border-radius: 12px;
  border: 0;
  background: linear-gradient(135deg, var(--accent), var(--accent2));
  color: rgba(255, 255, 255, 0.96);
  font-size: 13px;
  font-weight: 860;
  letter-spacing: -0.2px;
  white-space: nowrap;
  transition: transform 140ms ease, box-shadow 140ms ease;
}

.btnTealSm:hover {
  transform: translateY(-1px);
  box-shadow: 0 16px 50px rgba(20, 184, 166, 0.18);
}

.btnTealSm:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.btnGhost:hover {
  background: rgba(255, 255, 255, 0.92);
  border-color: rgba(15, 23, 42, 0.16);
  transform: translateY(-1px);
  box-shadow: 0 14px 40px rgba(2, 6, 23, 0.10);
}

.btnGhost:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.btnGhost.danger {
  color: rgba(239, 68, 68, 0.95);
  border-color: rgba(239, 68, 68, 0.26);
  background: rgba(255, 255, 255, 0.70);
}

.btnGhost.danger:hover {
  border-color: rgba(239, 68, 68, 0.34);
  background: rgba(255, 255, 255, 0.92);
}

.btnLink {
  background: transparent;
  border: 0;
  padding: 4px 6px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 800;
  color: rgba(15, 23, 42, 0.66);
  transition: background 140ms ease, color 140ms ease;
}

.btnLink:hover {
  background: rgba(15, 23, 42, 0.06);
  color: rgba(15, 23, 42, 0.88);
}

.btnLink:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.loading {
  padding: 16px 18px;
  font-size: 13px;
}

.grid {
  display: grid;
  grid-template-columns: 1.35fr 0.65fr;
  gap: 16px;
  align-items: stretch;
}

.block {
  padding: 14px 14px;
}

.span2 {
  grid-column: 1 / -1;
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

.form {
  display: grid;
  gap: 12px;
}

.row2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.label {
  font-size: 12px;
  margin-bottom: 6px;
}

.noteBox {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}

.timeline {
  display: grid;
  gap: 10px;
}

.timelineScroll {
  max-height: 520px;
  overflow: auto;
  padding-right: 6px;
  scrollbar-gutter: stable;
}

.timelineScroll::-webkit-scrollbar {
  width: 10px;
}

.timelineScroll::-webkit-scrollbar-thumb {
  background: rgba(15, 23, 42, 0.10);
  border: 3px solid rgba(255, 255, 255, 0.0);
  border-radius: 999px;
  background-clip: padding-box;
}

.timelineScroll::-webkit-scrollbar-thumb:hover {
  background: rgba(15, 23, 42, 0.16);
  border: 3px solid rgba(255, 255, 255, 0.0);
  border-radius: 999px;
  background-clip: padding-box;
}

.attachBox {
  margin-top: 10px;
  margin-bottom: 12px;
  border-radius: 18px;
  background:
    radial-gradient(560px 180px at 0% 0%, rgba(20, 184, 166, 0.10), transparent 58%),
    rgba(255, 255, 255, 0.76);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7), 0 12px 30px rgba(2, 6, 23, 0.05);
  padding: 12px 12px;
}

.attachHead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.attachHeadRight {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}

.attachTitleWrap {
  min-width: 0;
}

.attachSub {
  font-size: 12px;
  color: rgba(15, 23, 42, 0.52);
}

.fileList {
  margin-top: 10px;
  display: grid;
  gap: 8px;
}

.fileRow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 8px 24px rgba(2, 6, 23, 0.04);
  transition: transform 160ms ease, box-shadow 160ms ease, background 160ms ease;
}

.fileRow:hover,
.cFile:hover {
  transform: translateY(-1px);
  box-shadow: 0 16px 34px rgba(2, 6, 23, 0.08);
}

.fileMain {
  min-width: 0;
}

.fileTop,
.cFileTop {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.fileName {
  font-size: 13px;
  font-weight: 900;
  letter-spacing: -0.2px;
  color: rgba(15, 23, 42, 0.88);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fileMeta {
  margin-top: 4px;
  font-size: 12px;
}

.fileType {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 22px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(20, 184, 166, 0.12);
  color: rgba(13, 148, 136, 0.92);
  font-size: 11px;
  font-weight: 900;
  letter-spacing: -0.1px;
  flex-shrink: 0;
}

.fileType.small {
  height: 20px;
  padding: 0 8px;
}

.fileActions {
  display: inline-flex;
  gap: 6px;
  flex-shrink: 0;
}

.btnLink.preview {
  color: rgba(13, 148, 136, 0.88);
}

.btnLink.preview:hover {
  background: rgba(20, 184, 166, 0.10);
  color: rgba(13, 148, 136, 1);
}

.btnLink.danger {
  color: rgba(239, 68, 68, 0.72);
}

.btnLink.danger:hover {
  background: rgba(239, 68, 68, 0.08);
  color: rgba(239, 68, 68, 0.92);
}

.composer {
  display: grid;
  gap: 10px;
  margin-bottom: 14px;
}

.replyBanner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.72);
  border-radius: 12px;
  padding: 8px 10px;
}

.composerActions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.tip {
  font-size: 12px;
}

.commentList {
  display: grid;
  gap: 10px;
}

.cRow {
  display: grid;
  grid-template-columns: 30px 1fr;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.72);
}

.cRow.reply {
  margin-left: 18px;
  border-color: rgba(20, 184, 166, 0.18);
}

.cAvatar {
  width: 30px;
  height: 30px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 800;
  color: rgba(15, 23, 42, 0.86);
  background: #ffffff;
}

.cMain {
  min-width: 0;
}

.cHead {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 12px;
}

.cName {
  font-weight: 900;
  letter-spacing: -0.2px;
  font-size: 13px;
}

.cTime {
  font-size: 12px;
  white-space: nowrap;
}

.cContent {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: rgba(15, 23, 42, 0.86);
  white-space: pre-wrap;
}

.cFiles {
  margin-top: 8px;
  display: grid;
  gap: 6px;
}

.cFile {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.82);
}

.cFileMain {
  min-width: 0;
}

.cFileName {
  font-size: 12px;
  font-weight: 850;
  color: rgba(15, 23, 42, 0.82);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cFileMeta {
  margin-top: 2px;
  font-size: 12px;
}

.cFileActions {
  display: inline-flex;
  gap: 6px;
  flex-shrink: 0;
}

.cActions {
  margin-top: 6px;
}

.titem {
  border-radius: 14px;
  border: 1px solid var(--stroke2);
  background: rgba(255, 255, 255, 0.62);
  padding: 10px 12px;
}

.tt {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: baseline;
}

.tlabel {
  font-weight: 700;
  letter-spacing: -0.2px;
}

.ttime {
  font-size: 12px;
  white-space: nowrap;
}

.tdetail {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: rgba(15, 23, 42, 0.86);
  white-space: pre-wrap;
}

.deliverHeadActions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.deliverGrid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.deliverCol {
  border-radius: 16px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.72);
  padding: 12px 12px;
}

.deliverSubHead {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 10px;
}

.subTitle {
  font-weight: 900;
  letter-spacing: -0.2px;
  color: rgba(15, 23, 42, 0.88);
}

.subMeta {
  font-size: 12px;
}

.checkComposer {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}

.checkList {
  display: grid;
  gap: 8px;
}

.checkRow {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 10px 10px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.checkRow.done {
  background: rgba(20, 184, 166, 0.06);
  border-color: rgba(20, 184, 166, 0.14);
}

.checkLeft {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}

.checkMain {
  min-width: 0;
}

.checkText {
  font-size: 13px;
  font-weight: 800;
  color: rgba(15, 23, 42, 0.86);
  line-height: 1.45;
  word-break: break-word;
}

.checkMeta {
  margin-top: 4px;
  font-size: 12px;
}

.checkActions {
  flex-shrink: 0;
}

.deliverList {
  display: grid;
  gap: 8px;
}

.deliverRow {
  display: grid;
  grid-template-columns: 14px 1fr auto;
  gap: 12px;
  align-items: start;
  padding: 10px 10px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(15, 23, 42, 0.06);
  cursor: grab;
}

.deliverRow:active {
  cursor: grabbing;
}

.deliverRow.dragOver {
  box-shadow: 0 0 0 2px rgba(20, 184, 166, 0.18);
}

.dragGrip {
  width: 14px;
  margin-top: 2px;
  border-radius: 10px;
  background: repeating-linear-gradient(
    to bottom,
    rgba(15, 23, 42, 0.16) 0,
    rgba(15, 23, 42, 0.16) 2px,
    transparent 2px,
    transparent 6px
  );
}

.deliverRow.done {
  background: rgba(20, 184, 166, 0.06);
  border-color: rgba(20, 184, 166, 0.14);
}

.deliverTop {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.deliverTitle {
  font-size: 13px;
  font-weight: 900;
  color: rgba(15, 23, 42, 0.90);
  letter-spacing: -0.2px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.deliverBadges {
  display: inline-flex;
  gap: 6px;
  flex-shrink: 0;
}

.badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 20px;
  padding: 0 8px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
  color: rgba(15, 23, 42, 0.72);
  font-size: 11px;
  font-weight: 900;
}

.badge.status {
  background: rgba(245, 158, 11, 0.10);
  color: rgba(180, 83, 9, 0.92);
}

.badge.status.ok {
  background: rgba(20, 184, 166, 0.12);
  color: rgba(13, 148, 136, 0.92);
}

.deliverMeta {
  margin-top: 6px;
  font-size: 12px;
  word-break: break-word;
}

.deliverLink {
  color: rgba(13, 148, 136, 0.92);
  text-decoration: none;
}

.deliverLink:hover {
  text-decoration: underline;
}

.deliverActions {
  display: inline-flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.deliverForm {
  display: grid;
  gap: 12px;
}

.docHead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.docTabs {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.docTab {
  height: 28px;
  padding: 0 10px;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.06);
  color: rgba(15, 23, 42, 0.74);
  font-size: 12px;
  font-weight: 900;
}

.docTab.active {
  background: rgba(20, 184, 166, 0.12);
  color: rgba(13, 148, 136, 0.95);
}

.docPreview {
  margin-top: 8px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.03);
  max-height: 360px;
  overflow: auto;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.empty {
  padding: 8px 2px 2px;
  font-size: 12px;
}

@media (max-width: 980px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .row2 {
    grid-template-columns: 1fr;
  }
  .timelineScroll {
    max-height: none;
    overflow: visible;
    padding-right: 0;
  }
  .deliverGrid {
    grid-template-columns: 1fr;
  }
  .checkComposer {
    grid-template-columns: 1fr;
  }
  .deliverRow {
    grid-template-columns: 1fr;
  }
  .attachHead,
  .fileRow,
  .cFile {
    align-items: stretch;
    flex-direction: column;
  }
  .fileActions,
  .cFileActions,
  .attachHeadRight {
    width: 100%;
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}
</style>
