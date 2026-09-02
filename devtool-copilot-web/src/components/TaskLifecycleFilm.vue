<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { taskApi, type Task, type TaskTimelineItem } from '../api/task'
import { aiApi } from '../api/ai'
import MarkdownView from './MarkdownView.vue'
import AiToTaskButton from './AiToTaskButton.vue'

const props = withDefaults(
  defineProps<{
    task: Task | null
    timeline: TaskTimelineItem[]
    projectId?: number
    compact?: boolean
    mini?: boolean
  }>(),
  { projectId: 0, compact: false, mini: false }
)

const router = useRouter()

const projectIdSafe = computed(() => {
  const pid = Number(props.projectId || 0)
  if (pid > 0) return pid
  const fallback = Number((props.task as any)?.projectId || 0)
  return Number.isFinite(fallback) && fallback > 0 ? fallback : 0
})

function statusLabel(st?: string) {
  const v = String(st || '').toUpperCase()
  if (v === 'DOING') return '进行中'
  if (v === 'DONE') return '已完成'
  return '待办'
}

function timelineTypeLabel(t?: string) {
  const map: Record<string, string> = {
    CREATED: '创建任务',
    STATUS_CHANGED: '状态变更',
    UPDATED: '更新任务',
    NOTE: '备注',
    COMMENT: '评论',
    PR_LINKED: '关联 PR',
    PR_UNLINKED: '取消关联 PR'
  }
  return (t && map[t.toUpperCase()]) || t || ''
}

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

// ===================== 任务生命周期胶片 =====================
interface LcPhase {
  status: string
  start: number
  end: number
  days: number
  color: string
  daysText: string
  flag?: 'warn' | 'danger'
}

interface LcEvent {
  id: number
  time: number
  type: string
  title: string
  detail?: string
  kind: 'change' | 'comment' | 'note' | 'pr'
}

interface LcBase {
  count: number
  p25: number
  p50: number
  p75: number
  p85: number
  raw: number[]
  loading: boolean
}

const lcPhaseColors: Record<string, string> = {
  TODO: '#94a3b8',
  DOING: '#f59e0b',
  DONE: '#10b981'
}

const lcHoverPhase = ref<LcPhase | null>(null)
const lcHoverEvent = ref<LcEvent | null>(null)

function lcDaysText(days: number): string {
  if (days < 1) return `${Math.max(Math.round(days * 24), 1)} 小时`
  return `${Math.round(days)} 天`
}

function lcQuantile(sorted: number[], p: number): number {
  if (!sorted.length) return 0
  const idx = (sorted.length - 1) * p
  const lo = Math.floor(idx)
  const hi = Math.ceil(idx)
  if (lo === hi) return sorted[lo]
  return sorted[lo] + (sorted[hi] - sorted[lo]) * (idx - lo)
}

function lcMakePhase(status: string, start: number, end: number): LcPhase {
  const days = Math.max((end - start) / 86400000, 0)
  return {
    status,
    start,
    end,
    days,
    color: lcPhaseColors[status] || '#94a3b8',
    daysText: lcDaysText(days)
  }
}

// 项目基线分位（P25/P50/P75/P85），供停滞检测 / 分位定位 / 完成预测复用
const lcBase = ref<LcBase>({ count: 0, p25: 0, p50: 0, p75: 0, p85: 0, raw: [], loading: false })

async function loadProjectBase() {
  if (!projectIdSafe.value || lcBase.value.count || lcBase.value.loading) return
  lcBase.value.loading = true
  try {
    const res: any = await taskApi.search({ projectId: projectIdSafe.value, pageSize: 300 })
    const list: Task[] = res?.list || res?.items || []
    const now = Date.now()
    const durations: number[] = []
    for (const t of list) {
      const start = t.createdAt || (t.createTime ? Date.parse(t.createTime) : 0)
      if (!start) continue
      const doneRaw = (t as any).doneTime
      const done = doneRaw ? Date.parse(doneRaw) : now
      const d = done - start
      if (d > 0) durations.push(d)
    }
    if (durations.length) {
      const sorted = durations.slice().sort((a, b) => a - b)
      const day = (v: number) => v / 86400000
      lcBase.value = {
        count: sorted.length,
        p25: day(lcQuantile(sorted, 0.25)),
        p50: day(lcQuantile(sorted, 0.5)),
        p75: day(lcQuantile(sorted, 0.75)),
        p85: day(lcQuantile(sorted, 0.85)),
        raw: sorted,
        loading: false
      }
    } else {
      lcBase.value.loading = false
    }
  } catch {
    lcBase.value.loading = false
  }
}

