<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NDrawer, NDrawerContent, NSelect, NSpin } from 'naive-ui'
import { type Project } from '../api/project'
import { taskApi } from '../api/task'
import { aiApi } from '../api/ai'
import { useToast } from '../composables/useToast'
import MarkdownView from '../components/MarkdownView.vue'
import AiToTaskButton from '../components/AiToTaskButton.vue'
import {
  analyzeAll,
  analyzeMembers,
  daysText,
  statusLabel,
  type CenterData,
  type MemberStats,
  type ProjectPulseInput,
  type TaskLc
} from '../utils/lifecycleAnalytics'

const router = useRouter()
const toast = useToast()

const loading = ref(false)
const projects = ref<Project[]>([])
const pulses = ref<Map<number, ProjectPulseInput>>(new Map())

const filterProjectId = ref<number>(0)
const filterRange = ref<number>(0)
const filterStatus = ref<string>('all')
const sortBy = ref<'cycle' | 'switch' | 'flow' | 'score'>('cycle')

const projectOptions = computed(() => [
  { label: '全部项目', value: 0 },
  ...projects.value.map((p) => ({ label: p.name, value: p.id }))
])

const rangeOptions = [
  { label: '全部时间', value: 0 },
  { label: '近 30 天', value: 30 },
  { label: '近 90 天', value: 90 },
  { label: '近 180 天', value: 180 }
]

const statusOptions = [
  { label: '全部状态', value: 'all' },
  { label: '待办', value: 'TODO' },
  { label: '进行中', value: 'DOING' },
  { label: '已完成', value: 'DONE' }
]

const sortOptions = [
  { label: '按周期时长', value: 'cycle' },
  { label: '按切换次数', value: 'switch' },
  { label: '按流动效率', value: 'flow' },
  { label: '按节奏评分', value: 'score' }
]

const center = computed<CenterData | null>(() => {
  const inputs = [...pulses.value.values()]
  if (!inputs.length) return null
  return analyzeAll(inputs, {
    projectId: filterProjectId.value || undefined,
    rangeDays: filterRange.value || undefined,
    status: filterStatus.value,
    sortBy: sortBy.value
  })
})

const tasksByProject = computed(() => {
  const m = new Map<number, TaskLc[]>()
  const c = center.value
  if (!c) return m
  for (const t of c.tasks) {
    const arr = m.get(t.projectId) || []
    arr.push(t)
    m.set(t.projectId, arr)
  }
  return m
})

async function load() {
  loading.value = true
  try {
    const data = await taskApi.lifecycleCenter()
    projects.value = data.projects
    const next = new Map<number, ProjectPulseInput>()
    for (const pp of data.pulses) {
      const proj = data.projects.find((p) => p.id === pp.projectId)
      next.set(pp.projectId, {
        projectId: pp.projectId,
        projectName: proj?.name || `项目 ${pp.projectId}`,
        tasks: pp.tasks,
        events: pp.events
      })
    }
    pulses.value = next
  } catch {
    pulses.value = new Map()
  } finally {
    loading.value = false
  }
}

function refresh() {
  load()
}

onMounted(load)

// ===================== 行点击：跳转单任务独立胶片页 =====================
function openTask(t: TaskLc) {
  router.push({ name: 'task-lifecycle', params: { taskId: t.taskId } })
}

// ===================== 到期预警（3 天内到期 + 已逾期） =====================
const DUE_WINDOW = 3 * 86400000
const dueAlerts = computed<Array<TaskLc & { dueTs: number }>>(() => {
  const c = center.value
  if (!c) return []
  const now = Date.now()
  return c.tasks
    .filter((t) => t.status !== 'DONE' && t.dueTime)
    .map((t) => ({ ...t, dueTs: Date.parse(t.dueTime as string) || 0 }))
    .filter((t) => t.dueTs > 0 && t.dueTs <= now + DUE_WINDOW)
    .sort((a, b) => a.dueTs - b.dueTs)
})
const overdueAlerts = computed(() => dueAlerts.value.filter((t) => t.dueTs < Date.now()))
const upcomingAlerts = computed(() => dueAlerts.value.filter((t) => t.dueTs >= Date.now()))

// ===================== 成员效能 =====================
const members = computed<MemberStats[]>(() => {
  const c = center.value
  if (!c) return []
  return analyzeMembers(c.tasks)
})

// ===================== 图表数据 =====================
const trendPath = computed(() => {
  const trend = center.value?.trend || []
  const w = 560
  const h = 190
  const padL = 38
  const padR = 14
  const padT = 14
  const padB = 28
  const innerW = w - padL - padR
  const innerH = h - padT - padB
  const maxV = Math.max(1, ...trend.map((t) => Math.max(t.p50, t.p85))) * 1.15
  const n = trend.length
  const x = (i: number) => (n <= 1 ? padL + innerW / 2 : padL + (i / (n - 1)) * innerW)
  const y = (v: number) => padT + innerH - (v / maxV) * innerH
  const line = (key: 'p50' | 'p85') =>
    trend.map((t, i) => `${i === 0 ? 'M' : 'L'}${x(i)},${y(t[key])}`).join(' ')
  return {
    w,
    h,
    padL,
    padR,
    padT,
    padB,
    innerW,
    innerH,
    maxV,
    labels: trend.map((t) => t.label),
    p50: line('p50'),
    p85: line('p85'),
    dots: trend.map((t, i) => ({ cx: x(i), cy: y(t.p50) }))
  }
})

const trendTicks = computed(() => {
  const m = trendPath.value.maxV
  const n = 4
  const out: Array<{ v: number; y: number }> = []
  for (let i = 0; i < n; i++) {
    const v = (m / (n - 1)) * i
    out.push({ v, y: trendPath.value.padT + trendPath.value.innerH - (v / m) * trendPath.value.innerH })
  }
  return out
})

