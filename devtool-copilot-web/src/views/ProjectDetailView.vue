<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NCard, NDropdown, NInput, NModal, NSelect, NSpin, NSwitch, useDialog, useMessage } from 'naive-ui'
import { useProjectStore } from '../stores/project'
import { useTaskStore } from '../stores/task'
import { useRealtimeStore } from '../stores/realtime'
import { giteeApi, type GiteePanelDTO, type GiteeRepoConfigDTO } from '../api/gitee'
import { projectApi } from '../api/project'
import { projectTaskRuleApi, type ProjectTaskRuleDTO } from '../api/projectTaskRule'
import { projectCollabApi, type ProjectActivityItem, type ProjectMemberItem, type ProjectMemberRole } from '../api/projectCollab'
import { assetApi } from '../api/asset'
import { milestoneApi, type Milestone } from '../api/milestone'
import { releaseApi, type ProjectRelease } from '../api/release'
import MarkdownView from '../components/MarkdownView.vue'
import PresenceBar from '../components/PresenceBar.vue'
import TaskBoard from '../components/TaskBoard.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const projectStore = useProjectStore()
const taskStore = useTaskStore()
const rt = useRealtimeStore()

const projectId = computed(() => {
  const pid = Number(route.params.id)
  return Number.isFinite(pid) ? pid : 0
})
const project = computed(() => projectStore.byId.get(projectId.value))
const isArchived = computed(() => Number(project.value?.archived || 0) === 1)

const giteeConfig = ref<GiteeRepoConfigDTO | null>(null)
const panel = ref<GiteePanelDTO | null>(null)
const loadingPanel = ref(false)

const myRole = ref<ProjectMemberRole | null>(null)
const collabLoading = ref(false)
const collabMembers = ref<ProjectMemberItem[]>([])
const collabActivities = ref<ProjectActivityItem[]>([])

const configOpen = ref(false)
const cfgOwner = ref('')
const cfgRepo = ref('')
const cfgToken = ref('')
const savingCfg = ref(false)

const linkOpen = ref(false)
const linkTaskId = ref<number | null>(null)
const linkPr = ref('')
const linking = ref(false)

const taskRulesLoading = ref(false)
const taskRulesSaving = ref(false)
const taskRules = ref<ProjectTaskRuleDTO | null>(null)
const requireChecklistDoneForDone = ref(false)
const taskRulesReady = ref(false)

const milestonesLoading = ref(false)
const milestones = ref<Milestone[]>([])
const createMsOpen = ref(false)
const msName = ref('')
const msDesc = ref('')
const creatingMs = ref(false)

const releasesLoading = ref(false)
const releases = ref<ProjectRelease[]>([])
const createRelOpen = ref(false)
const relVersion = ref('')
const relSummary = ref('')
const relMilestoneId = ref<number | null>(null)
const creatingRel = ref(false)

const releaseOpen = ref(false)
const releaseLoading = ref(false)
const releaseMd = ref('')
const releaseTitle = ref('')

const workflowOpen = ref(false)
const devopsOpen = ref(false)

const moreOptions = computed(() => {
  const opts: Array<{ key: string; label: string }> = []
  opts.push({ key: 'workflow', label: '流程' })
  opts.push({ key: 'devops', label: 'PR 与 CI' })
  if (myRole.value === 'OWNER') {
    opts.push({ key: 'audit', label: '审计' })
    opts.push({ key: 'archive', label: Number(project.value?.archived || 0) === 1 ? '取消归档' : '归档' })
    opts.push({ key: 'delete', label: '删除' })
  }
  return opts
})

function onMoreSelect(key: string) {
  if (!project.value) return
  if (key === 'workflow') {
    workflowOpen.value = true
    return
  }
  if (key === 'devops') {
    devopsOpen.value = true
    return
  }
  if (key === 'audit') {
    router.push({ name: 'project-audit', params: { id: project.value.id } })
    return
  }
  if (key === 'archive') {
    toggleArchiveProject()
    return
  }
  if (key === 'delete') {
    removeProject()
  }
}

onMounted(async () => {
  if (!projectId.value) {
    router.replace({ name: 'workspace' })
    return
  }
  if (!projectStore.projects.length) await projectStore.load()
  try {
    await taskStore.loadByProject(projectId.value)
  } catch (e: any) {
    const msg = String(e?.message || '')
    if (msg.includes('成员已被禁用')) {
      message.error('你已被该项目禁用')
      router.replace({ name: 'workspace' })
      return
    }
    message.error(msg || '加载失败')
    return
  }
  rt.subscribe(projectId.value, 'PROJECT', projectId.value)
  await loadMyRole()
  await loadCollabHub()
  await loadGiteeConfig()
  await loadTaskRules()
  await loadMilestones()
  await loadReleases()
})

watch(projectId, async (id) => {
  if (!id || !Number.isFinite(id)) {
    router.replace({ name: 'workspace' })
    return
  }
  try {
    await taskStore.loadByProject(id)
  } catch (e: any) {
    const msg = String(e?.message || '')
    if (msg.includes('成员已被禁用')) {
      message.error('你已被该项目禁用')
      router.replace({ name: 'workspace' })
      return
    }
    message.error(msg || '加载失败')
    return
  }
  rt.subscribe(id, 'PROJECT', id)
  await loadMyRole()
  await loadCollabHub()
  await loadGiteeConfig()
  await loadTaskRules()
  await loadMilestones()
  await loadReleases()
})