// 停滞段标记：与项目 P85 基线对比
function flagPhases(phases: LcPhase[]): LcPhase[] {
  const b = lcBase.value
  if (!b.count || !phases.length) return phases
  const warn = Math.max(b.p85, 3)
  const danger = Math.max(b.p85 * 2, 7)
  for (const p of phases) {
    if (p.status === 'DONE' && p.end - p.start <= 86400000) continue
    if (p.days > danger) p.flag = 'danger'
    else if (p.days > warn) p.flag = 'warn'
  }
  return phases
}

const lifecyclePhases = computed<LcPhase[]>(() => {
  const evs = props.timeline
    .filter((e) => e.createdAt)
    .slice()
    .sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0))
  if (!evs.length) return []
  const phases: LcPhase[] = []
  let cur: string | null = null
  let segStart = 0
  for (const e of evs) {
    const t = e.createdAt || 0
    if (e.type === 'CREATED') {
      cur = 'TODO'
      segStart = t
      continue
    }
    if (e.type === 'STATUS_CHANGED' && cur) {
      if (t > segStart) phases.push(lcMakePhase(cur, segStart, t))
      const m = String(e.detail || '').match(/→\s*([A-Za-z]+)/)
      cur = m ? m[1].toUpperCase() : cur === 'TODO' ? 'DOING' : 'DONE'
      segStart = t
    }
  }
  let endTime = Date.now()
  if (props.task?.status === 'DONE') {
    const lastChange = [...evs].reverse().find((e) => e.type === 'STATUS_CHANGED')
    if (lastChange?.createdAt) endTime = lastChange.createdAt + 60_000
  }
  if (cur && endTime > segStart) phases.push(lcMakePhase(cur, segStart, endTime))
  return flagPhases(phases)
})

const lcRange = computed(() => {
  const phases = lifecyclePhases.value
  if (!phases.length) return { min: 0, span: 1 }
  let min = Infinity
  let max = -Infinity
  for (const p of phases) {
    min = Math.min(min, p.start)
    max = Math.max(max, p.end)
  }
  if (props.task?.status !== 'DONE') max = Math.max(max, Date.now())
  const pad = (max - min) * 0.05
  return { min: min - pad, span: Math.max(max - min + pad * 2, 60_000) }
})

function lcX(ts: number): number {
  const { min, span } = lcRange.value
  return Math.min(Math.max(((ts - min) / span) * 100, 0), 100)
}

const lcShowToday = computed(() => props.task?.status !== 'DONE')

const lcEvents = computed<LcEvent[]>(() =>
  props.timeline
    .filter((e) => e.createdAt && e.type !== 'CREATED')
    .map((e) => {
      let kind: LcEvent['kind'] = 'change'
      if (e.type === 'COMMENT') kind = 'comment'
      else if (e.type === 'NOTE') kind = 'note'
      else if (e.type === 'PR_LINKED' || e.type === 'PR_UNLINKED') kind = 'pr'
      return {
        id: e.id,
        time: e.createdAt || 0,
        type: e.type,
        title: e.title || timelineTypeLabel(e.type),
        detail: e.detail,
        kind
      }
    })
    .sort((a, b) => a.time - b.time)
)

const lcMetrics = computed(() => {
  const phases = lifecyclePhases.value
  if (!phases.length) return null
  const total = phases[phases.length - 1].end - phases[0].start
  const days = total / 86400000
  return {
    totalDays: days,
    totalText: lcDaysText(days),
    switchCount: Math.max(phases.length - 1, 0),
    phaseCount: phases.length
  }
})

// 本任务在项目分布中的百分位
const lcPercentile = computed(() => {
  const phases = lifecyclePhases.value
  const b = lcBase.value
  if (!phases.length || !b.raw.length) return null
  const totalMs = phases[phases.length - 1].end - phases[0].start
  let less = 0
  for (const d of b.raw) if (d <= totalMs) less++
  return Math.round((less / b.raw.length) * 100)
})

// 流动效率：执行时间 / 总周期
const lcFlow = computed(() => {
  const phases = lifecyclePhases.value
  if (!phases.length) return null
  let executing = 0
  let total = 0
  for (const p of phases) {
    total += p.days
    if (p.status === 'DOING') executing += p.days
  }
  if (total <= 0) return { execText: '0 小时', waitText: '0 小时', eff: 0 }
  return {
    execText: lcDaysText(executing),
    waitText: lcDaysText(Math.max(0, total - executing)),
    eff: Math.round((executing / total) * 100)
  }
})

