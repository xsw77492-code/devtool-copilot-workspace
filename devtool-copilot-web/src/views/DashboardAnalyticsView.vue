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

const statusPie = computed(() => {
  const dist = data.value?.taskStatusDist || []
  const colors: Record<string, string> = {
    TODO: 'rgba(15,23,42,0.18)',
    DOING: 'rgba(6,182,212,0.86)',
    DONE: 'rgba(20,184,166,0.92)'
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
  const rows = data.value?.memberActivity7d || []
  const maxV = Math.max(1, ...rows.map((x) => Number(x.actionCount || 0)))
  return rows.map((x) => {
    const name = String(x.username || 'User').trim() || 'User'
    const short = name.length > 10 ? name.slice(0, 10) + '…' : name
    const v = Number(x.actionCount || 0)
    const pct = Math.max(6, Math.round((v / maxV) * 100))
    return {
      userId: Number(x.userId || 0),
      name,
      short,
      value: v,
      width: `${pct}%`
    }
  })
})

const throughputBars = computed(() => {
  const pts = data.value?.throughputTrend || []
  return pts.map((x) => ({ label: x.day, value: Number(x.count || 0) }))
})

const throughputXLabels = computed(() => sparseLabels(throughputBars.value.map((x) => x.label.slice(5))))

const wipTotal = computed(() => Number(data.value?.wipTotal || 0))
const cycle = computed(() => data.value?.cycleTime || null)

const WIP_LIMIT = 8
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

function openTask(pid: number, tid: number): void {
  router.push({ name: 'task-detail', params: { projectId: pid, taskId: tid } })
}

const myActions = computed(() => data.value?.myActions || [])
const riskTasks = computed(() => data.value?.riskTasks || [])
const topDiscussedTasks = computed(() => data.value?.topDiscussedTasks || [])

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
  <div class="page lightPage">
    <div class="head">
      <div class="left">
        <h1 class="h1">Dashboard</h1>
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

      <div class="filterBar lightPanel">
        <div class="fLeft">
          <div class="fTitle">筛选</div>
          <div class="muted fDesc">{{ rangeText }}</div>
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

      <div class="kpiGrid">
        <div v-for="c in kpiCards" :key="c.key" class="kpiCard">
          <div class="kTop">
            <div class="kLabel muted">{{ c.label }}</div>
            <div class="kBadges">
              <span class="kBadge" :class="c.mom.tone">{{ c.mom.text }}</span>
              <span class="kBadge ghost" :class="c.yoy.tone">{{ c.yoy.text }}</span>
            </div>
          </div>
          <div class="kMain">
            <div class="kVal">{{ c.valueText }}</div>
            <svg v-if="c.spark" class="spark" viewBox="0 0 92 28" preserveAspectRatio="none">
              <path class="sparkLine" :d="c.spark" />
            </svg>
          </div>
          <div v-if="(c as any).barPct" class="bar">
            <div class="barIn" :style="{ width: (c as any).barPct }" />
          </div>
        </div>
      </div>

      <div class="actionGrid">
        <section class="panel lightPanel actionCard">
          <div class="aHead">
            <div class="aTitle">My Actions</div>
            <div class="muted aMeta">{{ myActions.length }}</div>
          </div>
          <div class="aList">
            <button v-for="t in myActions" :key="t.taskId" class="aRow" @click="openTask(t.projectId, t.taskId)">
              <div class="aMain">
                <div class="aName">{{ t.title }}</div>
                <div class="muted aSub">{{ t.projectName || `#${t.projectId}` }}</div>
              </div>
              <div class="aRight">
                <span class="sPill" :class="statusTone(t.status)">{{ statusText(t.status) }}</span>
                <span class="muted aDue">{{ fmtDueText(t.dueTime) }}</span>
              </div>
            </button>
            <div v-for="i in Math.max(0, 5 - myActions.length)" :key="`ma-ph-${i}`" class="aRow placeholder" />
          </div>
        </section>

        <section class="panel lightPanel actionCard">
          <div class="aHead">
            <div class="aTitle">At Risk</div>
            <div class="muted aMeta">{{ riskTasks.length }}</div>
          </div>
          <div class="aList">
            <button v-for="t in riskTasks" :key="t.taskId" class="aRow" @click="openTask(t.projectId, t.taskId)">
              <div class="aMain">
                <div class="aName">{{ t.title }}</div>
                <div class="muted aSub">{{ t.projectName || `#${t.projectId}` }}</div>
              </div>
              <div class="aRight">
                <span class="sPill risk">OVERDUE</span>
                <span class="muted aDue">{{ fmtDueText(t.dueTime) }}</span>
              </div>
            </button>
            <div v-for="i in Math.max(0, 5 - riskTasks.length)" :key="`rk-ph-${i}`" class="aRow placeholder" />
          </div>
        </section>

        <section class="panel lightPanel actionCard">
          <div class="aHead">
            <div class="aTitle">Top Discussed</div>
            <div class="muted aMeta">{{ topDiscussedTasks.length }}</div>
          </div>
          <div class="aList">
            <button
              v-for="t in topDiscussedTasks"
              :key="t.taskId"
              class="aRow"
              @click="openTask(t.projectId, t.taskId)"
            >
              <div class="aMain">
                <div class="aName">{{ t.title }}</div>
                <div class="muted aSub">{{ t.projectName || `#${t.projectId}` }}</div>
              </div>
              <div class="aRight">
                <span class="cntPill">{{ Number(t.commentCount || 0) }}</span>
                <span class="sPill" :class="statusTone(t.status)">{{ statusText(t.status) }}</span>
              </div>
            </button>
            <div v-for="i in Math.max(0, 5 - topDiscussedTasks.length)" :key="`td-ph-${i}`" class="aRow placeholder" />
          </div>
        </section>
      </div>

      <div class="mgmtGrid">
        <section class="panel lightPanel mgmtCard">
          <div class="mHead">
            <div class="mTitle">WIP</div>
            <div class="mRight">
              <span v-if="wipHigh" class="wipPill">High</span>
              <div class="muted mMeta">当前进行中</div>
            </div>
          </div>
          <div class="mVal">{{ fmtNum(wipTotal) }}</div>
        </section>

        <section class="panel lightPanel mgmtCard">
          <div class="mHead">
            <div class="mTitle">Throughput</div>
            <div class="muted mMeta">{{ rangeMeta }}</div>
          </div>
          <div class="mChart">
            <bar-chart :bars="throughputBars" :height="96" interactive @select="(b) => openThroughputDay(b.label)" />
          </div>
          <div class="xlabels">
            <span v-for="l in throughputXLabels" :key="l" class="xlab muted">{{ l }}</span>
          </div>
        </section>

        <section class="panel lightPanel mgmtCard">
          <div class="mHead">
            <div class="mTitle">Cycle Time</div>
            <div class="muted mMeta">{{ rangeMeta }}</div>
          </div>
          <div class="mStats">
            <div class="mStat">
              <div class="muted sLabel">P50</div>
              <div class="sVal">{{ (cycle?.p50Days ?? 0).toFixed(2) }}d</div>
            </div>
            <div class="mStat">
              <div class="muted sLabel">P90</div>
              <div class="sVal">{{ (cycle?.p90Days ?? 0).toFixed(2) }}d</div>
            </div>
            <div class="mStat">
              <div class="muted sLabel">Avg</div>
              <div class="sVal">{{ (cycle?.avgDays ?? 0).toFixed(2) }}d</div>
            </div>
          </div>
          <div class="muted mMeta">样本 {{ fmtNum(cycle?.sampleCount ?? 0) }}</div>
        </section>
      </div>

      <div class="grid">
        <section class="panel lightPanel chartCard">
          <div class="cHead">
            <div class="h2">本周任务趋势</div>
            <div class="muted meta">{{ rangeMeta }}</div>
          </div>
          <div class="chartBox">
            <line-chart :points="taskLine" :height="190" />
          </div>
          <div class="xlabels">
            <span v-for="l in taskXLabels" :key="l" class="xlab muted">{{ l }}</span>
          </div>
        </section>

        <section class="panel lightPanel chartCard">
          <div class="cHead">
            <div class="h2">AI 调用趋势</div>
            <div class="muted meta">{{ rangeMeta }}</div>
          </div>
          <div class="chartBox">
            <bar-chart :bars="aiBars" :height="190" />
          </div>
          <div class="xlabels">
            <span v-for="l in aiXLabels" :key="l" class="xlab muted">{{ l }}</span>
          </div>
        </section>

        <section class="panel lightPanel chartCard">
          <div class="cHead">
            <div class="h2">任务状态分布</div>
            <div class="muted meta">按筛选口径</div>
          </div>
          <div class="pieWrap">
            <div class="pie">
              <pie-chart :slices="statusPie" :size="200" />
            </div>
            <div class="legend">
              <div v-for="s in statusPie" :key="s.label" class="legRow">
                <span class="dot" :style="{ background: s.color || 'rgba(15,23,42,0.18)' }" />
                <div class="legMain">
                  <div class="legName">{{ s.label }}</div>
                  <div class="muted legCnt">{{ fmtNum(s.value) }}</div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section class="panel lightPanel chartCard">
          <div class="cHead">
            <div class="h2">成员活跃度</div>
            <div class="muted meta">{{ rangeMeta }}</div>
          </div>
          <div class="chartBox memberBox">
            <div class="memberList">
              <div v-for="m in memberActivityRows" :key="m.userId" class="mRow">
                <div class="mName" :title="m.name">{{ m.short }}</div>
                <div class="mBar">
                  <div class="mBarIn" :style="{ width: m.width }" />
                </div>
                <div class="mVal muted">{{ fmtNum(m.value) }}</div>
              </div>
              <div v-if="!memberActivityRows.length" class="mEmpty muted">当前口径下暂无活跃数据</div>
            </div>
          </div>
          <div class="hint muted">统计口径：任务时间线 + 项目动态（{{ rangeMeta }}）</div>
        </section>
      </div>
    </n-spin>
  </div>
</template>

<style scoped>
.lightPage {
  background: #ffffff;
  color: #0f172a;
}

.head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.sub {
  margin-top: 8px;
  font-size: 13px;
}

.filterBar {
  border-radius: 18px;
  padding: 12px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.fLeft {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.fTitle {
  font-weight: 950;
  letter-spacing: -0.4px;
}

.fDesc {
  font-size: 12px;
}

.fRight {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px 12px;
}

.fGroup {
  display: flex;
  align-items: center;
  gap: 10px;
}

.fLabel {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: -0.1px;
}

.fSelect {
  width: 220px;
}

.rangePills {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 4px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.75);
}

.pill {
  appearance: none;
  border: none;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: -0.2px;
  color: rgba(15, 23, 42, 0.78);
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
  color: rgba(2, 6, 23, 0.92);
  background:
    radial-gradient(420px 120px at 15% 0%, rgba(6, 182, 212, 0.20), transparent 60%),
    rgba(15, 23, 42, 0.04);
  box-shadow: 0 10px 24px rgba(2, 6, 23, 0.10);
}

.fDate {
  width: 250px;
}

.kpiGrid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.kpiCard {
  border-radius: 18px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background:
    radial-gradient(900px 380px at 10% 0%, rgba(6, 182, 212, 0.10), transparent 55%),
    #ffffff;
  box-shadow: 0 16px 45px rgba(2, 6, 23, 0.06);
  padding: 14px 14px;
  min-height: 92px;
}

.kTop {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.kLabel {
  font-weight: 800;
  font-size: 12px;
  letter-spacing: -0.1px;
}

.kBadges {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  flex-wrap: wrap;
}

.kBadge {
  padding: 4px 8px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.65);
  font-size: 11px;
  font-weight: 900;
  letter-spacing: -0.1px;
  color: rgba(15, 23, 42, 0.72);
}

.kBadge.ghost {
  background: rgba(255, 255, 255, 0.45);
}

.kBadge.up {
  color: rgba(2, 6, 23, 0.92);
  border-color: rgba(6, 182, 212, 0.28);
  background: radial-gradient(220px 80px at 10% 0%, rgba(6, 182, 212, 0.18), rgba(255, 255, 255, 0.55));
}

.kBadge.down {
  color: rgba(2, 6, 23, 0.92);
  border-color: rgba(15, 23, 42, 0.14);
  background: rgba(15, 23, 42, 0.03);
}

.kBadge.flat {
  border-color: rgba(15, 23, 42, 0.10);
  background: rgba(15, 23, 42, 0.02);
}

.kBadge.na {
  color: rgba(15, 23, 42, 0.48);
  border-color: rgba(15, 23, 42, 0.08);
  background: rgba(15, 23, 42, 0.02);
}

.kMain {
  margin-top: 10px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 10px;
}

.kVal {
  font-size: 26px;
  font-weight: 950;
  letter-spacing: -0.6px;
}

.actionGrid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  align-items: start;
}

.actionCard {
  padding: 14px 14px;
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  height: 332px;
}

.aHead {
  display: grid;
  grid-template-columns: 132px 1fr;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  min-height: 40px;
}

.aTitle {
  font-weight: 950;
  letter-spacing: -0.4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.aMeta {
  font-size: 12px;
  text-align: right;
}

.aList {
  flex: 1;
  min-height: 0;
  display: grid;
  gap: 8px;
  overflow: auto;
  padding-right: 2px;
}

.aRow {
  width: 100%;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.75);
  border-radius: 14px;
  padding: 10px 10px;
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

.aRow:hover {
  transform: translateY(-1px);
  box-shadow: 0 14px 40px rgba(2, 6, 23, 0.08);
  background: rgba(255, 255, 255, 0.92);
}

.aRow.placeholder {
  border-style: dashed;
  background: rgba(15, 23, 42, 0.02);
  cursor: default;
}

.aRow.placeholder:hover {
  transform: none;
  box-shadow: none;
  background: rgba(15, 23, 42, 0.02);
}

.aMain {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.aName {
  font-weight: 950;
  letter-spacing: -0.2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.aSub {
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.aRight {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.aDue {
  font-size: 12px;
}

.aEmpty {
  padding: 10px 10px;
  border-radius: 14px;
  border: 1px dashed rgba(15, 23, 42, 0.10);
  background: rgba(15, 23, 42, 0.02);
  font-size: 12px;
}

.sPill {
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 950;
  letter-spacing: -0.1px;
  border: 1px solid rgba(15, 23, 42, 0.10);
  background: rgba(15, 23, 42, 0.02);
  color: rgba(15, 23, 42, 0.70);
}

.sPill.todo {
  background: rgba(15, 23, 42, 0.02);
  border-color: rgba(15, 23, 42, 0.10);
}

.sPill.doing {
  color: rgba(2, 6, 23, 0.92);
  border-color: rgba(6, 182, 212, 0.26);
  background: radial-gradient(220px 80px at 10% 0%, rgba(6, 182, 212, 0.18), rgba(255, 255, 255, 0.55));
}

.sPill.done {
  color: rgba(2, 6, 23, 0.92);
  border-color: rgba(20, 184, 166, 0.26);
  background: radial-gradient(220px 80px at 10% 0%, rgba(20, 184, 166, 0.18), rgba(255, 255, 255, 0.55));
}

.sPill.risk {
  color: rgba(2, 6, 23, 0.92);
  border-color: rgba(15, 23, 42, 0.18);
  background: rgba(15, 23, 42, 0.05);
}

.cntPill {
  min-width: 26px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 8px;
  border-radius: 999px;
  border: 1px solid rgba(6, 182, 212, 0.26);
  background: rgba(6, 182, 212, 0.08);
  color: rgba(2, 6, 23, 0.92);
  font-size: 11px;
  font-weight: 950;
  letter-spacing: -0.1px;
}

.mgmtGrid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  align-items: start;
}

.mgmtCard {
  padding: 14px 14px;
  border-radius: 18px;
}

.mHead {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.mRight {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.mTitle {
  font-weight: 950;
  letter-spacing: -0.4px;
}

.wipPill {
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 950;
  letter-spacing: -0.1px;
  border: 1px solid rgba(15, 23, 42, 0.14);
  background: rgba(15, 23, 42, 0.05);
  color: rgba(2, 6, 23, 0.92);
}

.mMeta {
  font-size: 12px;
}

.mVal {
  margin-top: 10px;
  font-size: 30px;
  font-weight: 950;
  letter-spacing: -0.7px;
}

.mHint {
  margin-top: 8px;
  font-size: 12px;
}

.mChart {
  margin-top: 8px;
  width: 100%;
  height: 96px;
}

.mStats {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.mStat {
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.70);
  padding: 10px 10px;
}

.sLabel {
  font-size: 12px;
  font-weight: 900;
}

.sVal {
  margin-top: 6px;
  font-weight: 950;
  letter-spacing: -0.3px;
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
  padding: 10px 10px;
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
  font-size: 12px;
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
  padding: 10px 10px;
  border-radius: 14px;
  border: 1px dashed rgba(15, 23, 42, 0.10);
  background: rgba(15, 23, 42, 0.02);
  font-size: 12px;
}

.spark {
  width: 92px;
  height: 28px;
  opacity: 0.95;
}

.sparkLine {
  fill: none;
  stroke: rgba(6, 182, 212, 0.86);
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.bar {
  margin-top: 10px;
  height: 8px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
  overflow: hidden;
}

.barIn {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(20, 184, 166, 0.92), rgba(6, 182, 212, 0.86));
}

.grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  align-items: start;
}

.lightPanel {
  background:
    radial-gradient(900px 420px at 12% 0%, rgba(6, 182, 212, 0.10), transparent 58%),
    #ffffff;
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 16px 45px rgba(2, 6, 23, 0.06);
}

.chartCard {
  padding: 14px 14px;
  border-radius: 18px;
}

.cHead {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 10px;
}

.meta {
  font-size: 12px;
}

.chartBox {
  width: 100%;
  height: 190px;
}

.xlabels {
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.xlab {
  font-size: 11px;
  text-align: center;
}

.pieWrap {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 10px;
  align-items: center;
}

.pie {
  width: 200px;
  height: 200px;
}

.legend {
  display: grid;
  gap: 10px;
}

.legRow {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 10px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.75);
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
}

.legCnt {
  font-size: 12px;
  margin-top: 2px;
}

.hint {
  margin-top: 8px;
  font-size: 12px;
}

.memberBox {
  height: auto;
}

.memberList {
  display: grid;
  gap: 10px;
}

.mRow {
  display: grid;
  grid-template-columns: 110px 1fr 64px;
  align-items: center;
  gap: 10px;
  padding: 10px 10px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.75);
}

.mName {
  font-weight: 950;
  letter-spacing: -0.2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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
  background: linear-gradient(90deg, rgba(20, 184, 166, 0.92), rgba(6, 182, 212, 0.78));
  box-shadow: 0 12px 28px rgba(2, 6, 23, 0.12);
}

.mVal {
  text-align: right;
  font-size: 12px;
  font-weight: 900;
}

.mEmpty {
  padding: 10px 10px;
  border-radius: 14px;
  border: 1px dashed rgba(15, 23, 42, 0.10);
  background: rgba(15, 23, 42, 0.02);
  font-size: 12px;
}

@media (max-width: 1180px) {
  .kpiGrid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .grid {
    grid-template-columns: 1fr;
  }
  .pieWrap {
    grid-template-columns: 1fr;
  }
  .actionGrid {
    grid-template-columns: 1fr;
  }
  .mgmtGrid {
    grid-template-columns: 1fr;
  }
  .filterBar {
    flex-direction: column;
    align-items: stretch;
  }
  .fRight {
    justify-content: flex-start;
  }
  .fSelect {
    width: 100%;
    max-width: 420px;
  }
}
</style>
