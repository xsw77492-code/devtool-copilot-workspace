<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NCard, NInput, NModal, NSpin, NSwitch, useDialog, useMessage } from 'naive-ui'
import { useProjectStore } from '../stores/project'
import { useTaskStore } from '../stores/task'
import { useRealtimeStore } from '../stores/realtime'
import { giteeApi, type GiteePanelDTO, type GiteeRepoConfigDTO } from '../api/gitee'
import { projectApi } from '../api/project'
import { projectTaskRuleApi, type ProjectTaskRuleDTO } from '../api/projectTaskRule'
import { projectCollabApi, type ProjectMemberRole } from '../api/projectCollab'
import { assetApi } from '../api/asset'
import { milestoneApi, type Milestone } from '../api/milestone'
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

const giteeConfig = ref<GiteeRepoConfigDTO | null>(null)
const panel = ref<GiteePanelDTO | null>(null)
const loadingPanel = ref(false)

const myRole = ref<ProjectMemberRole | null>(null)

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

const releaseOpen = ref(false)
const releaseLoading = ref(false)
const releaseMd = ref('')
const releaseTitle = ref('')

const workflowOpen = ref(false)
const devopsOpen = ref(false)

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
  await loadGiteeConfig()
  await loadTaskRules()
  await loadMilestones()
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
  await loadGiteeConfig()
  await loadTaskRules()
  await loadMilestones()
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
    }
    if (
      t === 'MILESTONE_CREATED' ||
      t === 'MILESTONE_PUBLISHED' ||
      t === 'MILESTONE_ARCHIVED' ||
      t === 'MILESTONE_UNARCHIVED'
    ) {
      await loadMilestones()
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

function msStateClass(status: string) {
  const s = String(status || '').toUpperCase()
  if (s === 'PUBLISHED') return 'msBadge published'
  if (s === 'ARCHIVED') return 'msBadge archived'
  return 'msBadge open'
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

function openCreateMs() {
  msName.value = ''
  msDesc.value = ''
  createMsOpen.value = true
}

async function createMs() {
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
          await openRelease(res.assetId, `Release Notes · ${m.name}`)
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
  cfgOwner.value = giteeConfig.value?.owner || ''
  cfgRepo.value = giteeConfig.value?.repo || ''
  cfgToken.value = ''
  configOpen.value = true
}

async function saveConfig() {
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
  linkTaskId.value = taskId
  linkPr.value = ''
  linkOpen.value = true
}

async function submitLink() {
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
      <div class="top">
        <div>
          <div class="flex items-center gap-2">
            <h1 class="h1">{{ project.name }}</h1>
            <span
              v-if="Number(project.archived || 0) === 1"
              class="text-[11px] px-2 py-0.5 rounded-full bg-slate-100 text-slate-500 tracking-wide"
            >
              ARCHIVED
            </span>
          </div>
          <div v-if="project.description" class="muted desc">{{ project.description }}</div>
        </div>
        <div class="top-actions">
          <presence-bar :project-id="projectId" />
          <n-button tertiary @click="router.push({ name: 'project-members', params: { id: project.id } })">Members</n-button>
          <n-button tertiary @click="router.push({ name: 'project-activity', params: { id: project.id } })">Activity</n-button>
          <n-button v-if="myRole === 'OWNER'" tertiary @click="router.push({ name: 'project-audit', params: { id: project.id } })">
            Audit
          </n-button>
          <button v-if="myRole === 'OWNER'" class="btnGhost" type="button" @click="toggleArchiveProject">
            {{ Number(project.archived || 0) === 1 ? '取消归档' : '归档' }}
          </button>
          <button v-if="myRole === 'OWNER'" class="btnGhost danger" type="button" @click="removeProject">删除</button>
          <n-button secondary class="accentBtn" @click="workflowOpen = true">Workflow</n-button>
          <n-button secondary class="accentBtn" @click="devopsOpen = true">PR & CI</n-button>
        </div>
      </div>

      <div class="grid">
        <task-board :project-id="projectId" @open-task="openTask" />

        <section class="panel block">
          <div class="block-head">
            <div class="h2">Milestones</div>
            <div class="flex items-center gap-2">
              <div class="muted meta">Release</div>
              <button class="btnGhost" type="button" @click="openCreateMs">新建</button>
            </div>
          </div>
          <n-spin :show="milestonesLoading">
            <div v-if="!milestones.length" class="empty-summary" />
            <div v-else class="space-y-2">
              <div v-for="m in milestones" :key="m.id" class="ms-row">
                <div class="min-w-0">
                  <div class="flex items-center gap-2 min-w-0">
                    <div class="ms-title truncate">{{ m.name }}</div>
                    <span :class="msStateClass(String(m.status || ''))">{{ String(m.status || '').toUpperCase() }}</span>
                  </div>
                  <div v-if="m.publishedTime" class="muted ms-meta">Published · {{ m.publishedTime }}</div>
                  <div v-else-if="m.dueTime" class="muted ms-meta">Due · {{ m.dueTime }}</div>
                </div>
                <div class="ms-actions">
                  <button v-if="String(m.status || '').toUpperCase() === 'OPEN'" class="btnPrimarySm" @click="publishMs(m)">发布</button>
                  <button
                    v-else-if="m.releaseAssetId"
                    class="btnGhost"
                    type="button"
                    @click="openRelease(Number(m.releaseAssetId), `Release Notes · ${m.name}`)"
                  >
                    发布说明
                  </button>
                </div>
              </div>
            </div>
          </n-spin>
        </section>
      </div>

      <n-modal v-model:show="createMsOpen" :mask-closable="false">
        <n-card style="width: 520px" title="New Milestone" :bordered="false">
          <div class="form">
            <div class="row">
              <div class="muted label">Name</div>
              <n-input v-model:value="msName" placeholder="例如：v1.0 交付" />
            </div>
            <div class="row">
              <div class="muted label">Description</div>
              <n-input v-model:value="msDesc" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" placeholder="可选：本次发布范围/目标" />
            </div>
          </div>
          <template #footer>
            <div class="modal-actions">
              <n-button @click="createMsOpen = false">Cancel</n-button>
              <button class="btnPrimarySm" :disabled="creatingMs" @click="createMs">
                <span>Create</span>
                <span v-if="creatingMs" class="ml-2 inline-block h-3.5 w-3.5 rounded-full border-2 border-white/30 border-t-white animate-spin" />
              </button>
            </div>
          </template>
        </n-card>
      </n-modal>

      <n-modal v-model:show="releaseOpen" :mask-closable="true">
        <n-card style="width: 920px; max-width: calc(100vw - 32px)" :title="releaseTitle || 'Release Notes'" :bordered="false">
          <n-spin :show="releaseLoading">
            <markdown-view v-if="releaseMd" :content="releaseMd" />
            <div v-else class="empty-summary" />
          </n-spin>
        </n-card>
      </n-modal>

      <n-modal v-model:show="workflowOpen" :mask-closable="true">
        <n-card style="width: 560px; max-width: calc(100vw - 32px)" title="Workflow" :bordered="false">
          <n-spin :show="taskRulesLoading">
            <div class="ruleRow">
              <div class="ruleMain">
                <div class="ruleTitle">DONE 需清单完成</div>
              </div>
              <n-switch
                :value="requireChecklistDoneForDone"
                :disabled="myRole !== 'OWNER' || taskRulesSaving"
                @update:value="(v) => saveTaskRules(!!v)"
              />
            </div>
          </n-spin>
        </n-card>
      </n-modal>

      <n-modal v-model:show="devopsOpen" :mask-closable="true">
        <n-card style="width: 980px; max-width: calc(100vw - 32px)" title="PR & CI" :bordered="false">
          <div class="devops-actions">
            <n-button size="small" @click="openConfig">{{ giteeConfig?.hasToken ? 'Edit' : 'Connect' }}</n-button>
            <button class="btnPrimarySm" :disabled="loadingPanel || !giteeConfig?.hasToken || !giteeConfig?.owner || !giteeConfig?.repo" @click="refreshPanel">
              <span>Refresh</span>
              <span v-if="loadingPanel" class="ml-2 inline-block h-3.5 w-3.5 rounded-full border-2 border-white/30 border-t-white animate-spin" />
            </button>
          </div>

          <div v-if="!giteeConfig?.hasToken" class="empty-summary" />

          <n-spin :show="loadingPanel">
            <div v-if="giteeConfig?.hasToken && !panel" class="empty-summary" />

            <div v-else-if="panel" class="devops-list">
              <div v-for="t in panel.tasks" :key="t.taskId" class="devops-row">
                <div class="devops-main">
                  <div class="devops-title">
                    <div class="ttitle">{{ t.title }}</div>
                    <span :class="pillClass(t.status)">{{ t.status }}</span>
                  </div>
                  <div v-if="!t.prs.length" class="devops-empty" />
                  <div v-else class="pr-list">
                    <div v-for="pr in t.prs" :key="pr.number" class="pr-item">
                      <a class="pr-link" :href="pr.url || undefined" target="_blank" rel="noreferrer">
                        <span class="pr-no">#{{ pr.number }}</span>
                        <span class="pr-title">{{ pr.title }}</span>
                      </a>
                      <span :class="prStateClass(pr.state)">{{ pr.state }}</span>
                      <span :class="ciStateClass(pr.ciState)">{{ pr.ciState || 'UNKNOWN' }}</span>
                      <span class="muted pr-src">{{ pr.source }}</span>
                      <n-button v-if="pr.linkId" text size="tiny" @click="unlink(pr.linkId)">Unlink</n-button>
                    </div>
                  </div>
                </div>
                <div class="devops-side">
                  <n-button size="tiny" @click="openLink(t.taskId)">Link PR</n-button>
                </div>
              </div>
            </div>
          </n-spin>
        </n-card>
      </n-modal>

      <n-modal v-model:show="configOpen" :mask-closable="false">
        <n-card style="width: 520px" title="Connect Gitee Repo" :bordered="false">
          <div class="form">
            <div class="row">
              <div class="muted label">Owner</div>
              <n-input v-model:value="cfgOwner" placeholder="e.g. oschina" />
            </div>
            <div class="row">
              <div class="muted label">Repo</div>
              <n-input v-model:value="cfgRepo" placeholder="e.g. devtool-copilot" />
            </div>
            <div class="row">
              <div class="muted label">Token</div>
              <n-input v-model:value="cfgToken" type="password" show-password-on="click" placeholder="Personal Access Token" />
            </div>
          </div>
          <template #footer>
            <div class="modal-actions">
              <n-button @click="configOpen = false">Cancel</n-button>
              <button class="btnPrimarySm" :disabled="savingCfg" @click="saveConfig">
                <span>Save</span>
                <span v-if="savingCfg" class="ml-2 inline-block h-3.5 w-3.5 rounded-full border-2 border-white/30 border-t-white animate-spin" />
              </button>
            </div>
          </template>
        </n-card>
      </n-modal>

      <n-modal v-model:show="linkOpen" :mask-closable="false">
        <n-card style="width: 520px" title="Link PR to Task" :bordered="false">
          <div class="form">
            <div class="row">
              <div class="muted label">PR</div>
              <n-input v-model:value="linkPr" placeholder="PR url or number, e.g. https://gitee.com/owner/repo/pulls/12 or 12" />
            </div>
          </div>
          <template #footer>
            <div class="modal-actions">
              <n-button @click="linkOpen = false">Cancel</n-button>
              <button class="btnPrimarySm" :disabled="linking" @click="submitLink">
                <span>Link</span>
                <span v-if="linking" class="ml-2 inline-block h-3.5 w-3.5 rounded-full border-2 border-white/30 border-t-white animate-spin" />
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
.top {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 18px;
}
.top-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}
.desc {
  line-height: 1.55;
  margin-top: 6px;
  max-width: 900px;
}
.grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
  align-items: start;
}
.block {
  padding: 14px 14px;
}
.devops {
  margin-top: 16px;
}
.ms-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 12px;
  border-radius: 14px;
  background: rgba(241, 245, 249, 0.65);
  box-shadow: 0 10px 28px rgba(2, 6, 23, 0.06);
}
.ms-title {
  font-weight: 600;
  letter-spacing: -0.2px;
}
.ms-meta {
  margin-top: 4px;
  font-size: 12px;
}
.ms-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.msBadge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  letter-spacing: 0.4px;
  background: rgba(226, 232, 240, 0.8);
  color: #475569;
}
.msBadge.open {
  background: rgba(20, 184, 166, 0.12);
  color: #0f766e;
}
.msBadge.published {
  background: rgba(14, 165, 233, 0.12);
  color: #0369a1;
}
.msBadge.archived {
  background: rgba(148, 163, 184, 0.18);
  color: #64748b;
}
.devops-head {
  margin-bottom: 10px;
}
.devops-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.ruleRow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.ruleMain {
  min-width: 0;
}

