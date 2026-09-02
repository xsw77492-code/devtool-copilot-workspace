<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NSpin, NModal } from 'naive-ui'
import { taskApi, type Task, type PulseEvent } from '../api/task'
import { aiApi } from '../api/ai'
import { useProjectStore } from '../stores/project'
import MarkdownView from '../components/MarkdownView.vue'
import AiToTaskButton from '../components/AiToTaskButton.vue'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()

const projectId = Number(route.params.id)
const DAY = 86_400_000

/* ---------- 状态 ---------- */
const loading = ref(true)
const tasks = ref<Task[]>([])
const events = ref<PulseEvent[]>([])
const errorMsg = ref('')

const aiBusy = ref(false)
const aiErr = ref('')
const aiText = ref('')
const aiModalShow = ref(false)

function openAiDiagnosis() {
  aiModalShow.value = true
  runAiDiagnosis()
}

/* ---------- 基础工具 ---------- */
const STATUS_COLOR: Record<string, string> = {
  TODO: '#a8b2c1',
  DOING: '#f59e0b',
  DONE: '#10b981'
}
const STATUS_LABEL: Record<string, string> = {
  TODO: '待办',
  DOING: '进行中',
  DONE: '已完成'
}
const fmt = (ts?: number) => {
  if (!ts) return ''
  const d = new Date(ts)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const daysText = (ms: number) => {
  const d = ms / DAY
  if (d < 1) return `${Math.max(1, Math.round(ms / 3_600_000))}小时`
  return `${Math.round(d * 10) / 10}天`
}
function quantile(sorted: number[], p: number) {
  if (!sorted.length) return 0
  const idx = (sorted.length - 1) * p
  const lo = Math.floor(idx)
  const hi = Math.ceil(idx)
  if (lo === hi) return sorted[lo]
  return sorted[lo] + (sorted[hi] - sorted[lo]) * (idx - lo)
}

/* ---------- 生命周期分段（项目级批量） ---------- */
interface Phase {
  status: string
  start: number
  end: number
  color: string
}

function parseStatusDetail(detail?: string): string | null {
  if (!detail) return null
  const m = /→\s*([A-Za-z]+)/.exec(detail)
  return m ? m[1].toUpperCase() : null
}

function buildPhases(task: Task, taskEvents: PulseEvent[]): Phase[] {
  const created = task.createdAt || Date.now()
  const changes = taskEvents.filter((e) => e.type === 'STATUS_CHANGED')
  const phases: Phase[] = []
  let cur = 'TODO'
  let curStart = created
  for (const ev of changes) {
    const next = parseStatusDetail(ev.detail)
    if (!next || next === cur || !ev.createdAt) continue
    if (ev.createdAt < curStart) continue
    phases.push({ status: cur, start: curStart, end: ev.createdAt, color: STATUS_COLOR[cur] })
    cur = next
    curStart = ev.createdAt
  }
  // 兜底：无事件但有字段信号
  if (!changes.length) {
    if (task.status === 'DONE' && task.doneTime) {
      const end = Date.parse(task.doneTime)
      phases.push({ status: 'TODO', start: created, end, color: STATUS_COLOR.TODO })
      phases.push({ status: 'DONE', start: end, end: end + 60_000, color: STATUS_COLOR.DONE })
      return phases
    }
    if (task.status === 'DOING' && task.startedTime) {
      const st = Date.parse(task.startedTime)
      phases.push({ status: 'TODO', start: created, end: st, color: STATUS_COLOR.TODO })
      phases.push({ status: 'DOING', start: st, end: Date.now(), color: STATUS_COLOR.DOING })
      return phases
    }
  }
  // 收尾段
  let end = Date.now()
  if (cur === 'DONE') end = Math.max(curStart + 60_000, end)
  // 若事件流末尾状态与任务当前状态不一致，以任务当前状态收尾
  const finalStatus = task.status && ['TODO', 'DOING', 'DONE'].includes(task.status) ? task.status : cur
  if (finalStatus !== cur) {
    phases.push({ status: cur, start: curStart, end, color: STATUS_COLOR[cur] })
    cur = finalStatus
    curStart = end
    end = Date.now()
    if (cur === 'DONE') end = curStart + 60_000
  }
  phases.push({ status: cur, start: curStart, end, color: STATUS_COLOR[cur] })
  return phases
}

/* ---------- 全局时间窗 / 刻度 ---------- */
const timeWindow = computed(() => {
  let minStart = Infinity
  let maxEnd = -Infinity
  for (const t of tasks.value) {
    const c = t.createdAt
    if (c && c < minStart) minStart = c
    const d = t.doneTime ? Date.parse(t.doneTime) : 0
    const due = t.dueTime ? Date.parse(t.dueTime) : 0
    if (d && d > maxEnd) maxEnd = d
    if (due && due > maxEnd) maxEnd = due
  }
  if (!isFinite(minStart)) minStart = Date.now() - 30 * DAY
  maxEnd = Math.max(maxEnd, Date.now())
  if (maxEnd - minStart < DAY) minStart = maxEnd - 30 * DAY
  const pad = (maxEnd - minStart) * 0.02
  return { minStart: minStart - pad, maxEnd: maxEnd + pad }
})

function pct(ts: number) {
  const { minStart, maxEnd } = timeWindow.value
  if (maxEnd <= minStart) return 0
  return Math.min(100, Math.max(0, ((ts - minStart) / (maxEnd - minStart)) * 100))
}

const todayPct = computed(() => pct(Date.now()))

const ticks = computed(() => {
  const { minStart, maxEnd } = timeWindow.value
  const totalDays = (maxEnd - minStart) / DAY
  let step = totalDays <= 42 ? 7 : totalDays <= 130 ? 14 : 30
  const count = Math.floor(totalDays / step) + 1
  if (count * 72 > 640 && step === 7) step = 14
  const list: { ts: number; label: string }[] = []
  const start = new Date(minStart)
  if (step === 7 || step === 14) {
    const wd = start.getDay() || 7
    start.setDate(start.getDate() - wd + 1)
  } else {
    start.setDate(1)
  }
  start.setHours(0, 0, 0, 0)
  for (let t = start.getTime(); t <= maxEnd + DAY; t += step * DAY) {
    if (t < minStart - DAY) continue
    const d = new Date(t)
    list.push({ ts: t, label: step >= 30 ? `${d.getMonth() + 1}月` : `${d.getMonth() + 1}/${d.getDate()}` })
  }
  return list
})

/* ---------- 任务生命周期集合 ---------- */
const eventsByTask = computed(() => {
  const map = new Map<number, PulseEvent[]>()
  for (const ev of events.value) {
    if (!ev.taskId) continue
    const list = map.get(ev.taskId)
    if (list) list.push(ev)
    else map.set(ev.taskId, [ev])
  }
  return map
})

const rows = computed(() =>
  tasks.value
    .slice()
    .sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0))
    .map((t) => ({
      task: t,
      phases: buildPhases(t, eventsByTask.value.get(t.id) || [])
    }))
)