onUnmounted(() => {
  rt.subscribe(null)
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
      await taskStore.loadByProject(projectId.value)
      await loadCollabHub()
    }
    if (t === 'MEMBER_JOINED' || t === 'MEMBER_LEFT' || t === 'COMMENT_ADDED') {
      await loadCollabHub()
    }
    if (
      t === 'MILESTONE_CREATED' ||
      t === 'MILESTONE_PUBLISHED' ||
      t === 'MILESTONE_ARCHIVED' ||
      t === 'MILESTONE_UNARCHIVED'
    ) {
      await loadMilestones()
    }
    if (t === 'RELEASE_CREATED' || t === 'RELEASE_NOTES_GENERATED' || t === 'RELEASE_PUBLISHED' || t === 'RELEASE_DELETED') {
      await loadReleases()
    }
  }
)

function pillClass(status: string) {
  if (status === 'DONE') return 'pill done'
  if (status === 'DOING') return 'pill doing'
  return 'pill todo'
}

function prStateClass(state: string) {
  const s = String(state || '').toUpperCase()
  if (s === 'OPEN') return 'badge pr open'
  if (s === 'MERGED') return 'badge pr merged'
  if (s === 'CLOSED') return 'badge pr closed'
  return 'badge pr other'
}

function ciStateClass(state?: string | null) {
  const s = String(state || '').toUpperCase()
  if (s === 'SUCCESS') return 'badge ci success'
  if (s === 'FAILED' || s === 'FAILURE' || s === 'ERROR') return 'badge ci failed'
  if (s === 'RUNNING' || s === 'PENDING') return 'badge ci running'
  return 'badge ci unknown'
}

function statusLabel(status: string) {
  const s = String(status || '').toUpperCase()
  if (s === 'DONE') return '已完成'
  if (s === 'DOING') return '进行中'
  return '待办'
}

function prStateLabel(state: string) {
  const s = String(state || '').toUpperCase()
  if (s === 'OPEN') return '待合并'
  if (s === 'MERGED') return '已合并'
  if (s === 'CLOSED') return '已关闭'
  return '其他'
}

function ciStateLabel(state?: string | null) {
  const s = String(state || '').toUpperCase()
  if (s === 'SUCCESS') return '通过'
  if (s === 'FAILED' || s === 'FAILURE' || s === 'ERROR') return '失败'
  if (s === 'RUNNING' || s === 'PENDING') return '进行中'
  return '未知'
}

function msStateClass(status: string) {
  const s = String(status || '').toUpperCase()
  if (s === 'PUBLISHED') return 'msBadge published'
  if (s === 'ARCHIVED') return 'msBadge archived'
  return 'msBadge open'
}

function relStateClass(status: string) {
  const s = String(status || '').toUpperCase()
  if (s === 'PUBLISHED') return 'msBadge published'
  return 'msBadge open'
}

function msStateLabel(status: string) {
  const s = String(status || '').toUpperCase()
  if (s === 'PUBLISHED') return '已发布'
  if (s === 'ARCHIVED') return '已归档'
  return '进行中'
}

function relStateLabel(status: string) {
  const s = String(status || '').toUpperCase()
  if (s === 'PUBLISHED') return '已发布'
  return '草稿'
}

async function loadGiteeConfig() {
  if (!Number.isFinite(projectId.value)) return
  try {
    giteeConfig.value = await giteeApi.getConfig(projectId.value)
  } catch {
    giteeConfig.value = null
  }
  panel.value = null
}

async function loadMyRole() {
  try {
    const res = await projectCollabApi.members(projectId.value)
    myRole.value = res.myRole
  } catch {
    myRole.value = null
  }
}

function isTaskRisk(task: any) {
  if (!task || String(task.status || '') === 'DONE' || !task.dueTime) return false
  const ts = Date.parse(String(task.dueTime))
  if (Number.isNaN(ts)) return false
  return ts < Date.now()
}

function isTaskDueSoon(task: any) {
  if (!task || String(task.status || '') === 'DONE' || !task.dueTime) return false
  const ts = Date.parse(String(task.dueTime))
  if (Number.isNaN(ts)) return false
  return ts - Date.now() <= 3 * 24 * 60 * 60 * 1000
}

const collabOnlineCount = computed(() => collabMembers.value.filter((item) => Number(item.online || 0) === 1).length)
const collabRiskCount = computed(() => taskStore.tasks.filter((task) => isTaskRisk(task)).length)
const collabDueSoonCount = computed(() => taskStore.tasks.filter((task) => isTaskDueSoon(task)).length)
const collabDoingCount = computed(() => taskStore.tasks.filter((task) => String(task.status || '') === 'DOING').length)
const collabLeadMembers = computed(() => collabMembers.value.slice(0, 4).map((item) => item.username))
const collabFocus = computed(() => {
  if (collabRiskCount.value > 0 || collabDueSoonCount.value > 0) return 'risk'
  return 'all'
})
const collabHeadline = computed(() => {
  if (collabRiskCount.value > 0) return `${collabRiskCount.value} 项风险`
  if (collabDueSoonCount.value > 0) return `${collabDueSoonCount.value} 项临期`
  if (collabDoingCount.value > 0) return `${collabDoingCount.value} 项推进中`
  return '协作节奏稳定'
})