// 节奏评分：基础 100，按无效切换 / 返工 / 停滞 / 低流动 / 分位加成
const lcScore = computed(() => {
  const phases = lifecyclePhases.value
  if (!phases.length) return null
  const switches = Math.max(phases.length - 1, 0)
  const waste = Math.max(0, switches - 2)
  const revertCount = props.timeline.filter(
    (e) => e.type === 'STATUS_CHANGED' && /DONE\s*->\s*(TODO|DOING)/.test(String(e.detail || ''))
  ).length
  let score = 100
  score -= waste * 12
  score -= revertCount * 10
  const maxStuck = Math.max(...phases.map((p) => p.days), 0)
  if (maxStuck > 3) score -= Math.min(25, (maxStuck - 3) * 3)
  const eff = lcFlow.value?.eff
  if (eff !== undefined && eff < 40) score -= 10
  const pct = lcPercentile.value
  if (pct !== null && pct < 50) score += 5
  score = Math.max(0, Math.min(100, Math.round(score)))
  const grade = score >= 85 ? 'S' : score >= 70 ? 'A' : score >= 55 ? 'B' : score >= 40 ? 'C' : 'D'
  const color = score >= 85 ? '#10b981' : score >= 70 ? '#3b82f6' : score >= 55 ? '#f59e0b' : score >= 40 ? '#f97316' : '#ef4444'
  return { score, grade, color, waste, revertCount, switches }
})

const lcScoreRing = computed(() => {
  const s = lcScore.value?.score ?? 0
  const C = 2 * Math.PI * 15.5
  return `${(s / 100) * C} ${C}`
})

// 完成预测：按项目 P50 基线推算剩余时长
const lcPredict = computed(() => {
  const t = props.task
  const phases = lifecyclePhases.value
  if (!t || t.status === 'DONE' || !phases.length) return null
  const created = phases[0].start
  const spentDays = Math.max(0, (Date.now() - created) / 86400000)
  const spentText = lcDaysText(spentDays)
  const b = lcBase.value
  if (!b.count) return { spentText, canPredict: false, overdue: false }
  const remaining = Math.max(0, b.p50 - spentDays)
  const target = new Date(Date.now() + remaining * 86400000)
  return {
    spentText,
    remainingText: lcDaysText(remaining || 0.1),
    targetText: fmt(target.getTime()),
    overdue: spentDays > b.p85,
    canPredict: true
  }
})

// 圆环进度：已用 / 项目 P50
const lcPredictDash = computed(() => {
  const t = props.task
  const phases = lifecyclePhases.value
  if (!t || t.status === 'DONE' || !phases.length) return { dash: '0 100', show: false }
  const b = lcBase.value
  if (!b.count || !b.p50) return { dash: '0 100', show: false }
  const spent = Math.max(0, (Date.now() - phases[0].start) / 86400000)
  const ratio = Math.max(0, Math.min(spent / b.p50, 1))
  const C = 2 * Math.PI * 10
  return { dash: `${ratio * C} ${C}`, show: true }
})

onMounted(loadProjectBase)

// AI 节奏解读
const aiInsight = ref('')
const aiInsightLoading = ref(false)

async function generateInsight() {
  if (!projectIdSafe.value || aiInsightLoading.value) return
  aiInsightLoading.value = true
  aiInsight.value = ''
  try {
    const t = props.task
    const phases = lifecyclePhases.value
    const evCount = lcEvents.value.length
    const lines = [
      '请用中文分析下面这个任务的生命周期节奏，输出结构化解读。',
      '',
      '【排版规范（严格遵守）】',
      '- 严禁使用任何 # 井号标题',
      '- 严禁 *** 三星、* 单星，最多一层 ** 加粗',
      '- 小节用 emoji + 加粗：📊 **总评**、⚠️ **问题**、💡 **建议**',
      '- 关键数字加粗，例如评分 **75**、停滞 **88** 天',
      '',
      '【结构】',
      '1. 📊 **总评**：一句话总评，可引用节奏评分',
      '2. ⚠️ **问题**：最多 2 个，每条用数据佐证',
      '3. 💡 **建议**：2 条具体可执行的建议',
      '4. 若未完成，一句话判断完成预估是否合理',
      '全篇控制在 200 字以内。',
      '',
      `任务：#${t?.id ?? '-'} ${t?.title ?? ''}`,
      `优先级：${t?.priority || '未设置'} · 负责人：${t?.assignee || '未分配'} · 当前状态：${statusLabel(t?.status)}`,
      `截止时间：${t?.dueTime ? fmt(Date.parse(t.dueTime)) : '未设置'}`,
      `节奏评分：${lcScore.value?.score ?? '-'}（${lcScore.value?.grade ?? '-'}）· 状态切换 ${lcMetrics.value?.switchCount ?? 0} 次（无效 ${lcScore.value?.waste ?? 0} 次，返工 ${lcScore.value?.revertCount ?? 0} 次）`,
      `流动效率：${lcFlow.value?.eff ?? '-'}%（执行 ${lcFlow.value?.execText ?? '-'} / 等待 ${lcFlow.value?.waitText ?? '-'}）`,
      lcPercentile.value !== null
        ? `本任务耗时位于项目 ${lcPercentile.value}% 分位（项目 P50=${lcBase.value.p50.toFixed(1)}天，P85=${lcBase.value.p85.toFixed(1)}天）`
        : '暂无项目基线',
      lcPredict.value ? `完成预测：${lcPredict.value.overdue ? '已超 85% 同类任务' : `预计还需 ${lcPredict.value.remainingText}，目标 ${lcPredict.value.targetText}`}` : '',
      '生命周期状态分段（状态：停留时长，起止时间，标记）：',
      ...phases.map(
        (p) =>
          `- ${statusLabel(p.status)}：${p.daysText}（${fmt(p.start)} ~ ${fmt(p.end)}）${p.flag === 'danger' ? '【停滞】' : p.flag === 'warn' ? '【偏慢】' : ''}`
      ),
      `关键事件数：${evCount}`
    ]
    await aiApi.chatStream(
      { projectId: projectIdSafe.value, messages: [{ role: 'user', content: lines.join('\n') }], type: 'rhythm' },
      (delta) => {
        aiInsight.value += delta
      }
    )
  } catch (e: any) {
    aiInsight.value = `AI 解读失败：${e?.message || '未知错误'}`
  } finally {
    aiInsightLoading.value = false
  }
}

