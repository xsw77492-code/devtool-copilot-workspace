import type { Task, PulseEvent } from '../api/task'

// ===================== 生命周期分析聚合工具 =====================

export interface LcPhase {
  status: string
  start: number
  end: number
  days: number
}

export interface TaskLc {
  taskId: number
  projectId: number
  projectName: string
  title: string
  status: string
  priority?: string
  assignee?: string
  assigneeId?: number | null
  dueTime?: string
  createdAt?: number
  doneAt?: number
  cycleDays: number
  phases: LcPhase[]
  switchCount: number
  wasteCount: number
  revertCount: number
  execDays: number
  waitDays: number
  flowEff: number
  score: number
  grade: string
  color: string
  flag: 'warn' | 'danger' | null
  overdue: boolean
}

export interface ProjectStats {
  projectId: number
  projectName: string
  total: number
  done: number
  p50: number
  p85: number
  flowEff: number
  onTimeRate: number
  stuck: number
  revert: number
}

export interface CenterData {
  tasks: TaskLc[]
  projects: ProjectStats[]
  total: number
  done: number
  doing: number
  todo: number
  p25: number
  p50: number
  p75: number
  p85: number
  onTimeRate: number
  flowEff: number
  stuckCount: number
  revertCount: number
  trend: Array<{ label: string; p50: number; p85: number; done: number }>
  dist: Array<{ label: string; count: number }>
  quartiles: Array<{ label: string; value: number }>
}

export interface ProjectPulseInput {
  projectId: number
  projectName: string
  tasks: Task[]
  events: PulseEvent[]
}

// ===================== 成员效能 =====================
export interface MemberStats {
  member: string
  memberId?: number | null
  taskCount: number
  doneCount: number
  doingCount: number
  todoCount: number
  p50: number
  flowEff: number
  onTimeRate: number
  stuckCount: number
  revertCount: number
  avgScore: number
}

export function analyzeMembers(tasks: TaskLc[]): MemberStats[] {
  const by = new Map<string, TaskLc[]>()
  for (const t of tasks) {
    const key = t.assignee || '未分配'
    const arr = by.get(key) || []
    arr.push(t)
    by.set(key, arr)
  }
  const out: MemberStats[] = []
  for (const [member, list] of by) {
    const doneCycles = list
      .filter((t) => t.status === 'DONE')
      .map((t) => t.cycleDays)
      .sort((a, b) => a - b)
    const withDue = list.filter((t) => t.dueTime)
    out.push({
      member,
      memberId: list[0].assigneeId,
      taskCount: list.length,
      doneCount: list.filter((t) => t.status === 'DONE').length,
      doingCount: list.filter((t) => t.status === 'DOING').length,
      todoCount: list.filter((t) => t.status === 'TODO').length,
      p50: quantile(doneCycles, 0.5),
      flowEff: list.length ? Math.round(list.reduce((s, t) => s + t.flowEff, 0) / list.length) : 0,
      onTimeRate: withDue.length ? Math.round((withDue.filter((t) => !t.overdue).length / withDue.length) * 100) : 100,
      stuckCount: list.filter((t) => t.flag === 'danger').length,
      revertCount: list.reduce((s, t) => s + t.revertCount, 0),
      avgScore: list.length ? Math.round(list.reduce((s, t) => s + t.score, 0) / list.length) : 0
    })
  }
  return out.sort((a, b) => b.taskCount - a.taskCount)
}

export interface AnalyzeOptions {
  projectId?: number
  rangeDays?: number
  status?: string
  sortBy?: 'cycle' | 'switch' | 'flow' | 'score'
  sortDesc?: boolean
}

const DAY = 86400000

export function statusLabel(st?: string) {
  const v = String(st || '').toUpperCase()
  if (v === 'DOING') return '进行中'
  if (v === 'DONE') return '已完成'
  return '待办'
}

export function daysText(days: number): string {
  if (days < 1) return `${Math.max(Math.round(days * 24), 1)} 小时`
  if (days >= 30) return `${Math.round((days / 30) * 10) / 10} 个月`
  return `${Math.round(days)} 天`
}

