import { apiGet, apiPost } from './http'

export type TaskPriority = 'HIGH' | 'MEDIUM' | 'LOW'

export type ChatRole = 'user' | 'assistant'

export interface ChatMessageDTO {
  role: ChatRole
  content: string
}

export interface TaskPlan {
  title: string
  description: string
  priority: TaskPriority
  order: number
}

export interface AiChatHistoryItem {
  id: number
  projectId?: number | null
  prompt: string
  response: string
  createTime?: string
  createdAt?: number
}

export type AiRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'

export interface AiCodeReviewResult {
  riskLevel: AiRiskLevel
  report: string
}

export interface AiAgentDeliverable {
  type: string
  title: string
  url?: string | null
  content?: string | null
}

export interface AiAgentTask {
  title: string
  description: string
  priority: string
  checklist?: string[]
  deliverables?: AiAgentDeliverable[]
  sources?: string[]
}

export interface AiAgentSource {
  sourceId: string
  type: string
  refId?: number | null
  title?: string | null
  snippet?: string | null
}

export interface AiAgentPlanResponse {
  goal?: string | null
  tasks: AiAgentTask[]
  sources?: AiAgentSource[]
}

export interface AiAgentCrewResponse {
  pm: string
  techLead: string
  plan: AiAgentPlanResponse
}

export interface AiAgentApplyResponse {
  projectId: number
  taskIds: number[]
  requestedCount?: number
  createdCount?: number
  failedTitles?: string[] | null
}

async function postWithFallback<T>(paths: string[], body: any): Promise<T> {
  let lastErr: any = null
  for (const p of paths) {
    try {
      return await apiPost<T>(p, body)
    } catch (e: any) {
      lastErr = e
      const msg = String(e?.message || '')
      if (!msg.includes('接口不存在') && !msg.startsWith('请求失败(404)')) throw e
    }
  }
  throw lastErr || new Error('请求失败')
}