// compact 模式：跳转独立生命周期页
function openFilm() {
  if (!props.task) return
  router.push({ name: 'task-lifecycle', params: { taskId: props.task.id } })
}
</script>

<template>
  <div v-if="!compact && !mini" class="lcCard">
    <div class="block-head">
      <div class="h2">任务生命周期</div>
      <div class="lcHeadRight">
        <button class="btnGhost" type="button" :disabled="aiInsightLoading" @click="generateInsight">
          <span>{{ aiInsightLoading ? '解读中…' : 'AI 解读' }}</span>
          <span v-if="aiInsightLoading" class="ml-2 inline-block h-4 w-4 rounded-full border-2 border-white/30 border-t-white animate-spin" />
        </button>
        <div v-if="lcMetrics" class="muted meta">{{ lcMetrics.totalText }}</div>
      </div>
    </div>

    <template v-if="lifecyclePhases.length">
      <div class="lcHoverBar">
        <template v-if="lcHoverEvent">
          <span class="lcHoverTitle">{{ lcHoverEvent.title }}</span>
          <span v-if="lcHoverEvent.detail" class="muted lcHoverDetail">{{ lcHoverEvent.detail }}</span>
          <span class="muted lcHoverTime">{{ fmt(lcHoverEvent.time) }}</span>
        </template>
        <template v-else-if="lcHoverPhase">
          <span class="lcHoverTitle" :style="{ color: lcHoverPhase.color }">
            {{ statusLabel(lcHoverPhase.status) }} · {{ lcHoverPhase.daysText }}
            <span v-if="lcHoverPhase.flag === 'warn'" class="lcFlagTag warn">较慢</span>
            <span v-else-if="lcHoverPhase.flag === 'danger'" class="lcFlagTag danger">停滞</span>
          </span>
          <span class="muted lcHoverTime">{{ fmt(lcHoverPhase.start) }} ~ {{ fmt(lcHoverPhase.end) }}</span>
        </template>
      </div>

      <div class="lcTimeline">
        <div v-if="lcShowToday" class="lcToday" :style="{ left: lcX(Date.now()) + '%' }">
          <span class="lcTodayTag">今天</span>
        </div>
        <div
          v-for="(p, i) in lifecyclePhases"
          :key="i"
          class="lcPhase"
          :class="{ warn: p.flag === 'warn', danger: p.flag === 'danger' }"
          :style="{ left: lcX(p.start) + '%', width: Math.max(lcX(p.end) - lcX(p.start), 0.4) + '%', background: p.color }"
          @mouseenter="lcHoverPhase = p"
          @mouseleave="lcHoverPhase = null"
        >
          <span class="lcPhaseLabel">
            {{ statusLabel(p.status) }} {{ p.daysText }}
            <i v-if="p.flag" class="lcFlagMark" :class="p.flag">!</i>
          </span>
        </div>
        <div
          v-for="e in lcEvents"
          :key="'e' + e.id"
          class="lcEvent"
          :class="e.kind"
          :style="{ left: lcX(e.time) + '%' }"
          @mouseenter="lcHoverEvent = e"
          @mouseleave="lcHoverEvent = null"
        />
      </div>

      <div v-if="lcMetrics" class="lcMetrics">
        <div class="lcMetric lcMetricScore">
          <div class="lcRingWrap">
            <svg class="lcRing" viewBox="0 0 40 40">
              <circle cx="20" cy="20" r="15.5" fill="none" stroke="rgba(15,23,42,.08)" stroke-width="4" />
              <circle
                cx="20" cy="20" r="15.5" fill="none"
                :stroke="lcScore?.color" stroke-width="4" stroke-linecap="round"
                :stroke-dasharray="lcScoreRing" transform="rotate(-90 20 20)"
              />
            </svg>
            <span class="lcRingText" :style="{ color: lcScore?.color }">{{ lcScore?.score ?? '–' }}</span>
          </div>
          <div class="lcMetricLabel">节奏分{{ lcScore ? ' · ' + lcScore.grade : '' }}</div>
        </div>
        <div class="lcMetric">
          <div class="lcMetricValue">{{ lcMetrics.totalText }}</div>
          <div class="muted lcMetricLabel">
            总生命周期
            <span v-if="lcPercentile !== null" class="lcPctTag">{{ lcPercentile }}% 分位</span>
          </div>
        </div>
        <div class="lcMetric">
          <div class="lcMetricValue">
            {{ lcMetrics.switchCount }}
            <span v-if="lcScore && lcScore.waste > 0" class="lcWaste">+{{ lcScore.waste }} 无效</span>
          </div>
          <div class="muted lcMetricLabel">状态切换</div>
        </div>
        <div class="lcMetric">
          <div class="lcMetricValue">{{ lcFlow?.eff ?? '–' }}<span class="lcUnit">%</span></div>
          <div class="muted lcMetricLabel">流动效率 · 执行 {{ lcFlow?.execText }} / 等待 {{ lcFlow?.waitText }}</div>
        </div>
        <div class="lcMetric lcMetricAvg">
          <div class="lcMetricValue">{{ lcBase.count ? lcDaysText(lcBase.p50) : lcBase.loading ? '…' : '–' }}</div>
          <div class="muted lcMetricLabel">项目周期 P50 · {{ lcBase.count ? lcBase.count + ' 个任务' : lcBase.loading ? '计算中' : '暂无数据' }}</div>
        </div>
      </div>

      <div v-if="lcBase.count" class="lcPercentile">
        <div class="lcPctHead">
          <span class="lcPctTitle">本任务 vs 项目分布</span>
          <span v-if="lcPercentile !== null" class="small">
            {{ lcPercentile }}% 分位
            <span v-if="lcPercentile > 80" class="lcPctSlow">偏慢</span>
            <span v-else-if="lcPercentile < 40" class="lcPctFast">偏快</span>
          </span>
        </div>
        <div class="lcPctTrack">
          <span class="lcPctMark" :style="{ left: '10%' }">P25 {{ lcBase.p25.toFixed(1) }}d</span>
          <span class="lcPctMark" :style="{ left: '50%' }">P50 {{ lcBase.p50.toFixed(1) }}d</span>
          <span class="lcPctMark" :style="{ left: '80%' }">P85 {{ lcBase.p85.toFixed(1) }}d</span>
          <span
            class="lcPctDot"
            :style="{ left: Math.min(Math.max((lcPercentile ?? 50) / 100, 0), 1) * 100 + '%' }"
          ></span>
        </div>
      </div>

      <div v-if="lcPredict" class="lcPredict" :class="{ overdue: lcPredict.overdue }">
        <svg v-if="lcPredictDash.show && !lcPredict.overdue" class="lcPredictIcon" viewBox="0 0 26 26" aria-hidden="true">
          <circle cx="13" cy="13" r="10" fill="none" stroke="rgba(15,23,42,.08)" stroke-width="3" />
          <circle
            cx="13"
            cy="13"
            r="10"
            fill="none"
            stroke="#1f2329"
            stroke-width="3"
            stroke-linecap="round"
            :stroke-dasharray="lcPredictDash.dash"
            transform="rotate(-90 13 13)"
          />
        </svg>
        <span v-else class="lcPredictIcon lcPredictIconMark">!</span>
        <div class="lcPredictText">
          <span class="lcPredictTitle">{{ lcPredict.overdue ? '已超项目 85% 任务' : '预计完成' }}</span>
          <span class="muted small">
            已用 {{ lcPredict.spentText }}
            <template v-if="lcPredict.canPredict">
              · 需 {{ lcPredict.remainingText }} · 目标 {{ lcPredict.targetText }}
            </template>
            <template v-else>· 暂无项目基线</template>
          </span>
        </div>
      </div>

      <div v-if="aiInsight" class="lcInsight">
        <div class="lcInsightHead">
          <span class="lcInsightTag">AI</span>
          <span class="muted">节奏解读</span>
          <button class="lcInsightRegen" type="button" :disabled="aiInsightLoading" @click="generateInsight">重新生成</button>
          <AiToTaskButton :project-id="projectIdSafe" :content="aiInsight" />
        </div>
        <div class="lcInsightBody">
          <MarkdownView :content="aiInsight" />
        </div>
      </div>
    </template>
    <div v-else class="muted empty">暂无生命周期数据</div>
  </div>

  <div v-else-if="mini" class="lcMini" @click="openFilm" role="button" :title="'查看完整生命周期'">
    <span class="lcMiniName">生命周期</span>
    <span class="lcMiniTrack">
      <span
        v-if="lifecyclePhases.length"
        v-for="(p, i) in lifecyclePhases"
        :key="i"
        class="lcMiniSeg"
        :style="{ left: lcX(p.start) + '%', width: Math.max(lcX(p.end) - lcX(p.start), 0.4) + '%', background: p.color }"
        :title="statusLabel(p.status) + ' ' + p.daysText"
      />
    </span>
    <span v-if="lifecyclePhases.length" class="lcMiniNums">
      <span class="lcMiniNum">{{ lcMetrics?.totalText }}</span>
      <span class="lcMiniNum">切 {{ lcMetrics?.switchCount ?? 0 }}</span>
      <span class="lcMiniNum">{{ lcFlow?.eff ?? '–' }}%</span>
      <span v-if="lcScore" class="lcMiniScore" :style="{ color: lcScore.color }">{{ lcScore.grade }}</span>
    </span>
    <span v-else class="muted small">暂无数据</span>
    <span class="lcMiniGo">完整 →</span>
  </div>

  <div v-else class="lcCompact" @click="openFilm">
    <div class="lcCompactHead">
      <span class="h2">任务生命周期</span>
      <span v-if="lcMetrics" class="muted meta">{{ lcMetrics.totalText }}</span>
      <button class="btnGhost lcCompactBtn" type="button" @click.stop="openFilm">打开完整分析</button>
    </div>
    <template v-if="lifecyclePhases.length">
      <div class="lcCompactTrack">
        <span
          v-for="(p, i) in lifecyclePhases"
          :key="i"
          class="lcCompactSeg"
          :class="{ warn: p.flag === 'warn', danger: p.flag === 'danger' }"
          :style="{ left: lcX(p.start) + '%', width: Math.max(lcX(p.end) - lcX(p.start), 0.4) + '%', background: p.color }"
          :title="statusLabel(p.status) + ' ' + p.daysText"
        />
      </div>
      <div class="lcCompactMeta">
        <span class="muted small">切换 {{ lcMetrics?.switchCount ?? 0 }} 次 · 流动效率 {{ lcFlow?.eff ?? '–' }}%</span>
        <span v-if="lcScore" class="lcCompactScore" :style="{ color: lcScore.color }">{{ lcScore.score }} · {{ lcScore.grade }}</span>
      </div>
    </template>
    <div v-else class="muted small empty">暂无生命周期数据</div>
  </div>