const phaseLabel = (p: Phase) => `${STATUS_LABEL[p.status] || p.status} · ${daysText(p.end - p.start)}`

/* ---------- 健康评分 ---------- */
const health = computed(() => {
  const done = tasks.value.filter((t) => t.status === 'DONE')
  const withDue = done.filter((t) => t.dueTime)
  const onTime = withDue.filter((t) => {
    const d = t.doneTime ? Date.parse(t.doneTime) : 0
    const due = Date.parse(t.dueTime || '')
    return d && due && d <= due + 6 * 3_600_000
  })
  const onTimeRate = withDue.length ? onTime.length / withDue.length : 0

  // 流动效率：执行时间 / 总周期
  const effs = done
    .map((t) => {
      if (!t.createTime || !t.doneTime || !t.startedTime) return null
      const c = Date.parse(t.createTime)
      const d = Date.parse(t.doneTime)
      const s = Date.parse(t.startedTime)
      if (!c || !d || !s || d <= c) return null
      return Math.min(1, Math.max(0, (d - s) / (d - c)))
    })
    .filter((v): v is number => v !== null)
  const flowEff = effs.length ? effs.reduce((a, b) => a + b, 0) / effs.length : null

  // 开工及时性：已开工任务 queue P50
  const queues = tasks.value
    .map((t) => {
      if (!t.createTime || !t.startedTime) return null
      const c = Date.parse(t.createTime)
      const s = Date.parse(t.startedTime)
      if (!c || !s || s < c) return null
      return (s - c) / DAY
    })
    .filter((v): v is number => v !== null)
  const queueP50 = queues.length ? quantile(queues.slice().sort((a, b) => a - b), 0.5) : null

  // 停滞风险：未完成任务中最后阶段停留 > 7 天
  const open = tasks.value.filter((t) => t.status !== 'DONE')
  const stuckCount = open.filter((t) => {
    const ps = rows.value.find((r) => r.task.id === t.id)?.phases || []
    const lp = ps[ps.length - 1]
    return lp && Date.now() - lp.start > 7 * DAY
  }).length
  const stuckRatio = open.length ? stuckCount / open.length : 0

  // 返工率：存在 DONE -> TODO/DOING 回退事件的任务
  const revertTasks = new Set<number>()
  for (const ev of events.value) {
    if (ev.type !== 'STATUS_CHANGED' || !ev.detail) continue
    if (/DONE\s*->\s*(TODO|DOING)/.test(ev.detail)) revertTasks.add(ev.taskId)
  }
  const revertRatio = tasks.value.length ? revertTasks.size / tasks.value.length : 0

  const clamp01 = (v: number) => Math.max(0, Math.min(1, v))
  const parts = [
    { key: '按时交付', label: '按时交付', value: onTimeRate, weight: 30 },
    { key: '流动效率', label: '流动效率', value: flowEff === null ? 0.5 : flowEff, weight: 25 },
    { key: '开工及时', label: '开工及时', value: queueP50 === null ? 0.5 : clamp01(1 - queueP50 / 3), weight: 20 },
    { key: '低停滞', label: '低停滞风险', value: clamp01(1 - stuckRatio), weight: 15 },
    { key: '低返工', label: '低返工', value: clamp01(1 - revertRatio * 2), weight: 10 }
  ]
  const score = Math.round(parts.reduce((sum, p) => sum + p.value * p.weight, 0))
  const grade = score >= 85 ? 'S' : score >= 70 ? 'A' : score >= 55 ? 'B' : score >= 40 ? 'C' : 'D'
  return { score, grade, parts, onTimeRate, flowEff, queueP50, stuckCount, revertCount: revertTasks.size }
})

