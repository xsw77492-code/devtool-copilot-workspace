<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { NButton, NDatePicker, NModal, NSelect, NSpin, useMessage } from 'naive-ui'
import { useRoute, useRouter } from 'vue-router'
import { dashboardApi, type DashboardDoneTaskItem, type DashboardOverviewResponse } from '../api/dashboard'
import LineChart from '../components/charts/LineChart.vue'
import BarChart from '../components/charts/BarChart.vue'
import PieChart from '../components/charts/PieChart.vue'
import { useProjectStore } from '../stores/project'

const message = useMessage()
const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()

const loading = ref(false)
const data = ref<DashboardOverviewResponse | null>(null)
const prevData = ref<DashboardOverviewResponse | null>(null)
const yoyData = ref<DashboardOverviewResponse | null>(null)

type RangeKey = '7d' | '30d' | 'wtd' | 'mtd' | 'custom'

const projectId = ref<number | null>(null)
const rangeKey = ref<RangeKey>('7d')
const customRange = ref<[number, number] | null>(null)
const inited = ref(false)

const projectOptions = computed(() => [
  { label: '全部项目', value: null as any },
  ...projectStore.visibleProjects.map((p) => ({ label: p.name, value: p.id }))
])

function fmtYmd(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function startOfDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate(), 0, 0, 0, 0)
}

function endOfDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate(), 23, 59, 59, 999)
}

function mondayOfWeek(d: Date): Date {
  const day = d.getDay() || 7
  const base = startOfDay(d)
  base.setDate(base.getDate() - (day - 1))
  return base
}

function firstDayOfMonth(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), 1, 0, 0, 0, 0)
}

function addDays(d: Date, deltaDays: number): Date {
  const x = new Date(d)
  x.setDate(x.getDate() + deltaDays)
  return x
}

function shiftYear(d: Date, years: number): Date {
  return new Date(
    d.getFullYear() + years,
    d.getMonth(),
    d.getDate(),
    d.getHours(),
    d.getMinutes(),
    d.getSeconds(),
    d.getMilliseconds()
  )
}

function inclusiveDays(start: Date, end: Date): number {
  const s = startOfDay(start).getTime()
  const e = startOfDay(end).getTime()
  return Math.floor((e - s) / 86400000) + 1
}

const effectiveRange = computed(() => {
  const today = new Date()
  const end = endOfDay(today)
  if (rangeKey.value === 'custom') {
    const v = customRange.value
    if (!v) return null
    return { start: startOfDay(new Date(v[0])), end: endOfDay(new Date(v[1])) }
  }
  if (rangeKey.value === '30d') {
    const start = startOfDay(new Date(today))
    start.setDate(start.getDate() - 29)
    return { start, end }
  }
  if (rangeKey.value === 'wtd') {
    return { start: mondayOfWeek(today), end }
  }
  if (rangeKey.value === 'mtd') {
    return { start: firstDayOfMonth(today), end }
  }
  const start = startOfDay(new Date(today))
  start.setDate(start.getDate() - 6)
  return { start, end }
})

const overviewParams = computed(() => {
  const r = effectiveRange.value
  return {
    projectId: projectId.value ?? undefined,
    startDate: r ? fmtYmd(r.start) : undefined,
    endDate: r ? fmtYmd(r.end) : undefined
  }
})

const prevParams = computed(() => {
  const r = effectiveRange.value
  if (!r) return null
  const len = inclusiveDays(r.start, r.end)
  const start = startOfDay(addDays(r.start, -len))
  const end = endOfDay(addDays(r.end, -len))
  return {
    projectId: projectId.value ?? undefined,
    startDate: fmtYmd(start),
    endDate: fmtYmd(end)
  }
})

const yoyParams = computed(() => {
  const r = effectiveRange.value
  if (!r) return null
  const start = startOfDay(shiftYear(r.start, -1))
  const end = endOfDay(shiftYear(r.end, -1))
  return {
    projectId: projectId.value ?? undefined,
    startDate: fmtYmd(start),
    endDate: fmtYmd(end)
  }
})

const rangeText = computed(() => {
  const r = effectiveRange.value
  if (!r) return '自定义'
  return `${fmtYmd(r.start)} ~ ${fmtYmd(r.end)}`
})

const rangeMeta = computed(() => {
  const m: Record<RangeKey, string> = {
    '7d': '近 7 天',
    '30d': '近 30 天',
    wtd: '本周',
    mtd: '本月',
    custom: '自定义'
  }
  return m[rangeKey.value]
})

const selectedProjectName = computed(() => {
  if (projectId.value == null) return '全部项目'
  return projectStore.visibleProjects.find((item) => item.id === projectId.value)?.name || `项目 #${projectId.value}`
})

function syncQuery() {
  const q: Record<string, any> = { ...route.query }
  if (projectId.value == null) delete q.projectId
  else q.projectId = String(projectId.value)
  q.range = rangeKey.value
  if (rangeKey.value === 'custom') {
    const r = effectiveRange.value
    if (r) {
      q.startDate = fmtYmd(r.start)
      q.endDate = fmtYmd(r.end)
    }
  } else {
    delete q.startDate
    delete q.endDate
  }
  router.replace({ query: q })
}

async function load() {
  loading.value = true
  try {
    prevData.value = null
    yoyData.value = null

    const curP = { ...(overviewParams.value as any), lite: false }
    const prevP = prevParams.value ? { ...(prevParams.value as any), lite: true } : null
    const yoyP = yoyParams.value ? { ...(yoyParams.value as any), lite: true } : null

    const tasks: Array<Promise<any>> = [dashboardApi.overview(curP)]
    if (prevP) tasks.push(dashboardApi.overview(prevP).catch(() => null))
    if (yoyP) tasks.push(dashboardApi.overview(yoyP).catch(() => null))

    const res = await Promise.all(tasks)
    data.value = (res[0] || null) as any
    prevData.value = (res.length >= 2 ? res[1] : null) as any
    yoyData.value = (res.length >= 3 ? res[2] : null) as any
  } catch (e: any) {
    message.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    if (!projectStore.projects.length) await projectStore.load()
  } catch (e: any) {
    message.error(e?.message || '加载项目失败')
  }

  const qProjectId = Number(route.query.projectId || 0)
  projectId.value = qProjectId && Number.isFinite(qProjectId) ? qProjectId : null

  const qRange = String(route.query.range || '') as RangeKey
  rangeKey.value = (['7d', '30d', 'wtd', 'mtd', 'custom'] as const).includes(qRange) ? qRange : '7d'

  if (rangeKey.value === 'custom') {
    const sd = String(route.query.startDate || '')
    const ed = String(route.query.endDate || '')
    if (sd && ed) {
      const s = new Date(sd)
      const e = new Date(ed)
      if (Number.isFinite(s.getTime()) && Number.isFinite(e.getTime())) customRange.value = [s.getTime(), e.getTime()]
    }
    if (!customRange.value) rangeKey.value = '7d'
  }

  inited.value = true
  syncQuery()
  await load()
})

watch([projectId, rangeKey, customRange], async () => {
  if (!inited.value) return
  if (rangeKey.value === 'custom' && !effectiveRange.value) return
  syncQuery()
  await load()
})

const doneRateText = computed(() => {
  const v = data.value?.taskDoneRate || 0
  const pct = Math.round(v * 1000) / 10
  return `${pct}%`
})

const taskLine = computed(() => {
  const pts = data.value?.taskTrend7d || []
  return pts.map((x) => ({ label: x.day.slice(5), value: Number(x.count || 0) }))
})

const aiBars = computed(() => {
  const pts = data.value?.aiTrend7d || []
  return pts.map((x) => ({ label: x.day.slice(5), value: Number(x.count || 0) }))
})

function avgValue(values: number[]): number {
  if (!values.length) return 0
  return values.reduce((sum, item) => sum + Number(item || 0), 0) / values.length
}

function statusDisplayLabel(s: string | null | undefined): string {
  const v = String(s || '').toUpperCase()
  if (v === 'TODO') return '待处理'
  if (v === 'DOING') return '进行中'
  if (v === 'DONE') return '已完成'
  return v || '-'
}

const statusPie = computed(() => {
  const dist = data.value?.taskStatusDist || []
  const colors: Record<string, string> = {
    TODO: 'rgba(15,23,42,0.18)',
    DOING: 'rgba(var(--accent2-rgb), 0.84)',
    DONE: 'rgba(var(--accent-rgb), 0.86)'
  }
  return dist.map((x) => ({
    label: x.status,
    value: Number(x.count || 0),
    color: colors[String(x.status || '').toUpperCase()] || undefined
  }))
})

function sparseLabels(labels: string[], maxCount = 7): string[] {
  if (labels.length <= maxCount) return labels
  const out: string[] = []
  for (let i = 0; i < maxCount; i += 1) {
    const idx = Math.round((i * (labels.length - 1)) / (maxCount - 1))
    out.push(labels[idx])
  }
  return out
}

const taskXLabels = computed(() => sparseLabels(taskLine.value.map((x) => x.label)))
const aiXLabels = computed(() => sparseLabels(aiBars.value.map((x) => x.label)))

const memberActivityRows = computed(() => {
  const rows = (data.value?.memberActivity7d || []).map((x) => {
    const name = String(x.username || 'User').trim() || 'User'
    return {
      userId: Number(x.userId || 0),
      name,
      short: name.length > 10 ? name.slice(0, 10) + '…' : name,
      value: Number(x.actionCount || 0)
    }
  })
  rows.sort((a, b) => b.value - a.value)
  const maxV = Math.max(1, ...rows.map((x) => Number(x.value || 0)))
  return rows.map((x) => ({
    ...x,
    width: `${Math.max(6, Math.round((x.value / maxV) * 100))}%`
  }))
})

const throughputBars = computed(() => {
  const pts = data.value?.throughputTrend || []
  return pts.map((x) => ({ label: x.day, value: Number(x.count || 0) }))
})

const throughputXLabels = computed(() => sparseLabels(throughputBars.value.map((x) => x.label.slice(5))))

