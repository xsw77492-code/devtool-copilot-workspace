<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NButton, NCard, NCheckbox, NInput, NModal, NSelect, NSpin, useMessage } from 'naive-ui'
import { aiApi, type AiAgentPlanResponse, type AiAgentTask } from '../api/ai'
import { milestoneApi, type Milestone } from '../api/milestone'
import { taskApi, type Task } from '../api/task'

const props = defineProps<{ show: boolean; projectId: number; projectName?: string; archived?: boolean }>()
const emit = defineEmits<{ (e: 'update:show', value: boolean): void; (e: 'applied'): void }>()
const message = useMessage()

type Draft = AiAgentTask & { id: string; selected: boolean; priority: string; checklist: string[] }

const step = ref<'input' | 'plan' | 'done'>('input')
const requirement = ref('')
const goal = ref('')
const drafts = ref<Draft[]>([])
const milestoneId = ref<number | null>(null)
const parentTaskId = ref<number | null>(null)
const milestones = ref<Milestone[]>([])
const parents = ref<Task[]>([])
const planning = ref(false)
const applying = ref(false)
const result = ref<{ created: number; requested: number; failed: string[] }>({ created: 0, requested: 0, failed: [] })

const selectedCount = computed(() => drafts.value.filter((item) => item.selected).length)
const milestoneOptions = computed(() => [
  { label: '不关联里程碑', value: 0 },
  ...milestones.value
    .filter((item) => String(item.status || '').toUpperCase() !== 'ARCHIVED')
    .map((item) => ({ label: item.name, value: item.id }))
])
const parentOptions = computed(() => [
  { label: '作为顶层任务', value: 0 },
  ...parents.value.map((item) => ({ label: `#${item.id} ${item.title}`, value: item.id }))
])
const priorityOptions = [
  { label: '高优先级', value: 'HIGH' },
  { label: '中优先级', value: 'MEDIUM' },
  { label: '低优先级', value: 'LOW' }
]

const stepMeta = computed(() => [
  { label: '需求', state: step.value === 'input' ? 'active' : 'done' },
  { label: '计划', state: step.value === 'plan' ? 'active' : step.value === 'done' ? 'done' : 'idle' },
  { label: '完成', state: step.value === 'done' ? 'done' : 'idle' }
])

function close() {
  if (!planning.value && !applying.value) emit('update:show', false)
}

function reset() {
  step.value = 'input'
  requirement.value = ''
  goal.value = ''
  drafts.value = []
  milestoneId.value = null
  parentTaskId.value = null
  result.value = { created: 0, requested: 0, failed: [] }
}

async function loadMeta() {
  try {
    const [ms, list] = await Promise.all([milestoneApi.list(props.projectId, false), taskApi.listByProject(props.projectId)])
    milestones.value = ms || []
    parents.value = (list || []).filter((item) => !item.parentTaskId)
  } catch {
    milestones.value = []
    parents.value = []
  }
}

function normalizeTask(task: AiAgentTask, index: number): Draft {
  return {
    ...task,
    id: `${Date.now()}-${index}`,
    selected: true,
    title: String(task.title || '').trim(),
    description: String(task.description || '').trim(),
    priority: String(task.priority || 'MEDIUM').toUpperCase(),
    checklist: Array.isArray(task.checklist) ? task.checklist.filter(Boolean).slice(0, 12) : [],
    deliverables: Array.isArray(task.deliverables) ? task.deliverables : [],
    sources: Array.isArray(task.sources) ? task.sources : []
  }
}

async function generate() {
  const text = requirement.value.trim()
  if (!text) {
    message.warning('先描述这次要推进的需求')
    return
  }
  planning.value = true
  try {
    const plan = await aiApi.agentPlan({ requirement: text, projectId: props.projectId })
    goal.value = String(plan.goal || '')
    drafts.value = (plan.tasks || []).filter((item) => String(item?.title || '').trim()).map(normalizeTask)
    if (!drafts.value.length) {
      message.warning('AI 没有生成可执行任务，请补充需求')
      return
    }
    step.value = 'plan'
  } catch (error: any) {
    message.error(error?.message || '规划失败')
  } finally {
    planning.value = false
  }
}