async function loadCollabHub() {
  if (!Number.isFinite(projectId.value) || !projectId.value) return
  collabLoading.value = true
  try {
    const [membersRes, activitiesRes] = await Promise.all([
      projectCollabApi.members(projectId.value),
      projectCollabApi.activities(projectId.value, 6)
    ])
    collabMembers.value = membersRes.members || []
    collabActivities.value = activitiesRes || []
  } catch {
    collabMembers.value = []
    collabActivities.value = []
  } finally {
    collabLoading.value = false
  }
}

async function loadTaskRules() {
  if (!Number.isFinite(projectId.value)) return
  taskRulesLoading.value = true
  try {
    taskRules.value = await projectTaskRuleApi.get(projectId.value)
    requireChecklistDoneForDone.value = !!taskRules.value.requireChecklistDoneForDone
    taskRulesReady.value = true
  } catch {
    taskRules.value = null
    requireChecklistDoneForDone.value = false
    taskRulesReady.value = false
  } finally {
    taskRulesLoading.value = false
  }
}

async function loadMilestones() {
  if (!Number.isFinite(projectId.value)) return
  milestonesLoading.value = true
  try {
    milestones.value = await milestoneApi.list(projectId.value, false)
  } catch {
    milestones.value = []
  } finally {
    milestonesLoading.value = false
  }
}

async function loadReleases() {
  if (!Number.isFinite(projectId.value)) return
  releasesLoading.value = true
  try {
    releases.value = await releaseApi.list(projectId.value)
  } catch {
    releases.value = []
  } finally {
    releasesLoading.value = false
  }
}

function openCreateRelease(preset?: { milestoneId?: number | null; version?: string }) {
  if (isArchived.value) {
    message.warning('项目已归档，只读')
    return
  }
  relVersion.value = String(preset?.version || '').trim()
  relSummary.value = ''
  relMilestoneId.value = preset?.milestoneId ?? null
  createRelOpen.value = true
}

async function createRelease() {
  if (isArchived.value) {
    message.warning('项目已归档，只读')
    return
  }
  const version = relVersion.value.trim()
  if (!version) {
    message.warning('请输入版本号')
    return
  }
  if (creatingRel.value) return
  creatingRel.value = true
  try {
    const id = await releaseApi.create({
      projectId: projectId.value,
      milestoneId: relMilestoneId.value || null,
      version,
      summary: relSummary.value.trim() ? relSummary.value.trim() : null,
      generateNotes: true
    })
    createRelOpen.value = false
    await loadReleases()
    router.push({ name: 'release-detail', params: { projectId: projectId.value, releaseId: id } })
  } catch (e: any) {
    message.error(e?.message || '创建失败')
  } finally {
    creatingRel.value = false
  }
}

function openCreateMs() {
  if (isArchived.value) {
    message.warning('项目已归档，只读')
    return
  }
  msName.value = ''
  msDesc.value = ''
  createMsOpen.value = true
}

async function createMs() {
  if (isArchived.value) {
    message.warning('项目已归档，只读')
    return
  }
  const name = msName.value.trim()
  const description = msDesc.value.trim()
  if (!name) {
    message.warning('请输入里程碑名称')
    return
  }
  creatingMs.value = true
  try {
    await milestoneApi.create({ projectId: projectId.value, name, description: description || undefined })
    createMsOpen.value = false
    await loadMilestones()
    message.success('已创建')
  } catch (e: any) {
    message.error(e?.message || '创建失败')
  } finally {
    creatingMs.value = false
  }
}

async function publishMs(m: Milestone) {
  if (isArchived.value) {
    message.warning('项目已归档，只读')
    return
  }
  dialog.warning({
    title: '发布里程碑',
    content: `确认发布「${m.name}」？将生成 Release Notes。`,
    positiveText: '发布',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await milestoneApi.publish(m.id)
        await loadMilestones()
        message.success('已发布')
        if (res?.assetId) {
          await openRelease(res.assetId, `发布说明 · ${m.name}`)
        }
      } catch (e: any) {
        message.error(e?.message || '发布失败')
      }
    }
  })
}

async function openRelease(assetId: number, title: string) {
  if (!assetId) return
  releaseOpen.value = true
  releaseLoading.value = true
  releaseMd.value = ''
  releaseTitle.value = title
  try {
    const file = await assetApi.preview(assetId)
    const text = await file.blob.text()
    releaseMd.value = `[Preview](/api/assets/${assetId}/preview)   [Download](/api/assets/${assetId}/download)\n\n` + text
  } catch (e: any) {
    releaseMd.value = ''
    message.error(e?.message || '加载失败')
  } finally {
    releaseLoading.value = false
  }
}

async function toggleArchiveProject() {
  if (!project.value) return
  if (myRole.value !== 'OWNER') return
  const isArchived = Number(project.value.archived || 0) === 1
  dialog.warning({
    title: isArchived ? '取消归档项目' : '归档项目',
    content: isArchived ? '确认取消归档？' : '确认归档该项目？归档后建议只读收尾。',
    positiveText: isArchived ? '取消归档' : '归档',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        if (isArchived) {
          await projectApi.unarchive(project.value!.id)
        } else {
          await projectApi.archive(project.value!.id)
        }
        await projectStore.load()
        message.success(isArchived ? '已取消归档' : '已归档')
      } catch (e: any) {
        message.error(e?.message || '操作失败')
      }
    }
  })
}