function bucketPos(d: number): number {
  const bounds = [0, 1, 2, 3, 5, 7, 14, 30]
  if (d <= 0) return 0
  for (let i = 0; i < bounds.length - 1; i++) {
    if (d <= bounds[i + 1]) return i + (d - bounds[i]) / (bounds[i + 1] - bounds[i])
  }
  return bounds.length
}

const distChart = computed(() => {
  const dist = center.value?.dist || []
  const w = 560
  const h = 190
  const padL = 38
  const padR = 14
  const padT = 14
  const padB = 28
  const innerW = w - padL - padR
  const innerH = h - padT - padB
  const maxCount = Math.max(1, ...dist.map((d) => d.count))
  const bw = innerW / Math.max(dist.length, 1)
  const bars = dist.map((d, i) => ({
    x: padL + i * bw + bw * 0.18,
    y: padT + innerH - (d.count / maxCount) * innerH,
    w: bw * 0.64,
    h: (d.count / maxCount) * innerH,
    label: d.label,
    count: d.count
  }))
  const marks = (center.value?.quartiles || []).map((q) => ({
    label: q.label,
    x: padL + (bucketPos(q.value) / 8) * innerW,
    text: `${q.label} ${Math.round(q.value * 10) / 10}d`
  }))
  return { w, h, padL, padR, padT, padB, innerW, innerH, bars, marks, maxCount }
})

// ===================== AI 团队洞察 =====================
const aiOpen = ref(false)
const aiInsight = ref('')
const aiLoading = ref(false)

async function genInsight() {
  const c = center.value
  if (!c || aiLoading.value) return
  aiOpen.value = true
  aiInsight.value = ''
  aiLoading.value = true
  try {
    const lines = [
      '请用中文分析以下团队任务生命周期数据，输出结构化解读。',
      '',
      '【排版规范（严格遵守）】',
      '- 严禁使用任何 # 井号标题（不要 # ## ###）',
      '- 严禁 *** 三星、** 嵌套、* 单星，标题与强调只用一层 **',
      '- 小节标题用 emoji + 加粗，例如：📊 **总评**、⚠️ **问题**、💡 **建议**',
      '- 重点数字加粗，例如 **97** 分、停滞 **88** 天',
      '',
      '【结构】',
      '1. 📊 **总评**：一句话总评团队交付节奏',
      '2. ⚠️ **问题**：最多 2 个，每条一行（数据佐证放在句中）',
      '3. 💡 **建议**：2 条具体可执行建议',
      '4. 一句话判断整体完成预估是否健康',
      '全篇控制在 220 字以内。',
      '',
      `任务总数 ${c.total}（完成 ${c.done} / 进行中 ${c.doing} / 待办 ${c.todo}）`,
      `周期分位 P25=${c.p25.toFixed(1)}d P50=${c.p50.toFixed(1)}d P75=${c.p75.toFixed(1)}d P85=${c.p85.toFixed(1)}d`,
      `流动效率 ${c.flowEff}% · 按时完成率 ${c.onTimeRate}% · 停滞任务 ${c.stuckCount} · 返工 ${c.revertCount} 次`,
      '项目分布：',
      ...c.projects.map(
        (p) => `- ${p.projectName}（${p.total} 任务，完成 ${p.done}，P50=${p.p50.toFixed(1)}d，停滞 ${p.stuck}）`
      ),
      '周期最长 Top5：',
      ...c.tasks
        .slice(0, 5)
        .map((t) => `- ${t.title}（${daysText(t.cycleDays)}，${statusLabel(t.status)}，${t.assignee || '未分配'}）`)
    ]
    await aiApi.chatStream(
      { projectId: filterProjectId.value || 0, messages: [{ role: 'user', content: lines.join('\n') }], type: 'insight' },
      (delta) => {
        aiInsight.value += delta
      }
    )
    toast.success('AI 团队洞察已生成')
  } catch (e: any) {
    aiInsight.value = `AI 解读失败：${e?.message || '未知错误'}`
    toast.error(`AI 解读失败：${e?.message || '请稍后重试'}`)
  } finally {
    aiLoading.value = false
  }
}

// ===================== P2-1 单任务 AI 根因分析 =====================
const rootCauseOpen = ref(false)
const rootCauseTask = ref<TaskLc | null>(null)
const rootCauseText = ref('')
const rootCauseLoading = ref(false)

async function openRootCause(t: TaskLc) {
  rootCauseTask.value = t
  rootCauseText.value = ''
  rootCauseOpen.value = true
  rootCauseLoading.value = true
  try {
    const phasesDesc = t.phases.map((p) => `${statusLabel(p.status)} ${daysText(p.days)}`).join(' → ')
    const lines = [
      '请对这个任务做"根因分析"（中文，结构化解读，180 字内）。',
      '',
      '【排版规范（严格遵守）】',
      '- 严禁使用任何 # 井号标题',
      '- 严禁 *** 三星、* 单星，最多一层 ** 加粗',
      '- 小节用 emoji + 加粗：🎯 **主要问题**、🔍 **根因**、🛠 **改进建议**',
      '- 关键数字加粗，例如停滞 **88** 天、返工 **5** 次',
      '',
      '【结构】',
      '1. 🎯 **主要问题**：一句话概括',
      '2. 🔍 **根因**：2-3 条，用生命周期数据佐证',
      '3. 🛠 **改进建议**：2 条具体可执行建议',
      '',
      `任务：${t.title}`,
      `项目：${t.projectName}（当前状态：${statusLabel(t.status)}）`,
      `负责人：${t.assignee || '未分配'}`,
      `总周期：${daysText(t.cycleDays)}，阶段分段：${phasesDesc}`,
      `状态切换 ${t.switchCount} 次 · 返工 ${t.revertCount} 次 · 流动效率 ${t.flowEff}% · 节奏评分 ${t.score}（${t.grade}）`,
      ...(t.overdue ? ['到期状态：已逾期'] : [])
    ]
    await aiApi.chatStream(
      {
        projectId: t.projectId || filterProjectId.value || 0,
        messages: [{ role: 'user', content: lines.join('\n') }],
        type: 'rootcause'
      },
      (delta) => {
        rootCauseText.value += delta
      }
    )
    toast.success('AI 根因分析已生成')
  } catch (e: any) {
    rootCauseText.value = `根因分析失败：${e?.message || '未知错误'}`
    toast.error('根因分析失败，请稍后重试')
  } finally {
    rootCauseLoading.value = false
  }
}