const wipTotal = computed(() => Number(data.value?.wipTotal || 0))
const cycle = computed(() => data.value?.cycleTime || null)

const WIP_LIMIT = 8
const THROUGHPUT_ACTIVE_DAY_TARGET = 4
const CYCLE_TARGET_DAYS = 2
const wipHigh = computed(() => wipTotal.value > WIP_LIMIT)

function fmtDueText(v: string | null | undefined): string {
  if (!v) return '未设置'
  const d = new Date(String(v))
  if (!Number.isFinite(d.getTime())) return '未设置'
  const now = new Date()
  const a = startOfDay(d).getTime()
  const b = startOfDay(now).getTime()
  const diffDays = Math.round((a - b) / 86400000)
  if (diffDays === 0) return '今天'
  if (diffDays === 1) return '明天'
  if (diffDays === -1) return '昨天'
  if (diffDays < 0) return `逾期 ${Math.abs(diffDays)} 天`
  return `还有 ${diffDays} 天`
}

function statusText(s: string | null | undefined): string {
  const v = String(s || '').toUpperCase()
  if (v === 'TODO') return 'TODO'
  if (v === 'DOING') return 'DOING'
  if (v === 'DONE') return 'DONE'
  return v || '-'
}

function statusTone(s: string | null | undefined): 'todo' | 'doing' | 'done' | 'na' {
  const v = String(s || '').toUpperCase()
  if (v === 'TODO') return 'todo'
  if (v === 'DOING') return 'doing'
  if (v === 'DONE') return 'done'
  return 'na'
}

function dueDiffDays(v: string | null | undefined): number | null {
  if (!v) return null
  const d = new Date(String(v))
  if (!Number.isFinite(d.getTime())) return null
  const now = new Date()
  const a = startOfDay(d).getTime()
  const b = startOfDay(now).getTime()
  return Math.round((a - b) / 86400000)
}

function priorityText(v: string | null | undefined): string {
  const x = String(v || '').toUpperCase()
  if (x === 'HIGH' || x === 'P0' || x === 'P1') return '高优先级'
  if (x === 'MEDIUM' || x === 'P2') return '中优先级'
  if (x === 'LOW' || x === 'P3' || x === 'P4') return '低优先级'
  return '未分级'
}

function priorityTone(v: string | null | undefined): 'high' | 'medium' | 'low' | 'na' {
  const x = String(v || '').toUpperCase()
  if (x === 'HIGH' || x === 'P0' || x === 'P1') return 'high'
  if (x === 'MEDIUM' || x === 'P2') return 'medium'
  if (x === 'LOW' || x === 'P3' || x === 'P4') return 'low'
  return 'na'
}

function openTask(pid: number, tid: number): void {
  router.push({ name: 'task-detail', params: { projectId: pid, taskId: tid } })
}

const myActions = computed(() => data.value?.myActions || [])
const riskTasks = computed(() => data.value?.riskTasks || [])
const topDiscussedTasks = computed(() => data.value?.topDiscussedTasks || [])
const throughputTotal = computed(() => throughputBars.value.reduce((sum, item) => sum + Number(item.value || 0), 0))
const highPriorityCount = computed(() =>
  myActions.value.filter((item) => priorityTone(item.priority) === 'high').length
)
const dueSoonCount = computed(() =>
  myActions.value.filter((item) => {
    const diff = dueDiffDays(item.dueTime)
    return diff != null && diff >= 0 && diff <= 3
  }).length
)
const dashboardHeadline = computed(() => {
  if (riskTasks.value.length > 0) {
    return {
      eyebrow: '当前重点',
      title: `当前有 ${riskTasks.value.length} 项风险需要处理`,
      summary: `优先处理逾期与阻塞，再推进剩余 ${myActions.value.length} 项执行事项。`
    }
  }
  if (myActions.value.length > 0) {
    return {
      eyebrow: '当前重点',
      title: `当前有 ${myActions.value.length} 项待推进事项`,
      summary: highPriorityCount.value
        ? `其中 ${highPriorityCount.value} 项为高优先级，建议先处理关键路径。`
        : '执行队列已经收口，当前可以直接按优先级推进。'
    }
  }
  return {
    eyebrow: '当前重点',
    title: '当前运行稳定',
    summary: '没有明显阻塞，可以继续推进本期交付。'
  }
})
const overviewStats = computed(() => [
  { key: 'project', label: '项目', value: fmtNum(data.value?.projectTotal), meta: '当前口径' },
  { key: 'task', label: '任务', value: fmtNum(data.value?.taskTotal), meta: rangeMeta.value },
  { key: 'done', label: '已完成', value: fmtNum(data.value?.doneTaskTotal), meta: '本期完成' },
  { key: 'ai', label: 'AI 调用', value: fmtNum(data.value?.aiCallTotal), meta: '本期使用' }
])
const heroInsights = computed(() => [
  { key: 'priority', label: '高优先级', value: fmtNum(highPriorityCount.value) },
  { key: 'dueSoon', label: '近 3 天到期', value: fmtNum(dueSoonCount.value) },
  { key: 'throughput', label: '本期吞吐', value: fmtNum(throughputTotal.value) }
])
const signalCards = computed(() => [
  {
    key: 'risk',
    label: '风险任务',
    value: fmtNum(riskTasks.value.length),
    meta: riskTasks.value.length ? '优先处置' : '当前稳定'
  },
  {
    key: 'dueSoon',
    label: '近期到期',
    value: fmtNum(dueSoonCount.value),
    meta: dueSoonCount.value ? '建议提前处理' : '近期压力较低'
  },
  {
    key: 'wip',
    label: '当前 WIP',
    value: fmtNum(wipTotal.value),
    meta: wipHigh.value ? '超出建议阈值' : '执行压力可控'
  }
])
const visibleActions = computed(() => myActions.value.slice(0, 5))
const hiddenActionCount = computed(() => Math.max(0, myActions.value.length - visibleActions.value.length))
const visibleRiskTasks = computed(() => riskTasks.value.slice(0, 3))
const visibleDiscussedTasks = computed(() => topDiscussedTasks.value.slice(0, 3))
const topDecision = computed(() => {
  const title = riskTasks.value.length
    ? `优先处理 ${riskTasks.value.length} 项风险任务`
    : myActions.value.length
      ? `当前有 ${myActions.value.length} 项待推进事项`
      : '当前经营指标整体可控'
  const summary = riskTasks.value.length
    ? '先清风险，再恢复执行节奏。'
    : highPriorityCount.value
      ? `${highPriorityCount.value} 项高优先级待处理。`
      : myActions.value.length
        ? '按当前顺序推进执行。'
        : '当前没有明显阻塞。'
  return {
    eyebrow: '经营结论',
    title,
    summary,
    signals: lowerDecisionSummary.value.chips.slice(0, 2)
  }
})
const executionSummary = computed(() =>
  hiddenActionCount.value > 0 ? `其余 ${hiddenActionCount.value} 项留在队列中` : '当前显示全部'
)
const riskSummary = computed(() =>
  riskTasks.value.length ? `当前 ${riskTasks.value.length} 项待优先处理` : '当前无新增风险'
)
const discussionSummary = computed(() =>
  topDiscussedTasks.value.length ? `当前 ${topDiscussedTasks.value.length} 项讨论升温` : '当前无讨论热点'
)
const taskTrendTotal = computed(() => taskLine.value.reduce((sum, item) => sum + Number(item.value || 0), 0))
const taskTrendPeak = computed(() => Math.max(0, ...taskLine.value.map((item) => Number(item.value || 0))))
const aiTrendTotal = computed(() => aiBars.value.reduce((sum, item) => sum + Number(item.value || 0), 0))
const aiTrendPeak = computed(() => Math.max(0, ...aiBars.value.map((item) => Number(item.value || 0))))
const statusTotal = computed(() => statusPie.value.reduce((sum, item) => sum + Number(item.value || 0), 0))
const activeMemberCount = computed(() => memberActivityRows.value.length)
const memberPeak = computed(() => Math.max(0, ...memberActivityRows.value.map((item) => Number(item.value || 0))))
const totalActionCount = computed(() => memberActivityRows.value.reduce((sum, item) => sum + Number(item.value || 0), 0))
const topContributor = computed(() => memberActivityRows.value[0] || null)
const topContributorShare = computed(() =>
  totalActionCount.value > 0 && topContributor.value ? topContributor.value.value / totalActionCount.value : 0
)
const throughputPeak = computed(() => Math.max(0, ...throughputBars.value.map((item) => Number(item.value || 0))))
const throughputAvg = computed(() => avgValue(throughputBars.value.map((item) => Number(item.value || 0))))
const throughputActiveDays = computed(() => throughputBars.value.filter((item) => Number(item.value || 0) > 0).length)
const taskTrendActiveDays = computed(() => taskLine.value.filter((item) => Number(item.value || 0) > 0).length)
const aiActiveDays = computed(() => aiBars.value.filter((item) => Number(item.value || 0) > 0).length)
const prevThroughputTotal = computed(() =>
  (prevData.value?.throughputTrend || []).reduce((sum, item) => sum + Number(item.count || 0), 0)
)
const prevTaskTrendTotal = computed(() =>
  (prevData.value?.taskTrend7d || []).reduce((sum, item) => sum + Number(item.count || 0), 0)
)
const prevAiTrendTotal = computed(() =>
  (prevData.value?.aiTrend7d || []).reduce((sum, item) => sum + Number(item.count || 0), 0)
)
const doneRateValue = computed(() => Number(data.value?.taskDoneRate || 0))
const statusMap = computed(() =>
  statusPie.value.reduce(
    (acc, item) => {
      acc[String(item.label || '').toUpperCase()] = Number(item.value || 0)
      return acc
    },
    {} as Record<string, number>
  )
)
const todoCount = computed(() => statusMap.value.TODO || 0)
const doingCount = computed(() => statusMap.value.DOING || 0)
const doneCount = computed(() => statusMap.value.DONE || 0)
const taskDelta = computed(() => cmpPct(taskTrendTotal.value, prevTaskTrendTotal.value, '较上期'))
const throughputDelta = computed(() => cmpPct(throughputTotal.value, prevThroughputTotal.value, '较上期'))
const aiDelta = computed(() => cmpPct(aiTrendTotal.value, prevAiTrendTotal.value, '较上期'))
const aiPerTask = computed(() => {
  const taskTotal = Number(data.value?.taskTotal || 0)
  if (taskTotal <= 0) return 0
  return Number(data.value?.aiCallTotal || 0) / taskTotal
})
const aiPerTaskText = computed(() => (aiPerTask.value > 0 ? `${aiPerTask.value.toFixed(1)} 次/任务` : '暂无渗透'))
const statusLegendRows = computed(() =>
  statusPie.value.map((item) => ({
    ...item,
    displayLabel: statusDisplayLabel(item.label),
    shareText: statusTotal.value > 0 ? fmtPct((Number(item.value || 0) / statusTotal.value) * 100, 1) : '0.0%'
  }))
)
const memberContributionRows = computed(() =>
  memberActivityRows.value.slice(0, 5).map((item) => ({
    ...item,
    shareText: totalActionCount.value > 0 ? fmtPct((item.value / totalActionCount.value) * 100, 1) : '0.0%'
  }))
)