const gradeColor = computed(() => {
  const s = health.value.score
  if (s >= 85) return '#10b981'
  if (s >= 70) return '#3b82f6'
  if (s >= 55) return '#f59e0b'
  if (s >= 40) return '#f97316'
  return '#ef4444'
})

const ringDash = computed(() => {
  const C = 2 * Math.PI * 34
  return `${(health.value.score / 100) * C} ${C}`
})

/* ---------- 吞吐量 ---------- */
const throughput = computed(() => {
  const weeks: { start: number; count: number; label: string }[] = []
  const now = new Date()
  for (let i = 9; i >= 0; i--) {
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - now.getDay() + 1 - i * 7)
    start.setHours(0, 0, 0, 0)
    weeks.push({ start: start.getTime(), count: 0, label: `${start.getMonth() + 1}/${start.getDate()}` })
  }
  for (const t of tasks.value) {
    if (t.status !== 'DONE' || !t.doneTime) continue
    const d = Date.parse(t.doneTime)
    const w = weeks.find((w) => d >= w.start && d < w.start + 7 * DAY)
    if (w) w.count++
  }
  return weeks
})
const maxThroughput = computed(() => Math.max(1, ...throughput.value.map((w) => w.count)))

/* ---------- 控制图 ---------- */
const controlPoints = computed(() => {
  return tasks.value
    .filter((t) => t.status === 'DONE' && t.doneTime && t.createTime)
    .map((t) => {
      const done = Date.parse(t.doneTime!)
      const created = t.createdAt || Date.parse(t.createTime!)
      const due = t.dueTime ? Date.parse(t.dueTime) : 0
      return {
        id: t.id,
        title: t.title,
        days: Math.max(0, (done - created) / DAY),
        done,
        late: due ? done > due + 6 * 3_600_000 : false
      }
    })
    .sort((a, b) => a.done - b.done)
})
const ctrlStats = computed(() => {
  const days = controlPoints.value.map((p) => p.days).sort((a, b) => a - b)
  return { p50: quantile(days, 0.5), p75: quantile(days, 0.75), p85: quantile(days, 0.85), max: Math.max(1, days[days.length - 1] || 1) }
})

/* ---------- 瓶颈识别 ---------- */
const stuckTasks = computed(() => {
  return rows.value
    .filter((r) => r.task.status === 'DOING')
    .map((r) => {
      const lp = r.phases[r.phases.length - 1]
      return { id: r.task.id, title: r.task.title, days: lp ? (Date.now() - lp.start) / DAY : 0 }
    })
    .filter((x) => x.days > 0.1)
    .sort((a, b) => b.days - a.days)
    .slice(0, 5)
})
const queuedTasks = computed(() => {
  return rows.value
    .filter((r) => r.task.status === 'TODO')
    .map((r) => {
      const c = r.task.createdAt || Date.now()
      return { id: r.task.id, title: r.task.title, days: (Date.now() - c) / DAY }
    })
    .filter((x) => x.days > 0.1)
    .sort((a, b) => b.days - a.days)
    .slice(0, 5)
})
const flappingTasks = computed(() => {
  const countMap = new Map<number, { title: string; count: number }>()
  for (const ev of events.value) {
    if (ev.type !== 'STATUS_CHANGED') continue
    const cur = countMap.get(ev.taskId)
    const title = tasks.value.find((t) => t.id === ev.taskId)?.title || ''
    if (cur) cur.count++
    else countMap.set(ev.taskId, { title, count: 1 })
  }
  return [...countMap.entries()]
    .map(([id, v]) => ({ id, title: v.title, count: v.count }))
    .filter((x) => x.count >= 3)
    .sort((a, b) => b.count - a.count)
    .slice(0, 5)
})

/* ---------- hover ---------- */
const hoverRow = ref<{ task: Task; phases: Phase[] } | null>(null)
const hoverPhase = ref<Phase | null>(null)

const goTask = (id: number) => router.push(`/projects/${projectId}/tasks/${id}`)