async function apply() {
  const selected = drafts.value.filter((item) => item.selected && item.title.trim())
  if (!selected.length) {
    message.warning('至少选择一条任务')
    return
  }
  applying.value = true
  try {
    const plan: AiAgentPlanResponse = {
      goal: goal.value,
      tasks: selected.map(({ id: _id, selected: _selected, ...task }) => task)
    }
    const res = await aiApi.agentApply({
      projectId: props.projectId,
      plan,
      milestoneId: milestoneId.value || null,
      parentTaskId: parentTaskId.value || null
    })
    result.value = {
      created: Number(res.createdCount || res.taskIds?.length || 0),
      requested: Number(res.requestedCount || selected.length),
      failed: res.failedTitles || []
    }
    step.value = 'done'
    emit('applied')
  } catch (error: any) {
    message.error(error?.message || '落地失败')
  } finally {
    applying.value = false
  }
}

watch(
  () => props.show,
  async (visible) => {
    if (!visible) return
    reset()
    await loadMeta()
  }
)
</script>

<template>
  <n-modal
    :show="props.show"
    :mask-closable="false"
    :closable="false"
    class="project-planner-modal ai-anim"
    @update:show="(value) => !value && close()"
  >
    <n-card :bordered="false" size="large" class="project-planner-card">
      <template #header>
        <div class="planner-header">
          <div class="plTitle">AI 规划</div>
          <div class="plHeaderRight">
            <button class="plIconBtn plClose" type="button" aria-label="关闭" @click="close">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <path d="M6 6l12 12M18 6L6 18" />
              </svg>
            </button>
          </div>
        </div>
      </template>

      <template #default>
        <n-spin :show="planning || applying">
          <div class="plSteps">
            <div v-for="(s, i) in stepMeta" :key="i" class="plFlowStep" :class="s.state">
              <span class="plFlowDot">
                <svg v-if="s.state === 'done'" class="plFlowCheck" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M5 13l4 4L19 7" />
                </svg>
                <span v-else class="plFlowNum">{{ i + 1 }}</span>
              </span>
              <span class="plFlowLabel">{{ s.label }}</span>
              <span v-if="i < stepMeta.length - 1" class="plFlowLine" :class="{ lit: stepMeta[i + 1].state !== 'idle' }" />
            </div>
          </div>

          <div v-if="step === 'input'" class="planner-input-step ai-anim">
            <section class="plHero">
              <div class="plHeroHead">
                <div class="plHeroTitle">
                  <svg class="plHeroSpark" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 2l2.4 6.2L21 10.5l-6.6 2.3L12 19l-2.4-6.2L3 10.5l6.6-2.3L12 2z" />
                  </svg>
                  描述这次要推进的需求
                </div>
                <span class="plHeroTag">AI 拆解</span>
              </div>
              <n-input
                v-model:value="requirement"
                type="textarea"
                class="plHeroInput"
                placeholder="描述这次要推进的需求、目标或交付结果"
                :autosize="{ minRows: 6, maxRows: 10 }"
              />
              <div class="plHeroBar">
                <span class="plHeroHint">AI 会基于当前项目的任务和协作上下文生成计划</span>
                <div class="plHeroActions">
                  <n-button type="primary" :disabled="props.archived" :loading="planning" @click="generate">开始规划</n-button>
                </div>
              </div>
            </section>
          </div>

          <div v-else-if="step === 'plan'" class="planner-plan-step ai-anim">
            <div class="plan-toolbar">
              <div class="plan-heading">
                <span class="plan-goal">{{ goal || '本次任务计划' }}</span>
                <span class="plan-selected">
                  <b>{{ selectedCount }}</b>/{{ drafts.length }} 已选择
                </span>
              </div>
              <n-button quaternary size="small" @click="reset">重新规划</n-button>
            </div>

            <div class="draft-list">
              <div
                v-for="(draft, di) in drafts"
                :key="draft.id"
                class="draft-item ai-anim"
                :class="{ muted: !draft.selected }"
                :style="{ animationDelay: `${Math.min(di, 8) * 40}ms` }"
              >
                <span class="plDraftBar" :class="`p-${String(draft.priority || 'MEDIUM').toLowerCase()}`" />
                <div class="draft-select">
                  <n-checkbox v-model:checked="draft.selected" />
                </div>
                <div class="draft-content">
                  <n-input v-model:value="draft.title" size="small" placeholder="任务标题" />
                  <n-input v-model:value="draft.description" type="textarea" size="small" :autosize="{ minRows: 1, maxRows: 3 }" placeholder="任务说明" />
                  <div class="draft-meta">
                    <n-select v-model:value="draft.priority" size="small" :options="priorityOptions" class="draft-prio-sel" />
                    <span v-if="draft.checklist.length" class="checklist-pill">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M9 11l3 3L22 4" />
                        <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
                      </svg>
                      {{ draft.checklist.length }} 个验收点
                    </span>
                  </div>
                </div>
              </div>
            </div>

            <section class="plSection plLand">
              <div class="plSectionHead">
                <span class="plSectionTitle">落地位置</span>
                <span class="plSectionMeta">创建后会出现在当前项目的任务看板</span>
              </div>
              <div class="landing-fields">
                <n-select v-model:value="milestoneId" clearable size="small" :options="milestoneOptions" placeholder="里程碑" />
                <n-select v-model:value="parentTaskId" clearable filterable size="small" :options="parentOptions" placeholder="父任务" />
              </div>
            </section>

            <div class="planner-footer">
              <span class="planner-muted">已选择 {{ selectedCount }} 条任务</span>
              <n-button type="primary" class="plMainCta" :disabled="props.archived || !selectedCount" :loading="applying" @click="apply">
                创建 {{ selectedCount }} 个任务
              </n-button>
            </div>
          </div>

          <div v-else class="planner-done-step ai-anim">
            <div class="done-hero">
              <span class="done-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M5 13l4 4L19 7" />
                </svg>
              </span>
              <div class="done-count">已创建 {{ result.created }} 个任务</div>
              <div class="planner-muted">{{ result.requested }} 条任务已提交到当前项目</div>
              <div v-if="result.failed.length" class="failed-list">未创建：{{ result.failed.join('、') }}</div>
              <div class="done-actions">
                <n-button type="primary" @click="close">完成</n-button>
              </div>
            </div>
          </div>
        </n-spin>
      </template>
    </n-card>
  </n-modal>