async function saveTaskRules(next: boolean) {
  if (myRole.value !== 'OWNER') return
  if (isArchived.value) {
    message.warning('项目已归档，只读')
    return
  }
  const prev = requireChecklistDoneForDone.value
  requireChecklistDoneForDone.value = next
  taskRulesSaving.value = true
  try {
    taskRules.value = await projectTaskRuleApi.save(projectId.value, { requireChecklistDoneForDone: next })
    requireChecklistDoneForDone.value = !!taskRules.value.requireChecklistDoneForDone
    taskRulesReady.value = true
    message.success('已保存')
  } catch (e: any) {
    requireChecklistDoneForDone.value = prev
    message.error(e?.message || '保存失败')
  } finally {
    taskRulesSaving.value = false
  }
}

async function refreshPanel() {
  if (!giteeConfig.value?.owner || !giteeConfig.value?.repo || !giteeConfig.value?.hasToken) {
    message.warning('请先绑定 Gitee 仓库与 Token')
    return
  }
  loadingPanel.value = true
  try {
    panel.value = await giteeApi.panel(projectId.value)
  } catch (e: any) {
    message.error(e?.message || '加载失败')
  } finally {
    loadingPanel.value = false
  }
}

function openConfig() {
  if (isArchived.value) {
    message.warning('项目已归档，只读')
    return
  }
  cfgOwner.value = giteeConfig.value?.owner || ''
  cfgRepo.value = giteeConfig.value?.repo || ''
  cfgToken.value = ''
  configOpen.value = true
}

async function saveConfig() {
  if (isArchived.value) {
    message.warning('项目已归档，只读')
    return
  }
  const owner = cfgOwner.value.trim()
  const repo = cfgRepo.value.trim()
  const token = cfgToken.value.trim()
  if (!owner || !repo || !token) {
    message.warning('请填写 owner / repo / token')
    return
  }
  savingCfg.value = true
  try {
    giteeConfig.value = await giteeApi.saveConfig({
      projectId: projectId.value,
      owner,
      repo,
      accessToken: token
    })
    configOpen.value = false
    await refreshPanel()
  } catch (e: any) {
    message.error(e?.message || '保存失败')
  } finally {
    savingCfg.value = false
  }
}

function openLink(taskId: number) {
  if (isArchived.value) {
    message.warning('项目已归档，只读')
    return
  }
  linkTaskId.value = taskId
  linkPr.value = ''
  linkOpen.value = true
}

async function submitLink() {
  if (isArchived.value) {
    message.warning('项目已归档，只读')
    return
  }
  if (!linkTaskId.value) return
  const pr = linkPr.value.trim()
  if (!pr) {
    message.warning('请输入 PR 链接或编号')
    return
  }
  linking.value = true
  try {
    await giteeApi.linkTask({ projectId: projectId.value, taskId: linkTaskId.value, pr })
    linkOpen.value = false
    await refreshPanel()
  } catch (e: any) {
    message.error(e?.message || '绑定失败')
  } finally {
    linking.value = false
  }
}

async function unlink(id?: number | null) {
  if (isArchived.value) {
    message.warning('项目已归档，只读')
    return
  }
  if (!id) return
  try {
    await giteeApi.unlink({ id })
    await refreshPanel()
  } catch (e: any) {
    message.error(e?.message || '解绑失败')
  }
}

function openTask(taskId: number) {
  router.push({ name: 'task-detail', params: { projectId: projectId.value, taskId } })
}