type HealthTone = 'good' | 'warn' | 'risk' | 'na'
type HealthBadge = { label: string; tone: HealthTone; insight: string }

const wipHealth = computed<HealthBadge>(() => {
  if (wipTotal.value > WIP_LIMIT) return { label: '超阈值', tone: 'risk', insight: `超出建议阈值 ${wipTotal.value - WIP_LIMIT}` }
  if (wipTotal.value >= Math.max(1, Math.round(WIP_LIMIT * 0.75))) {
    return { label: '接近阈值', tone: 'warn', insight: `当前在制接近阈值 ${WIP_LIMIT}` }
  }
  return { label: '可控', tone: 'good', insight: `当前在制处于建议阈值 ${WIP_LIMIT} 内` }
})

const throughputHealth = computed<HealthBadge>(() => {
  if (!throughputTotal.value) return { label: '待激活', tone: 'na', insight: '当前周期暂无交付产出' }
  if (throughputActiveDays.value <= 2) return { label: '集中出清', tone: 'warn', insight: `${throughputActiveDays.value} 个活跃交付日` }
  if (throughputPeak.value >= Math.max(2, throughputAvg.value * 2.2)) {
    return { label: '波峰明显', tone: 'warn', insight: `峰值 ${fmtNum(throughputPeak.value)}，节奏偏集中` }
  }
  return { label: '节奏稳定', tone: 'good', insight: `${throughputActiveDays.value} 个活跃交付日` }
})

const cycleHealth = computed<HealthBadge>(() => {
  const sampleCount = Number(cycle.value?.sampleCount || 0)
  const p50 = Number(cycle.value?.p50Days || 0)
  const p90 = Number(cycle.value?.p90Days || 0)
  if (!sampleCount) return { label: '样本不足', tone: 'na', insight: '当前周期暂无有效样本' }
  const ratio = p90 / Math.max(p50, 0.01)
  if (ratio >= 2.2) return { label: '波动偏高', tone: 'warn', insight: `P90 / P50 为 ${ratio.toFixed(1)}x` }
  return { label: '交付稳定', tone: 'good', insight: `P90 / P50 为 ${ratio.toFixed(1)}x` }
})

const taskRhythm = computed<HealthBadge>(() => {
  const values = taskLine.value.map((item) => Number(item.value || 0))
  const last = values.length ? values[values.length - 1] : 0
  const prevAvg = values.length > 1 ? avgValue(values.slice(0, -1)) : 0
  if (!taskTrendTotal.value) return { label: '节奏待激活', tone: 'na', insight: '当前周期暂无交付动作' }
  if (taskTrendActiveDays.value <= 2) return { label: '节奏偏弱', tone: 'warn', insight: `${taskTrendActiveDays.value} 个活跃工作日` }
  if (last >= Math.max(2, prevAvg * 2.2) && last === taskTrendPeak.value) {
    return { label: '末端冲量', tone: 'warn', insight: '近期交付集中在周期末端释放' }
  }
  return { label: '节奏平稳', tone: 'good', insight: `峰值 ${fmtNum(taskTrendPeak.value)}，走势平滑` }
})

const aiParticipation = computed<HealthBadge>(() => {
  if (!aiTrendTotal.value) return { label: '参与偏低', tone: 'na', insight: '当前周期暂无 AI 参与记录' }
  if (aiActiveDays.value <= 2) return { label: '集中使用', tone: 'warn', insight: `${aiActiveDays.value} 个活跃使用日` }
  if (aiPerTask.value >= 2) return { label: '深度参与', tone: 'good', insight: aiPerTaskText.value }
  return { label: '稳定参与', tone: 'good', insight: aiPerTaskText.value }
})

const statusHealth = computed<HealthBadge>(() => {
  if (!statusTotal.value) return { label: '暂无结构', tone: 'na', insight: '当前口径暂无任务结构样本' }
  const todoShare = todoCount.value / statusTotal.value
  const doingShare = doingCount.value / statusTotal.value
  const doneShare = doneCount.value / statusTotal.value
  if (todoShare >= 0.65) return { label: '积压偏高', tone: 'risk', insight: `待处理占比 ${fmtPct(todoShare * 100, 1)}` }
  if (doneShare >= 0.45) return { label: '出清良好', tone: 'good', insight: `已完成占比 ${fmtPct(doneShare * 100, 1)}` }
  if (doingShare >= 0.25) return { label: '推进平衡', tone: 'good', insight: `进行中占比 ${fmtPct(doingShare * 100, 1)}` }
  return { label: '推进偏弱', tone: 'warn', insight: `在制占比 ${fmtPct(doingShare * 100, 1)}` }
})

const contributionHealth = computed<HealthBadge>(() => {
  if (!totalActionCount.value) return { label: '暂无贡献', tone: 'na', insight: '当前周期暂无活跃协作记录' }
  if (topContributorShare.value >= 0.7) {
    return { label: '单点依赖', tone: 'risk', insight: `Top 1 占比 ${fmtPct(topContributorShare.value * 100, 1)}` }
  }
  if (activeMemberCount.value <= 2) {
    return { label: '贡献集中', tone: 'warn', insight: `${fmtNum(activeMemberCount.value)} 位核心贡献成员` }
  }
  return { label: '协作均衡', tone: 'good', insight: `${fmtNum(activeMemberCount.value)} 位成员参与交付` }
})
const lowerDecisionSummary = computed(() => {
  const alerts: string[] = []
  if (riskTasks.value.length > 0) alerts.push(`优先清理 ${riskTasks.value.length} 项风险任务`)
  if (wipHealth.value.tone === 'risk' || wipHealth.value.tone === 'warn') alerts.push('控制在制规模')
  if (statusHealth.value.tone === 'risk' || statusHealth.value.tone === 'warn') alerts.push('加快积压出清')
  if (!alerts.length) alerts.push('当前经营指标整体可控')

  return {
    title: alerts[0],
    summary: `${throughputHealth.value.insight}，${cycleHealth.value.insight}。`,
    chips: [
      { key: 'wip', label: '在制', value: wipHealth.value.label, tone: wipHealth.value.tone },
      { key: 'throughput', label: '吞吐', value: throughputHealth.value.label, tone: throughputHealth.value.tone },
      { key: 'cycle', label: '周期', value: cycleHealth.value.label, tone: cycleHealth.value.tone }
    ]
  }
})
const throughputTargetText = computed(() => `建议活跃日 >= ${fmtNum(THROUGHPUT_ACTIVE_DAY_TARGET)}`)
const cycleTargetText = computed(() => `建议 Avg <= ${CYCLE_TARGET_DAYS.toFixed(1)}d`)
const structureTargetText = computed(() => `待处理占比 <= 60%`)
const contributionTargetText = computed(() => `Top 1 占比 <= 70%`)

function openFirstTaskFrom(list: Array<{ projectId: number; taskId: number }>): void {
  const target = list[0]
  if (!target) return
  openTask(target.projectId, target.taskId)
}

function openFirstRiskTask(): void {
  openFirstTaskFrom(riskTasks.value)
}

function openFirstActionTask(): void {
  openFirstTaskFrom(myActions.value)
}

function openFirstDiscussedTask(): void {
  openFirstTaskFrom(topDiscussedTasks.value)
}

function openPeakThroughputDay(): void {
  const peak = throughputBars.value.reduce<{ label: string; value: number } | null>((best, item) => {
    if (!best || Number(item.value || 0) > Number(best.value || 0)) return item
    return best
  }, null)
  if (!peak?.label) return
  void openThroughputDay(peak.label)
}

function openPrimaryBottomAction(): void {
  if (riskTasks.value.length) {
    openFirstRiskTask()
    return
  }
  if (myActions.value.length) {
    openFirstActionTask()
    return
  }
  if (throughputBars.value.length) {
    openPeakThroughputDay()
  }
}

const showDrill = ref(false)
const drillDay = ref<string>('')
const drillLoading = ref(false)
const drillTasks = ref<DashboardDoneTaskItem[]>([])

async function openThroughputDay(day: string) {
  drillDay.value = day
  showDrill.value = true
  drillLoading.value = true
  try {
    drillTasks.value = await dashboardApi.throughputTasks({ projectId: projectId.value ?? undefined, day })
  } catch (e: any) {
    message.error(e?.message || '加载失败')
    drillTasks.value = []
  } finally {
    drillLoading.value = false
  }
}

function closeDrill() {
  showDrill.value = false
  drillTasks.value = []
}

