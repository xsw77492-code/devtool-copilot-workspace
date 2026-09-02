import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { aiApi, type AiAgentPlanResponse } from '../api/ai'

export interface AiChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  createdAt: number
  historyId?: number | null
  attachments?: Array<{ assetId: number; filename: string; extractedText: string }>
}

let seq = 0
function localId() {
  seq += 1
  return `m${Date.now().toString(36)}${seq}${Math.random().toString(36).slice(2, 7)}`
}

/** 触发 Agent 执行分解的关键词 */
const PLAN_KEYWORDS = [
  '执行分解',
  '做方案',
  '做个方案',
  '出方案',
  '出个方案',
  '方案设计',
  '整体规划',
  '项目规划',
  '怎么安排',
  '怎么推进',
  '如何推进',
  '拆解',
  '分解'
]

function looksLikePlanRequest(text: string) {
  const t = String(text || '').trim()
  if (!t) return false
  return PLAN_KEYWORDS.some((k) => t.includes(k))
}

export const useAiChatStore = defineStore('aiChat', () => {
  const messages = ref<AiChatMessage[]>([])
  const sending = ref(false)
  const lastAgentPlanMessageId = ref<string | null>(null)
  const autoOpenNonce = ref(0)
  const autoOpenTask = ref<{ projectId: number; taskId: number } | null>(null)

  const planCache = ref<{ messageId: string; plan: AiAgentPlanResponse } | null>(null)

  const lastAgentPlan = computed<AiAgentPlanResponse | null>(() => {
    if (planCache.value && messages.value.some((m) => m.id === planCache.value!.messageId)) {
      return planCache.value.plan
    }
    return null
  })

  function pushUserMessage(
    content: string,
    attachments?: Array<{ assetId: number; filename: string; extractedText: string }>,
    historyId?: number | null
  ) {
    const msg: AiChatMessage = { id: localId(), role: 'user', content: String(content || ''), createdAt: Date.now() }
    if (historyId) msg.historyId = historyId
    if (attachments && attachments.length) msg.attachments = attachments
    messages.value.push(msg)
    return msg
  }

  function pushAssistantMessage(content: string) {
    const msg: AiChatMessage = { id: localId(), role: 'assistant', content: String(content || ''), createdAt: Date.now() }
    messages.value.push(msg)
    return msg
  }

  async function runAgentPlan(text: string, projectId: number | null, assistantId: string) {
    const thinkingSteps: Array<{ state: string; label: string }> = []
    const plan = await aiApi.agentCrewStream(
      { requirement: text, projectId: projectId ?? undefined },
      (stage, data) => {
        if (stage === 'pm') {
          thinkingSteps.push({ state: '已完成', label: `项目经理 · ${String(data || '').trim() || '需求理解完成'}` })
        } else if (stage === 'tech') {
          thinkingSteps.push({ state: '已完成', label: `技术负责人 · ${String(data || '').trim() || '技术方案确认'}` })
        } else if (stage === 'plan') {
          thinkingSteps.push({ state: '已完成', label: '执行分解已整理' })
        }
      }
    )
    const tasks = (plan?.tasks || []).filter((t) => String(t?.title || '').trim())
    const goal = String(plan?.goal || text || '').trim()

    const lines: string[] = []
    lines.push('### AI 思考')
    const steps = thinkingSteps.length
      ? thinkingSteps
      : [{ state: '已完成', label: '需求已理解' }, { state: '已完成', label: '执行分解已整理' }]
    for (const s of steps) lines.push(`- ${s.state} · ${s.label}`)
    lines.push('')
    lines.push('---')
    lines.push('')
    lines.push('## 当前方案')
    lines.push(goal)
    lines.push('')
    lines.push(`执行分解我已经整理好了：${tasks.length} 项执行内容已就绪，点击下方"查看完整分解"可预览并一键落地。`)
    if (tasks.length) {
      lines.push('')
      tasks.forEach((t, i) => {
        const prio = String(t.priority || '').trim().toUpperCase()
        lines.push(`${i + 1}. ${t.title}${prio ? `（优先级：${prio}）` : ''}`)
      })
    }

    const msg = messages.value.find((m) => m.id === assistantId)
    if (msg) msg.content = lines.join('\n')
    planCache.value = { messageId: assistantId, plan: { goal, tasks } }
    lastAgentPlanMessageId.value = assistantId
  }

  async function runChatStream(
    text: string,
    projectId: number | null,
    uploaded: Array<{ assetId: number; filename: string; extractedText: string }>,
    assistantId: string
  ) {
    const attachmentParts = (uploaded || []).map((u) =>
      u.extractedText ? `【${u.filename}】\n${u.extractedText}` : `（已上传：${u.filename}）`
    )
    const fullUserText = [text, ...attachmentParts].filter(Boolean).join('\n\n')
    await aiApi.chatStream(
      { messages: [{ role: 'user', content: fullUserText }], projectId: projectId ?? null },
      (delta) => {
        const msg = messages.value.find((m) => m.id === assistantId)
        if (msg) msg.content += delta
      }
    )
  }

  async function send(
    text: string,
    projectId: number | null,
    uploaded: Array<{ assetId: number; filename: string; extractedText: string }> = []
  ) {
    const content = String(text || '').trim()
    if ((!content && (!uploaded || !uploaded.length)) || sending.value) return

    const userMsg = pushUserMessage(content, uploaded)
    const assistantId = localId()
    messages.value.push({ id: assistantId, role: 'assistant', content: '', createdAt: Date.now() })

    sending.value = true
    try {
      if (looksLikePlanRequest(content)) {
        await runAgentPlan(content, projectId, assistantId)
      } else {
        await runChatStream(content, projectId, uploaded, assistantId)
      }
    } catch (e: any) {
      const msg = messages.value.find((m) => m.id === assistantId)
      if (msg) msg.content = `调用失败：${e?.message || '未知错误'}`
    } finally {
      sending.value = false
      void userMsg
    }
  }

  async function loadHistory(projectId: number | null) {
    if (!projectId) {
      messages.value = []
      planCache.value = null
      lastAgentPlanMessageId.value = null
      return
    }
    const list = await aiApi.historyList({ projectId, limit: 100 })
    const items = list
      .slice()
      .sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0))
    const next: AiChatMessage[] = []
    for (const h of items) {
      const prompt = String(h.prompt || '').trim()
      const response = String(h.response || '').trim()
      if (prompt) {
        next.push({
          id: localId(),
          role: 'user',
          content: prompt,
          createdAt: h.createdAt || Date.now(),
          historyId: h.id
        })
      }
      if (response) {
        next.push({ id: localId(), role: 'assistant', content: response, createdAt: (h.createdAt || Date.now()) + 1 })
      }
    }
    messages.value = next
    planCache.value = null
    lastAgentPlanMessageId.value = null
  }

  async function deleteUserMessage(id: string) {
    const idx = messages.value.findIndex((m) => m.id === id)
    if (idx < 0) return
    const target = messages.value[idx]
    if (target.historyId) {
      try {
        await aiApi.historyDelete([target.historyId])
      } catch {
        // 忽略删除失败，仍从本地移除
      }
    }
    const removed = messages.value.splice(idx, 1)
    // 若下一跳是 assistant（本次会话对应用户消息的回复），一并移除
    const nextMsg = messages.value[idx]
    if (nextMsg && nextMsg.role === 'assistant' && !nextMsg.historyId) {
      messages.value.splice(idx, 1)
    }
    if (removed.length && removed[0].id === planCache.value?.messageId) {
      planCache.value = null
      lastAgentPlanMessageId.value = null
    }
  }

  return {
    messages,
    sending,
    lastAgentPlan,
    lastAgentPlanMessageId,
    autoOpenNonce,
    autoOpenTask,
    send,
    loadHistory,
    pushAssistantMessage,
    deleteUserMessage
  }
})