</template>

<style scoped>
/* 完整模式卡片：原生 div，宽度完全可控，杜绝溢出 */
.lcCard {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  padding: 20px 24px;
  background: #ffffff;
  border: 1px solid #eef0f4;
  border-radius: 14px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  overflow: hidden;
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

.btnGhost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 9px 14px;
  border-radius: 10px;
  background: #ffffff;
  color: #1f2329;
  border: 1px solid #d1d5db;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: -0.2px;
  white-space: nowrap;
  cursor: pointer;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  transition: background 140ms ease, border-color 140ms ease, color 140ms ease, box-shadow 140ms ease, transform 140ms ease;
}

.btnGhost:hover {
  background: #f3f4f6;
  border-color: #9ca3af;
  color: #111827;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
}

.btnGhost:disabled {
  background: #f3f4f6;
  color: #9ca3af;
  border-color: #e5e7eb;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

/* 任务生命周期胶片 */
.lcHeadRight {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
}

.lcHoverBar {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 22px;
  font-size: 12.5px;
  margin-bottom: 10px;
}

.lcHoverTitle {
  font-weight: 700;
  color: rgba(15, 23, 42, 0.85);
}

.lcHoverDetail {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 40%;
}

.lcHoverTime {
  margin-left: auto;
  white-space: nowrap;
}

.lcTimeline {
  position: relative;
  height: 64px;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.035);
  border: 1px solid var(--line, #e5e7eb);
  overflow: hidden;
  min-width: 0;
  max-width: 100%;
}

.lcPhase {
  position: absolute;
  top: 8px;
  bottom: 8px;
  border-radius: 6px;
  opacity: 0.92;
  transition: opacity 0.15s ease, filter 0.15s ease;
  display: flex;
  align-items: center;
  padding-left: 8px;
  min-width: 4px;
  overflow: hidden;
  cursor: pointer;
}

.lcTimeline:hover .lcPhase {
  opacity: 0.55;
}

.lcPhase:hover {
  opacity: 1 !important;
  filter: saturate(1.15) brightness(1.02);
}

.lcPhaseLabel {
  font-size: 11.5px;
  font-weight: 700;
  color: #fff;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
  white-space: nowrap;
  pointer-events: none;
}

.lcToday {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 0;
  border-left: 2px dashed #ef4444;
  z-index: 2;
  pointer-events: none;
}

.lcTodayTag {
  position: absolute;
  top: 4px;
  left: 4px;
  font-size: 10px;
  font-weight: 700;
  color: #ef4444;
  background: rgba(255, 255, 255, 0.92);
  border-radius: 4px;
  padding: 1px 5px;
}

.lcEvent {
  position: absolute;
  top: 50%;
  width: 9px;
  height: 9px;
  border-radius: 999px;
  transform: translate(-50%, -50%);
  background: #fff;
  border: 2px solid #64748b;
  z-index: 3;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.lcEvent:hover {
  transform: translate(-50%, -50%) scale(1.5);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.25);
}

.lcEvent.comment {
  border-color: #6366f1;
}

.lcEvent.note {
  border-color: #0ea5e9;
}

.lcEvent.pr {
  border-color: #0f172a;
}

.lcEvent.change {
  border-color: #f59e0b;
}

.lcMetrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
  margin-top: 14px;
  min-width: 0;
  max-width: 100%;
  width: 100%;
  box-sizing: border-box;
}