function exportCsv(): void {
  const r = effectiveRange.value
  const lines: string[] = []
  const row = (cols: any[]) =>
    cols
      .map((x) => {
        const s = x == null ? '' : String(x)
        const escaped = s.replace(/"/g, '""')
        return `"${escaped}"`
      })
      .join(',')

  lines.push(row(['DevTool Copilot Dashboard Export']))
  lines.push(row(['ProjectId', projectId.value == null ? 'ALL' : projectId.value]))
  lines.push(row(['Range', rangeMeta.value, r ? `${fmtYmd(r.start)}~${fmtYmd(r.end)}` : '']))
  lines.push('')
  lines.push(row(['KPI', 'Value']))
  lines.push(row(['projectTotal', data.value?.projectTotal]))
  lines.push(row(['taskTotal', data.value?.taskTotal]))
  lines.push(row(['doneTaskTotal', data.value?.doneTaskTotal]))
  lines.push(row(['taskDoneRate', doneRateText.value]))
  lines.push(row(['aiCallTotal', data.value?.aiCallTotal]))
  lines.push(row(['tasksCreated', data.value?.tasksCreatedThisWeek]))
  lines.push(row(['wipTotal', wipTotal.value]))
  if (cycle.value) {
    lines.push(row(['cycle.sampleCount', cycle.value.sampleCount]))
    lines.push(row(['cycle.p50Days', cycle.value.p50Days]))
    lines.push(row(['cycle.p90Days', cycle.value.p90Days]))
    lines.push(row(['cycle.avgDays', cycle.value.avgDays]))
  }
  lines.push('')
  lines.push(row(['ThroughputTrend(day)', 'count']))
  for (const it of data.value?.throughputTrend || []) lines.push(row([it.day, it.count]))
  lines.push('')
  lines.push(row(['MyActions', 'taskId', 'projectId', 'projectName', 'title', 'status', 'dueTime']))
  for (const it of myActions.value) lines.push(row(['', it.taskId, it.projectId, it.projectName, it.title, it.status, it.dueTime]))
  lines.push('')
  lines.push(row(['AtRisk', 'taskId', 'projectId', 'projectName', 'title', 'status', 'dueTime']))
  for (const it of riskTasks.value) lines.push(row(['', it.taskId, it.projectId, it.projectName, it.title, it.status, it.dueTime]))
  lines.push('')
  lines.push(row(['TopDiscussed', 'taskId', 'projectId', 'projectName', 'title', 'commentCount']))
  for (const it of topDiscussedTasks.value) lines.push(row(['', it.taskId, it.projectId, it.projectName, it.title, it.commentCount]))

  const bom = '\ufeff'
  const blob = new Blob([bom + lines.join('\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `dashboard_${projectId.value == null ? 'all' : projectId.value}_${rangeKey.value}.csv`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

function fmtPct(v: number, digits = 1): string {
  const n = Math.round(v * Math.pow(10, digits)) / Math.pow(10, digits)
  const s = digits === 0 ? String(Math.round(n)) : n.toFixed(digits)
  return `${s}%`
}

function fmtPp(v: number, digits = 1): string {
  const n = Math.round(v * Math.pow(10, digits)) / Math.pow(10, digits)
  const s = digits === 0 ? String(Math.round(n)) : n.toFixed(digits)
  return `${s}pp`
}

type Tone = 'up' | 'down' | 'flat' | 'na'
type Badge = { text: string; tone: Tone }

function toneBySign(delta: number): Tone {
  if (!Number.isFinite(delta) || delta === 0) return delta === 0 ? 'flat' : 'na'
  return delta > 0 ? 'up' : 'down'
}

function cmpPct(cur: number | null | undefined, base: number | null | undefined, label: string): Badge {
  const c = Number(cur || 0)
  const b = Number(base || 0)
  if (!Number.isFinite(c) || !Number.isFinite(b) || b <= 0) return { text: `${label} —`, tone: 'na' }
  const delta = (c - b) / b
  const sign = delta > 0 ? '+' : ''
  return { text: `${label} ${sign}${fmtPct(delta * 100, 1)}`, tone: toneBySign(delta) }
}

function cmpPp(curRate: number | null | undefined, baseRate: number | null | undefined, label: string): Badge {
  const c = Number(curRate || 0)
  const b = Number(baseRate || 0)
  if (!Number.isFinite(c) || !Number.isFinite(b)) return { text: `${label} —`, tone: 'na' }
  const deltaPp = (c - b) * 100
  const sign = deltaPp > 0 ? '+' : ''
  return { text: `${label} ${sign}${fmtPp(deltaPp, 1)}`, tone: toneBySign(deltaPp) }
}

function sparkD(values: number[], w = 92, h = 28, pad = 2): string | null {
  if (!values || values.length < 2) return null
  const xs = values.map((v) => Number(v || 0))
  const min = Math.min(...xs)
  const max = Math.max(...xs)
  const span = max - min || 1
  const step = (w - pad * 2) / (xs.length - 1)
  const pts = xs.map((v, i) => {
    const x = pad + i * step
    const y = pad + (h - pad * 2) * (1 - (v - min) / span)
    return { x, y }
  })
  return pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(2)},${p.y.toFixed(2)}`).join(' ')
}

const sparkTasks = computed(() => sparkD(taskLine.value.map((x) => x.value)))
const sparkAi = computed(() => sparkD(aiBars.value.map((x) => x.value)))

const kpiCards = computed(() => {
  const cur = data.value
  const prev = prevData.value
  const yoy = yoyData.value

  const rate = Number(cur?.taskDoneRate || 0)
  const pct = Math.round(rate * 1000) / 10

  return [
    {
      key: 'projectTotal',
      label: '项目总数',
      valueText: fmtNum(cur?.projectTotal),
      mom: cmpPct(cur?.projectTotal, prev?.projectTotal, '环比'),
      yoy: cmpPct(cur?.projectTotal, yoy?.projectTotal, '同比'),
      spark: null as string | null
    },
    {
      key: 'taskTotal',
      label: 'Task 总数',
      valueText: fmtNum(cur?.taskTotal),
      mom: cmpPct(cur?.taskTotal, prev?.taskTotal, '环比'),
      yoy: cmpPct(cur?.taskTotal, yoy?.taskTotal, '同比'),
      spark: sparkTasks.value
    },
    {
      key: 'doneTaskTotal',
      label: '已完成 Task',
      valueText: fmtNum(cur?.doneTaskTotal),
      mom: cmpPct(cur?.doneTaskTotal, prev?.doneTaskTotal, '环比'),
      yoy: cmpPct(cur?.doneTaskTotal, yoy?.doneTaskTotal, '同比'),
      spark: null as string | null
    },
    {
      key: 'taskDoneRate',
      label: 'Task 完成率',
      valueText: `${pct}%`,
      mom: cmpPp(cur?.taskDoneRate, prev?.taskDoneRate, '环比'),
      yoy: cmpPp(cur?.taskDoneRate, yoy?.taskDoneRate, '同比'),
      barPct: `${pct}%`
    },
    {
      key: 'aiCallTotal',
      label: 'AI 调用次数',
      valueText: fmtNum(cur?.aiCallTotal),
      mom: cmpPct(cur?.aiCallTotal, prev?.aiCallTotal, '环比'),
      yoy: cmpPct(cur?.aiCallTotal, yoy?.aiCallTotal, '同比'),
      spark: sparkAi.value
    },
    {
      key: 'tasksCreated',
      label: '新增任务',
      valueText: fmtNum(cur?.tasksCreatedThisWeek),
      mom: cmpPct(cur?.tasksCreatedThisWeek, prev?.tasksCreatedThisWeek, '环比'),
      yoy: cmpPct(cur?.tasksCreatedThisWeek, yoy?.tasksCreatedThisWeek, '同比'),
      spark: sparkTasks.value
    }
  ]
})

function fmtNum(v: number | null | undefined) {
  const n = Number(v || 0)
  return n.toLocaleString()
}
</script>

<template>
  <div class="page dashPage">
    <div class="head">
      <div class="left">
        <h1 class="h1"><span class="h1Bar"></span>数据总览</h1>
      </div>
      <div class="right">
        <n-button tertiary @click="load">刷新</n-button>
        <n-button tertiary @click="exportCsv">导出</n-button>
      </div>
    </div>

    <n-spin :show="loading">
      <n-modal v-model:show="showDrill" class="drillModal" preset="card" :title="`Throughput · ${drillDay.slice(5)}`" @after-leave="closeDrill">
        <n-spin :show="drillLoading">
          <div class="dList">
            <button
              v-for="t in drillTasks"
              :key="t.taskId"
              class="dRow"
              @click="openTask(t.projectId, t.taskId)"
            >
              <div class="dMain">
                <div class="dName">{{ t.title }}</div>
                <div class="muted dSub">{{ t.projectName || `#${t.projectId}` }}</div>
              </div>
              <div class="dRight">
                <span class="sPill" :class="statusTone(t.status)">{{ statusText(t.status) }}</span>
              </div>
            </button>
            <div v-if="!drillTasks.length && !drillLoading" class="muted dEmpty">当日没有完成记录</div>
          </div>
        </n-spin>
      </n-modal>

      <div class="surface filterBar">
        <div class="fLeft">
          <div class="fTitle">经营口径</div>
          <div class="muted fDesc">先看范围，再看风险与节奏。</div>
          <div class="filterSummary">
            <span class="filterChip">
              <span class="filterChipLabel">项目</span>
              <span class="filterChipValue">{{ selectedProjectName }}</span>
            </span>
            <span class="filterChip">
              <span class="filterChipLabel">周期</span>
              <span class="filterChipValue">{{ rangeMeta }}</span>
            </span>
            <span class="filterChip wide">
              <span class="filterChipLabel">区间</span>
              <span class="filterChipValue">{{ rangeText }}</span>
            </span>
          </div>
        </div>
        <div class="fRight">
          <div class="fGroup">
            <div class="fLabel muted">项目</div>
            <n-select v-model:value="projectId" class="fSelect" :options="projectOptions" placeholder="全部项目" />
          </div>
          <div class="fGroup">
            <div class="fLabel muted">时间</div>
            <div class="rangePills">
              <button class="pill" :class="{ on: rangeKey === '7d' }" @click="rangeKey = '7d'">近 7 天</button>
              <button class="pill" :class="{ on: rangeKey === '30d' }" @click="rangeKey = '30d'">近 30 天</button>
              <button class="pill" :class="{ on: rangeKey === 'wtd' }" @click="rangeKey = 'wtd'">本周</button>
              <button class="pill" :class="{ on: rangeKey === 'mtd' }" @click="rangeKey = 'mtd'">本月</button>
              <button class="pill" :class="{ on: rangeKey === 'custom' }" @click="rangeKey = 'custom'">自定义</button>
            </div>
            <n-date-picker
              v-if="rangeKey === 'custom'"
              v-model:value="customRange"
              class="fDate"
              type="daterange"
              clearable
              :close-on-select="true"
            />
          </div>
        </div>
      </div>

      <section class="surface overviewHero">
        <div class="heroMain">
          <div class="heroTop">
            <div class="heroHeading">
              <div class="heroEyebrow">{{ topDecision.eyebrow }}</div>
              <h2 class="heroTitle">{{ topDecision.title }}</h2>
              <div class="heroSummary muted">{{ topDecision.summary }}</div>
            </div>
            <div class="heroActionPane">
              <div class="heroInsightRow">
                <div v-for="item in heroInsights" :key="item.key" class="heroInsight">
                  <span class="heroInsightLabel">{{ item.label }}</span>
                  <span class="heroInsightValue">{{ item.value }}</span>
                </div>
              </div>
              <div class="heroActionRow">
                <div v-for="item in topDecision.signals" :key="item.key" class="heroSignal">
                  <span class="heroSignalLabel">{{ item.label }}</span>
                  <span class="healthPill sm" :class="item.tone">{{ item.value }}</span>
                </div>
                <button class="miniAction primary" @click="openPrimaryBottomAction">查看优先动作</button>
              </div>
            </div>
          </div>
          <div class="heroStats">
            <div v-for="item in overviewStats" :key="item.key" class="heroStat">
              <div class="muted heroStatLabel">{{ item.label }}</div>
              <div class="heroStatValue">{{ item.value }}</div>
              <div class="muted heroStatMeta">{{ item.meta }}</div>
            </div>
          </div>
        </div>
        <div class="heroAside">
          <div v-for="card in signalCards" :key="card.key" class="signalCard">
            <div class="muted signalLabel">{{ card.label }}</div>
            <div class="signalValue">{{ card.value }}</div>
            <div class="muted signalMeta">{{ card.meta }}</div>
          </div>
        </div>
      </section>

      <div class="focusGrid">
        <section class="surface focusCard focusPrimary">
          <div class="panelHead">
            <div>
              <div class="panelEyebrow">执行队列</div>
              <div class="panelTitle">需要推进</div>
            </div>
            <div class="muted panelMeta">{{ myActions.length }}</div>
          </div>
          <div class="muted focusLead">{{ executionSummary }}</div>
          <div class="focusList">
            <button v-for="t in visibleActions" :key="t.taskId" class="focusRow" @click="openTask(t.projectId, t.taskId)">
              <div class="focusMain">
                <div class="focusName">{{ t.title }}</div>
                <div class="focusSubRow">
                  <span class="muted focusSub">{{ t.projectName || `#${t.projectId}` }}</span>
                  <span class="muted focusSub">{{ t.assigneeName || '未分配' }}</span>
                </div>
              </div>
              <div class="focusRight">
                <span class="pPill" :class="priorityTone(t.priority)">{{ priorityText(t.priority) }}</span>
                <span class="sPill" :class="statusTone(t.status)">{{ statusText(t.status) }}</span>
                <span class="focusDue" :class="{ warn: (dueDiffDays(t.dueTime) ?? 999) <= 3 }">{{ fmtDueText(t.dueTime) }}</span>
              </div>
            </button>
            <div v-if="!myActions.length" class="aEmpty">暂无待推进</div>
          </div>
          <div v-if="hiddenActionCount > 0" class="queueTail muted">其余 {{ hiddenActionCount }} 项留在队列中</div>
        </section>

        <div class="sideStack">
          <section class="surface focusCard focusSecondary" :class="{ emptyCompact: !riskTasks.length }">
            <div class="panelHead">
              <div>
                <div class="panelEyebrow">风险</div>
                <div class="panelTitle">风险与阻塞</div>
              </div>
              <div class="muted panelMeta">{{ riskTasks.length }}</div>
            </div>
            <div class="muted focusLead compact">{{ riskSummary }}</div>
            <div v-if="riskTasks.length" class="focusList compact">
              <button v-for="t in visibleRiskTasks" :key="t.taskId" class="focusRow compact" @click="openTask(t.projectId, t.taskId)">
                <div class="focusMain">
                  <div class="focusName">{{ t.title }}</div>
                  <div class="focusSubRow">
                    <span class="muted focusSub">{{ t.projectName || `#${t.projectId}` }}</span>
                    <span class="muted focusSub">{{ fmtDueText(t.dueTime) }}</span>
                  </div>
                </div>
                <div class="focusRight">
                  <span class="pPill" :class="priorityTone(t.priority)">{{ priorityText(t.priority) }}</span>
                  <span class="sPill risk">已逾期</span>
                </div>
              </button>
            </div>
            <div v-else class="focusBrief">暂无风险</div>
          </section>

          <section class="surface focusCard focusSecondary" :class="{ emptyCompact: !topDiscussedTasks.length }">
            <div class="panelHead">
              <div>
                <div class="panelEyebrow">讨论</div>
                <div class="panelTitle">讨论集中</div>
              </div>
              <div class="muted panelMeta">{{ topDiscussedTasks.length }}</div>
            </div>
            <div class="muted focusLead compact">{{ discussionSummary }}</div>
            <div v-if="topDiscussedTasks.length" class="focusList compact">
              <button
                v-for="t in visibleDiscussedTasks"
                :key="t.taskId"
                class="focusRow compact"
                @click="openTask(t.projectId, t.taskId)"
              >
                <div class="focusMain">
                  <div class="focusName">{{ t.title }}</div>
                  <div class="focusSubRow">
                    <span class="muted focusSub">{{ t.projectName || `#${t.projectId}` }}</span>
                    <span class="muted focusSub">{{ t.assigneeName || '未分配' }}</span>
                  </div>
                </div>
                <div class="focusRight">
                  <span class="pPill" :class="priorityTone(t.priority)">{{ priorityText(t.priority) }}</span>
                  <span class="cntPill">{{ Number(t.commentCount || 0) }}</span>
                </div>
              </button>
            </div>
            <div v-else class="focusBrief">暂无热点</div>
          </section>
        </div>
      </div>

      <section class="opsBand">
        <div class="opsCell">
          <div class="opsTop">
            <div>
              <div class="panelEyebrow">流转</div>
              <div class="panelTitle">在制规模</div>
            </div>
            <span class="healthPill" :class="wipHealth.tone">{{ wipHealth.label }}</span>
          </div>
          <div class="opsMain">
            <div class="opsValue">{{ fmtNum(wipTotal) }}</div>
            <div class="opsSideMetric">
              <div class="muted opsSideLabel">建议阈值</div>
              <div class="opsSideValue">{{ fmtNum(WIP_LIMIT) }}</div>
            </div>
          </div>
          <div class="muted opsInsight">{{ wipHealth.insight }}</div>
          <div class="opsFacts">
            <div class="opsFact">
              <span class="muted opsFactLabel">风险任务</span>
              <span class="opsFactValue">{{ fmtNum(riskTasks.length) }}</span>
            </div>
            <div class="opsFact">
              <span class="muted opsFactLabel">近期到期</span>
              <span class="opsFactValue">{{ fmtNum(dueSoonCount) }}</span>
            </div>
          </div>
          <div class="cardActions">
            <button class="miniAction" @click="openFirstRiskTask">查看风险任务</button>
          </div>
        </div>

        <div class="opsCell">
          <div class="opsTop">
            <div>
              <div class="panelEyebrow">流转</div>
              <div class="panelTitle">交付吞吐</div>
            </div>
            <span class="healthPill" :class="throughputHealth.tone">{{ throughputHealth.label }}</span>
          </div>
          <div class="opsMain">
            <div class="opsValue">{{ fmtNum(throughputTotal) }}</div>
            <span class="deltaBadge" :class="throughputDelta.tone">{{ throughputDelta.text }}</span>
          </div>
          <div class="muted opsInsight">{{ throughputHealth.insight }}</div>
          <div class="mChart opsMiniChart">
            <template v-if="throughputBars.some((b) => Number(b.value || 0) > 0)">
              <bar-chart :bars="throughputBars" :height="78" interactive @select="(b) => openThroughputDay(b.label)" />
            </template>
            <div v-else class="emptyMini muted">暂无数据</div>
          </div>
          <div class="xlabels compact">
            <span v-for="l in throughputXLabels" :key="l" class="xlab muted">{{ l }}</span>
          </div>
          <div class="opsFacts">
            <div class="opsFact">
              <span class="muted opsFactLabel">活跃日</span>
              <span class="opsFactValue">{{ fmtNum(throughputActiveDays) }}</span>
            </div>
            <div class="opsFact">
              <span class="muted opsFactLabel">峰值</span>
              <span class="opsFactValue">{{ fmtNum(throughputPeak) }}</span>
            </div>
          </div>
          <div class="cardActions">
            <button class="miniAction" @click="openPeakThroughputDay">查看吞吐明细</button>
          </div>
        </div>

        <div class="opsCell">
          <div class="opsTop">
            <div>
              <div class="panelEyebrow">效率</div>
              <div class="panelTitle">交付周期</div>
            </div>
            <span class="healthPill" :class="cycleHealth.tone">{{ cycleHealth.label }}</span>
          </div>
          <div class="opsMain">
            <div class="opsValue">{{ cycle?.sampleCount ? `${(cycle?.avgDays ?? 0).toFixed(2)}d` : '—' }}</div>
            <div class="opsSideMetric">
              <div class="muted opsSideLabel">样本</div>
              <div class="opsSideValue">{{ fmtNum(cycle?.sampleCount ?? 0) }}</div>
            </div>
          </div>
          <div class="muted opsInsight">{{ cycleHealth.insight }}</div>
          <div class="cycleStats">
            <div class="cycleStat">
              <div class="muted cycleLabel">P50</div>
              <div class="cycleValue">{{ (cycle?.p50Days ?? 0).toFixed(2) }}d</div>
            </div>
            <div class="cycleStat">
              <div class="muted cycleLabel">P90</div>
              <div class="cycleValue">{{ (cycle?.p90Days ?? 0).toFixed(2) }}d</div>
            </div>
            <div class="cycleStat">
              <div class="muted cycleLabel">Avg</div>
              <div class="cycleValue">{{ (cycle?.avgDays ?? 0).toFixed(2) }}d</div>
            </div>
          </div>
          <div class="cardActions">
            <button class="miniAction" @click="openPeakThroughputDay">查看样本任务</button>
          </div>
        </div>
      </section>

      <div class="chartGrid">
        <section class="surface chartCard chartHeroCard">
          <div class="analysisSplit">
            <div class="analysisMain">
              <div class="cHead">
                <div class="cHeadMain">
                  <div class="h2">交付节奏</div>
                  <div class="muted meta">{{ rangeMeta }}</div>
                </div>
                <div class="cMetric">
                  <div class="cMetricValue">{{ fmtNum(taskTrendTotal) }}</div>
                  <div class="muted cMetricMeta">峰值 {{ fmtNum(taskTrendPeak) }}</div>
                </div>
              </div>
              <div class="cSummary">
                <span class="healthPill sm" :class="taskRhythm.tone">{{ taskRhythm.label }}</span>
                <span class="muted cInsight">{{ taskRhythm.insight }}</span>
                <span class="deltaBadge" :class="taskDelta.tone">{{ taskDelta.text }}</span>
              </div>
              <div class="chartBox chartBoxLg">
                <template v-if="taskLine.some((p) => Number(p.value || 0) > 0)">
                  <line-chart :points="taskLine" axis :y-ticks="4" :height="182" />
                </template>
                <div v-else class="emptyChart emptyChartLg muted">暂无数据</div>
              </div>
              <div class="xlabels">
                <span v-for="l in taskXLabels" :key="l" class="xlab muted">{{ l }}</span>
              </div>
            </div>
            <div class="analysisSide">
              <div class="cHead">
                <div class="cHeadMain">
                  <div class="h2">AI 参与强度</div>
                  <div class="muted meta">{{ rangeMeta }}</div>
                </div>
                <div class="cMetric">
                  <div class="cMetricValue">{{ fmtNum(aiTrendTotal) }}</div>
                  <div class="muted cMetricMeta">{{ aiPerTaskText }}</div>
                </div>
              </div>
              <div class="cSummary">
                <span class="healthPill sm" :class="aiParticipation.tone">{{ aiParticipation.label }}</span>
                <span class="deltaBadge" :class="aiDelta.tone">{{ aiDelta.text }}</span>
              </div>
              <div class="muted aiInsight">{{ aiParticipation.insight }}</div>
              <div class="chartBox chartBoxSm">
                <template v-if="aiBars.some((b) => Number(b.value || 0) > 0)">
                  <bar-chart :bars="aiBars" axis :y-ticks="4" :height="126" />
                </template>
                <div v-else class="emptyChart emptyChartSm muted">暂无数据</div>
              </div>
              <div class="xlabels compact">
                <span v-for="l in aiXLabels" :key="l" class="xlab muted">{{ l }}</span>
              </div>
            </div>
          </div>
          <div class="cardActions">
            <button class="miniAction" @click="openFirstActionTask">查看执行队列</button>
          </div>
        </section>

        <section class="surface chartCard">
          <div class="cHead">
            <div class="cHeadMain">
              <div class="h2">交付结构</div>
              <div class="muted meta">按筛选口径</div>
            </div>
            <div class="cMetric">
              <div class="cMetricValue">{{ fmtNum(todoCount) }}</div>
              <div class="muted cMetricMeta">待处理</div>
            </div>
          </div>
          <div class="cSummary">
            <span class="healthPill sm" :class="statusHealth.tone">{{ statusHealth.label }}</span>
            <span class="muted cInsight">{{ statusHealth.insight }}</span>
            <span class="deltaBadge static">{{ doneRateText }} 完成率</span>
          </div>
          <div class="pieWrap">
            <div class="pieShell">
              <div class="pieCenter">
                <div class="pieCenterValue">{{ doneRateText }}</div>
                <div class="muted pieCenterLabel">完成率</div>
              </div>
              <div class="pie">
                <pie-chart :slices="statusPie" :size="176" />
              </div>
            </div>
            <div class="legend">
              <div v-for="s in statusLegendRows" :key="s.label" class="legRow">
                <span class="dot" :style="{ background: s.color || 'rgba(15,23,42,0.18)' }" />
                <div class="legMain">
                  <div class="legName">{{ s.displayLabel }}</div>
                  <div class="muted legCnt">{{ fmtNum(s.value) }} · {{ s.shareText }}</div>
                </div>
              </div>
            </div>
          </div>
          <div class="cardActions">
            <button class="miniAction" @click="openFirstRiskTask">查看风险任务</button>
          </div>
        </section>

        <section class="surface chartCard">
          <div class="cHead">
            <div class="cHeadMain">
              <div class="h2">交付贡献</div>
              <div class="muted meta">{{ rangeMeta }}</div>
            </div>
            <div class="cMetric">
              <div class="cMetricValue">{{ fmtNum(activeMemberCount) }}</div>
              <div class="muted cMetricMeta">Top 1 {{ topContributor?.short || '—' }}</div>
            </div>
          </div>
          <div class="cSummary">
            <span class="healthPill sm" :class="contributionHealth.tone">{{ contributionHealth.label }}</span>
            <span class="muted cInsight">{{ contributionHealth.insight }}</span>
            <span class="deltaBadge static">{{ fmtNum(totalActionCount) }} 总动作</span>
          </div>
          <div class="chartBox memberBox">
            <div class="memberSummary">
              <div class="memberSummaryItem">
                <div class="muted memberSummaryLabel">活跃成员</div>
                <div class="memberSummaryValue">{{ fmtNum(activeMemberCount) }}</div>
              </div>
              <div class="memberSummaryItem">
                <div class="muted memberSummaryLabel">Top 1</div>
                <div class="memberSummaryValue">{{ topContributor?.short || '—' }}</div>
              </div>
              <div class="memberSummaryItem">
                <div class="muted memberSummaryLabel">集中度</div>
                <div class="memberSummaryValue">{{ fmtPct(topContributorShare * 100, 1) }}</div>
              </div>
            </div>
            <div class="memberList">
              <div v-for="(m, index) in memberContributionRows" :key="m.userId" class="mRow">
                <div class="mRank">{{ index + 1 }}</div>
                <div class="mIdentity">
                  <div class="mName" :title="m.name">{{ m.short }}</div>
                  <div class="muted mShare">{{ m.shareText }}</div>
                </div>
                <div class="mBar">
                  <div class="mBarIn" :style="{ width: m.width }" />
                </div>
                <div class="mVal muted">{{ fmtNum(m.value) }}</div>
              </div>
              <div v-if="!memberContributionRows.length" class="mEmpty muted">当前口径下暂无活跃数据</div>
            </div>
          </div>
          <div class="cardActions">
            <button class="miniAction" @click="openFirstDiscussedTask">查看讨论热点</button>
          </div>
        </section>
      </div>
    </n-spin>
  </div>
</template>

<style scoped>
.dashPage {
  --ink-strong: rgba(30, 41, 59, 0.94);
  --ink-title: rgba(51, 65, 85, 0.92);
  --ink-body: rgba(71, 85, 105, 0.78);
  --ink-muted: rgba(100, 116, 139, 0.64);
  --ink-faint: rgba(148, 163, 184, 0.82);
  color: var(--ink-body);
}

.muted {
  color: var(--ink-muted) !important;
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}

.left :deep(.h1) {
  margin: 0;
  font-size: 22px;
  line-height: 1.15;
  letter-spacing: -0.5px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.h1Bar {
  width: 4px;
  height: 18px;
  border-radius: 2px;
  background: rgba(var(--accent-rgb), 0.9);
}

.right {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.eyebrow {
  font-size: 10px;
  font-weight: 750;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.42);
}

.sub {
  margin-top: 8px;
  font-size: 13px;
  max-width: 720px;
  line-height: 1.5;
}

.surface {
  border-radius: 16px;
  border: 1px solid rgba(15, 23, 42, 0.04);
  background: rgba(255, 255, 255, 0.54);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.015);
}

.filterBar {
  padding: 10px 12px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  backdrop-filter: blur(6px);
}

.fLeft {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.fTitle {
  font-size: 12px;
  font-weight: 900;
  letter-spacing: -0.2px;
  color: var(--ink-title);
}

.fDesc {
  font-size: 11px;
  color: var(--ink-muted);
}

.filterSummary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.filterChip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 26px;
  padding: 0 9px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(15, 23, 42, 0.028);
}

.filterChip.wide {
  max-width: min(360px, 100%);
}

.filterChipLabel {
  font-size: 10px;
  font-weight: 800;
  color: var(--ink-faint);
}

.filterChipValue {
  min-width: 0;
  font-size: 11px;
  font-weight: 800;
  color: var(--ink-title);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.fRight {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px 10px;
}

.fGroup {
  display: flex;
  align-items: center;
  gap: 8px;
}

.fLabel {
  font-size: 11px;
  font-weight: 750;
  letter-spacing: -0.1px;
  color: var(--ink-muted);
}

.fSelect {
  width: 204px;
}

.rangePills {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 3px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.05);
  background: rgba(255, 255, 255, 0.34);
}

.pill {
  appearance: none;
  border: none;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 750;
  letter-spacing: -0.1px;
  color: var(--ink-body);
  background: transparent;
  transition:
    background 0.15s ease,
    transform 0.15s ease,
    box-shadow 0.15s ease,
    color 0.15s ease;
}

.pill:hover {
  transform: translateY(-1px);
  background: rgba(15, 23, 42, 0.05);
}

.pill.on {
  color: rgba(var(--accent-rgb), 0.95);
  background: rgba(var(--accent-rgb), 0.08);
  box-shadow: none;
}

.fDate {
  width: 236px;
}

.overviewHero {
  margin-top: 8px;
  padding: 4px 0 0;
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  align-items: start;
  border: none;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.heroMain {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.heroTop {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.heroHeading {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.heroEyebrow {
  font-size: 10px;
  font-weight: 750;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: rgba(var(--accent-rgb), 0.82);
}

.heroTitle {
  margin: 0;
  font-size: 20px;
  line-height: 1.15;
  letter-spacing: -0.55px;
  font-weight: 950;
  color: var(--ink-strong);
}

.heroSummary {
  font-size: 11px;
  max-width: 640px;
  color: var(--ink-body);
}

.heroActionPane {
  min-width: 280px;
  max-width: 360px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.heroInsightRow {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
  max-width: 360px;
}

.heroInsight {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  background: rgba(71, 85, 105, 0.05);
  border: 1px solid rgba(71, 85, 105, 0.05);
}

.heroInsightLabel,
.heroInsightValue {
  font-size: 11px;
}

.heroInsightLabel {
  color: var(--ink-faint);
}

.heroInsightValue {
  font-weight: 800;
  color: var(--ink-title);
}

.heroActionRow {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.heroSignal {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  background: rgba(71, 85, 105, 0.04);
}

.heroSignalLabel {
  font-size: 10px;
  font-weight: 800;
  color: var(--ink-faint);
}

.heroStats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-top: 2px;
  padding-top: 0;
  border-top: none;
}

.heroStat {
  min-height: 66px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 6px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.018);
}

.heroStatLabel,
.heroStatMeta {
  font-size: 10px;
  color: var(--ink-faint);
}

.heroStatValue {
  font-size: 20px;
  line-height: 1;
  font-weight: 950;
  letter-spacing: -0.45px;
  color: var(--ink-title);
}

.heroAside {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  border-top: none;
  padding-top: 0;
}

.signalCard {
  min-height: 58px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 4px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.018);
}

.signalLabel,
.signalMeta {
  font-size: 11px;
  color: var(--ink-muted);
}

.signalValue {
  font-size: 20px;
  line-height: 1;
  font-weight: 950;
  letter-spacing: -0.45px;
  color: var(--ink-title);
}

.focusGrid {
  margin-top: 10px;
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(300px, 0.95fr);
  gap: 20px;
  align-items: start;
}

.focusCard {
  padding: 8px 0 0;
  border: none;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}

.focusPrimary {
  min-height: 0;
}

.focusPrimary .focusList {
  max-height: 304px;
  overflow: auto;
  padding-right: 10px;
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  scrollbar-color: rgba(100, 116, 139, 0.28) transparent;
}

.sideStack {
  display: grid;
  gap: 12px;
}

.focusSecondary {
  min-height: 0;
}

.focusLead {
  margin-bottom: 8px;
  font-size: 11px;
  color: var(--ink-muted);
}

.focusLead.compact {
  margin-bottom: 6px;
}

.focusSecondary .focusList {
  max-height: 164px;
  overflow: auto;
  padding-right: 8px;
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  scrollbar-color: rgba(100, 116, 139, 0.22) transparent;
}

.panelHead {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.panelHead.slim {
  margin-bottom: 12px;
}

.panelEyebrow {
  font-size: 10px;
  font-weight: 750;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: rgba(var(--accent-rgb), 0.75);
}

.panelTitle {
  margin-top: 3px;
  font-size: 14px;
  line-height: 1.1;
  font-weight: 900;
  letter-spacing: -0.2px;
  color: var(--ink-title);
}

.panelMeta {
  font-size: 11px;
  line-height: 1.6;
  color: var(--ink-muted);
}

.focusList {
  display: grid;
  gap: 0;
}

.focusList::-webkit-scrollbar {
  width: 6px;
}

.focusList::-webkit-scrollbar-track {
  background: transparent;
}

.focusList::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(100, 116, 139, 0.22);
}

.focusList::-webkit-scrollbar-thumb:hover {
  background: rgba(71, 85, 105, 0.34);
}

.focusList.compact {
  gap: 0;
}

.focusBrief {
  padding: 4px 0 0;
  font-size: 11px;
  color: var(--ink-muted);
}

.queueTail {
  padding-top: 10px;
  font-size: 11px;
  color: var(--ink-muted);
}

.focusSecondary.emptyCompact {
  padding-bottom: 2px;
}

.focusRow {
  width: 100%;
  padding: 9px 0;
  border: none;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
  text-align: left;
  transition: opacity 0.15s ease;
}

.focusRow.compact {
  padding: 8px 0;
}

.focusRow:hover {
  opacity: 0.76;
}

.focusMain {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.focusName {
  font-size: 12px;
  font-weight: 900;
  letter-spacing: -0.2px;
  color: var(--ink-strong);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.focusSubRow {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.focusSub,
.focusDue {
  font-size: 10px;
  color: var(--ink-muted);
}

.focusDue.warn {
  color: var(--ink-title);
}

.focusRight {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.sPill {
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: -0.1px;
  border: 1px solid rgba(15, 23, 42, 0.10);
  background: rgba(15, 23, 42, 0.02);
  color: rgba(71, 85, 105, 0.76);
}

.pPill {
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: -0.1px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  color: rgba(71, 85, 105, 0.72);
  background: rgba(71, 85, 105, 0.03);
}

.pPill.high {
  color: rgba(36, 52, 71, 0.9);
  border-color: rgba(71, 85, 105, 0.12);
  background: rgba(71, 85, 105, 0.06);
}

.pPill.medium {
  color: rgba(51, 65, 85, 0.8);
  border-color: rgba(71, 85, 105, 0.08);
  background: rgba(71, 85, 105, 0.035);
}

.pPill.low,
.pPill.na {
  color: rgba(15, 23, 42, 0.58);
}

.sPill.todo {
  background: rgba(15, 23, 42, 0.02);
  border-color: rgba(15, 23, 42, 0.10);
}

.sPill.doing {
  color: rgba(2, 6, 23, 0.92);
  border-color: rgba(var(--accent2-rgb), 0.22);
  background: rgba(var(--accent2-rgb), 0.10);
}

.sPill.done {
  color: rgba(2, 6, 23, 0.92);
  border-color: rgba(var(--accent-rgb), 0.22);
  background: rgba(var(--accent-rgb), 0.10);
}

.sPill.risk {
  color: rgba(2, 6, 23, 0.92);
  border-color: rgba(15, 23, 42, 0.18);
  background: rgba(15, 23, 42, 0.05);
}

.cntPill {
  min-width: 26px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 8px;
  border-radius: 999px;
  border: 1px solid rgba(var(--accent2-rgb), 0.20);
  background: rgba(var(--accent2-rgb), 0.08);
  color: rgba(2, 6, 23, 0.92);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: -0.1px;
}

.aEmpty {
  padding: 10px 0;
  border-top: 1px dashed rgba(15, 23, 42, 0.1);
  font-size: 11px;
  color: rgba(15, 23, 42, 0.55);
}

.opsBand {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0;
  border-top: 1px solid rgba(15, 23, 42, 0.06);
  border-bottom: 1px solid rgba(15, 23, 42, 0.04);
}

.opsCell {
  min-height: 0;
  padding: 14px 18px 12px;
  border-right: 1px solid rgba(15, 23, 42, 0.05);
}

.opsCell:last-child {
  border-right: none;
}

.opsTop {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.healthPill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: -0.1px;
  border: 1px solid rgba(15, 23, 42, 0.10);
  background: rgba(15, 23, 42, 0.03);
  color: rgba(30, 41, 59, 0.86);
}

.healthPill.sm {
  min-height: 20px;
}

.healthPill.good {
  color: rgba(36, 52, 71, 0.94);
  background: rgba(71, 85, 105, 0.055);
  border-color: rgba(71, 85, 105, 0.12);
}

.healthPill.warn {
  color: rgba(51, 65, 85, 0.9);
  background: rgba(71, 85, 105, 0.08);
  border-color: rgba(71, 85, 105, 0.15);
}

.healthPill.risk {
  color: rgba(15, 23, 42, 0.95);
  background: rgba(15, 23, 42, 0.09);
  border-color: rgba(15, 23, 42, 0.16);
}

.healthPill.na {
  color: rgba(100, 116, 139, 0.9);
  background: rgba(148, 163, 184, 0.08);
  border-color: rgba(148, 163, 184, 0.18);
}

.opsMain {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.opsSideMetric {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.opsSideLabel {
  font-size: 10px;
}

.opsSideValue {
  font-size: 14px;
  line-height: 1;
  font-weight: 900;
  color: var(--ink-title);
}

.opsValue {
  margin-top: 4px;
  font-size: 26px;
  line-height: 1;
  font-weight: 950;
  letter-spacing: -0.5px;
  color: var(--ink-title);
}

.opsInsight {
  margin-top: 8px;
  font-size: 11px;
  line-height: 1.45;
  color: var(--ink-body);
}

.opsTarget,
.cTarget {
  margin-top: 6px;
  font-size: 10px;
  color: var(--ink-faint);
}

.mChart {
  width: 100%;
  height: 96px;
  margin-top: 4px;
}

.opsMiniChart {
  height: 78px;
  margin-top: 10px;
}

.opsFacts {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.opsFact {
  min-height: 40px;
  padding: 0;
  border-radius: 0;
  border: none;
  background: transparent;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 2px;
}

.opsFactLabel {
  font-size: 10px;
}

.opsFactValue {
  font-size: 14px;
  line-height: 1;
  font-weight: 900;
  color: var(--ink-title);
}

.cardActions {
  margin-top: 12px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 10px;
  border-top: 1px solid rgba(15, 23, 42, 0.035);
}

.miniAction {
  appearance: none;
  border: 1px solid rgba(15, 23, 42, 0.04);
  background: rgba(255, 255, 255, 0.12);
  color: rgba(51, 65, 85, 0.88);
  min-height: 26px;
  padding: 0 9px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 800;
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease;
}

.miniAction:hover {
  background: rgba(255, 255, 255, 0.22);
  border-color: rgba(15, 23, 42, 0.08);
}

.miniAction.primary {
  background: rgba(var(--accent-rgb), 0.92);
  border-color: rgba(var(--accent-rgb), 0.92);
  color: rgba(255, 255, 255, 0.96);
}

.emptyMini {
  height: 78px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px dashed rgba(15, 23, 42, 0.05);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
}

.cycleStats {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.cycleStat {
  min-height: 54px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  border: 1px solid rgba(15, 23, 42, 0.03);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.08);
}

.cycleLabel {
  font-size: 11px;
  font-weight: 800;
}

.cycleValue {
  font-weight: 950;
  letter-spacing: -0.35px;
  font-size: 16px;
  color: var(--ink-title);
}

.dList {
  display: grid;
  gap: 8px;
}

.dRow {
  width: 100%;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.75);
  border-radius: 14px;
  padding: 9px 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  cursor: pointer;
  text-align: left;
  transition:
    transform 0.15s ease,
    box-shadow 0.15s ease,
    background 0.15s ease;
}

.dRow:hover {
  transform: translateY(-1px);
  box-shadow: 0 14px 40px rgba(2, 6, 23, 0.08);
  background: rgba(255, 255, 255, 0.92);
}

.dMain {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.dName {
  font-weight: 950;
  letter-spacing: -0.2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dSub {
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dRight {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.dEmpty {
  padding: 10px;
  border-radius: 14px;
  border: 1px dashed rgba(15, 23, 42, 0.10);
  background: rgba(15, 23, 42, 0.02);
  font-size: 11px;
}

.chartGrid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  align-items: start;
}

.chartCard {
  padding: 14px;
  min-height: 292px;
  display: flex;
  flex-direction: column;
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.02);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.2), rgba(255, 255, 255, 0.1));
  box-shadow: none;
}

.chartHeroCard {
  grid-column: 1 / -1;
  min-height: 0;
}

.analysisSplit {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(280px, 0.85fr);
  gap: 24px;
  align-items: stretch;
}

.analysisMain,
.analysisSide {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.analysisSide {
  padding-left: 20px;
  border-left: 1px solid rgba(15, 23, 42, 0.05);
}

.aiInsight {
  margin-bottom: 8px;
  font-size: 11px;
  color: var(--ink-muted);
}

.cHead {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 6px;
}

.cHeadMain {
  min-width: 0;
}

.cMetric {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.cMetricValue {
  font-size: 22px;
  line-height: 1;
  font-weight: 950;
  letter-spacing: -0.4px;
  color: var(--ink-title);
}

.cMetricMeta {
  font-size: 11px;
  color: var(--ink-muted);
}

.cSummary {
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.cInsight {
  flex: 1;
  min-width: 180px;
  font-size: 11px;
  color: var(--ink-body);
}

.deltaBadge {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 800;
  border: 1px solid rgba(15, 23, 42, 0.04);
  background: rgba(255, 255, 255, 0.12);
  color: rgba(100, 116, 139, 0.9);
}

.deltaBadge.up {
  color: rgba(36, 52, 71, 0.95);
  background: rgba(71, 85, 105, 0.08);
  border-color: rgba(71, 85, 105, 0.14);
}

.deltaBadge.down {
  color: rgba(15, 23, 42, 0.9);
  background: rgba(15, 23, 42, 0.06);
  border-color: rgba(15, 23, 42, 0.14);
}

.deltaBadge.flat,
.deltaBadge.na,
.deltaBadge.static {
  color: rgba(100, 116, 139, 0.9);
}

.chartCard :deep(.h2) {
  margin: 0;
  font-size: 15px;
  line-height: 1.15;
  letter-spacing: -0.2px;
  color: var(--ink-title);
}

.meta {
  font-size: 11px;
  color: var(--ink-muted);
}

.chartBox {
  width: 100%;
  height: 156px;
  flex: 1;
  min-height: 0;
}

.chartBoxLg {
  height: 182px;
}

.chartBoxSm {
  height: 126px;
}

.emptyChart {
  height: 156px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px dashed rgba(15, 23, 42, 0.05);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
}

.emptyChartLg {
  height: 182px;
}

.emptyChartSm {
  height: 126px;
}

.xlabels {
  margin-top: 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.xlabels.compact {
  margin-top: 8px;
}

.xlab {
  font-size: 10px;
  text-align: center;
}

.pieWrap {
  display: grid;
  grid-template-columns: 176px 1fr;
  gap: 10px;
  align-items: center;
}

.pieShell {
  position: relative;
  width: 176px;
  height: 176px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pie {
  width: 176px;
  height: 176px;
}

.pieCenter {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  z-index: 1;
  pointer-events: none;
}

.pieCenterValue {
  font-size: 22px;
  line-height: 1;
  font-weight: 950;
  color: var(--ink-title);
}

.pieCenterLabel {
  font-size: 10px;
}

.legend {
  display: grid;
  gap: 6px;
}

.legRow {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 0;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
}

.legRow:last-child {
  border-bottom: none;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  box-shadow: 0 0 0 4px rgba(15, 23, 42, 0.06);
}

.legName {
  font-weight: 900;
  letter-spacing: -0.2px;
  font-size: 12px;
  color: var(--ink-title);
}

.legCnt {
  font-size: 11px;
  margin-top: 2px;
  color: var(--ink-muted);
}

.memberBox {
  height: auto;
}

.memberSummary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 8px;
}

.memberSummaryItem {
  min-height: 48px;
  padding: 8px 10px;
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.03);
  background: rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 4px;
}

.memberSummaryLabel {
  font-size: 10px;
}

.memberSummaryValue {
  font-size: 14px;
  line-height: 1.15;
  font-weight: 900;
  color: var(--ink-title);
}

.memberList {
  display: grid;
  gap: 2px;
}

.mRow {
  display: grid;
  grid-template-columns: 28px 110px 1fr 64px;
  align-items: center;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
}

.mRow:last-child {
  border-bottom: none;
}

.mRank {
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.05);
  color: rgba(51, 65, 85, 0.78);
  font-size: 10px;
  font-weight: 900;
}

.mIdentity {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.mName {
  font-weight: 950;
  letter-spacing: -0.2px;
  font-size: 12px;
  color: var(--ink-strong);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mShare {
  font-size: 10px;
}

.mBar {
  height: 10px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
  overflow: hidden;
}

.mBarIn {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(15, 23, 42, 0.72), rgba(71, 85, 105, 0.72));
  box-shadow: none;
}

.mVal {
  text-align: right;
  font-size: 11px;
  font-weight: 800;
  color: var(--ink-muted);
}

.mEmpty {
  padding: 10px 0;
  border-top: 1px dashed rgba(15, 23, 42, 0.10);
  font-size: 11px;
}

@media (max-width: 1180px) {
  .heroTop {
    flex-direction: column;
  }
  .heroActionPane {
    min-width: 0;
    max-width: none;
    width: 100%;
    align-items: flex-start;
  }
  .heroInsightRow {
    justify-content: flex-start;
    max-width: none;
  }
  .heroActionRow {
    justify-content: flex-start;
  }
  .heroAside {
    grid-template-columns: 1fr;
  }
  .heroStats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .focusGrid {
    grid-template-columns: 1fr;
  }
  .opsBand {
    grid-template-columns: 1fr;
  }
  .chartGrid {
    grid-template-columns: 1fr;
  }
  .analysisSplit {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  .analysisSide {
    padding-left: 0;
    padding-top: 10px;
    border-left: none;
    border-top: 1px solid rgba(15, 23, 42, 0.05);
  }
  .pieWrap {
    grid-template-columns: 1fr;
  }
  .memberSummary {
    grid-template-columns: 1fr;
  }
  .filterBar {
    flex-direction: column;
    align-items: stretch;
  }
  .filterChip.wide {
    max-width: 100%;
  }
  .fRight {
    justify-content: flex-start;
  }
  .fSelect {
    width: 100%;
    max-width: 420px;
  }
}

@media (max-width: 760px) {
  .head {
    flex-direction: column;
    align-items: stretch;
  }
  .heroStats {
    grid-template-columns: 1fr;
  }
  .heroInsightRow {
    gap: 8px;
  }
  .heroAside {
    grid-template-columns: 1fr;
  }
  .heroStat {
    min-height: 60px;
    padding: 10px 12px;
  }
  .cHead {
    flex-direction: column;
    gap: 6px;
  }
  .cMetric {
    align-items: flex-start;
  }
  .cycleStats {
    grid-template-columns: 1fr;
  }
  .cycleStat {
    min-height: 54px;
    padding: 8px 10px;
  }
  .focusPrimary .focusList,
  .focusSecondary .focusList {
    max-height: none;
    overflow: visible;
  }
  .focusRow {
    flex-direction: column;
    align-items: flex-start;
  }
  .focusRight {
    width: 100%;
    justify-content: space-between;
  }
  .pieWrap {
    grid-template-columns: 1fr;
  }
  .opsFacts,
  .memberSummary {
    grid-template-columns: 1fr;
  }
  .opsCell {
    padding: 12px 0;
    border-right: none;
    border-bottom: 1px solid rgba(15, 23, 42, 0.05);
  }
  .opsCell:last-child {
    border-bottom: none;
  }
  .opsMain {
    flex-direction: column;
    align-items: flex-start;
  }
  .opsSideMetric {
    align-items: flex-start;
  }
  .cardActions {
    gap: 6px;
  }
  .mRow {
    grid-template-columns: 24px 74px 1fr 52px;
  }
}
</style>