</template>

<style scoped>
/* ── 弹窗外壳 ─────────────────────────────── */
:global(.project-planner-modal) {
  width: min(760px, calc(100vw - 32px));
}
:global(.project-planner-modal .n-card),
:global(.project-planner-modal.n-card) {
  border-radius: var(--ai-modal-radius);
  border: 1px solid rgba(17, 24, 39, 0.1);
  box-shadow: var(--ai-modal-shadow);
}
:global(.project-planner-modal .n-card-header) {
  padding: 18px 24px 16px;
  border-bottom: 1px solid var(--ai-line);
  background: var(--ai-surface);
}
:global(.project-planner-modal .n-card__content) {
  padding: 20px 24px 24px;
}

/* ── 头部 ─────────────────────────────────── */
.planner-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.plTitle {
  font-size: 17px;
  font-weight: 800;
  letter-spacing: -0.3px;
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
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  border: 1px solid var(--ai-line);
  background: var(--ai-surface);
  color: var(--ai-muted);
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
.plClose:hover {
  background: #fef2f2;
  border-color: rgba(239, 68, 68, 0.25);
  color: var(--ai-danger);
}
.plIconBtn:active {
  transform: translateY(1px);
}

/* ── 步骤条 ───────────────────────────────── */
.plSteps {
  display: flex;
  align-items: center;
  margin: 2px 0 18px;
}
.plFlowStep {
  display: flex;
  align-items: center;
  gap: 8px;
}
.plFlowDot {
  width: 26px;
  height: 26px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1.5px solid var(--ai-line-strong);
  color: var(--ai-faint);
  background: var(--ai-surface);
  font-size: 12px;
  font-weight: 700;
  transition: all 240ms var(--ai-ease);
  flex: 0 0 auto;
}
.plFlowDot .plFlowNum {
  line-height: 1;
}
.plFlowCheck {
  width: 13px;
  height: 13px;
}
.plFlowLabel {
  font-size: 12px;
  font-weight: 650;
  color: var(--ai-faint);
  transition: color 200ms ease;
  white-space: nowrap;
}
.plFlowStep.done .plFlowDot {
  background: var(--ai-grad);
  border-color: transparent;
  color: #fff;
  box-shadow: 0 4px 12px rgba(30, 64, 175, 0.35);
}
.plFlowStep.done .plFlowLabel {
  color: var(--ai-ink-2);
}
.plFlowStep.active .plFlowDot {
  border-color: var(--ai-grad-start);
  color: var(--ai-grad-start);
  box-shadow: 0 0 0 4px rgba(30, 64, 175, 0.12);
}
.plFlowStep.active .plFlowLabel {
  color: var(--ai-grad-start);
}
.plFlowLine {
  height: 1.5px;
  flex: 1;
  min-width: 28px;
  margin: 0 10px;
  background: var(--ai-line);
  border-radius: 2px;
  transition: background 300ms ease;
}
.plFlowLine.lit {
  background: var(--brand);
}

/* ── 步骤容器 ─────────────────────────────── */
.planner-input-step,
.planner-plan-step,
.planner-done-step {
  display: grid;
  gap: 14px;
}

/* ── 需求 Hero ────────────────────────────── */
.plHero {
  position: relative;
  border-radius: 16px;
  border: 1px solid var(--ai-line);
  background: var(--ai-surface);
  padding: 16px 16px 14px;
  transition: border-color 200ms ease, box-shadow 200ms ease;
}
.plHero:focus-within {
  border-color: rgba(30, 64, 175, 0.45);
  box-shadow: 0 0 0 4px rgba(30, 64, 175, 0.1);
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
.plHeroSpark {
  width: 15px;
  height: 15px;
  color: var(--ai-grad-start);
}
.plHeroTag {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.4px;
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--ai-grad-soft);
  color: var(--ai-grad-start);
  border: 1px solid rgba(30, 64, 175, 0.18);
}
.plHero :deep(.n-input) {
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
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--ai-line);
}
.plHeroHint {
  font-size: 11px;
  color: var(--ai-faint);
}
.plHeroActions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}
.plHeroActions :deep(.n-button) {
  height: 34px;
  padding: 0 18px;
  border-radius: 9px;
  font-size: 13px;
  font-weight: 650;
}