.ruleTitle {
  font-size: 13px;
  font-weight: 900;
  letter-spacing: -0.2px;
  color: rgba(250, 250, 250, 0.92);
}

.ruleDesc {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.45;
  max-width: 760px;
}

.ruleHint {
  margin-top: 10px;
  font-size: 12px;
}

.btnPrimary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 34px;
  padding: 0 12px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.92);
  color: rgba(255, 255, 255, 0.95);
  border: 1px solid rgba(2, 6, 23, 0.16);
  font-size: 13px;
  font-weight: 900;
  letter-spacing: -0.2px;
  transition: transform 140ms ease, box-shadow 140ms ease, background 140ms ease;
}

.btnPrimary:hover {
  transform: translateY(-1px);
  background: rgba(15, 23, 42, 0.98);
  box-shadow: 0 16px 44px rgba(2, 6, 23, 0.18);
}

.btnPrimary:disabled {
  opacity: 0.58;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.btnGhost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 34px;
  padding: 0 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.75);
  color: rgba(15, 23, 42, 0.88);
  border: 1px solid rgba(15, 23, 42, 0.12);
  font-size: 13px;
  font-weight: 900;
  letter-spacing: -0.2px;
  white-space: nowrap;
  transition: transform 140ms ease, box-shadow 140ms ease, background 140ms ease, border-color 140ms ease;
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