.lcMetric {
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
  background: rgba(15, 23, 42, 0.03);
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 10px;
  padding: 10px 14px;
}

.lcMetricValue {
  font-size: 18px;
  font-weight: 800;
  color: rgba(15, 23, 42, 0.9);
  line-height: 1.2;
}

.lcMetricLabel {
  font-size: 12px;
  margin-top: 2px;
}

.lcMetricAvg .lcMetricValue {
  color: #f59e0b;
}

.lcInsight {
  margin-top: 14px;
  border: 1px solid rgba(99, 102, 241, 0.18);
  background: rgba(99, 102, 241, 0.05);
  border-radius: 12px;
  padding: 12px 14px;
}

.lcInsightHead {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.lcInsightTag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 8px;
  background: #0f172a;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}

.lcInsightRegen {
  margin-left: auto;
  font-size: 12px;
  color: rgba(15, 23, 42, 0.55);
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: #fff;
  border-radius: 8px;
  padding: 3px 10px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.lcInsightRegen:hover {
  color: rgba(15, 23, 42, 0.85);
  border-color: rgba(15, 23, 42, 0.3);
}

.lcInsightBody {
  font-size: 13px;
  line-height: 1.7;
  color: rgba(15, 23, 42, 0.85);
  white-space: pre-wrap;
}

/* 停滞段标记 */
.lcPhase.warn {
  box-shadow: inset 0 0 0 1.5px rgba(245, 158, 11, 0.55);
}

.lcPhase.danger::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: repeating-linear-gradient(45deg, rgba(255, 255, 255, 0.22) 0 5px, transparent 5px 10px);
  pointer-events: none;
}

.lcFlagMark {
  font-style: normal;
  font-size: 9px;
  font-weight: 800;
  padding: 0 4px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.85);
  color: #b45309;
  margin-left: 4px;
  vertical-align: 1px;
}