async function ssePostWithFallback(
  paths: string[],
  body: any,
  onEvent: (event: string, data: string) => void
): Promise<void> {
  const token = localStorage.getItem('dtc_token')
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`

  let lastErr: any = null
  for (const p of paths) {
    try {
      const resp = await fetch(p, { method: 'POST', headers, body: JSON.stringify(body) })
      if (!resp.ok || !resp.body) throw new Error(`请求失败(${resp.status})`)

      const reader = resp.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buf = ''

      function handleEventBlock(block: string) {
        const lines = block.split('\n')
        let event = ''
        const dataLines: string[] = []
        for (const line of lines) {
          if (line.startsWith('event:')) event = line.slice(6).trim()
          else if (line.startsWith('data:')) dataLines.push(line.slice(5))
        }
        const data = dataLines.join('\n')
        if (event) onEvent(event, data)
        return event
      }

      while (true) {
        const { value, done } = await reader.read()
        if (done) break
        buf += decoder.decode(value, { stream: true })
        while (true) {
          const idx = buf.indexOf('\n\n')
          if (idx < 0) break
          const block = buf.slice(0, idx).trim()
          buf = buf.slice(idx + 2)
          if (!block) continue
          handleEventBlock(block)
        }
      }
      return
    } catch (e: any) {
      lastErr = e
      const msg = String(e?.message || '')
      if (!msg.includes('接口不存在') && !msg.startsWith('请求失败(404)') && !msg.startsWith('请求失败(404')) throw e
    }
  }
  throw lastErr || new Error('请求失败')
}

export const aiApi = {
  async chat(input: { messages: ChatMessageDTO[]; projectId?: number }) {
    const res = await apiPost<{ reply: string }>('/ai/chat', input)
    return res.reply
  },

  async chatStream(
    input: { messages: ChatMessageDTO[]; projectId?: number },
    onDelta: (text: string) => void
  ): Promise<string> {
    const token = localStorage.getItem('dtc_token')
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (token) headers.Authorization = `Bearer ${token}`

    const resp = await fetch('/ai/chat/stream', {
      method: 'POST',
      headers,
      body: JSON.stringify(input)
    })
    if (!resp.ok || !resp.body) {
      throw new Error(`请求失败(${resp.status})`)
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buf = ''
    let full = ''

    function handleEventBlock(block: string) {
      const lines = block.split('\n')
      let event = ''
      const dataLines: string[] = []
      for (const line of lines) {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        else if (line.startsWith('data:')) dataLines.push(line.slice(5))
      }
      const data = dataLines.join('\n')
      if (event === 'delta') {
        full += data
        onDelta(data)
      } else if (event === 'error') {
        throw new Error(data.trim() || 'AI服务调用失败')
      }
      return event
    }

    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      while (true) {
        const idx = buf.indexOf('\n\n')
        if (idx < 0) break
        const block = buf.slice(0, idx).trim()
        buf = buf.slice(idx + 2)
        if (!block) continue
        const ev = handleEventBlock(block)
        if (ev === 'done') return full
      }
    }

    return full
  },

  async projectSummary(input: { projectId: number }) {
    const res = await apiPost<{ projectId: number; report: string }>('/ai/project/summary', input)
    return res.report
  },

  async projectSummaryStream(
    input: { projectId: number },
    onDelta: (text: string) => void
  ): Promise<string> {
    const token = localStorage.getItem('dtc_token')
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (token) headers.Authorization = `Bearer ${token}`

    const resp = await fetch('/ai/project/summary/stream', {
      method: 'POST',
      headers,
      body: JSON.stringify(input)
    })
    if (!resp.ok || !resp.body) {
      throw new Error(`请求失败(${resp.status})`)
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buf = ''
    let full = ''

    function handleEventBlock(block: string) {
      const lines = block.split('\n')
      let event = ''
      const dataLines: string[] = []
      for (const line of lines) {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        else if (line.startsWith('data:')) dataLines.push(line.slice(5))
      }
      const data = dataLines.join('\n')
      if (event === 'delta') {
        full += data
        onDelta(data)
      } else if (event === 'error') {
        throw new Error(data.trim() || 'AI服务调用失败')
      }
      return event
    }

    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      while (true) {
        const idx = buf.indexOf('\n\n')
        if (idx < 0) break
        const block = buf.slice(0, idx).trim()
        buf = buf.slice(idx + 2)
        if (!block) continue
        const ev = handleEventBlock(block)
        if (ev === 'done') return full
      }
    }

    return full
  },

  async taskSplit(input: { requirement: string }) {
    const list = await apiPost<TaskPlan[]>('/ai/task/split', input)
    return list.slice().sort((a, b) => (a.order || 0) - (b.order || 0))
  },

  async taskSplitStream(
    input: { requirement: string },
    onDelta: (text: string) => void
  ): Promise<TaskPlan[]> {
    const token = localStorage.getItem('dtc_token')
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (token) headers.Authorization = `Bearer ${token}`

    const resp = await fetch('/ai/task/split/stream', {
      method: 'POST',
      headers,
      body: JSON.stringify(input)
    })
    if (!resp.ok || !resp.body) {
      throw new Error(`请求失败(${resp.status})`)
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buf = ''
    let resultJson = ''

    function handleEventBlock(block: string) {
      const lines = block.split('\n')
      let event = ''
      const dataLines: string[] = []
      for (const line of lines) {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        else if (line.startsWith('data:')) dataLines.push(line.slice(5))
      }
      const data = dataLines.join('\n')
      if (event === 'delta') {
        onDelta(data)
      } else if (event === 'result') {
        resultJson = data
      } else if (event === 'error') {
        throw new Error(data.trim() || 'AI服务调用失败')
      }
      return event
    }

    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      while (true) {
        const idx = buf.indexOf('\n\n')
        if (idx < 0) break
        const block = buf.slice(0, idx).trim()
        buf = buf.slice(idx + 2)
        if (!block) continue
        const ev = handleEventBlock(block)
        if (ev === 'done') {
          const list = resultJson ? (JSON.parse(resultJson) as TaskPlan[]) : []
          return list.slice().sort((a, b) => (a.order || 0) - (b.order || 0))
        }
      }
    }

    const list = resultJson ? (JSON.parse(resultJson) as TaskPlan[]) : []
    return list.slice().sort((a, b) => (a.order || 0) - (b.order || 0))
  },

  async historyList(input?: { projectId?: number | null; limit?: number }): Promise<AiChatHistoryItem[]> {
    const list = await apiGet<AiChatHistoryItem[]>('/api/ai/history/list', {
      projectId: input?.projectId ?? undefined,
      limit: input?.limit ?? 100
    })
    return list
      .map((h) => ({
        ...h,
        createdAt: h.createTime ? Date.parse(h.createTime) : undefined
      }))
      .slice()
      .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
  },

  async historyDelete(ids: number[]) {
    await apiPost<number>('/api/ai/history/delete', { ids })
  },

  async historyClear(projectId?: number | null) {
    await apiPost<number>('/api/ai/history/clear', projectId ? { projectId } : {})
  },

  async codeReview(input: { language?: string; code: string }) {
    return apiPost<AiCodeReviewResult>('/ai/code/review', input)
  },

  async agentPlan(input: { requirement: string; projectId?: number | null }): Promise<AiAgentPlanResponse> {
    return postWithFallback<AiAgentPlanResponse>(['/ai/agent/plan', '/api/ai/agent/plan'], {
      requirement: input.requirement,
      projectId: input.projectId ?? undefined
    })
  },

  async agentCrew(input: { requirement: string; projectId?: number | null }): Promise<AiAgentCrewResponse> {
    return postWithFallback<AiAgentCrewResponse>(['/ai/agent/crew', '/api/ai/agent/crew'], {
      requirement: input.requirement,
      projectId: input.projectId ?? undefined
    })
  },

  async agentCrewStream(
    input: { requirement: string; projectId?: number | null },
    onStage: (stage: 'pm' | 'tech' | 'plan' | 'error' | 'done', data: string) => void
  ): Promise<AiAgentPlanResponse> {
    let plan: AiAgentPlanResponse | null = null
    await ssePostWithFallback(
      ['/ai/agent/crew/stream', '/api/ai/agent/crew/stream'],
      { requirement: input.requirement, projectId: input.projectId ?? undefined },
      (event, data) => {
        if (event === 'plan') {
          try {
            plan = JSON.parse(data || '{}') as AiAgentPlanResponse
          } catch {
          }
        }
        onStage(event as any, data)
      }
    )
    return plan || { goal: null, tasks: [] }
  },

  async agentPlanStream(
    input: { requirement: string; projectId?: number | null },
    onEvent: (event: 'result' | 'error' | 'done', data: string) => void
  ): Promise<AiAgentPlanResponse> {
    let plan: AiAgentPlanResponse | null = null
    await ssePostWithFallback(
      ['/ai/agent/plan/stream', '/api/ai/agent/plan/stream'],
      { requirement: input.requirement, projectId: input.projectId ?? undefined },
      (event, data) => {
        if (event === 'result') {
          try {
            plan = JSON.parse(data || '{}') as AiAgentPlanResponse
          } catch {
          }
        }
        onEvent(event as any, data)
      }
    )
    return plan || { goal: null, tasks: [] }
  },

  async agentApply(input: { projectId: number; plan: AiAgentPlanResponse }): Promise<AiAgentApplyResponse> {
    return postWithFallback<AiAgentApplyResponse>(['/ai/agent/apply', '/api/ai/agent/apply'], {
      projectId: input.projectId,
      plan: input.plan
    })
  }
}