.btnPrimarySm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  padding: 0 12px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.92);
  color: rgba(255, 255, 255, 0.95);
  border: 1px solid rgba(2, 6, 23, 0.16);
  font-size: 12px;
  font-weight: 900;
  letter-spacing: -0.2px;
  transition: transform 140ms ease, box-shadow 140ms ease, background 140ms ease;
}

.btnPrimarySm:hover {
  transform: translateY(-1px);
  background: rgba(15, 23, 42, 0.98);
  box-shadow: 0 14px 38px rgba(2, 6, 23, 0.18);
}

.btnPrimarySm:disabled {
  opacity: 0.58;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
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
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.05);
  background: rgba(255, 255, 255, 0.02);
  padding: 12px 12px;
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
  max-width: 620px;
  text-decoration: none;
  color: rgba(250, 250, 250, 0.86);
}
.pr-link:hover {
  color: rgba(255, 255, 255, 0.95);
}
.pr-no {
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 999px;
  background: rgba(20, 184, 166, 0.14);
  border: 1px solid rgba(20, 184, 166, 0.24);
  color: rgba(255, 255, 255, 0.9);
  white-space: nowrap;
}
.pr-title {
  font-size: 12px;
  line-height: 1.5;
  color: rgba(255, 255, 255, 0.74);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 520px;
}
.badge {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.02);
  color: rgba(255, 255, 255, 0.72);
  white-space: nowrap;
}
.badge.pr.open {
  background: rgba(6, 182, 212, 0.12);
  border-color: rgba(6, 182, 212, 0.22);
  color: rgba(250, 250, 250, 0.9);
}
.badge.pr.merged {
  background: rgba(34, 197, 94, 0.12);
  border-color: rgba(34, 197, 94, 0.2);
  color: rgba(220, 252, 231, 0.95);
}
.badge.pr.closed {
  background: rgba(248, 113, 113, 0.14);
  border-color: rgba(248, 113, 113, 0.22);
  color: rgba(254, 226, 226, 0.95);
}
.badge.ci.success {
  background: rgba(34, 197, 94, 0.12);
  border-color: rgba(34, 197, 94, 0.2);
  color: rgba(220, 252, 231, 0.95);
}
.badge.ci.failed {
  background: rgba(248, 113, 113, 0.14);
  border-color: rgba(248, 113, 113, 0.22);
  color: rgba(254, 226, 226, 0.95);
}
.badge.ci.running {
  background: rgba(251, 191, 36, 0.14);
  border-color: rgba(251, 191, 36, 0.22);
  color: rgba(254, 243, 199, 0.95);
}
.badge.ci.unknown {
  background: rgba(255, 255, 255, 0.03);
}
.pr-src {
  font-size: 12px;
}
.devops-side {
  display: flex;
  justify-content: flex-end;
}
.form {
  display: grid;
  gap: 12px;
}
.row {
  display: grid;
  gap: 6px;
}
.label {
  font-size: 12px;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
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
.create {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 16px;
}
.group {
  margin-top: 14px;
}
.ghead {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 8px;
}
.rows {
  display: grid;
  gap: 8px;
}
.trow {
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.05);
  background: rgba(255, 255, 255, 0.02);
  padding: 10px 10px;
}
.tmain {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}
.ttitle {
  font-weight: 560;
  line-height: 1.45;
}
.tlink {
  cursor: pointer;
  transition: color 120ms ease;
}
.tlink:hover {
  color: var(--accent);
}
.tactions {
  display: flex;
  gap: 10px;
  margin-top: 8px;
  flex-wrap: wrap;
}
.pill {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.10);
  color: rgba(15, 23, 42, 0.72);
  white-space: nowrap;
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
  font-weight: 900;
}
.empty {
  font-size: 12px;
}
.empty-summary {
  line-height: 1.65;
}
@media (max-width: 1120px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