.lcFlagMark.danger {
  background: #ef4444;
  color: #fff;
}

.lcFlagTag {
  font-size: 10px;
  font-weight: 600;
  border-radius: 4px;
  padding: 0 5px;
  margin-left: 5px;
}

.lcFlagTag.warn {
  color: #b45309;
  background: rgba(245, 158, 11, 0.14);
}

.lcFlagTag.danger {
  color: #dc2626;
  background: rgba(239, 68, 68, 0.12);
}

/* 节奏评分卡 */
.lcMetricScore {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 10px 8px !important;
}

.lcRingWrap {
  position: relative;
  width: 44px;
  height: 44px;
}

.lcRing {
  width: 44px;
  height: 44px;
}

.lcRingText {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 800;
}

.lcPctTag {
  display: inline-block;
  font-size: 10px;
  font-weight: 600;
  color: #4f46e5;
  background: rgba(99, 102, 241, 0.1);
  border-radius: 4px;
  padding: 0 5px;
  margin-left: 4px;
}

.lcWaste {
  font-size: 10px;
  font-weight: 700;
  color: #dc2626;
  background: rgba(239, 68, 68, 0.1);
  border-radius: 4px;
  padding: 0 5px;
  margin-left: 4px;
  vertical-align: 2px;
}

.lcUnit {
  font-size: 10px;
  color: rgba(15, 23, 42, 0.4);
  margin-left: 1px;
}