export function fmtDate(ts?: number): string {
  if (!ts) return ''
  const d = new Date(ts)
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${dd} ${hh}:${mm}`
}

function quantile(sorted: number[], p: number): number {
  if (!sorted.length) return 0
  const idx = (sorted.length - 1) * p
  const lo = Math.floor(idx)
  const hi = Math.ceil(idx)
  if (lo === hi) return sorted[lo]
  return sorted[lo] + (sorted[hi] - sorted[lo]) * (idx - lo)
}

// 由任务事件流重建生命周期分段
export function buildPhases(events: PulseEvent[], task: Task): { phases: LcPhase[]; doneAt?: number } {
  const evs = events
    .filter((e) => e.taskId === task.id && (e.createTime || e.createdAt))
    .map((e) => ({ ...e, ts: e.createdAt || (e.createTime ? Date.parse(e.createTime) : 0) }))
    .sort((a, b) => a.ts - b.ts)
  if (!evs.length) return { phases: [] }
  const phases: LcPhase[] = []
  let cur: string | null = null
  let segStart = 0
  let doneAt: number | undefined
  for (const e of evs) {
    if (e.type === 'CREATED') {
      cur = 'TODO'
      segStart = e.ts
      continue
    }
    if (e.type === 'STATUS_CHANGED' && cur) {
      if (e.ts > segStart) phases.push(makePhase(cur, segStart, e.ts))
      const m = String(e.detail || '').match(/→\s*([A-Za-z]+)/)
      cur = m ? m[1].toUpperCase() : cur === 'TODO' ? 'DOING' : 'DONE'
      segStart = e.ts
      if (cur === 'DONE') doneAt = e.ts
    }
  }
  let endTime = Date.now()
  if (task.status === 'DONE') {
    const lastChange = [...evs].reverse().find((e) => e.type === 'STATUS_CHANGED')
    if (lastChange) endTime = lastChange.ts + 60_000
    if (!doneAt) doneAt = lastChange ? lastChange.ts : Date.now()
  }
  if (cur && endTime > segStart) phases.push(makePhase(cur, segStart, endTime))
  return { phases, doneAt }
}

function makePhase(status: string, start: number, end: number): LcPhase {
  return { status, start, end, days: Math.max((end - start) / DAY, 0) }
}

function scoreTask(phases: LcPhase[], flowEff: number, revertCount: number, percentile: number | null): { score: number; grade: string; color: string } {
  const switches = Math.max(phases.length - 1, 0)
  const waste = Math.max(0, switches - 2)
  let score = 100
  score -= waste * 12
  score -= revertCount * 10
  const maxStuck = phases.reduce((m, p) => Math.max(m, p.days), 0)
  if (maxStuck > 3) score -= Math.min(25, (maxStuck - 3) * 3)
  if (flowEff < 40) score -= 10
  if (percentile !== null && percentile < 50) score += 5
  score = Math.max(0, Math.min(100, Math.round(score)))
  const grade = score >= 85 ? 'S' : score >= 70 ? 'A' : score >= 55 ? 'B' : score >= 40 ? 'C' : 'D'
  const color = score >= 85 ? '#10b981' : score >= 70 ? '#3b82f6' : score >= 55 ? '#f59e0b' : score >= 40 ? '#f97316' : '#ef4444'
  return { score, grade, color }
}

// 聚合全部项目数据 -> 中心数据
export function analyzeAll(inputs: ProjectPulseInput[], opts: AnalyzeOptions = {}): CenterData {
  const now = Date.now()
  const rangeDays = opts.rangeDays && opts.rangeDays > 0 ? opts.rangeDays : 0
  const from = rangeDays ? now - rangeDays * DAY : 0
  const statusFilter = opts.status && opts.status !== 'all' ? opts.status.toUpperCase() : ''

  // 1) 单任务分析
  const rawTasks: TaskLc[] = []
  for (const inp of inputs) {
    if (opts.projectId && inp.projectId !== opts.projectId) continue
    const pEvents = inp.events
    const pTasks = inp.tasks
    for (const t of pTasks) {
      const { phases, doneAt } = buildPhases(pEvents, t)
      if (!phases.length) continue
      const createdTs = t.createdAt || (t.createTime ? Date.parse(t.createTime) : 0) || phases[0].start
      if (from && createdTs < from && !doneAt) continue
      if (statusFilter && t.status.toUpperCase() !== statusFilter) continue
      let execDays = 0
      let waitDays = 0
      for (const p of phases) {
        if (p.status === 'DOING') execDays += p.days
        else waitDays += p.days
      }
      const cycleDays = ((doneAt || now) - phases[0].start) / DAY
      const switchCount = Math.max(phases.length - 1, 0)
      const revertCount = pEvents.filter(
        (e) => e.taskId === t.id && e.type === 'STATUS_CHANGED' && /DONE\s*->\s*(TODO|DOING)/.test(String(e.detail || ''))
      ).length
      const flowEff = execDays + waitDays > 0 ? Math.round((execDays / (execDays + waitDays)) * 100) : 100
      const due = t.dueTime ? Date.parse(t.dueTime) : 0
      const overdue = !!due && (doneAt || now) > due
      rawTasks.push({
        taskId: t.id,
        projectId: inp.projectId,
        projectName: inp.projectName,
        title: t.title,
        status: t.status,
        priority: t.priority,
        assignee: t.assignee,
        assigneeId: t.assigneeId,
        dueTime: t.dueTime,
        createdAt: createdTs || phases[0].start,
        doneAt,
        cycleDays,
        phases,
        switchCount,
        wasteCount: Math.max(0, switchCount - 2),
        revertCount,
        execDays,
        waitDays,
        flowEff,
        score: 0,
        grade: 'D',
        color: '#94a3b8',
        flag: null,
        overdue
      })
    }
  }

  // 2) 项目基线（P50/P85）用于停滞标记 + 分位
  const sortedCycles = rawTasks.map((t) => t.cycleDays).sort((a, b) => a - b)
  const p25 = quantile(sortedCycles, 0.25)
  const p50 = quantile(sortedCycles, 0.5)
  const p75 = quantile(sortedCycles, 0.75)
  const p85 = quantile(sortedCycles, 0.85)

  for (const t of rawTasks) {
    let less = 0
    for (const c of sortedCycles) if (c <= t.cycleDays) less++
    const percentile = sortedCycles.length ? Math.round((less / sortedCycles.length) * 100) : null
    const scored = scoreTask(t.phases, t.flowEff, t.revertCount, percentile)
    t.score = scored.score
    t.grade = scored.grade
    t.color = scored.color
    const warn = Math.max(p85, 3)
    const danger = Math.max(p85 * 2, 7)
    if (t.status === 'DONE' && t.doneAt && t.doneAt - (t.createdAt || 0) <= DAY) {
      t.flag = null
    } else if (t.cycleDays > danger) {
      t.flag = 'danger'
    } else if (t.cycleDays > warn) {
      t.flag = 'warn'
    }
  }

  // 3) 项目统计
  const projects: ProjectStats[] = []
  const byProject = new Map<number, TaskLc[]>()
  for (const t of rawTasks) {
    const arr = byProject.get(t.projectId) || []
    arr.push(t)
    byProject.set(t.projectId, arr)
  }
  for (const [pid, list] of byProject) {
    const cycles = list.map((t) => t.cycleDays).sort((a, b) => a - b)
    const flow = list.length ? Math.round(list.reduce((s, t) => s + t.flowEff, 0) / list.length) : 0
    const withDue = list.filter((t) => t.dueTime)
    const onTime = withDue.length ? Math.round((withDue.filter((t) => !t.overdue).length / withDue.length) * 100) : 100
    projects.push({
      projectId: pid,
      projectName: list[0].projectName || `项目 ${pid}`,
      total: list.length,
      done: list.filter((t) => t.status === 'DONE').length,
      p50: quantile(cycles, 0.5),
      p85: quantile(cycles, 0.85),
      flowEff: flow,
      onTimeRate: onTime,
      stuck: list.filter((t) => t.flag === 'danger').length,
      revert: list.reduce((s, t) => s + t.revertCount, 0)
    })
  }
  projects.sort((a, b) => b.total - a.total)

  // 4) 排序
  const sortKey = opts.sortBy || 'cycle'
  const desc = opts.sortDesc !== false
  rawTasks.sort((a, b) => {
    let va = 0
    let vb = 0
    if (sortKey === 'cycle') {
      va = a.cycleDays
      vb = b.cycleDays
    } else if (sortKey === 'switch') {
      va = a.switchCount
      vb = b.switchCount
    } else if (sortKey === 'flow') {
      va = a.flowEff
      vb = b.flowEff
    } else {
      va = a.score
      vb = b.score
    }
    return desc ? vb - va : va - vb
  })

  // 5) 趋势（按周聚合已完成任务）
  const trend = buildTrend(rawTasks)

  // 6) 分布直方图
  const dist = buildDist(rawTasks.map((t) => t.cycleDays))

  const withDueTotal = rawTasks.filter((t) => t.dueTime)
  return {
    tasks: rawTasks,
    projects,
    total: rawTasks.length,
    done: rawTasks.filter((t) => t.status === 'DONE').length,
    doing: rawTasks.filter((t) => t.status === 'DOING').length,
    todo: rawTasks.filter((t) => t.status === 'TODO').length,
    p25,
    p50,
    p75,
    p85,
    onTimeRate: withDueTotal.length ? Math.round((withDueTotal.filter((t) => !t.overdue).length / withDueTotal.length) * 100) : 100,
    flowEff: rawTasks.length ? Math.round(rawTasks.reduce((s, t) => s + t.flowEff, 0) / rawTasks.length) : 0,
    stuckCount: rawTasks.filter((t) => t.flag === 'danger').length,
    revertCount: rawTasks.reduce((s, t) => s + t.revertCount, 0),
    trend,
    dist,
    quartiles: [
      { label: 'P25', value: p25 },
      { label: 'P50', value: p50 },
      { label: 'P75', value: p75 },
      { label: 'P85', value: p85 }
    ]
  }
}

function buildTrend(tasks: TaskLc[]) {
  const done = tasks.filter((t) => t.status === 'DONE' && t.doneAt)
  if (!done.length) return []
  const weeks = new Map<string, number[]>()
  for (const t of done) {
    const d = new Date(t.doneAt as number)
    const start = new Date(d.getFullYear(), d.getMonth(), d.getDate())
    start.setDate(start.getDate() - ((start.getDay() + 6) % 7))
    const key = start.getTime()
    const arr = weeks.get(String(key)) || []
    arr.push(t.cycleDays)
    weeks.set(String(key), arr)
  }
  const keys = [...weeks.keys()].map(Number).sort((a, b) => a - b)
  return keys.map((k) => {
    const arr = (weeks.get(String(k)) || []).slice().sort((a, b) => a - b)
    return {
      label: `${new Date(k).getMonth() + 1}/${new Date(k).getDate()}`,
      p50: quantile(arr, 0.5),
      p85: quantile(arr, 0.85),
      done: arr.length
    }
  })
}

const DIST_BUCKETS: Array<{ max: number; label: string }> = [
  { max: 1, label: '<1天' },
  { max: 2, label: '1-2天' },
  { max: 3, label: '2-3天' },
  { max: 5, label: '3-5天' },
  { max: 7, label: '5-7天' },
  { max: 14, label: '7-14天' },
  { max: 30, label: '14-30天' },
  { max: Infinity, label: '30天+' }
]

function buildDist(cycles: number[]): Array<{ label: string; count: number }> {
  const out = DIST_BUCKETS.map((b) => ({ label: b.label, count: 0 }))
  for (const c of cycles) {
    let idx = DIST_BUCKETS.findIndex((b) => c < b.max)
    if (idx < 0) idx = DIST_BUCKETS.length - 1
    out[idx].count++
  }
  return out
}