/* ── 计划工具条 ───────────────────────────── */
.plan-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.plan-heading {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}
.plan-goal {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ai-ink);
  font-weight: 750;
  letter-spacing: -0.15px;
  font-size: 14px;
}
.plan-selected {
  font-size: 11.5px;
  color: var(--ai-faint);
  white-space: nowrap;
}
.plan-selected b {
  color: var(--ai-grad-start);
  font-weight: 800;
}

/* ── 任务草稿卡片 ──────────────────────────── */
.draft-list {
  display: grid;
  gap: 10px;
  max-height: 400px;
  overflow: auto;
  padding-right: 4px;
}
.draft-item {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 14px 12px 0;
  border-radius: 14px;
  border: 1px solid var(--ai-line);
  background: var(--ai-surface);
  overflow: hidden;
  animation: ai-fade-up 320ms var(--ai-ease) both;
  transition: border-color 160ms ease, box-shadow 160ms ease, opacity 200ms ease;
}
.draft-item:hover {
  border-color: var(--ai-line-strong);
  box-shadow: 0 4px 16px rgba(17, 24, 39, 0.05);
}
.draft-item.muted {
  opacity: 0.55;
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
.draft-select {
  padding-top: 8px;
  flex: 0 0 auto;
}
.draft-content {
  flex: 1;
  display: grid;
  gap: 8px;
  min-width: 0;
}
.draft-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}
.draft-prio-sel {
  width: 130px;
}
.checklist-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 650;
  color: var(--ai-grad-start);
  padding: 3px 9px;
  border-radius: 999px;
  background: rgba(30, 64, 175, 0.08);
  white-space: nowrap;
}
.checklist-pill svg {
  width: 12px;
  height: 12px;
}