// ===================== P2-2 导出周报（Markdown） =====================
const fmtNum = (v: number) => (v ? Math.round(v * 10) / 10 : '–')
function exportWeekly() {
  const c = center.value
  if (!c) {
    toast.warning('暂无数据，无法导出周报')
    return
  }
  const now = new Date()
  const md = String(now.getMonth() + 1).padStart(2, '0')
  const dd = String(now.getDate()).padStart(2, '0')
  const lines: string[] = []
  lines.push(`# 生命周期周报（${now.getFullYear()}-${md}-${dd}）`)
  lines.push('')
  lines.push(`> 任务总数 **${c.total}**（完成 ${c.done} / 进行中 ${c.doing} / 待办 ${c.todo}）`)
  lines.push(`> 周期分位 P25 ${fmtNum(c.p25)}d · P50 ${fmtNum(c.p50)}d · P75 ${fmtNum(c.p75)}d · P85 ${fmtNum(c.p85)}d`)
  lines.push(`> 流动效率 **${c.flowEff}%** · 按时完成率 **${c.onTimeRate}%** · 停滞任务 ${c.stuckCount} · 返工 ${c.revertCount} 次`)
  lines.push('')
  lines.push('## 项目概览')
  lines.push('')
  lines.push('| 项目 | 任务数 | 完成 | P50 | P85 | 流动效率 | 按时率 | 停滞 |')
  lines.push('| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |')
  for (const p of c.projects) {
    lines.push(`| ${p.projectName} | ${p.total} | ${p.done} | ${fmtNum(p.p50)} | ${fmtNum(p.p85)} | ${p.flowEff}% | ${p.onTimeRate}% | ${p.stuck} |`)
  }
  lines.push('')
  lines.push('## 成员效能')
  lines.push('')
  lines.push('| 成员 | 任务 | 完成 | P50 | 流动效率 | 按时率 | 停滞 | 返工 |')
  lines.push('| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |')
  for (const mm of members.value) {
    lines.push(`| ${mm.member} | ${mm.taskCount} | ${mm.doneCount} | ${fmtNum(mm.p50)} | ${mm.flowEff}% | ${mm.onTimeRate}% | ${mm.stuckCount} | ${mm.revertCount} |`)
  }
  lines.push('')
  if (dueAlerts.value.length) {
    lines.push('## 到期预警')
    lines.push('')
    for (const t of dueAlerts.value) {
      const due = new Date(t.dueTs)
      lines.push(`- [${t.dueTs < Date.now() ? '已逾期' : '即将到期'}] ${t.title}（${t.projectName}，${t.assignee || '未分配'}，到期 ${due.getMonth() + 1}/${due.getDate()}）`)
    }
    lines.push('')
  }
  lines.push('## 重点任务（周期最长 Top10）')
  lines.push('')
  for (const t of c.tasks.slice(0, 10)) {
    lines.push(`- ${t.title}（${daysText(t.cycleDays)}，${statusLabel(t.status)}，${t.assignee || '未分配'}，评分 ${t.score} ${t.grade}）`)
  }
  lines.push('')
  const blob = new Blob([lines.join('\n')], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const filename = `生命周期周报-${now.getFullYear()}${md}${dd}.md`
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
  toast.success(`已导出周报：${filename}`)
}

// ===================== P2-3 历史快照 =====================
interface Snapshot {
  time: number
  label: string
  total: number
  done: number
  p50: number
  flowEff: number
  onTimeRate: number
  stuckCount: number
}
const SNAP_KEY = 'dtc_lifecycle_snapshots'
const MAX_SNAPS = 30
function loadSnaps(): Snapshot[] {
  try {
    const raw = localStorage.getItem(SNAP_KEY)
    if (!raw) return []
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? (arr as Snapshot[]) : []
  } catch {
    return []
  }
}
const snaps = ref<Snapshot[]>(loadSnaps())
const snapOpen = ref(false)

function saveSnap() {
  const c = center.value
  if (!c) {
    toast.warning('暂无数据，请等待加载完成')
    return
  }
  const now = new Date()
  const label = `${now.getMonth() + 1}/${now.getDate()}`
  const snap: Snapshot = {
    time: Date.now(),
    label,
    total: c.total,
    done: c.done,
    p50: Math.round(c.p50 * 10) / 10,
    flowEff: c.flowEff,
    onTimeRate: c.onTimeRate,
    stuckCount: c.stuckCount
  }
  const arr = loadSnaps()
  const sameDay = arr.findIndex((s) => s.label === label)
  const existed = sameDay >= 0
  if (sameDay >= 0) arr[sameDay] = snap
  else arr.push(snap)
  const sorted = arr.sort((a, b) => a.time - b.time).slice(-MAX_SNAPS)
  snaps.value = sorted
  try {
    localStorage.setItem(SNAP_KEY, JSON.stringify(sorted))
  } catch {}
  toast.success(existed ? `已覆盖 ${label} 当日快照（共 ${sorted.length} 份）` : `已记录 ${label} 快照（共 ${sorted.length} 份）`)
}

function clearSnaps() {
  const had = snaps.value.length
  snaps.value = []
  try {
    localStorage.removeItem(SNAP_KEY)
  } catch {}
  if (had > 0) toast.success(`已清空 ${had} 份历史快照`)
}

const snapTrend = computed(() => {
  const list = snaps.value
  const w = 560
  const h = 170
  const padL = 34
  const padR = 10
  const padT = 14
  const padB = 26
  const innerW = w - padL - padR
  const innerH = h - padT - padB
  if (list.length < 2) return null
  const series = [
    { key: 'p50' as const, color: '#1769e0', label: 'P50 周期(d)' },
    { key: 'flowEff' as const, color: '#0d9f5f', label: '流动效率(%)' },
    { key: 'onTimeRate' as const, color: '#d97706', label: '按时率(%)' }
  ]
  const n = list.length
  const x = (i: number) => (n <= 1 ? padL + innerW / 2 : padL + (i / (n - 1)) * innerW)
  const paths = series.map((s) => {
    const maxV = Math.max(1, ...list.map((e) => e[s.key])) * 1.15
    const y = (v: number) => padT + innerH - (v / maxV) * innerH
    return {
      ...s,
      maxV,
      d: list.map((e, i) => `${i === 0 ? 'M' : 'L'}${x(i)},${y(e[s.key])}`).join(' '),
      dots: list.map((e, i) => ({ cx: x(i), cy: y(e[s.key]) }))
    }
  })
  return { w, h, padL, padR, padT, padB, paths, labels: list.map((e) => e.label) }
})
</script>

<template>
  <div class="page lc">
    <div class="toolbar">
      <n-select v-model:value="filterProjectId" :options="projectOptions" size="small" class="sel" />
      <n-select v-model:value="filterRange" :options="rangeOptions" size="small" class="sel" />
      <n-select v-model:value="filterStatus" :options="statusOptions" size="small" class="sel" />
      <n-select v-model:value="sortBy" :options="sortOptions" size="small" class="sel" />
      <span class="spacer" />
      <span class="muted small">共 {{ center?.total ?? 0 }} 个任务</span>
      <button class="btnDark btn-refresh" type="button" :disabled="loading" @click="refresh">刷新</button>
      <button class="btnDark btn-snap" type="button" :disabled="!center" @click="saveSnap">记录快照</button>
      <button class="btnDark btn-history" type="button" :disabled="!center" @click="snapOpen = true">历史快照</button>
      <button class="btnDark btn-export" type="button" :disabled="!center" @click="exportWeekly">导出周报</button>
      <button class="btnDark btn-ai" type="button" :disabled="aiLoading || !center" @click="genInsight">
        {{ aiLoading ? '分析中…' : 'AI 团队洞察' }}
      </button>
    </div>

    <n-spin :show="loading">
      <template v-if="center">
        <!-- KPI -->
        <div class="kpis">
          <div class="panel kpi">
            <div class="kpiValue">{{ center.p50 ? Math.round(center.p50 * 10) / 10 : '–' }}<span class="unit">天</span></div>
            <div class="kpiLabel muted">P50</div>
          </div>
          <div class="panel kpi">
            <div class="kpiValue">{{ center.flowEff }}<span class="unit">%</span></div>
            <div class="kpiLabel muted">流动效率</div>
          </div>
          <div class="panel kpi">
            <div class="kpiValue">{{ center.onTimeRate }}<span class="unit">%</span></div>
            <div class="kpiLabel muted">按时完成率</div>
          </div>
          <div class="panel kpi" :class="{ risk: center.stuckCount > 0 }">
            <div class="kpiValue">{{ center.stuckCount }}</div>
            <div class="kpiLabel muted">停滞任务</div>
          </div>
          <div class="panel kpi">
            <div class="kpiValue">{{ center.revertCount }}</div>
            <div class="kpiLabel muted">返工</div>
          </div>
        </div>

        <!-- 到期预警 -->
        <div v-if="dueAlerts.length" class="alertBar">
          <span class="alertTitle">到期预警</span>
          <span
            v-for="t in dueAlerts.slice(0, 6)"
            :key="t.taskId"
            class="alertChip"
            :class="{ overdue: t.dueTs < Date.now() }"
            @click="openTask(t)"
          >
            <i class="aDot" />
            <span class="aTitle">{{ t.title }}</span>
            <span class="aDue">{{ t.dueTs < Date.now() ? `已逾期 ${daysText(Math.ceil((Date.now() - t.dueTs) / 86400000))}` : `剩 ${daysText(Math.ceil((t.dueTs - Date.now()) / 86400000))}` }}</span>
          </span>
          <span v-if="dueAlerts.length > 6" class="alertMore muted">+{{ dueAlerts.length - 6 }} 条</span>
        </div>

        <!-- 图表 -->
        <div class="charts">
          <div class="panel chartCard">
            <div class="chartHead">
              <span class="chartTitle">周期趋势</span>
            </div>
            <svg :viewBox="`0 0 ${trendPath.w} ${trendPath.h}`" preserveAspectRatio="none" class="chart">
              <g v-for="t in trendTicks" :key="t.y">
                <line :x1="trendPath.padL" :x2="trendPath.w - trendPath.padR" :y1="t.y" :y2="t.y" class="grid" />
                <text :x="trendPath.padL - 6" :y="t.y + 4" text-anchor="end" class="tick">{{ Math.round(t.v * 10) / 10 }}</text>
              </g>
              <path :d="trendPath.p85" class="line p85" />
              <path :d="trendPath.p50" class="line p50" />
              <circle v-for="(d, i) in trendPath.dots" :key="i" :cx="d.cx" :cy="d.cy" r="3" class="dot" />
              <text
                v-for="(lb, i) in trendPath.labels"
                :key="'l' + i"
                :x="trendPath.padL + (i / Math.max(trendPath.labels.length - 1, 1)) * trendPath.innerW"
                :y="trendPath.h - 8"
                text-anchor="middle"
                class="tick xlabel"
              >{{ lb }}</text>
              <text :x="trendPath.w - trendPath.padR" :y="trendPath.padT + 4" text-anchor="end" class="legend p50">P50</text>
              <text :x="trendPath.w - trendPath.padR" :y="trendPath.padT + 18" text-anchor="end" class="legend p85">P85</text>
            </svg>
          </div>

          <div class="panel chartCard">
            <div class="chartHead">
              <span class="chartTitle">周期分布</span>
            </div>
            <svg :viewBox="`0 0 ${distChart.w} ${distChart.h}`" preserveAspectRatio="none" class="chart">
              <g v-for="t in distChart.bars" :key="t.label">
                <rect :x="t.x" :y="t.y" :width="t.w" :height="t.h" rx="3" class="bar" />
                <text :x="t.x + t.w / 2" :y="distChart.h - 8" text-anchor="middle" class="tick xlabel">{{ t.label }}</text>
                <text v-if="t.count" :x="t.x + t.w / 2" :y="t.y - 4" text-anchor="middle" class="count">{{ t.count }}</text>
              </g>
              <g v-for="m in distChart.marks" :key="m.label">
                <line :x1="m.x" :x2="m.x" :y1="distChart.padT" :y2="distChart.h - distChart.padB" class="qline" />
                <text :x="m.x" :y="distChart.padT - 4" text-anchor="middle" class="qtext">{{ m.text }}</text>
              </g>
            </svg>
          </div>
        </div>

        <!-- 成员效能 -->
        <div class="panel memberPanel">
          <div class="chartHead">
            <span class="chartTitle">成员效能</span>
            <span class="muted small">按负责人统计：任务 / 完成 / P50 / 流动效率 / 按时率</span>
          </div>
          <div class="memberWrap">
            <table class="memberTable">
              <thead>
                <tr>
                  <th>成员</th>
                  <th>任务</th>
                  <th>完成</th>
                  <th>进行中</th>
                  <th>P50</th>
                  <th>流动效率</th>
                  <th>按时率</th>
                  <th>停滞</th>
                  <th>返工</th>
                  <th>平均分</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="mm in members" :key="mm.member">
                  <td class="mName">{{ mm.member }}</td>
                  <td>{{ mm.taskCount }}</td>
                  <td class="done">{{ mm.doneCount }}</td>
                  <td>{{ mm.doingCount }}</td>
                  <td>{{ mm.p50 ? Math.round(mm.p50 * 10) / 10 : '–' }}d</td>
                  <td>{{ mm.flowEff }}%</td>
                  <td :class="{ risk: mm.onTimeRate < 60 }">{{ mm.onTimeRate }}%</td>
                  <td :class="{ risk: mm.stuckCount > 0 }">{{ mm.stuckCount }}</td>
                  <td>{{ mm.revertCount }}</td>
                  <td>{{ mm.avgScore }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- 任务清单 -->
        <div class="tasks">
          <div v-for="proj in center.projects" :key="proj.projectId" class="panel taskGroup">
            <div class="groupHead">
              <span class="gName">{{ proj.projectName }}</span>
              <span class="muted small">共 {{ proj.total }} 任务 · 完成 {{ proj.done }}</span>
              <span class="gTrack">
                <span class="gFill" :style="{ width: proj.total ? Math.round((proj.done / proj.total) * 100) + '%' : '0%' }" />
              </span>
              <span class="muted small gP50">P50 {{ proj.p50 ? Math.round(proj.p50 * 10) / 10 : '–' }}d</span>
              <span v-if="proj.stuck" class="gRisk">停滞 {{ proj.stuck }}</span>
            </div>
            <div
              v-for="t in tasksByProject.get(proj.projectId) || []"
              :key="t.taskId"
              class="taskRow"
              @click="openTask(t)"
            >
              <div class="rowMain">
                <span class="chip" :class="t.status.toLowerCase()">{{ statusLabel(t.status) }}</span>
                <span class="title" :title="t.title">{{ t.title }}</span>
                <span v-if="t.priority && t.priority !== 'MEDIUM'" class="prio" :class="t.priority.toLowerCase()">{{ t.priority }}</span>
                <span class="who muted" :title="t.assignee">{{ t.assignee || '未分配' }}</span>
                <span class="metric" :title="`生命周期 ${daysText(t.cycleDays)}`">{{ daysText(t.cycleDays) }}</span>
                <span class="metric muted" :title="`状态切换 ${t.switchCount} 次`">切 {{ t.switchCount }}</span>
                <span class="metric muted" :title="`流动效率 ${t.flowEff}%`">效 {{ t.flowEff }}%</span>
                <span class="score" :style="{ color: t.color }">{{ t.score }} {{ t.grade }}</span>
                <span v-if="t.flag" class="risk" :class="t.flag" :title="t.flag === 'danger' ? '停滞风险' : '周期偏慢'">
                  {{ t.flag === 'danger' ? '停滞' : '偏慢' }}
                </span>
                <button class="btnCause" type="button" title="AI 根因分析" @click.stop="openRootCause(t)">根因</button>
              </div>
              <div class="band">
                <span
                  v-for="(p, i) in t.phases"
                  :key="i"
                  :class="p.status.toLowerCase()"
                  :style="{ width: t.cycleDays ? Math.max((p.days / t.cycleDays) * 100, 2) + '%' : '0%' }"
                  :title="`${statusLabel(p.status)} ${daysText(p.days)}`"
                />
              </div>
            </div>
          </div>
          <div v-if="!center.projects.length" class="panel empty">当前筛选条件下暂无任务数据</div>
        </div>
      </template>

      <div v-else-if="!loading" class="panel empty">
        暂无数据，请点击「刷新」加载
      </div>
    </n-spin>

    <!-- AI 洞察 -->
    <n-drawer v-model:show="aiOpen" :width="440" placement="right">
      <n-drawer-content title="AI 团队洞察" closable>
        <div v-if="aiInsight" class="aiBody">
          <MarkdownView :content="aiInsight" />
        </div>
        <div v-else class="aiLoading">正在生成洞察…</div>
        <div v-if="aiInsight" class="aiActions">
          <AiToTaskButton :project-id="filterProjectId || null" :content="aiInsight" />
        </div>
      </n-drawer-content>
    </n-drawer>

    <!-- AI 根因分析 -->
    <n-drawer v-model:show="rootCauseOpen" :width="440" placement="right">
      <n-drawer-content :title="rootCauseTask ? `AI 根因：${rootCauseTask.title}` : 'AI 根因'" closable>
        <div v-if="rootCauseTask" class="rcMeta muted small">
          {{ rootCauseTask.projectName }} · {{ statusLabel(rootCauseTask.status) }} · 周期 {{ daysText(rootCauseTask.cycleDays) }} · 评分 {{ rootCauseTask.score }} {{ rootCauseTask.grade }}
        </div>
        <div v-if="rootCauseText" class="aiBody">
          <MarkdownView :content="rootCauseText" />
        </div>
        <div v-else class="aiLoading">正在分析任务根因…</div>
        <div v-if="rootCauseText" class="aiActions">
          <AiToTaskButton :project-id="rootCauseTask?.projectId || filterProjectId || null" :content="rootCauseText" />
        </div>
      </n-drawer-content>
    </n-drawer>

    <!-- 历史快照 -->
    <n-drawer v-model:show="snapOpen" :width="620" placement="right">
      <n-drawer-content title="历史快照" closable>
        <div class="snapHead">
          <span class="muted small">每日记录关键指标（同日覆盖），最多保留 30 条</span>
          <span class="spacer" />
          <button class="btnDark small" type="button" :disabled="!center" @click="saveSnap">记录今天</button>
          <button v-if="snaps.length" class="btnDark small" type="button" @click="clearSnaps">清空</button>
        </div>
        <div v-if="snapTrend" class="snapChartWrap">
          <svg :viewBox="`0 0 ${snapTrend.w} ${snapTrend.h}`" preserveAspectRatio="none" class="chart">
            <path v-for="t in snapTrend.paths" :key="t.key" :d="t.d" :stroke="t.color" fill="none" stroke-width="2" />
            <template v-for="p in snapTrend.paths" :key="'d' + p.key">
              <circle v-for="(d, di) in p.dots" :key="'dt' + di" :cx="d.cx" :cy="d.cy" r="3" :fill="p.color" />
            </template>
            <text
              v-for="(lb, i) in snapTrend.labels"
              :key="'l' + i"
              :x="snapTrend.padL + (i / Math.max(snapTrend.labels.length - 1, 1)) * snapTrend.innerW"
              :y="snapTrend.h - 8"
              text-anchor="middle"
              class="tick xlabel"
            >{{ lb }}</text>
            <text
              v-for="t in snapTrend.paths"
              :key="'lg' + t.key"
              :x="snapTrend.w - snapTrend.padR"
              :y="snapTrend.padT + 4 + 14 * snapTrend.paths.indexOf(t)"
              text-anchor="end"
              :fill="t.color"
              class="legend"
            >{{ t.label }}</text>
          </svg>
        </div>
        <div v-else class="snapEmpty">暂无快照，点击「记录今天」保存第一份</div>
        <table v-if="snaps.length" class="memberTable snapTable">
          <thead>
            <tr>
              <th>日期</th>
              <th>任务</th>
              <th>完成</th>
              <th>P50</th>
              <th>流动效率</th>
              <th>按时率</th>
              <th>停滞</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in snaps.slice().reverse()" :key="s.time">
              <td>{{ s.label }}</td>
              <td>{{ s.total }}</td>
              <td>{{ s.done }}</td>
              <td>{{ s.p50 }}</td>
              <td>{{ s.flowEff }}%</td>
              <td>{{ s.onTimeRate }}%</td>
              <td :class="{ risk: s.stuckCount > 0 }">{{ s.stuckCount }}</td>
            </tr>
          </tbody>
        </table>
      </n-drawer-content>
    </n-drawer>

    <!-- 全局 toast 浮层（操作反馈） -->
    <transition-group name="toast" tag="div" class="toastStack">
      <div
        v-for="t in toast.list.value"
        :key="t.id"
        class="toastItem"
        :class="`toast-${t.type}`"
        role="status"
      >
        <span class="toastIcon" aria-hidden="true">
          <template v-if="t.type === 'success'">✓</template>
          <template v-else-if="t.type === 'error'">✕</template>
          <template v-else-if="t.type === 'warning'">!</template>
          <template v-else>i</template>
        </span>
        <span class="toastText">{{ t.text }}</span>
      </div>
    </transition-group>
  </div>
</template>

<style scoped>
.page {
  max-width: 1280px;
}

/* ============== 工具栏按钮（柔和色系，不再全黑） ============== */
.btnDark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 7px 14px;
  border-radius: 8px;
  background: #f3f4f6;
  color: #374151;
  border: 1px solid #e5e7eb;
  font-size: 12.5px;
  font-weight: 600;
  letter-spacing: -0.1px;
  white-space: nowrap;
  cursor: pointer;
  transition: background 140ms ease, transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
}

.btnDark:hover {
  background: #e5e7eb;
  border-color: #d1d5db;
  transform: translateY(-1px);
  box-shadow: 0 6px 14px rgba(15, 23, 42, 0.08);
}

.btnDark:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

/* 5 个操作按钮：4 个灰底（与刷新一致）+ AI 洞察深色 */
.btnDark.btn-ai {
  background: #1f2329;
  color: #fff;
  border-color: #1f2329;
}
.btnDark.btn-ai:hover { background: #111827; border-color: #111827; }
.btnDark.btn-ai:disabled { background: #4b5563; border-color: #4b5563; color: #fff; opacity: 0.7; }

.btnDark.btn-refresh:disabled { background: #f3f4f6; color: #9ca3af; }
.btnDark.btn-snap:disabled,
.btnDark.btn-history:disabled,
.btnDark.btn-export:disabled { background: #f3f4f6; color: #9ca3af; }

.btnDark.small {
  padding: 6px 10px;
  border-radius: 7px;
  font-size: 12px;
  flex: none;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.sel {
  width: 150px;
}

.spacer {
  flex: 1;
}

.kpis {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.kpi {
  padding: 14px 16px;
}

.kpiValue {
  font-size: 24px;
  font-weight: 800;
  color: rgba(15, 23, 42, 0.9);
  line-height: 1.1;
}

.kpiValue .unit {
  font-size: 12px;
  color: rgba(15, 23, 42, 0.4);
  margin-left: 2px;
  font-weight: 600;
}

.kpiLabel {
  font-size: 12px;
  margin-top: 6px;
}

.kpi.risk .kpiValue {
  color: #dc2626;
}

.charts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 14px;
}

.chartCard {
  padding: 14px 16px 10px;
}

.chartHead {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 6px;
}

.chartTitle {
  font-size: 14px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.85);
}

.chart {
  width: 100%;
  display: block;
}

.grid {
  stroke: rgba(15, 23, 42, 0.08);
  stroke-width: 1;
}

.tick {
  fill: rgba(15, 23, 42, 0.45);
  font-size: 10px;
  font-weight: 600;
}

.xlabel {
  font-size: 9.5px;
}

.legend {
  font-size: 10px;
  font-weight: 700;
}

.legend.p50 {
  fill: #1769e0;
}

.legend.p85 {
  fill: #d97706;
}

.line {
  fill: none;
  stroke-width: 2;
}

.line.p50 {
  stroke: #1769e0;
}

.line.p85 {
  stroke: #d97706;
  stroke-width: 1.6;
  stroke-dasharray: 5 4;
}

.dot {
  fill: #1769e0;
}

.bar {
  fill: #c9d4e8;
}

.count {
  fill: rgba(15, 23, 42, 0.55);
  font-size: 10px;
  font-weight: 700;
}

.qline {
  stroke: #ef4444;
  stroke-width: 1;
  stroke-dasharray: 3 3;
}

.qtext {
  fill: #dc2626;
  font-size: 9.5px;
  font-weight: 700;
}

.tasks {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.taskGroup {
  padding: 12px 14px 6px;
}

.groupHead {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.gName {
  font-size: 14px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.9);
}

.gTrack {
  flex: 1;
  height: 6px;
  border-radius: 3px;
  background: rgba(15, 23, 42, 0.06);
  overflow: hidden;
  max-width: 200px;
}

.gFill {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: #1769e0;
  opacity: 0.85;
}

.gP50 {
  white-space: nowrap;
}

.gRisk {
  font-size: 11px;
  font-weight: 700;
  color: #dc2626;
  background: rgba(239, 68, 68, 0.08);
  border-radius: 5px;
  padding: 1px 7px;
  white-space: nowrap;
}

.taskRow {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 9px 8px;
  border-top: 1px solid rgba(15, 23, 42, 0.05);
  cursor: pointer;
  transition: background 0.12s ease;
}

.taskRow:hover {
  background: rgba(23, 105, 224, 0.04);
}

.rowMain {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.chip {
  flex: none;
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.chip.todo {
  background: #eef0f3;
  color: #6b7280;
}

.chip.doing {
  background: #eaf1fb;
  color: #1769e0;
}

.chip.done {
  background: #e8f5ee;
  color: #0d9f5f;
}

.title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
  color: rgba(15, 23, 42, 0.88);
}

.prio {
  flex: none;
  font-size: 10px;
  font-weight: 700;
  color: #d97706;
  background: rgba(217, 119, 6, 0.1);
  border-radius: 4px;
  padding: 0 5px;
}

.prio.low {
  color: #6b7280;
  background: rgba(107, 114, 128, 0.12);
}

.who {
  flex: none;
  max-width: 90px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}

.metric {
  flex: none;
  font-size: 12px;
  color: rgba(15, 23, 42, 0.75);
  min-width: 52px;
  text-align: right;
  white-space: nowrap;
}

.metric.muted {
  color: rgba(15, 23, 42, 0.45);
}

.score {
  flex: none;
  min-width: 46px;
  text-align: right;
  font-size: 12.5px;
  font-weight: 800;
  white-space: nowrap;
}

.risk {
  flex: none;
  font-size: 11px;
  font-weight: 700;
  border-radius: 5px;
  padding: 1px 7px;
  white-space: nowrap;
}

.risk.warn {
  color: #b45309;
  background: rgba(245, 158, 11, 0.14);
}

.risk.danger {
  color: #dc2626;
  background: rgba(239, 68, 68, 0.1);
}

.band {
  display: flex;
  height: 8px;
  border-radius: 4px;
  overflow: hidden;
  background: rgba(15, 23, 42, 0.04);
  border: 1px solid rgba(15, 23, 42, 0.05);
}

.band span {
  display: block;
  height: 100%;
}

.band .todo {
  background: #c7ccd4;
}

.band .doing {
  background: #f5c06b;
}

.band .done {
  background: #6cc99a;
}

.empty {
  padding: 40px 16px;
  text-align: center;
  color: rgba(15, 23, 42, 0.45);
  font-size: 13px;
}

.aiBody {
  font-size: 13px;
  line-height: 1.75;
  color: rgba(15, 23, 42, 0.85);
  white-space: pre-wrap;
}

.aiLoading {
  color: rgba(15, 23, 42, 0.45);

  font-size: 13px;
}

.aiActions {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed rgba(15, 23, 42, 0.12);
  display: flex;
  justify-content: flex-end;
}

/* ===== 到期预警 ===== */
.alertBar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
  padding: 10px 14px;
  border-radius: 10px;
  background: #fffdf5;
  border: 1px solid rgba(245, 158, 11, 0.35);
}

.alertTitle {
  font-size: 12.5px;
  font-weight: 700;
  color: #b45309;
  margin-right: 2px;
}

.alertChip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.1);
  font-size: 12px;
  color: rgba(15, 23, 42, 0.8);
  cursor: pointer;
  transition: border-color 0.12s ease, box-shadow 0.12s ease;
}

.alertChip:hover {
  border-color: rgba(23, 105, 224, 0.5);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
}

.alertChip.overdue {
  background: #fef2f2;
  border-color: rgba(239, 68, 68, 0.4);
}

.alertChip.overdue .aDot {
  background: #dc2626;
}

.aDot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #d97706;
  flex: none;
}

.aTitle {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}

.aDue {
  font-size: 11px;
  color: rgba(15, 23, 42, 0.45);
  white-space: nowrap;
}

.alertMore {
  font-size: 12px;
}

/* ===== 成员效能 ===== */
.memberPanel {
  padding: 14px 16px;
  margin-bottom: 14px;
}

.memberWrap {
  overflow-x: auto;
}

.memberTable {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
  white-space: nowrap;
}

.memberTable th {
  text-align: left;
  padding: 6px 12px 6px 0;
  font-size: 11.5px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.45);
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
}

.memberTable td {
  padding: 8px 12px 8px 0;
  color: rgba(15, 23, 42, 0.8);
  border-bottom: 1px solid rgba(15, 23, 42, 0.05);
}

.memberTable tbody tr:last-child td {
  border-bottom: 0;
}

.memberTable .mName {
  font-weight: 700;
  color: rgba(15, 23, 42, 0.9);
}

.memberTable .done {
  color: #0d9f5f;
  font-weight: 700;
}

.memberTable td.risk {
  color: #dc2626;
  font-weight: 700;
}

/* ===== 任务行根因按钮 ===== */
.btnCause {
  flex: none;
  border: 1px solid rgba(15, 23, 42, 0.14);
  background: #fff;
  color: rgba(15, 23, 42, 0.65);
  font-size: 11px;
  font-weight: 600;
  border-radius: 5px;
  padding: 1px 7px;
  cursor: pointer;
  transition: all 0.12s ease;
}

.btnCause:hover {
  border-color: rgba(23, 105, 224, 0.5);
  color: #1769e0;
}

/* ===== AI 根因抽屉 ===== */
.rcMeta {
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px dashed rgba(15, 23, 42, 0.1);
}

/* ===== 历史快照 ===== */
.snapHead {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.btnGhost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 6px 10px;
  border-radius: 7px;
  background: transparent;
  color: rgba(15, 23, 42, 0.65);
  border: 1px solid rgba(15, 23, 42, 0.16);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.12s ease;
}

.btnGhost:hover {
  border-color: rgba(239, 68, 68, 0.5);
  color: #dc2626;
}

.snapChartWrap {
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 10px;
  padding: 8px 6px 0;
  margin-bottom: 12px;
}

.snapEmpty {
  padding: 26px 12px;
  text-align: center;
  color: rgba(15, 23, 42, 0.45);
  font-size: 12.5px;
  border: 1px dashed rgba(15, 23, 42, 0.16);
  border-radius: 10px;
  margin-bottom: 12px;
}

.snapTable th:first-child,
.snapTable td:first-child {
  padding-left: 2px;
}

/* ============== 全局 toast 浮层 ============== */
.toastStack {
  position: fixed;
  top: 76px;
  right: 24px;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: none;
}

.toastItem {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 220px;
  max-width: 360px;
  padding: 9px 14px;
  border-radius: 10px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.10);
  font-size: 13px;
  color: #111827;
  pointer-events: auto;
}

.toastIcon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  flex: none;
}

.toast-success .toastIcon { background: #10b981; }
.toast-success { border-color: #a7f3d0; background: #ecfdf5; color: #065f46; }

.toast-error .toastIcon { background: #dc2626; }
.toast-error { border-color: #fecaca; background: #fef2f2; color: #991b1b; }

.toast-warning .toastIcon { background: #d97706; }
.toast-warning { border-color: #fde68a; background: #fffbeb; color: #92400e; }

.toast-info .toastIcon { background: #1e40af; }
.toast-info { border-color: #c7d2fe; background: #eef2ff; color: #1e3a8a; }

.toast-enter-active,
.toast-leave-active {
  transition: all 220ms cubic-bezier(0.22, 1, 0.36, 1);
}
.toast-enter-from {
  opacity: 0;
  transform: translateX(20px);
}
.toast-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

@media (max-width: 1024px) {
  .kpis {
    grid-template-columns: repeat(2, 1fr);
  }
  .charts {
    grid-template-columns: 1fr;
  }
}
</style>