/* ---------- 加载 ---------- */
const project = computed(() => projectStore.byId.get(projectId))

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    if (!project.value) {
      await projectStore.load()
    }
    const data = await taskApi.pulse(projectId)
    tasks.value = data.tasks
    events.value = data.events
  } catch (e: any) {
    errorMsg.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

/* ---------- AI 诊断 ---------- */
async function runAiDiagnosis() {
  if (aiBusy.value) return
  aiErr.value = ''
  aiText.value = ''
  aiBusy.value = true
  aiErr.value = ''
  aiText.value = ''
  const h = health.value
  const payload = {
    score: h.score,
    grade: h.grade,
    onTimeRate: Math.round(h.onTimeRate * 100),
    flowEfficiency: h.flowEff === null ? null : Math.round(h.flowEff * 100),
    queueP50Days: h.queueP50 === null ? null : Math.round(h.queueP50 * 10) / 10,
    cycleP50Days: Math.round(ctrlStats.value.p50 * 10) / 10,
    cycleP85Days: Math.round(ctrlStats.value.p85 * 10) / 10,
    wip: tasks.value.filter((t) => t.status !== 'DONE').length,
    revertTasks: h.revertCount,
    stuckTop: stuckTasks.value.map((s) => ({ title: s.title, days: Math.round(s.days) })),
    queuedTop: queuedTasks.value.map((s) => ({ title: s.title, days: Math.round(s.days) })),
    flappingTop: flappingTasks.value.map((s) => ({ title: s.title, count: s.count })),
    throughputLast10Weeks: throughput.value.map((w) => w.count)
  }
  const prompt = `你是敏捷项目管理顾问。以下是某项目的节奏数据（JSON），请输出一份中文诊断报告：
数据：${JSON.stringify(payload)}

【排版规范（严格遵守）】
- 严禁使用任何 # 井号标题（不要 #、##、###、#### 任何级别）
- 小节用 emoji + 加粗标记，例如：🔍 **关键发现**、✅ **值得肯定**、🎯 **两周行动**
- 每个小节之间用 --- 单行分隔
- 重点数字加粗，例如 **97** 分、**78** 天、停滞 **88** 天
- 关键短语可用加粗强调，全篇保持紧凑，不要空话

【报告结构】
1. 一句话状态总结（基于健康评分等级，单独一段，不加小标题）
2. 🔍 **关键发现**：2-3 个最关键问题，每个问题 4 行：
   - **现象**：...
   - **数据**：...（含具体数字）
   - **根因**：...
   - **建议**：...
3. ✅ **值得肯定**：1 个亮点，1-2 句
4. 🎯 **两周行动**：2-3 条，每条以 - 开头

全文中文化，数据要具体，行动要可执行。`
  try {
    await aiApi.chatStream({ messages: [{ role: 'user', content: prompt }], projectId, type: 'diagnosis' }, (delta) => {
      aiText.value += delta
    })
  } catch (e: any) {
    aiErr.value = e?.message || 'AI 诊断失败'
  } finally {
    aiBusy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page pulsePage">
    <!-- 顶部独立一行：返回按钮（真正左上顶格，SVG 图标 + 文字） -->
    <div class="pulseBreadRow">
      <button class="pulseBack" type="button" @click="router.push(`/projects/${projectId}`)">
        <svg class="pulseBackIcon" viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M12.5 4.5 7 10l5.5 5.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        <span>返回</span>
      </button>
    </div>

    <header class="pulseTop">
      <div class="pulseHead">
        <h1>{{ project?.name || '项目' }} <span class="pulseSub">节奏洞察</span></h1>
      </div>
      <div class="pulseActions">
        <button class="btnGhost" :disabled="loading" @click="load()">刷新</button>
        <button class="btnPrimary" :disabled="aiBusy || loading" @click="openAiDiagnosis">
          {{ aiBusy ? '分析中…' : 'AI 诊断' }}
        </button>
      </div>
    </header>

    <div v-if="loading" class="pulseLoading">
      <n-spin size="large" />
    </div>
    <div v-else-if="errorMsg" class="pulseError">{{ errorMsg }}</div>
    <div v-else-if="!tasks.length" class="pulseEmpty">
      <p>项目还没有任务</p>
      <span>创建任务并流转状态后，这里会生成项目节奏洞察</span>
    </div>
    <template v-else>
      <!-- 1. 健康评分 -->
      <section class="scoreRow">
        <div class="scoreCard main">
          <div class="scoreRingBox">
            <svg class="scoreRing" viewBox="0 0 80 80">
              <circle cx="40" cy="40" r="34" fill="none" stroke="var(--bg-2)" stroke-width="7" />
              <circle
                cx="40" cy="40" r="34" fill="none"
                :stroke="gradeColor" stroke-width="7" stroke-linecap="round"
                :stroke-dasharray="ringDash" transform="rotate(-90 40 40)"
              />
            </svg>
            <span class="scoreBig" :style="{ color: gradeColor }">{{ health.score }}</span>
          </div>
          <div class="scoreTitle">项目健康度</div>
          <span class="gradeBadge" :style="{ background: gradeColor }">{{ health.grade }}</span>
          <div class="scoreParts">
            <div v-for="p in health.parts" :key="p.key" class="part">
              <div class="partTop">
                <span>{{ p.label }}</span>
                <span>{{ Math.round(p.value * 100) }}</span>
              </div>
              <div class="partTrack">
                <div class="partFill" :style="{ width: Math.round(p.value * 100) + '%' }" />
              </div>
            </div>
          </div>
        </div>

        <div class="scoreCard">
          <div class="scoreNum" :style="{ color: health.onTimeRate >= 0.7 ? '#10b981' : '#f59e0b' }">
            {{ Math.round(health.onTimeRate * 100) }}<span class="unit">%</span>
          </div>
          <div class="scoreTitle">按时交付率</div>
          <div class="scoreHint">
            {{ tasks.filter((t) => t.status === 'DONE' && t.dueTime).length }} 个有截止期的已完成任务
          </div>
        </div>

        <div class="scoreCard">
          <div class="scoreNum">{{ Math.round(ctrlStats.p50 * 10) / 10 }}<span class="unit">天</span></div>
          <div class="scoreTitle">周期中位数 P50</div>
          <div class="scoreHint">
            P75 {{ Math.round(ctrlStats.p75) }} 天 · P85 {{ Math.round(ctrlStats.p85) }} 天
          </div>
        </div>

        <div class="scoreCard">
          <div class="scoreNum">{{ tasks.filter((t) => t.status !== 'DONE').length }}</div>
          <div class="scoreTitle">在途任务</div>
          <div class="scoreHint">
            {{ tasks.filter((t) => t.status === 'DOING').length }} 进行中 · {{ tasks.filter((t) => t.status === 'TODO').length }} 未开工
          </div>
        </div>
      </section>

      <!-- 2. 项目节奏热力图 -->
      <section class="card heatCard">
        <div class="cardHead">
          <h2>项目节奏热力图</h2>
          <div class="heatLegend">
            <span v-for="s in ['TODO', 'DOING', 'DONE']" :key="s" class="legendItem">
              <i :style="{ background: STATUS_COLOR[s] }" />{{ STATUS_LABEL[s] }}
            </span>
            <span class="legendItem"><i class="legendToday" />今天</span>
          </div>
        </div>
        <div class="heatScroll">
          <div class="heatGrid">
            <div class="heatLane tickLane">
              <div class="heatTitleCol"></div>
              <div class="heatBody">
                <div v-for="tk in ticks" :key="tk.ts" class="heatTick" :style="{ left: pct(tk.ts) + '%' }">
                  <span>{{ tk.label }}</span>
                </div>
              </div>
            </div>
            <div v-for="r in rows" :key="r.task.id" class="heatLane">
              <div class="heatTitleCol">
                <span class="heatTitle">{{ r.task.title }}</span>
                <span class="heatBadge" :class="r.task.status.toLowerCase()">{{ STATUS_LABEL[r.task.status] }}</span>
              </div>
              <div class="heatBody">
                <div class="heatToday" :style="{ left: todayPct + '%' }"></div>
                <div
                  v-for="(p, i) in r.phases" :key="i"
                  class="heatPhase"
                  :style="{
                    left: pct(p.start) + '%',
                    width: Math.max(pct(p.end) - pct(p.start), 0.12) + '%',
                    background: p.color
                  }"
                  @mouseenter="hoverRow = r; hoverPhase = p"
                  @mouseleave="hoverRow = null; hoverPhase = null"
                  @click="goTask(r.task.id)"
                >
                  <span class="heatPhaseLabel" :class="p.status.toLowerCase()">{{ phaseLabel(p) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="heatHover">
          <span v-if="hoverRow && hoverPhase" class="heatHoverText">
            <b>{{ hoverRow.task.title }}</b>
            <i class="dot" :style="{ background: hoverPhase.color }"></i>
            {{ STATUS_LABEL[hoverPhase.status] }} · {{ daysText(hoverPhase.end - hoverPhase.start) }}
            <span class="muted">{{ fmt(hoverPhase.start) }} ~ {{ fmt(hoverPhase.end) }}</span>
          </span>
          <span v-else class="muted">悬停色带查看任务在该状态停留的时长 · 点击跳转任务详情</span>
        </div>
      </section>

      <!-- 3. 吞吐量 + 控制图 -->
      <section class="twoCol">
        <div class="card">
          <div class="cardHead">
            <h2>每周完成吞吐</h2>
            <span class="muted small">{{ throughput.reduce((a, w) => a + w.count, 0) }} 个任务完成于近 10 周</span>
          </div>
          <div class="barChart">
            <div v-for="w in throughput" :key="w.start" class="barCol">
              <span class="barVal">{{ w.count || '' }}</span>
              <div class="barTrack">
                <div class="bar" :style="{ height: (w.count / maxThroughput) * 100 + '%' }"></div>
              </div>
              <span class="barLabel">{{ w.label }}</span>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="cardHead">
            <h2>周期时间控制图</h2>
            <span class="muted small">绿=按时 · 红=超期 · 虚线=P50/P85</span>
          </div>
          <div class="ctrlWrap">
            <svg v-if="controlPoints.length" viewBox="0 0 600 240" class="ctrlSvg">
              <!-- 网格 -->
              <line v-for="i in 4" :key="'g' + i" x1="50" x2="580" :y1="50 + i * 40" :y2="50 + i * 40" class="gridLine" />
              <!-- P50 / P85 -->
              <line
                x1="50" x2="580" class="pLine"
                :y1="230 - (ctrlStats.p50 / ctrlStats.max) * 170" :y2="230 - (ctrlStats.p50 / ctrlStats.max) * 170"
              />
              <line
                x1="50" x2="580" class="pLine p85"
                :y1="230 - (ctrlStats.p85 / ctrlStats.max) * 170" :y2="230 - (ctrlStats.p85 / ctrlStats.max) * 170"
              />
              <text x="584" class="pText" :y="230 - (ctrlStats.p50 / ctrlStats.max) * 170 - 4">P50 {{ Math.round(ctrlStats.p50) }}d</text>
              <text x="584" class="pText p85Text" :y="230 - (ctrlStats.p85 / ctrlStats.max) * 170 - 4">P85 {{ Math.round(ctrlStats.p85) }}d</text>
              <!-- 散点 -->
              <g v-for="p in controlPoints" :key="p.id">
                <circle
                  class="pt" :class="{ late: p.late, spike: p.days > ctrlStats.p85 }"
                  :cx="50 + (p.done - (controlPoints[0]?.done ?? 0)) / Math.max(1, (controlPoints[controlPoints.length - 1]?.done ?? 1) - (controlPoints[0]?.done ?? 0)) * 530"
                  :cy="230 - (p.days / ctrlStats.max) * 170"
                  r="4"
                >
                  <title>{{ p.title }} · {{ Math.round(p.days) }}天</title>
                </circle>
              </g>
            </svg>
            <div v-else class="emptyMini">暂无已完成任务</div>
          </div>
        </div>
      </section>

      <!-- 4. 瓶颈识别 -->
      <section class="threeCol">
        <div class="card bottleneck">
          <div class="cardHead">
            <h2>执行停滞</h2>
            <span class="muted small">进行中但卡了很久</span>
          </div>
          <ul v-if="stuckTasks.length" class="botList">
            <li v-for="s in stuckTasks" :key="s.id" @click="goTask(s.id)">
              <span class="botTitle">{{ s.title }}</span>
              <span class="botBadge warn">{{ Math.round(s.days) }}天</span>
            </li>
          </ul>
          <div v-else class="emptyMini">没有停滞任务，流动健康</div>
        </div>

        <div class="card bottleneck">
          <div class="cardHead">
            <h2>迟迟未开工</h2>
            <span class="muted small">建了但一直没启动</span>
          </div>
          <ul v-if="queuedTasks.length" class="botList">
            <li v-for="s in queuedTasks" :key="s.id" @click="goTask(s.id)">
              <span class="botTitle">{{ s.title }}</span>
              <span class="botBadge warn">{{ Math.round(s.days) }}天</span>
            </li>
          </ul>
          <div v-else class="emptyMini">没有积压的未开工任务</div>
        </div>

        <div class="card bottleneck">
          <div class="cardHead">
            <h2>反复横跳</h2>
            <span class="muted small">状态切换最频繁</span>
          </div>
          <ul v-if="flappingTasks.length" class="botList">
            <li v-for="s in flappingTasks" :key="s.id" @click="goTask(s.id)">
              <span class="botTitle">{{ s.title }}</span>
              <span class="botBadge">{{ s.count }}次切换</span>
            </li>
          </ul>
          <div v-else class="emptyMini">没有反复横跳的任务</div>
        </div>
      </section>

      <!-- 5. AI 诊断（已移至弹窗） -->
    </template>

    <!-- AI 诊断弹窗（流式输出） -->
    <n-modal
      v-model:show="aiModalShow"
      preset="card"
      :title="'AI 诊断'"
      style="max-width: 720px; width: calc(100vw - 48px);"
      :mask-closable="!aiBusy"
      :closable="!aiBusy"
      :bordered="false"
      content-style="padding: 0;"
    >
      <template #header-extra>
        <button v-if="aiText && !aiBusy" class="btnGhost sm" @click="runAiDiagnosis">重新生成</button>
      </template>
      <div class="aiModalBody">
        <div v-if="aiErr" class="aiErr">AI 诊断失败：{{ aiErr }}</div>
        <div v-if="aiBusy || aiText" class="aiMarkdown">
          <MarkdownView :content="aiText" />
          <span v-if="aiBusy" class="typingDot typingDotInline" aria-hidden="true" />
        </div>
        <div v-if="!aiBusy && !aiText && !aiErr" class="aiIdle">
          AI 将基于健康评分、瓶颈识别与周期数据，输出项目级诊断与行动建议
        </div>
        <div v-if="aiText && !aiBusy" class="aiToTaskRow">
          <AiToTaskButton :project-id="projectId" :content="aiText" />
        </div>
      </div>
      <template #action>
        <div class="aiModalFoot">
          <span class="aiFootMeta" v-if="aiBusy || aiText">
            <span v-if="aiBusy">流式生成中…</span>
            <span v-else>已完成</span>
          </span>
          <button class="btnGhost" :disabled="aiBusy" @click="aiModalShow = false">关闭</button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.pulsePage {
  max-width: 1280px;
  margin: 0 auto;
  /* 跟随 AppLayout 的 .app-content padding(24px 28px)，去双层 padding 让返回按钮真正顶格 */
  padding: 0 0 64px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.pulseTop {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}
.pulseHead {
  flex: 1;
  min-width: 0;
}
.pulseTitleRow {
  display: flex;
  align-items: baseline;
  gap: 14px;
  flex-wrap: wrap;
}
.pulseTitleRow h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--ink-1);
  display: flex;
  align-items: center;
  gap: 10px;
}
.pulseSub {
  font-size: 14px;
  font-weight: 500;
  color: var(--primary);
  background: color-mix(in srgb, var(--primary) 12%, transparent);
  border-radius: var(--radius-sm);
  padding: 3px 10px;
}
.pulseDesc {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--ink-3);
}
/* 顶部独立一行：返回按钮（真正左上顶格） */
.pulseBreadRow {
  display: flex;
  align-items: center;
  height: 28px;
}
.pulseBack {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 0;
  background: transparent;
  color: #374151;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  padding: 5px 10px 5px 6px;
  margin-left: -8px;
  border-radius: 8px;
  cursor: pointer;
  transition: color 0.15s ease, background 0.15s ease;
}
.pulseBack:hover {
  color: #111827;
  background: rgba(15, 23, 42, 0.05);
}
.pulseBackIcon {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
}
.pulseActions {
  display: flex;
  gap: 10px;
}
.btnGhost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 34px;
  padding: 0 14px;
  border-radius: var(--radius-sm);
  background: #ffffff;
  color: #1f2329;
  border: 1px solid #d1d5db;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}
.btnGhost:hover {
  background: #f3f4f6;
  border-color: #9ca3af;
  color: #111827;
  box-shadow: 0 4px 10px rgba(15, 23, 42, 0.06);
}
.btnPrimary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 34px;
  padding: 0 16px;
  border-radius: var(--radius-sm);
  /* 用明确的深色，不再依赖 var(--primary) 在浅主题下褪色透明 */
  background: #1f2329;
  color: #ffffff;
  border: 1px solid #1f2329;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.15);
}
.btnPrimary:hover {
  background: #000000;
  border-color: #000000;
}
.btnPrimary:disabled,
.btnGhost:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btnGhost.sm {
  height: 28px;
  padding: 0 10px;
  font-size: 12px;
}
.pulseLoading,
.pulseError,
.pulseEmpty {
  background: var(--bg-card);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  padding: 60px 20px;
  text-align: center;
  color: var(--ink-3);
  font-size: 14px;
}
.pulseEmpty p {
  margin: 0 0 6px;
  font-size: 15px;
  color: var(--ink-1);
  font-weight: 500;
}

/* ---- 评分卡 ---- */
.scoreRow {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1fr;
  gap: 14px;
}
.scoreCard {
  background: var(--bg-card);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  padding: 18px 16px;
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.scoreCard.main {
  grid-row: span 1;
}
.scoreRingBox {
  position: relative;
  width: 72px;
  height: 72px;
}
.scoreRing {
  width: 72px;
  height: 72px;
}
.scoreBig {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
}
.gradeBadge {
  position: absolute;
  top: 16px;
  right: 16px;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.scoreTitle {
  font-size: 13px;
  font-weight: 500;
  color: var(--ink-1);
}
.scoreNum {
  font-size: 30px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--ink-1);
}
.scoreNum .unit {
  font-size: 14px;
  font-weight: 500;
  color: var(--ink-3);
  margin-left: 2px;
}
.scoreHint {
  font-size: 12px;
  color: var(--ink-3);
  line-height: 1.5;
}
.scoreParts {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.part {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.partTop {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--ink-3);
}
.partTop span:last-child {
  color: var(--ink-2);
  font-weight: 600;
}
.partTrack {
  height: 4px;
  border-radius: 2px;
  background: var(--bg-2);
  overflow: hidden;
}
.partFill {
  height: 100%;
  border-radius: 2px;
  background: var(--primary);
  transition: width 0.4s ease;
}

/* ---- 卡片通用 ---- */
.card {
  background: var(--bg-card);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  padding: 18px;
}
.cardHead {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.cardHead h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--ink-1);
}
.muted {
  color: var(--ink-3);
}
.small {
  font-size: 12px;
}

/* ---- 热力图 ---- */
.heatCard {
  display: flex;
  flex-direction: column;
}
.heatLegend {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: var(--ink-2);
}
.legendItem {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.legendItem i {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  display: inline-block;
}
.legendToday {
  width: 14px !important;
  height: 0 !important;
  border-top: 2px solid #ef4444;
  border-radius: 0 !important;
}
.heatScroll {
  overflow-x: auto;
  border-radius: var(--radius-sm);
}
.heatGrid {
  min-width: 860px;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.heatLane {
  display: flex;
  align-items: stretch;
  gap: 10px;
}
.tickLane {
  height: 26px;
  align-items: flex-end;
}
.heatTitleCol {
  width: 210px;
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
  white-space: nowrap;
}
.heatTitle {
  font-size: 12.5px;
  color: var(--ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
}
.heatBadge {
  flex: none;
  font-size: 10.5px;
  border-radius: 4px;
  padding: 1px 6px;
  background: var(--bg-2);
  color: var(--ink-3);
}
.heatBadge.doing {
  background: color-mix(in srgb, #f59e0b 14%, transparent);
  color: #b45309;
}
.heatBadge.done {
  background: color-mix(in srgb, #10b981 14%, transparent);
  color: #047857;
}
.heatBody {
  flex: 1;
  position: relative;
  height: 34px;
  border-radius: 5px;
  background: var(--bg-1);
  overflow: hidden;
  cursor: pointer;
}
.tickLane .heatBody {
  background: transparent;
  cursor: default;
}
.heatTick {
  position: absolute;
  bottom: 0;
  transform: translateX(-50%);
  font-size: 10.5px;
  color: var(--ink-3);
  border-left: 1px dashed var(--line);
  padding-left: 3px;
  line-height: 1.4;
  white-space: nowrap;
}
.heatToday {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 0;
  border-left: 1.5px solid #ef4444;
  z-index: 2;
  pointer-events: none;
}
.heatToday::after {
  content: '';
  position: absolute;
  top: 0;
  left: -3px;
  width: 0;
  height: 0;
  border-left: 3.5px solid transparent;
  border-right: 3.5px solid transparent;
  border-top: 5px solid #ef4444;
}
.heatPhase {
  position: absolute;
  top: 5px;
  bottom: 5px;
  border-radius: 4px;
  z-index: 1;
  transition: filter 0.12s ease, opacity 0.12s ease;
  opacity: 0.92;
}
.heatPhase:hover {
  filter: brightness(1.12);
  opacity: 1;
  z-index: 3;
}
.heatPhaseLabel {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  padding: 0 7px;
  font-size: 10.5px;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
}
.heatPhaseLabel.todo {
  color: rgba(40, 50, 65, 0.65);
  text-shadow: none;
  font-weight: 600;
}
.heatHover {
  margin-top: 12px;
  min-height: 20px;
  font-size: 12.5px;
  color: var(--ink-1);
  border-top: 1px dashed var(--line);
  padding-top: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.heatHoverText {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

/* ---- 吞吐 + 控制图 ---- */
.twoCol {
  display: grid;
  grid-template-columns: 1fr 1.3fr;
  gap: 14px;
}
.barChart {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  height: 168px;
}
.barCol {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  height: 100%;
  justify-content: flex-end;
}
.barVal {
  font-size: 11px;
  color: var(--ink-2);
  font-weight: 600;
}
.barTrack {
  flex: 1;
  width: 100%;
  max-width: 26px;
  display: flex;
  align-items: flex-end;
  background: var(--bg-2);
  border-radius: 4px;
  overflow: hidden;
}
.bar {
  width: 100%;
  min-height: 2px;
  border-radius: 4px;
  background: linear-gradient(180deg, color-mix(in srgb, var(--primary) 85%, #fff), var(--primary));
  transition: height 0.4s ease;
}
.barLabel {
  font-size: 10px;
  color: var(--ink-3);
  white-space: nowrap;
}
.ctrlWrap {
  overflow-x: auto;
}
.ctrlSvg {
  width: 100%;
  min-width: 520px;
  display: block;
}
.gridLine {
  stroke: var(--line);
  stroke-width: 1;
  stroke-dasharray: 3 4;
}
.pLine {
  stroke: #3b82f6;
  stroke-width: 1.5;
  stroke-dasharray: 6 4;
}
.pLine.p85 {
  stroke: #f97316;
}
.pText {
  font-size: 10px;
  fill: #3b82f6;
}
.p85Text {
  fill: #f97316;
}
.pt {
  fill: #10b981;
  stroke: #fff;
  stroke-width: 1.2;
}
.pt.late {
  fill: #ef4444;
}
.pt.spike {
  stroke: #f97316;
  stroke-width: 2;
}

/* ---- 瓶颈 ---- */
.threeCol {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}
.botList {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.botList li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background 0.12s ease;
}
.botList li:hover {
  background: var(--bg-1);
}
.botTitle {
  font-size: 12.5px;
  color: var(--ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.botBadge {
  flex: none;
  font-size: 11px;
  font-weight: 600;
  color: var(--ink-2);
  background: var(--bg-2);
  border-radius: 6px;
  padding: 2px 8px;
}
.botBadge.warn {
  color: #b45309;
  background: color-mix(in srgb, #f59e0b 14%, transparent);
}
.emptyMini {
  font-size: 12.5px;
  color: var(--ink-3);
  padding: 12px 0;
}

/* ---- AI 诊断弹窗 ---- */
.aiModalBody {
  padding: 18px 22px 8px;
  min-height: 120px;
  max-height: calc(100vh - 320px);
  overflow-y: auto;
}
.aiModalBody::-webkit-scrollbar { width: 8px; }
.aiModalBody::-webkit-scrollbar-thumb { background: rgba(15, 23, 42, 0.18); border-radius: 999px; }
.aiMarkdown {
  /* 弹窗里不需要单独限高，由 .aiModalBody 控制 */
}
.aiToTaskRow {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed rgba(15, 23, 42, 0.12);
  display: flex;
  justify-content: flex-end;
}
.aiIdle {
  border: 1px dashed var(--line);
  border-radius: var(--radius-sm);
  padding: 36px 20px;
  text-align: center;
  font-size: 13px;
  color: var(--ink-3);
  background: var(--bg-1);
}
.aiErr {
  background: color-mix(in srgb, #ef4444 8%, transparent);
  border: 1px solid color-mix(in srgb, #ef4444 30%, transparent);
  color: #dc2626;
  border-radius: var(--radius-sm);
  padding: 12px 14px;
  font-size: 13px;
  margin-bottom: 12px;
}
.aiModalFoot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
}
.aiFootMeta {
  font-size: 12px;
  color: var(--ink-3);
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.typingDot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary);
  animation: typing 1s ease-in-out infinite;
}
.typingDotInline {
  display: inline-block;
  margin-left: 4px;
  vertical-align: middle;
}
@keyframes typing {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.1); }
}

@media (max-width: 1000px) {
  .scoreRow { grid-template-columns: 1fr 1fr; }
  .twoCol, .threeCol { grid-template-columns: 1fr; }
}
</style>