/* 分位对比条 */
.lcPercentile {
  margin-top: 14px;
  background: rgba(15, 23, 42, 0.03);
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 10px;
  padding: 12px 14px;
}

.lcPctHead {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.lcPctTitle {
  font-size: 12.5px;
  font-weight: 600;
  color: rgba(15, 23, 42, 0.75);
}

.lcPctTrack {
  position: relative;
  height: 22px;
}

.lcPctTrack::before {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 9px;
  height: 4px;
  border-radius: 2px;
  background: linear-gradient(90deg, #10b981, #f59e0b, #ef4444);
  opacity: 0.75;
}

.lcPctMark {
  position: absolute;
  top: 0;
  transform: translateX(-50%);
  font-size: 10px;
  color: rgba(15, 23, 42, 0.5);
  white-space: nowrap;
}

.lcPctMark::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 17px;
  width: 1px;
  height: 5px;
  background: rgba(15, 23, 42, 0.25);
  transform: translateX(-50%);
}

.lcPctDot {
  position: absolute;
  top: 3px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #fff;
  border: 3px solid #0f172a;
  transform: translateX(-50%);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.25);
  z-index: 1;
}

.lcPctSlow {
  color: #dc2626;
  font-weight: 600;
}

.lcPctFast {
  color: #059669;
  font-weight: 600;
}

/* 完成预测 */
.lcPredict {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.76);
  border-radius: 10px;
  padding: 10px 14px;
}

.lcPredict.overdue {
  border-color: rgba(220, 38, 38, 0.22);
  background: rgba(220, 38, 38, 0.04);
}

.lcPredictIcon {
  flex: none;
  width: 26px;
  height: 26px;
  display: block;
}

.lcPredictIconMark {
  border-radius: 8px;
  background: rgba(220, 38, 38, 0.1);
  color: #dc2626;
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.lcPredictText {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.lcPredictTitle {
  font-size: 12.5px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.8);
}

/* mini 极简预览 */
.lcMini {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.75);
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.lcMini:hover {
  background: #fff;
  border-color: rgba(15, 23, 42, 0.16);
}

.lcMiniName {
  flex: none;
  font-size: 12.5px;
  font-weight: 600;
  color: rgba(15, 23, 42, 0.78);
}

.lcMiniTrack {
  flex: 1;
  position: relative;
  height: 8px;
  border-radius: 4px;
  background: rgba(15, 23, 42, 0.05);
  overflow: hidden;
}

.lcMiniSeg {
  position: absolute;
  top: 0;
  bottom: 0;
  border-radius: 4px;
}

.lcMiniNums {
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: rgba(15, 23, 42, 0.62);
  white-space: nowrap;
}

.lcMiniNum {
  font-weight: 600;
}

.lcMiniScore {
  font-weight: 700;
}

.lcMiniGo {
  flex: none;
  font-size: 12px;
  font-weight: 600;
  color: #1f2329;
}

/* compact 精简预览 */
.lcCompact {
  display: flex;
  flex-direction: column;
  gap: 10px;
  cursor: pointer;
}

.lcCompactHead {
  display: flex;
  align-items: center;
  gap: 10px;
}

.lcCompactBtn {
  margin-left: auto;
  padding: 6px 12px;
  border-radius: 8px;
  font-size: 12px;
}

.lcCompactTrack {
  position: relative;
  height: 12px;
  border-radius: 6px;
  background: rgba(15, 23, 42, 0.04);
  border: 1px solid rgba(15, 23, 42, 0.08);
  overflow: hidden;
}

.lcCompactSeg {
  position: absolute;
  top: 1px;
  bottom: 1px;
  border-radius: 4px;
  opacity: 0.92;
}

.lcCompactSeg.warn {
  box-shadow: inset 0 0 0 1px rgba(245, 158, 11, 0.5);
}

.lcCompactSeg.danger {
  box-shadow: inset 0 0 0 1px rgba(239, 68, 68, 0.55);
}

.lcCompactMeta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
}

.lcCompactScore {
  font-weight: 700;
}
</style>