/* ── 落地位置 ─────────────────────────────── */
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
.plSectionMeta {
  font-size: 11px;
  color: var(--ai-faint);
}
.landing-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

/* ── 页脚 ─────────────────────────────────── */
.planner-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 4px;
}
.planner-muted {
  color: var(--ai-faint);
  font-size: 12px;
}
.plMainCta {
  min-width: 128px;
  font-weight: 700;
  background: var(--brand);
}
.plMainCta:hover {
  filter: brightness(1.06);
}

/* ── 完成态 ───────────────────────────────── */
.done-hero {
  text-align: center;
  padding: 24px 16px 8px;
  animation: ai-fade-up 360ms var(--ai-ease) both;
}
.done-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto 16px;
  border-radius: 999px;
  background: var(--ai-grad);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--ai-grad-glow);
  animation: ai-pop 480ms var(--ai-ease) both;
}
.done-icon svg {
  width: 30px;
  height: 30px;
}
.done-count {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.3px;
  color: var(--ai-ink);
}
.done-hero .planner-muted {
  margin-top: 6px;
}
.failed-list {
  margin-top: 14px;
  padding: 10px 12px;
  color: var(--danger-ink);
  background: rgba(185, 28, 28, 0.06);
  border: 1px solid rgba(185, 28, 28, 0.24);
  border-radius: 10px;
  font-size: 12.5px;
  text-align: left;
  display: inline-block;
}
.done-actions {
  margin-top: 22px;
}
.done-actions :deep(.n-button) {
  min-width: 120px;
  height: 34px;
  font-weight: 650;
  border-radius: 9px;
}

/* ── 控件统一 ─────────────────────────────── */
.planner-plan-step :deep(.n-input),
.planner-plan-step :deep(.n-base-selection) {
  --n-border: var(--ai-line) !important;
  --n-border-hover: var(--ai-line-strong) !important;
  --n-border-focus: rgba(30, 64, 175, 0.5) !important;
  --n-box-shadow-focus: 0 0 0 3px rgba(30, 64, 175, 0.1) !important;
  --n-color: var(--ai-surface) !important;
  --n-color-disabled: var(--ai-surface-2) !important;
  --n-color-active: var(--ai-surface) !important;
  --n-border-radius: 9px !important;
}
.planner-plan-step :deep(.n-button--primary-type) {
  --n-color: var(--brand) !important;
  --n-color-hover: var(--brand-hover) !important;
  --n-color-pressed: #172554 !important;
  --n-border: transparent !important;
  --n-text-color: #fff !important;
}

@media (max-width: 640px) {
  .landing-fields {
    grid-template-columns: 1fr;
  }
  .planner-footer {
    align-items: stretch;
    flex-direction: column;
  }
  .plHeroBar {
    flex-direction: column;
    align-items: stretch;
  }
  .plHeroActions {
    justify-content: flex-end;
  }
}
</style>