async function removeProject() {
  if (myRole.value !== 'OWNER') return
  if (!project.value) return
  const pid = projectId.value
  const name = project.value.name || ''
  dialog.warning({
    title: '删除项目',
    content: `确认删除项目「${name}」？项目下的任务与数据将一并删除，且不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await projectApi.delete(pid)
        await projectStore.load()
        message.success('已删除项目')
        router.replace({ name: 'workspace' })
      } catch (e: any) {
        message.error(e?.message || '删除失败')
      }
    }
  })
}
</script>

<template>
  <div class="page">
    <div v-if="!project" class="panel empty">
      <div class="h2">项目不存在</div>
      <div class="muted">—</div>
    </div>

    <template v-else>
      <!-- 头部 -->
      <div class="top">
        <div class="topMain">
          <div class="titleRow">
            <h1 class="proTitle">{{ project.name }}</h1>
            <span v-if="Number(project.archived || 0) === 1" class="archTag">已归档</span>
          </div>
          <div v-if="project.description" class="proDesc">{{ project.description }}</div>
        </div>
        <div class="topActions">
          <presence-bar :project-id="projectId" />
          <button
            class="btnGhost"
            type="button"
            @click="router.push({ name: 'collab-center', query: { projectId: project.id, focus: collabFocus } })"
          >
            协作中心
          </button>
          <button class="btnGhost" type="button" @click="router.push({ name: 'project-members', params: { id: project.id } })">
            成员
          </button>
          <button class="btnGhost" type="button" @click="router.push({ name: 'project-activity', params: { id: project.id } })">
            动态
          </button>
          <button class="btnPulse" type="button" @click="router.push({ name: 'project-pulse', params: { id: project.id } })">
            节奏洞察
          </button>
          <n-dropdown :options="moreOptions" placement="bottom-end" @select="onMoreSelect">
            <button class="btnGhost" type="button">更多</button>
          </n-dropdown>
        </div>
      </div>

      <!-- 主-侧栏 -->
      <div class="grid">
        <div class="mainCol">
          <task-board :project-id="projectId" :archived="isArchived" @open-task="openTask" />
        </div>

        <aside class="sideCol">
          <!-- 协作概览 -->
          <section class="sideBlock">
            <div class="sideHead">
              <span class="sideTitle">协作概览</span>
              <button
                class="sideMore"
                type="button"
                @click="router.push({ name: 'collab-center', query: { projectId: project.id, focus: collabFocus } })"
              >
                查看全部
              </button>
            </div>
            <n-spin :show="collabLoading">
              <div class="collabStats">
                <div class="csItem">
                  <div class="csVal">{{ collabOnlineCount }}</div>
                  <div class="csLabel">在线</div>
                </div>
                <div class="csItem">
                  <div class="csVal">{{ collabDoingCount }}</div>
                  <div class="csLabel">推进中</div>
                </div>
                <div class="csItem">
                  <div class="csVal">{{ collabDueSoonCount }}</div>
                  <div class="csLabel">临期</div>
                </div>
                <div class="csItem" :class="{ risk: collabRiskCount > 0 }">
                  <div class="csVal">{{ collabRiskCount }}</div>
                  <div class="csLabel">风险</div>
                </div>
              </div>
              <div class="memberRail">
                <span v-for="name in collabLeadMembers" :key="name" class="memberDot" :title="name">
                  {{ String(name || 'U').slice(0, 1).toUpperCase() }}
                </span>
                <span v-if="!collabLeadMembers.length" class="memberEmpty">暂无成员</span>
              </div>
              <div class="collabLine">{{ collabHeadline }}</div>
            </n-spin>
          </section>

          <!-- 里程碑 -->
          <section class="sideBlock">
            <div class="sideHead">
              <span class="sideTitle">里程碑</span>
              <button class="sideMore" type="button" :disabled="isArchived" @click="openCreateMs">新建</button>
            </div>
            <n-spin :show="milestonesLoading">
              <div v-if="!milestones.length" class="sideEmpty">暂无里程碑</div>
              <div v-else class="msList">
                <div v-for="m in milestones" :key="m.id" class="msItem">
                  <div class="msMain">
                    <div class="msNameRow">
                      <div class="msName truncate">{{ m.name }}</div>
                      <span :class="msStateClass(String(m.status || ''))">{{ msStateLabel(String(m.status || '')) }}</span>
                    </div>
                    <div class="msMeta">
                      {{ m.publishedTime ? `已发布 · ${m.publishedTime}` : m.dueTime ? `截止 · ${m.dueTime}` : '未设置截止' }}
                    </div>
                  </div>
                  <div class="msOps">
                    <button v-if="String(m.status || '').toUpperCase() === 'OPEN'" class="msBtn" type="button" :disabled="isArchived" @click="publishMs(m)">
                      发布
                    </button>
                    <button
                      v-else-if="m.releaseAssetId"
                      class="msBtn"
                      type="button"
                      @click="openRelease(Number(m.releaseAssetId), `发布说明 · ${m.name}`)"
                    >
                      说明
                    </button>
                    <button
                      class="msBtnGhost"
                      type="button"
                      :disabled="isArchived"
                      @click="openCreateRelease({ milestoneId: Number(m.id), version: String(m.name || '').trim() })"
                    >
                      发版
                    </button>
                  </div>
                </div>
              </div>
            </n-spin>
          </section>

          <!-- 发布记录 -->
          <section class="sideBlock">
            <div class="sideHead">
              <span class="sideTitle">发布记录</span>
              <button class="sideMore" type="button" :disabled="isArchived" @click="openCreateRelease()">新建</button>
            </div>
            <n-spin :show="releasesLoading">
              <div v-if="!releases.length" class="sideEmpty">暂无发布</div>
              <div v-else class="relList">
                <button
                  v-for="r in releases"
                  :key="r.id"
                  class="relItem"
                  type="button"
                  @click="router.push({ name: 'release-detail', params: { projectId: projectId, releaseId: r.id } })"
                >
                  <div class="relMain">
                    <div class="relVersionRow">
                      <div class="relVersion">{{ r.version }}</div>
                      <span :class="relStateClass(String(r.status || ''))">{{ relStateLabel(String(r.status || '')) }}</span>
                    </div>
                    <div class="relMeta">{{ r.publishedTime ? `已发布 · ${r.publishedTime}` : '草稿' }}</div>
                  </div>
                  <span v-if="r.milestoneId" class="relMs">里程碑 #{{ r.milestoneId }}</span>
                </button>
              </div>
            </n-spin>
          </section>
        </aside>
      </div>

      <n-modal v-model:show="createMsOpen" :mask-closable="false">
        <n-card style="width: 520px" title="新建里程碑" :bordered="false">
          <div class="form">
            <div class="row">
              <div class="muted label">名称</div>
              <n-input v-model:value="msName" placeholder="例如：v1.0 交付" />
            </div>
            <div class="row">
              <div class="muted label">描述</div>
              <n-input v-model:value="msDesc" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" placeholder="可选：本次发布范围/目标" />
            </div>
          </div>
          <template #footer>
            <div class="modal-actions">
              <n-button @click="createMsOpen = false">取消</n-button>
              <button class="btnPrimarySm" type="button" :disabled="creatingMs" @click="createMs">
                <span>创建</span>
                <span v-if="creatingMs" class="spin" />
              </button>
            </div>
          </template>
        </n-card>
      </n-modal>

      <n-modal v-model:show="createRelOpen" :mask-closable="false">
        <n-card style="width: 560px" title="新建发布" :bordered="false">
          <div class="form">
            <div class="row">
              <div class="muted label">版本号</div>
              <n-input v-model:value="relVersion" placeholder="例如：v1.0.0" />
            </div>
            <div class="row">
              <div class="muted label">里程碑</div>
              <n-select
                :value="relMilestoneId || 0"
                :options="[{ label: '选择里程碑（用于生成发布说明）', value: 0 }, ...milestones.map((m) => ({ label: m.name, value: m.id }))]"
                @update:value="(v) => (relMilestoneId = Number(v) || null)"
              />
            </div>
            <div class="row">
              <div class="muted label">变更摘要</div>
              <n-input v-model:value="relSummary" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" placeholder="可选" />
            </div>
          </div>
          <template #footer>
            <div class="modal-actions">
              <n-button @click="createRelOpen = false">取消</n-button>
              <button class="btnPrimarySm" type="button" :disabled="creatingRel || !relVersion.trim()" @click="createRelease">
                <span>创建</span>
                <span v-if="creatingRel" class="spin" />
              </button>
            </div>
          </template>
        </n-card>
      </n-modal>

      <n-modal v-model:show="releaseOpen" :mask-closable="true">
        <n-card style="width: 920px; max-width: calc(100vw - 32px)" :title="releaseTitle || '发布说明'" :bordered="false">
          <n-spin :show="releaseLoading">
            <markdown-view v-if="releaseMd" :content="releaseMd" />
            <div v-else class="empty-summary" />
          </n-spin>
        </n-card>
      </n-modal>

      <n-modal v-model:show="workflowOpen" :mask-closable="true">
        <n-card style="width: 560px; max-width: calc(100vw - 32px)" title="流程" :bordered="false">
          <n-spin :show="taskRulesLoading">
            <div class="ruleRow">
              <div class="ruleMain">
                <div class="ruleTitle">完成后需通过验收清单</div>
                <div class="ruleDesc">任务标记为「已完成」前，必须完成验收清单中的全部子项。</div>
              </div>
              <n-switch
                :value="requireChecklistDoneForDone"
                :disabled="myRole !== 'OWNER' || taskRulesSaving || isArchived"
                @update:value="(v) => saveTaskRules(!!v)"
              />
            </div>
          </n-spin>
        </n-card>
      </n-modal>

      <n-modal v-model:show="devopsOpen" :mask-closable="true">
        <n-card style="width: 980px; max-width: calc(100vw - 32px)" title="PR 与 CI" :bordered="false">
          <div class="devops-actions">
            <n-button size="small" :disabled="isArchived" @click="openConfig">{{ giteeConfig?.hasToken ? '编辑配置' : '连接仓库' }}</n-button>
            <button class="btnPrimarySm" type="button" :disabled="loadingPanel || !giteeConfig?.hasToken || !giteeConfig?.owner || !giteeConfig?.repo" @click="refreshPanel">
              <span>刷新</span>
              <span v-if="loadingPanel" class="spin" />
            </button>
          </div>

          <div v-if="!giteeConfig?.hasToken" class="empty-summary">尚未连接 Gitee 仓库</div>

          <n-spin :show="loadingPanel">
            <div v-if="giteeConfig?.hasToken && !panel" class="empty-summary" />

            <div v-else-if="panel" class="devops-list">
              <div v-for="t in panel.tasks" :key="t.taskId" class="devops-row">
                <div class="devops-main">
                  <div class="devops-title">
                    <div class="ttitle">{{ t.title }}</div>
                    <span :class="pillClass(t.status)">{{ statusLabel(t.status) }}</span>
                  </div>
                  <div v-if="!t.prs.length" class="devops-empty">暂无关联 PR</div>
                  <div v-else class="pr-list">
                    <div v-for="pr in t.prs" :key="pr.number" class="pr-item">
                      <a class="pr-link" :href="pr.url || undefined" target="_blank" rel="noreferrer">
                        <span class="pr-no">#{{ pr.number }}</span>
                        <span class="pr-title">{{ pr.title }}</span>
                      </a>
                      <span :class="prStateClass(pr.state)">{{ prStateLabel(pr.state) }}</span>
                      <span :class="ciStateClass(pr.ciState)">{{ ciStateLabel(pr.ciState) }}</span>
                      <span class="muted pr-src">{{ pr.source }}</span>
                      <n-button v-if="pr.linkId" text size="tiny" :disabled="isArchived" @click="unlink(pr.linkId)">解绑</n-button>
                    </div>
                  </div>
                </div>
                <div class="devops-side">
                  <n-button size="tiny" :disabled="isArchived" @click="openLink(t.taskId)">关联 PR</n-button>
                </div>
              </div>
            </div>
          </n-spin>
        </n-card>
      </n-modal>

      <n-modal v-model:show="configOpen" :mask-closable="false">
        <n-card style="width: 520px" title="连接 Gitee 仓库" :bordered="false">
          <div class="form">
            <div class="row">
              <div class="muted label">仓库属主</div>
              <n-input v-model:value="cfgOwner" placeholder="例如：oschina" />
            </div>
            <div class="row">
              <div class="muted label">仓库名</div>
              <n-input v-model:value="cfgRepo" placeholder="例如：devtool-copilot" />
            </div>
            <div class="row">
              <div class="muted label">访问令牌</div>
              <n-input v-model:value="cfgToken" type="password" show-password-on="click" placeholder="个人访问令牌（Personal Access Token）" />
            </div>
          </div>
          <template #footer>
            <div class="modal-actions">
              <n-button @click="configOpen = false">取消</n-button>
              <button class="btnPrimarySm" type="button" :disabled="savingCfg || isArchived" @click="saveConfig">
                <span>保存</span>
                <span v-if="savingCfg" class="spin" />
              </button>
            </div>
          </template>
        </n-card>
      </n-modal>

      <n-modal v-model:show="linkOpen" :mask-closable="false">
        <n-card style="width: 520px" title="关联 PR 到任务" :bordered="false">
          <div class="form">
            <div class="row">
              <div class="muted label">PR</div>
              <n-input v-model:value="linkPr" placeholder="PR 链接或编号，例如 https://gitee.com/owner/repo/pulls/12 或 12" />
            </div>
          </div>
          <template #footer>
            <div class="modal-actions">
              <n-button @click="linkOpen = false">取消</n-button>
              <button class="btnPrimarySm" type="button" :disabled="linking || isArchived" @click="submitLink">
                <span>关联</span>
                <span v-if="linking" class="spin" />
              </button>
            </div>
          </template>
        </n-card>
      </n-modal>
    </template>
  </div>
</template>

<style scoped>
.empty {
  padding: 18px;
}

/* ── 头部 ─────────────────────────────────────── */
.top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 22px 0 20px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--line);
  flex-wrap: wrap;
}
.topMain {
  min-width: 0;
}
.titleRow {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.proTitle {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.3px;
  color: var(--ink);
  margin: 0;
}
.archTag {
  font-size: 11px;
  color: var(--ink-3);
  border: 1px solid var(--line);
  border-radius: 4px;
  padding: 2px 8px;
  letter-spacing: 0.2px;
  flex-shrink: 0;
}
.proDesc {
  margin-top: 6px;
  font-size: 13px;
  color: var(--ink-3);
  line-height: 1.55;
  max-width: 720px;
}
.topActions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

/* ── 按钮 ─────────────────────────────────────── */
.btnPrimarySm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  padding: 0 14px;
  border-radius: var(--radius-sm);
  background: #1f2329;
  color: #fff;
  border: none;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s ease;
}
.btnPrimarySm:hover {
  background: #111827;
}
.btnPrimarySm:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.btnGhost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  padding: 0 12px;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--ink-2);
  border: 1px solid var(--line);
  font-size: 13px;
  font-weight: 400;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s ease;
}
.btnGhost:hover {
  background: var(--surface-2);
  border-color: var(--line-strong);
  color: var(--ink);
}
.btnGhost:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.btnPulse {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  padding: 0 12px;
  border-radius: var(--radius-sm);
  /* 不依赖 var(--primary) 的 mix，避免浅主题下褪色透明；用明确的深色 */
  background: #1f2329;
  color: #ffffff;
  border: 1px solid #1f2329;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.18);
}
.btnPulse:hover {
  background: #000000;
  border-color: #000000;
}

/* ── 主-侧栏布局 ─────────────────────────────── */
.grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 20px;
  align-items: start;
}
.mainCol {
  min-width: 0;
}
.mainCol :deep(.taskBoard) {
  padding: 0;
  border: none;
  background: transparent;
  box-shadow: none;
}
.sideCol {
  display: grid;
  gap: 16px;
  position: sticky;
  top: 16px;
}

.sideBlock {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 16px 16px 14px;
}
.sideHead {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.sideTitle {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
  letter-spacing: 0.1px;
}
.sideMore {
  font-size: 12px;
  color: var(--ink-4);
  background: none;
  border: none;
  cursor: pointer;
  padding: 2px 0;
  transition: color 0.15s ease;
}
.sideMore:hover {
  color: var(--brand);
}
.sideMore:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.sideEmpty {
  font-size: 12px;
  color: var(--ink-4);
  padding: 6px 0 8px;
}

/* ── 协作概览 ───────────────────────────────── */
.collabStats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}
.csItem {
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 10px 4px 8px;
  text-align: center;
}
.csItem.risk .csVal {
  color: var(--danger-ink);
}
.csVal {
  font-size: 17px;
  font-weight: 600;
  color: var(--ink);
  line-height: 1.1;
  letter-spacing: -0.3px;
}
.csLabel {
  margin-top: 4px;
  font-size: 11px;
  color: var(--ink-4);
}
.memberRail {
  display: flex;
  gap: 6px;
  margin-top: 12px;
  align-items: center;
}
.memberDot {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--surface-3);
  border: 1px solid var(--line);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 500;
  color: var(--ink-2);
}
.memberEmpty {
  font-size: 12px;
  color: var(--ink-4);
}
.collabLine {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--line);
  font-size: 12px;
  color: var(--ink-3);
}

/* ── 里程碑 / 发布列表 ──────────────────────── */
.msList,
.relList {
  display: grid;
}
.msItem,
.relItem {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
}
.msItem:last-child,
.relItem:last-child {
  border-bottom: none;
}
.msMain,
.relMain {
  min-width: 0;
  flex: 1;
}
.msNameRow,
.relVersionRow {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.msName,
.relVersion {
  font-size: 13px;
  font-weight: 500;
  color: var(--ink);
}
.msMeta,
.relMeta {
  margin-top: 3px;
  font-size: 11px;
  color: var(--ink-4);
  line-height: 1.4;
}
.msOps {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
  align-items: center;
}
.msBtn {
  height: 24px;
  padding: 0 10px;
  font-size: 12px;
  border-radius: 5px;
  border: 1px solid var(--brand);
  color: var(--brand);
  background: transparent;
  cursor: pointer;
  transition: all 0.15s ease;
}
.msBtn:hover {
  background: var(--brand-soft);
}
.msBtn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.msBtnGhost {
  height: 24px;
  padding: 0 6px;
  font-size: 12px;
  border: none;
  color: var(--ink-4);
  background: none;
  cursor: pointer;
  transition: color 0.15s ease;
}
.msBtnGhost:hover {
  color: var(--ink);
}
.msBtnGhost:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.relItem {
  background: none;
  width: 100%;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease;
  border: none;
  outline: none;
}
.relItem:hover {
  background: var(--surface-2);
}
.relItem:hover .relVersion {
  color: var(--brand);
}
.relMs {
  font-size: 11px;
  color: var(--ink-4);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  flex-shrink: 0;
  align-self: center;
}

/* ── 状态徽章（灰度，仅已发布/进行中用深墨蓝） ─ */
.msBadge {
  font-size: 10px;
  padding: 1px 7px;
  border-radius: 4px;
  letter-spacing: 0.4px;
  border: 1px solid var(--line);
  color: var(--ink-3);
  background: var(--surface-2);
  white-space: nowrap;
  flex-shrink: 0;
}
.msBadge.open {
  color: var(--ink-2);
  border-color: var(--line-strong);
  background: var(--surface-3);
}
.msBadge.published {
  color: var(--brand);
  border-color: var(--brand-soft-2);
  background: var(--brand-soft);
}
.msBadge.archived {
  color: var(--ink-4);
}

.pill {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 4px;
  border: 1px solid var(--line);
  color: var(--ink-3);
  white-space: nowrap;
}
.pill.todo {
  background: var(--surface-2);
}
.pill.doing {
  color: var(--brand);
  border-color: var(--brand-soft-2);
  background: var(--brand-soft);
}
.pill.done {
  color: var(--ink-2);
  background: var(--surface-3);
  border-color: var(--line-strong);
}

.badge {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 4px;
  border: 1px solid var(--line);
  color: var(--ink-3);
  white-space: nowrap;
  background: var(--surface-2);
}
.badge.pr.open {
  color: var(--ink-2);
  border-color: var(--line-strong);
}
.badge.pr.merged {
  color: var(--ink-2);
  background: var(--surface-3);
  border-color: var(--line-strong);
}
.badge.pr.closed {
  color: var(--ink-4);
}
.badge.ci.success {
  color: var(--ink-2);
  border-color: var(--line-strong);
  background: var(--surface-3);
}
.badge.ci.failed {
  color: var(--danger-ink);
  border-color: rgba(185, 28, 28, 0.3);
  background: rgba(185, 28, 28, 0.05);
}
.badge.ci.running {
  color: var(--brand);
  border-color: var(--brand-soft-2);
  background: var(--brand-soft);
}
.badge.ci.unknown {
  color: var(--ink-4);
}

/* ── 弹窗表单 / Devops ──────────────────────── */
.form {
  display: grid;
  gap: 14px;
}
.row {
  display: grid;
  gap: 6px;
}
.label {
  font-size: 12px;
  color: var(--ink-3);
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  align-items: center;
}
.devops-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
}
.devops-list {
  display: grid;
  gap: 10px;
}
.devops-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  align-items: start;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  padding: 12px;
}
.devops-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.devops-empty {
  margin-top: 8px;
  font-size: 12px;
  color: var(--ink-4);
}
.pr-list {
  margin-top: 10px;
  display: grid;
  gap: 8px;
}
.pr-item {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.pr-link {
  display: inline-flex;
  gap: 8px;
  align-items: baseline;
  max-width: 560px;
  color: var(--ink-2);
}
.pr-link:hover {
  color: var(--brand);
}
.pr-no {
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 4px;
  background: var(--surface-3);
  border: 1px solid var(--line);
  color: var(--ink-2);
  white-space: nowrap;
}
.pr-title {
  font-size: 12px;
  color: var(--ink-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 480px;
}
.pr-src {
  font-size: 12px;
  color: var(--ink-4);
}
.devops-side {
  display: flex;
  justify-content: flex-end;
}
.empty-summary {
  font-size: 12px;
  color: var(--ink-4);
  padding: 8px 0;
}

.ruleRow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
}
.ruleMain {
  min-width: 0;
}
.ruleTitle {
  font-size: 13px;
  font-weight: 500;
  color: var(--ink);
}
.ruleDesc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ink-3);
  line-height: 1.45;
}

.spin {
  display: inline-block;
  width: 12px;
  height: 12px;
  margin-left: 8px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  animation: pro-spin 0.7s linear infinite;
  vertical-align: -2px;
}
@keyframes pro-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1120px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .sideCol {
    position: static;
  }
}
</style>
