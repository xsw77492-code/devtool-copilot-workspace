import { apiGet } from './http'

export interface DashboardDayCountItem {
  day: string
  count: number
}

export interface DashboardStatusCountItem {
  status: string
  count: number
}

export interface DashboardMemberActivityItem {
  userId: number
  username: string | null
  actionCount: number
}

export interface DashboardTaskActionItem {
  taskId: number
  projectId: number
  projectName: string | null
  title: string
  status: string
  priority: string | null
  assigneeName: string | null
  dueTime: string | null
  commentCount: number | null
}

export interface DashboardCycleTimeStats {
  sampleCount: number
  p50Days: number
  p90Days: number
  avgDays: number
}

export interface DashboardDoneTaskItem {
  taskId: number
  projectId: number
  projectName: string | null
  title: string
  status: string
  assigneeName: string | null
  doneTime: string | null
}

export interface DashboardOverviewResponse {
  projectTotal: number
  taskTotal: number
  doneTaskTotal: number
  taskDoneRate: number
  aiCallTotal: number
  tasksCreatedThisWeek: number
  taskTrend7d: DashboardDayCountItem[]
  aiTrend7d: DashboardDayCountItem[]
  taskStatusDist: DashboardStatusCountItem[]
  memberActivity7d: DashboardMemberActivityItem[]
  myActions: DashboardTaskActionItem[]
  riskTasks: DashboardTaskActionItem[]
  topDiscussedTasks: DashboardTaskActionItem[]
  wipTotal: number
  throughputTrend: DashboardDayCountItem[]
  cycleTime: DashboardCycleTimeStats
}

export const dashboardApi = {
  overview: (params?: { projectId?: number; startDate?: string; endDate?: string; lite?: boolean }) =>
    apiGet<DashboardOverviewResponse>('/api/dashboard/overview', params as any),
  throughputTasks: (params: { projectId?: number; day: string }) =>
    apiGet<DashboardDoneTaskItem[]>('/api/dashboard/throughput/tasks', params as any)
}
