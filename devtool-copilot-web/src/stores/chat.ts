import { defineStore } from 'pinia'
import { ref } from 'vue'
import { aiApi, type AiAgentPlanResponse, type ChatMessageDTO, type ChatRole } from '../api/ai'
import { dashboardApi } from '../api/dashboard'
import { inboxApi, type InboxItem } from '../api/inbox'
import { kbApi } from '../api/kb'
import { milestoneApi, type Milestone } from '../api/milestone'
import { taskApi } from '../api/task'
import { useRealtimeStore } from './realtime'

export interface ChatMessage {
  id: string
  role: ChatRole
  content: string
  createdAt: number
  attachments?: { assetId: number; filename: string }[]
}

function uid() {
  return Math.random().toString(16).slice(2) + Date.now().toString(16)
}

export const useChatStore = defineStore('chat', () => {
  const messages = ref<ChatMessage[]>([])
  const sending = ref(false)
  const lastAgentPlan = ref<AiAgentPlanResponse | null>(null)
  const pendingApply = ref<{ projectId: number; plan: AiAgentPlanResponse } | null>(null)
  const lastApply = ref<{ projectId: number; planKey: string; taskIds: number[]; at: number } | null>(null)
  const pendingUndo = ref<{ projectId: number; taskIds: number[]; at: number } | null>(null)
  const pendingTaskDelete = ref<{ taskIds: number[]; at: number } | null>(null)
  const pendingIntent = ref<
    | null
    | { kind: 'AGENT_PLAN'; projectId: number | null; requirement: string }
    | { kind: 'WEEKLY_REPORT'; projectId: number | null }
    | { kind: 'RELEASE_NOTES_DRAFT'; projectId: number | null; text: string }
    | { kind: 'GATE'; projectId: number | null; text: string }
    | { kind: 'INBOX_TRIAGE'; projectId: number | null }
  >(null)
  const pendingAction = ref<
    | null
    | { kind: 'WEEKLY_REPORT'; projectId: number; title: string; markdown: string }
    | { kind: 'RELEASE_NOTES_DRAFT'; projectId: number; milestoneId: number; title: string; markdown: string }
    | { kind: 'GATE_FIX'; projectId: number; taskIds: number[] }
    | { kind: 'INBOX_HANDLE'; ids: number[] }
  >(null)
  const autoOpenTask = ref<{ projectId: number; taskId: number } | null>(null)
  const autoOpenNonce = ref(0)
  const typing = new Map<string, number>()
  const historyProjectId = ref<number | null>(null)
  const pendingFile = ref<{ assetId: number; filename: string; extractedText: string } | null>(null)

  const rt = useRealtimeStore()
  const AGENT_STATE_KEY = 'dtc_ai_agent_state_v1'

  try {
    const raw = typeof localStorage === 'undefined' ? null : localStorage.getItem(AGENT_STATE_KEY)
    const obj = raw ? (JSON.parse(raw) as any) : null
    if (obj && obj.lastAgentPlan && typeof obj.lastAgentPlan === 'object') {
      lastAgentPlan.value = obj.lastAgentPlan as AiAgentPlanResponse
    }
    if (obj && obj.pendingApply && typeof obj.pendingApply === 'object') {
      const pid = Number(obj.pendingApply.projectId || 0)
      const plan = obj.pendingApply.plan as any
      if (pid && plan && typeof plan === 'object') {
        pendingApply.value = { projectId: pid, plan: plan as AiAgentPlanResponse }
      }
    }
    if (obj && obj.lastApply && typeof obj.lastApply === 'object') {
      const la = obj.lastApply as any
      if (la && la.projectId && la.planKey) {
        lastApply.value = {
          projectId: Number(la.projectId),
          planKey: String(la.planKey),
          taskIds: Array.isArray(la.taskIds) ? la.taskIds.map((x: any) => Number(x)).filter((x: any) => Number.isFinite(x)) : [],
          at: Number(la.at || 0)
        }
      }
    }
  } catch {
  }

  function shouldRunCrew(text: string) {
    const s = (text || '').trim()
    if (!s) return false
    if (s.startsWith('/') || s.startsWith('／')) return false
    return /(拆解|规划|计划|方案|闭环|需求|prd|设计|实现|开发|做一个|做个|做一套|搭建|拆成|分解|拆分|排期|甘特)/i.test(s)
  }

  function parseCommand(text: string) {
    const s = (text || '').trim()
    if (!s) return { cmd: '', args: '' }
    const slash = s.startsWith('／') ? '／' : s.startsWith('/') ? '/' : ''
    if (!slash) return { cmd: '', args: '' }
    const rest = s.slice(1)
    const m = rest.match(/^([a-zA-Z]+)\s*(.*)$/)
    if (!m) return { cmd: '', args: '' }
    return { cmd: m[1].toLowerCase(), args: (m[2] || '').trim() }
  }

  function norm(s: string) {
    return String(s || '')
      .trim()
      .replace(/[，。！？、；：]/g, '')
      .replace(/\s+/g, ' ')
  }

  function isHelpText(text: string) {
    const raw = String(text || '').trim()
    if (!raw) return false
    if (raw.startsWith('/') || raw.startsWith('／')) return false
    const s = norm(raw)
    if (!s) return false
    if (/[?？]$/.test(raw)) return true
    if (/(怎么|如何|在哪|哪里|如何做|怎么做|怎么用|用法|入口|在哪儿|在哪裏|能不能|可不可以|是否可以|有没有|是什么|为啥|为什么)/.test(s))
      return true
    if (/^(我能不能|能不能|可不可以|我怎么|怎么|如何)/.test(s)) return true
    return false
  }

  function localHelpMarkdown(text: string, projectId: number | null) {
    const s = String(text || '')
    const hasProject = !!projectId
    const pidTip = hasProject ? `（当前 Context 项目：${projectId}）` : '（请先在右上角选择 Context 项目）'

    if (/(里程碑|milestone)/i.test(s) && /(创建|新建|怎么|如何|在哪|入口)/.test(s)) {
      const link = hasProject ? `/projects/${projectId}` : '/projects'
      return [
        `## 创建里程碑`,
        ``,
        `1. 打开项目详情 ${pidTip}`,
        `2. 在项目详情页的 **Milestones** 区域点击“新建”`,
        `3. 填写里程碑名称（可选描述/截止时间）并保存`,
        ``,
        `入口：${hasProject ? `[打开项目](${link})` : `打开任意项目进入详情页（${link}）`}`,
        ``,
        `如果你愿意，我也可以帮你“先草拟里程碑 + 发布说明”，但会先征求你的确认。`
      ].join('\n')
    }

    if (/(发布说明|版本说明|更新说明|release notes)/i.test(s) && /(怎么|如何|在哪|入口|草拟|生成)/.test(s)) {
      return [
        `## 发布说明（Release Notes）怎么做`,
        ``,
        `推荐流程：`,
        `1. 先创建里程碑（Milestone）`,
        `2. 把任务关联到里程碑，并完成到 DONE`,
        `3. 在项目详情页里程碑上点击“发布”，系统会生成并保存 Release Notes`,
        ``,
        hasProject ? `入口：[打开项目](/projects/${projectId})` : `入口：先选择 Context 项目，再进入项目详情页`,
        ``,
        `如果你想我帮你草拟一份 Release Notes，我会先给预览，你确认后再保存到知识库。`
      ].join('\n')
    }

    if (/(gate|发布.*检查|验收.*检查|交付.*检查|上线.*检查|里程碑.*检查)/i.test(s) && /(怎么|如何|能不能|有没有|入口)/.test(s)) {
      return [
        `## Gate 检查是什么`,
        ``,
        `Gate 检查用于保证“可交付”：`,
        `- 里程碑是否还有未完成任务`,
        `- 已完成任务是否缺验收清单/交付物（PR/DOC/LINK）`,
        ``,
        `你可以直接对我说“发布检查 / 验收检查”。我会先给一份检查预览，你确认后才会自动补齐缺失项（仅补占位，不改已有内容）。`
      ].join('\n')
    }

    if (/(父任务|子任务|epic|subtask)/i.test(s) && /(怎么|如何|在哪|入口|创建)/.test(s)) {
      return [
        `## 父任务 / 子任务怎么用`,
        ``,
        `- 进入任务详情页，在 **Epic/Subtasks** 区域可以新增子任务`,
        `- 子任务会自动绑定父任务，并且默认继承父任务里程碑（如有）`,
        ``,
        `如果你想我帮你把需求落地为父子任务结构，我会先问你“要不要做成父任务+子任务”，你确认后再规划并落地。`
      ].join('\n')
    }

    if (/(收件箱|inbox)/i.test(s) && /(怎么|如何|在哪|入口)/.test(s)) {
      return [
        `## 收件箱在哪里`,
        ``,
        `左侧导航进入“收件箱”。`,
        ``,
        `如果你说“整理收件箱/收件箱分流”，我会先给分流预览，你确认后才会批量标记已处理。`
      ].join('\n')
    }

    return null
  }

  function isConfirmApplyText(text: string) {
    const s = norm(text)
    if (!s) return false
    if (/^(确认|确定|可以|ok|好的|好|执行|开始|落地|就这样|按这个|直接做|继续)$/.test(s)) return true
    if (/(确认.*落地|确定.*落地|开始.*落地|执行.*落地|就按.*做|按.*落地)/.test(s)) return true
    return false
  }

  function isCancelApplyText(text: string) {
    const s = norm(text)
    if (!s) return false
    if (/^(取消|算了|先不做|不执行|停止|终止)$/.test(s)) return true
    if (/(取消.*落地|先不.*落地|不.*落地|停止.*落地|终止.*落地)/.test(s)) return true
    return false
  }

  function isStrongConfirmText(text: string) {
    const s = norm(text)
    if (!s) return false
    return /^(确认|确定|ok|好的|好|可以|开始|执行|继续|就这样|按这个|直接做)$/.test(s.toLowerCase())
  }

  function isStrongCancelText(text: string) {
    const s = norm(text)
    if (!s) return false
    return /^(取消|算了|先不做|不做了|不执行|停止|终止)$/.test(s)
  }

  function isUndoText(text: string) {
    const raw = String(text || '').trim()
    if (!raw) return false
    const low = raw.toLowerCase()
    if (raw.startsWith('/') || raw.startsWith('／')) return low === '/undo' || low.startsWith('/undo ')
    const s = norm(raw)
    if (!s) return false
    return /(撤销|undo|回滚|取消刚才|撤回刚才)/i.test(s)
  }

  function parseTaskIdsFromText(text: string) {
    const raw = String(text || '').trim()
    if (!raw) return []
    const ids = new Set<number>()
    const rangeRe = /#?\s*(\d+)\s*(?:~|～|-|—|–|到|至)\s*#?\s*(\d+)/g
    let m: RegExpExecArray | null
    while ((m = rangeRe.exec(raw))) {
      const a = Number(m[1])
      const b = Number(m[2])
      if (!Number.isFinite(a) || !Number.isFinite(b)) continue
      const lo = Math.min(a, b)
      const hi = Math.max(a, b)
      if (hi - lo > 50) continue
      for (let i = lo; i <= hi; i += 1) ids.add(i)
    }
    const hashRe = /#\s*(\d+)/g
    while ((m = hashRe.exec(raw))) {
      const id = Number(m[1])
      if (Number.isFinite(id)) ids.add(id)
    }
    const taskRe = /任务\s*#?\s*(\d+)/g
    while ((m = taskRe.exec(raw))) {
      const id = Number(m[1])
      if (Number.isFinite(id)) ids.add(id)
    }
    return Array.from(ids).sort((x, y) => x - y)
  }

  function isDeleteTaskText(text: string) {
    const raw = String(text || '').trim()
    if (!raw) return false
    const low = raw.toLowerCase()
    if (raw.startsWith('/') || raw.startsWith('／')) return low === '/delete' || low.startsWith('/delete ') || low === '/del' || low.startsWith('/del ')
    if (!/(删除|删)/.test(raw)) return false
    if (!/(任务|#)/.test(raw)) return false
    return /\d+/.test(raw)
  }

  function formatTaskDeletePreview(taskIds: number[]) {
    const lines: string[] = []
    lines.push(`## 删除任务（需要确认）`)
    lines.push(`\n将删除：${taskIds.length} 条`)
    if (taskIds.length) {
      lines.push(`\n任务：`)
      for (const id of taskIds.slice(0, 12)) lines.push(`- #${id}`)
      if (taskIds.length > 12) lines.push(`- …（已省略 ${taskIds.length - 12} 条）`)
    }
    lines.push(`\n---\n回复“确认/好/可以/开始”执行删除；回复“取消/算了/先不做”取消。`)
    return lines.join('\n')
  }

  async function runTaskDeleteConfirm(assistantId: string, taskIds: number[]) {
    const ok: number[] = []
    const blocked: { id: number; reason: string }[] = []
    for (const id of taskIds) {
      try {
        await taskApi.get(id)
        ok.push(id)
      } catch (e: any) {
        blocked.push({ id, reason: e?.message || '任务不存在或无权限' })
      }
    }
    if (!ok.length) {
      appendAssistant(assistantId, `\n没有可删除的任务（可能不存在或无权限）。\n`)
      if (blocked.length) {
        appendAssistant(assistantId, `\n失败项：\n`)
        for (const b of blocked.slice(0, 12)) appendAssistant(assistantId, `- #${b.id}：${b.reason}\n`)
      }
      return
    }
    if (blocked.length) {
      appendAssistant(assistantId, `\n为安全起见，本次只会删除“可读取”的任务：${ok.length} 条。\n`)
      appendAssistant(assistantId, `\n不可删除项：\n`)
      for (const b of blocked.slice(0, 12)) appendAssistant(assistantId, `- #${b.id}：${b.reason}\n`)
    }
    appendAssistant(assistantId, `\n开始删除…\n`)
    let deleted = 0
    const verifyFailed: number[] = []
    for (const id of ok) {
      try {
        await taskApi.delete(id)
        deleted += 1
        try {
          await taskApi.get(id)
          verifyFailed.push(id)
        } catch {
        }
      } catch (e: any) {
        appendAssistant(assistantId, `- 删除 #${id} 失败：${e?.message || '失败'}\n`)
      }
    }
    appendAssistant(assistantId, `\n删除回执：请求=${ok.length}，已删除=${deleted}\n`)
    if (verifyFailed.length) {
      appendAssistant(assistantId, `\n校验失败（删除后仍可读取）：\n`)
      for (const id of verifyFailed.slice(0, 12)) appendAssistant(assistantId, `- #${id}\n`)
    } else {
      appendAssistant(assistantId, `\n校验：删除后均不可读取（通过）\n`)
    }
  }

  function isApplyPreviewText(text: string) {
    const s = norm(text)
    if (!s) return false
    if (/^(apply|落地|执行|开始落地|落库|写入|提交|生成任务|创建任务)$/.test(s.toLowerCase())) return true
    if (/(确认|确定|开始|执行|可以|好).*(落地|执行)/.test(s)) return true
    if (/(落地|执行)$/.test(s)) return true
    if (/(落地|执行|写入|落库|创建).*(任务|计划|这些|刚才|上面)/.test(s)) return true
    return false
  }

  function persistAgentState() {
    try {
      localStorage.setItem(
        AGENT_STATE_KEY,
        JSON.stringify({
          ts: Date.now(),
          lastAgentPlan: lastAgentPlan.value,
          pendingApply: pendingApply.value,
          lastApply: lastApply.value
        })
      )
    } catch {
    }
  }

  function proposeIntentMarkdown(title: string, detail: string) {
    const lines: string[] = []
    lines.push(`## ${title}`)
    lines.push(`\n${detail}`)
    lines.push(`\n---\n回复“确认/好/可以/开始”继续执行；回复“取消/算了/先不做”保持为普通聊天。`)
    return lines.join('\n')
  }

  function intentTitle(kind: string) {
    if (kind === 'AGENT_PLAN') return '我可以帮你把它落地成任务计划'
    if (kind === 'WEEKLY_REPORT') return '我可以帮你生成项目周报'
    if (kind === 'RELEASE_NOTES_DRAFT') return '我可以帮你草拟发布说明（Release Notes）'
    if (kind === 'GATE') return '我可以帮你做一次交付/Gate 检查并补齐缺项'
    if (kind === 'INBOX_TRIAGE') return '我可以帮你整理收件箱（分流）'
    return '我可以帮你执行一个动作'
  }

  async function runPendingIntentConfirm() {
    const pi = pendingIntent.value
    pendingIntent.value = null
    if (!pi) return
    if (pi.kind === 'AGENT_PLAN') {
      await planByCrew(pi.requirement, pi.projectId)
      return
    }
    if (pi.kind === 'WEEKLY_REPORT') {
      await planWeeklyReport(pi.projectId)
      return
    }
    if (pi.kind === 'RELEASE_NOTES_DRAFT') {
      await planReleaseNotesDraft(pi.projectId, pi.text)
      return
    }
    if (pi.kind === 'GATE') {
      await planGate(pi.projectId, pi.text)
      return
    }
    if (pi.kind === 'INBOX_TRIAGE') {
      await planInboxTriage(pi.projectId)
      return
    }
  }

  function planKeyOf(plan: AiAgentPlanResponse) {
    const goal = String((plan as any)?.goal || '').trim()
    const tasks = Array.isArray((plan as any)?.tasks) ? ((plan as any).tasks as any[]) : []
    const base =
      goal +
      '|' +
      tasks
        .slice(0, 120)
        .map((t) => {
          const title = String(t?.title || '').trim()
          const desc = String(t?.description || '').trim()
          const pr = String(t?.priority || '').trim()
          const cl = Array.isArray(t?.checklist) ? t.checklist.slice(0, 30).join('|') : ''
          const dl = Array.isArray(t?.deliverables)
            ? t.deliverables
                .slice(0, 20)
                .map((d: any) => `${String(d?.type || '').trim()}:${String(d?.title || '').trim()}`)
                .join('|')
            : ''
          return [title, pr, desc, cl, dl].join('~')
        })
        .join('||')
    let h = 0
    for (let i = 0; i < base.length; i += 1) {
      h = (h * 31 + base.charCodeAt(i)) >>> 0
    }
    return String(h)
  }

  function toRequestMessages(list: ChatMessage[]): ChatMessageDTO[] {
    const last = list.slice(-20)
    return last
      .filter((m) => (m.role === 'user' || m.role === 'assistant') && m.content && m.content.trim())
      .map((m) => ({ role: m.role, content: m.content.trim() }))
  }

  function setPendingFileContext(v: { assetId: number; filename: string; extractedText: string } | null) {
    pendingFile.value = v
  }

  function deleteUserMessage(userMessageId: string) {
    if (!userMessageId) return
    if (sending.value) return
    const idx = messages.value.findIndex((m) => m.id === userMessageId)
    if (idx < 0) return
    const m = messages.value[idx]
    if (!m || m.role !== 'user') return
    const next: ChatMessage[] = []
    let passedNextUser = false
    for (let i = 0; i < messages.value.length; i += 1) {
      if (i < idx) {
        next.push(messages.value[i])
        continue
      }
      if (i === idx) continue
      if (i > idx && !passedNextUser) {
        if (messages.value[i].role === 'user') {
          passedNextUser = true
          next.push(messages.value[i])
        }
        continue
      }
      next.push(messages.value[i])
    }
    messages.value = next
  }

  function formatUndoPreview(projectId: number, taskIds: number[]) {
    const lines: string[] = []
    lines.push(`## 撤销本次落地（Undo）`)
    lines.push(`\nContext 项目：${projectId}`)
    lines.push(`\n将删除任务：${taskIds.length} 条`)
    if (taskIds.length) {
      lines.push(`\n任务：`)
      for (const id of taskIds.slice(0, 12)) lines.push(`- #${id}`)
      if (taskIds.length > 12) lines.push(`- …（已省略 ${taskIds.length - 12} 条）`)
    }
    lines.push(`\n---\n回复“确认/好/可以/开始”执行撤销；回复“取消/算了/先不做”取消撤销。`)
    return lines.join('\n')
  }

  function parseMs(raw?: string | null) {
    if (!raw) return null
    const t = Date.parse(raw)
    return Number.isFinite(t) ? t : null
  }

  async function runUndoConfirm(assistantId: string, projectId: number, taskIds: number[], at: number) {
    if (Date.now() - at > 12 * 60 * 1000) {
      appendAssistant(assistantId, `\n撤销失败：已超过时间窗口（12分钟），为安全起见不允许自动撤销。\n`)
      return
    }
    const okIds: number[] = []
    const blocked: { id: number; reason: string }[] = []
    for (const id of taskIds) {
      try {
        const t = await taskApi.get(id)
        if (!t || t.projectId !== projectId) {
          blocked.push({ id, reason: '项目不匹配或任务不存在' })
          continue
        }
        const c = parseMs(t.createTime || null)
        const u = parseMs(t.updatedAt || null)
        if (c != null && u != null && u - c > 5000) {
          blocked.push({ id, reason: '任务已被修改' })
          continue
        }
        okIds.push(id)
      } catch (e: any) {
        blocked.push({ id, reason: e?.message || '读取失败' })
      }
    }
    if (blocked.length) {
      appendAssistant(assistantId, `\n撤销被拦截：检测到部分任务不满足安全条件（未修改/项目匹配）。\n`)
      appendAssistant(assistantId, `\n不可撤销项：\n`)
      for (const b of blocked.slice(0, 12)) appendAssistant(assistantId, `- #${b.id}：${b.reason}\n`)
      appendAssistant(assistantId, `\n为避免误删，本次撤销未执行。你可以手动检查后再删除。\n`)
      return
    }
    appendAssistant(assistantId, `\n开始撤销（删除任务）…\n`)
    let deleted = 0
    for (const id of okIds) {
      try {
        await taskApi.delete(id)
        deleted += 1
      } catch (e: any) {
        appendAssistant(assistantId, `- 删除 #${id} 失败：${e?.message || '失败'}\n`)
      }
    }
    appendAssistant(assistantId, `\n已删除：${deleted}/${okIds.length}\n`)
    appendAssistant(assistantId, `\n打开看板：[/board?projectId=${projectId}](/board?projectId=${projectId})\n`)
    appendAssistant(assistantId, `打开项目：[/projects/${projectId}](/projects/${projectId})\n`)
    lastApply.value = null
    persistAgentState()
    try {
      rt.publishEvent({
        projectId,
        type: 'AI_APPLY_UNDONE',
        payloadJson: JSON.stringify({ taskIds: okIds }),
        time: new Date().toISOString()
      })
    } catch {
    }
  }

  async function send(
    text: string,
    projectId?: number | null,
    files?: { assetId: number; filename: string; extractedText?: string }[]
  ) {
    let content = text.trim()
    if (!content && (!files || !files.length)) return
    if (!content && files && files.length) {
      content = '请结合附件内容回答。'
    }
    const userMsg: ChatMessage = {
      id: uid(),
      role: 'user',
      content,
      createdAt: Date.now(),
      attachments: files && files.length ? files.map((f) => ({ assetId: f.assetId, filename: f.filename })) : undefined
    }
    messages.value = [...messages.value, userMsg]
    sending.value = true
    try {
      const lowered = content.toLowerCase()

      if (pendingTaskDelete.value) {
        const pd = pendingTaskDelete.value
        if (isStrongConfirmText(content)) {
          pendingTaskDelete.value = null
          const assistantId = uid()
          messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: '', createdAt: Date.now() }]
          await runTaskDeleteConfirm(assistantId, pd.taskIds)
          return
        }
        if (isStrongCancelText(content)) {
          pendingTaskDelete.value = null
          const assistantId = uid()
          messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: '已取消删除。', createdAt: Date.now() }]
          return
        }
        pendingTaskDelete.value = null
      }

      if (pendingUndo.value) {
        const pu = pendingUndo.value
        if (isStrongConfirmText(content)) {
          pendingUndo.value = null
          const assistantId = uid()
          messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: '', createdAt: Date.now() }]
          await runUndoConfirm(assistantId, pu.projectId, pu.taskIds, pu.at)
          return
        }
        if (isStrongCancelText(content)) {
          pendingUndo.value = null
          const assistantId = uid()
          messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: '已取消撤销。', createdAt: Date.now() }]
          return
        }
        pendingUndo.value = null
      }

      if (pendingApply.value) {
        if (lowered === 'apply' || isStrongConfirmText(content)) {
          const pid = (projectId ?? null) || pendingApply.value.projectId
          await applyLastPlan(pid, 'confirm')
          persistAgentState()
          return
        }
        if (isStrongCancelText(content)) {
          pendingApply.value = null
          persistAgentState()
          const assistantId = uid()
          messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: '已取消落地。', createdAt: Date.now() }]
          return
        }
        pendingApply.value = null
        persistAgentState()
      }

      if (pendingAction.value) {
        if (isStrongConfirmText(content)) {
          await runPendingActionConfirm()
          return
        }
        if (isStrongCancelText(content)) {
          pendingAction.value = null
          const assistantId = uid()
          messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: '已取消。', createdAt: Date.now() }]
          return
        }
        pendingAction.value = null
      }

      if (pendingIntent.value) {
        if (isStrongConfirmText(content)) {
          await runPendingIntentConfirm()
          return
        }
        if (isStrongCancelText(content)) {
          pendingIntent.value = null
          const assistantId = uid()
          messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: '好的，我们继续聊天。', createdAt: Date.now() }]
          return
        }
        pendingIntent.value = null
      }

      if (lowered === 'apply') {
        await applyLastPlan(projectId ?? null, '')
        return
      }
      if (lowered === 'crew' || lowered.startsWith('crew ')) {
        await planByCrew(content.slice(4).trim(), projectId ?? null)
        return
      }
      if (lowered === 'agent' || lowered.startsWith('agent ')) {
        await planByAgent(content.slice(5).trim(), projectId ?? null)
        return
      }

      const parsed = parseCommand(content)
      if (parsed.cmd === 'delete' || parsed.cmd === 'del') {
        const ids = parseTaskIdsFromText(parsed.args)
        if (!ids.length) {
          const assistantId = uid()
          messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: '请提供要删除的任务ID，例如：`/delete #12` 或 `删除任务 #96~#103`。', createdAt: Date.now() }]
          return
        }
        pendingTaskDelete.value = { taskIds: ids, at: Date.now() }
        const assistantId = uid()
        messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: formatTaskDeletePreview(ids), createdAt: Date.now() }]
        return
      }
      if (parsed.cmd === 'apply') {
        await applyLastPlan(projectId ?? null, parsed.args)
        return
      }
      if (parsed.cmd === 'crew') {
        await planByCrew(parsed.args, projectId ?? null)
        return
      }
      if (parsed.cmd === 'agent') {
        await planByAgent(parsed.args, projectId ?? null)
        return
      }
      if (!pendingApply.value && lastAgentPlan.value && isApplyPreviewText(content)) {
        await applyLastPlan(projectId ?? null, '')
        persistAgentState()
        return
      }

      if (!pendingApply.value && !lastAgentPlan.value && (isConfirmApplyText(content) || isApplyPreviewText(content))) {
        const assistantId = uid()
        messages.value = [
          ...messages.value,
          {
            id: assistantId,
            role: 'assistant',
            content: '当前没有待落地的计划。你可以先描述需求让我规划（我会先征求确认），或直接输入 `/agent 需求`。',
            createdAt: Date.now()
          }
        ]
        return
      }
      if (parsed.cmd === 'undo' || isUndoText(content)) {
        const la = lastApply.value
        if (!la || !la.taskIds.length) {
          const assistantId = uid()
          messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: '当前没有可撤销的落地。', createdAt: Date.now() }]
          return
        }
        pendingUndo.value = { projectId: la.projectId, taskIds: la.taskIds, at: la.at }
        const assistantId = uid()
        messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: formatUndoPreview(la.projectId, la.taskIds), createdAt: Date.now() }]
        return
      }

      if (isDeleteTaskText(content)) {
        const ids = parseTaskIdsFromText(content)
        if (!ids.length) {
          const assistantId = uid()
          messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: '请提供要删除的任务ID，例如：`删除任务 #12` 或 `删除任务 #96~#103`。', createdAt: Date.now() }]
          return
        }
        pendingTaskDelete.value = { taskIds: ids, at: Date.now() }
        const assistantId = uid()
        messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: formatTaskDeletePreview(ids), createdAt: Date.now() }]
        return
      }

      if (shouldRunWeeklyReport(content)) {
        pendingIntent.value = { kind: 'WEEKLY_REPORT', projectId: projectId ?? null }
        const assistantId = uid()
        messages.value = [
          ...messages.value,
          {
            id: assistantId,
            role: 'assistant',
            content: proposeIntentMarkdown(intentTitle('WEEKLY_REPORT'), '我会基于当前项目的任务/风险/讨论等数据生成一份简洁周报，并可一键保存到知识库。'),
            createdAt: Date.now()
          }
        ]
        return
      }
      if (shouldRunReleaseNotesDraft(content)) {
        pendingIntent.value = { kind: 'RELEASE_NOTES_DRAFT', projectId: projectId ?? null, text: content }
        const assistantId = uid()
        messages.value = [
          ...messages.value,
          {
            id: assistantId,
            role: 'assistant',
            content: proposeIntentMarkdown(
              intentTitle('RELEASE_NOTES_DRAFT'),
              '我会基于当前项目的里程碑与已完成任务，草拟一份可交付的 Release Notes，并可一键保存到知识库。'
            ),
            createdAt: Date.now()
          }
        ]
        return
      }
      if (shouldRunGate(content)) {
        pendingIntent.value = { kind: 'GATE', projectId: projectId ?? null, text: content }
        const assistantId = uid()
        messages.value = [
          ...messages.value,
          {
            id: assistantId,
            role: 'assistant',
            content: proposeIntentMarkdown(intentTitle('GATE'), '我会检查里程碑未完成项、以及已完成但缺验收/交付物的项，并可自动补齐缺失占位。'),
            createdAt: Date.now()
          }
        ]
        return
      }
      if (shouldRunInboxTriage(content)) {
        pendingIntent.value = { kind: 'INBOX_TRIAGE', projectId: projectId ?? null }
        const assistantId = uid()
        messages.value = [
          ...messages.value,
          {
            id: assistantId,
            role: 'assistant',
            content: proposeIntentMarkdown(intentTitle('INBOX_TRIAGE'), '我会把收件箱里未处理的消息做一次分流，并可一键标记为已处理。'),
            createdAt: Date.now()
          }
        ]
        return
      }

      if (isHelpText(content)) {
        const md = localHelpMarkdown(content, projectId ?? null)
        if (md) {
          const assistantId = uid()
          messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: md, createdAt: Date.now() }]
          return
        }
      }

      if (shouldRunCrew(content)) {
        pendingIntent.value = { kind: 'AGENT_PLAN', projectId: projectId ?? null, requirement: content }
        const assistantId = uid()
        messages.value = [
          ...messages.value,
          {
            id: assistantId,
            role: 'assistant',
            content: proposeIntentMarkdown(intentTitle('AGENT_PLAN'), '我可以先给出一个短摘要计划（Top5），你确认后再落地到任务。'),
            createdAt: Date.now()
          }
        ]
        return
      }

      const reqMsgs = toRequestMessages(messages.value)
      const mergedFiles: { assetId: number; filename: string; extractedText: string }[] = []
      if (files && files.length) {
        for (const f of files) {
          mergedFiles.push({ assetId: f.assetId, filename: f.filename, extractedText: String(f.extractedText || '') })
        }
      }
      const pf = pendingFile.value
      if (pf) {
        mergedFiles.push({ assetId: pf.assetId, filename: pf.filename, extractedText: String(pf.extractedText || '') })
        pendingFile.value = null
      }
      if (mergedFiles.length && reqMsgs.length && reqMsgs[reqMsgs.length - 1].role === 'user') {
        const blocks = mergedFiles.map((f) => {
          const extracted = String(f.extractedText || '').trim()
          const body = extracted ? extracted : '（未提取到可用文本；图片暂不做 OCR）'
          return `[上传文件：${f.filename} · assetId=${f.assetId}]\n${body}`
        })
        const extra = `\n\n${blocks.join('\n\n')}`
        reqMsgs[reqMsgs.length - 1] = { ...reqMsgs[reqMsgs.length - 1], content: reqMsgs[reqMsgs.length - 1].content + extra }
      }

      const req = { messages: reqMsgs, ...(projectId ? { projectId } : {}) }
      const assistantId = uid()
      messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: '', createdAt: Date.now() }]

      let pending = ''
      let timer: number | null = null
      const flush = () => {
        if (!pending) return
        const chunk = pending
        pending = ''
        messages.value = messages.value.map((m) =>
          m.id === assistantId ? { ...m, content: (m.content || '') + chunk } : m
        )
      }
      timer = window.setInterval(flush, 30)
      try {
        await aiApi.chatStream(req, (d) => {
          pending += d
        })
      } catch (e: any) {
        pending += `\n\n请求失败：${e?.message || 'AI服务调用失败'}`
      } finally {
        if (timer != null) window.clearInterval(timer)
        flush()
      }
    } finally {
      sending.value = false
    }
  }

  function typewriterSet(id: string, full: string) {
    const text = String(full || '')
    const old = typing.get(id)
    if (old != null) window.clearInterval(old)
    let i = 0
    setAssistant(id, '')
    const timer = window.setInterval(() => {
      const next = text.slice(i, i + 18)
      i += 18
      if (next) appendAssistant(id, next)
      if (i >= text.length) {
        window.clearInterval(timer)
        typing.delete(id)
      }
    }, 18)
    typing.set(id, timer)
  }

  async function planByAgent(requirement: string, projectId: number | null) {
    const assistantId = uid()
    messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: '', createdAt: Date.now() }]
    if (!requirement.trim()) {
      setAssistant(assistantId, '用法：`/agent 你的需求`\n\n建议先选择右上角 Context 项目，这样可以把任务落地到项目。')
      return
    }
    try {
      let reqText = requirement
      const pf = pendingFile.value
      if (pf && pf.extractedText && pf.extractedText.trim()) {
        reqText = `${reqText}\n\n[上传文件：${pf.filename} · assetId=${pf.assetId}]\n${pf.extractedText.trim()}`
        pendingFile.value = null
      }
      await aiApi.agentPlanStream({ requirement: reqText, projectId }, (event, data) => {
        if (event === 'result') {
          try {
            const plan = JSON.parse(data || '{}') as AiAgentPlanResponse
            lastAgentPlan.value = plan
            persistAgentState()
            typewriterSet(assistantId, formatPlanMarkdown(plan, projectId))
          } catch {
            setAssistant(assistantId, 'AI输出解析失败')
          }
        } else if (event === 'error') {
          setAssistant(assistantId, `请求失败：${data || 'AI服务调用失败'}`)
        }
      })
    } catch (e: any) {
      setAssistant(assistantId, `请求失败：${e?.message || 'AI服务调用失败'}`)
    }
  }

  async function planByCrew(requirement: string, projectId: number | null) {
    const pmId = uid()
    const techId = uid()
    const execId = uid()
    messages.value = [
      ...messages.value,
      { id: pmId, role: 'assistant', content: '### PM Agent\n…', createdAt: Date.now() },
      { id: techId, role: 'assistant', content: '### Tech Lead Agent\n…', createdAt: Date.now() },
      { id: execId, role: 'assistant', content: '### Executor\n…', createdAt: Date.now() }
    ]
    if (!requirement.trim()) {
      setAssistant(
        pmId,
        '直接描述你要做的事情即可。\n\n也可显式输入：`/crew 你的需求`\n\n建议先选择右上角 Context 项目，这样可以引用项目上下文，并可用 `/apply` 落地到项目。'
      )
      setAssistant(techId, '')
      setAssistant(execId, '')
      return
    }
    try {
      let reqText = requirement
      const pf = pendingFile.value
      if (pf && pf.extractedText && pf.extractedText.trim()) {
        reqText = `${reqText}\n\n[上传文件：${pf.filename} · assetId=${pf.assetId}]\n${pf.extractedText.trim()}`
        pendingFile.value = null
      }
      await aiApi.agentCrewStream({ requirement: reqText, projectId }, (stage, data) => {
        if (stage === 'pm') typewriterSet(pmId, formatStageMarkdown('规划建议', data || '（无输出）'))
        else if (stage === 'tech') typewriterSet(techId, formatStageMarkdown('技术建议', data || '（无输出）'))
        else if (stage === 'plan') {
          try {
            const plan = JSON.parse(data || '{}') as AiAgentPlanResponse
            lastAgentPlan.value = plan
            persistAgentState()
            typewriterSet(execId, formatPlanMarkdown(plan, projectId))
          } catch {
            typewriterSet(execId, '### Executor\nAI输出解析失败')
          }
        } else if (stage === 'error') {
          const tip = `请求失败：${data || 'AI服务调用失败'}`
          setAssistant(pmId, tip)
          setAssistant(techId, tip)
          setAssistant(execId, tip)
        }
      })
    } catch (e: any) {
      const msg = e?.message || 'AI服务调用失败'
      if (msg === '接口不存在') {
        const tip =
          '请求失败：接口不存在\n\n这通常是后端还没重启到最新代码（新增了 /ai/agent/crew 接口），或前端/后端部署的路径前缀不一致。请重启后端后再试。'
        setAssistant(pmId, tip)
        setAssistant(techId, tip)
        setAssistant(execId, tip)
      } else {
        const tip = `请求失败：${msg}`
        setAssistant(pmId, tip)
        setAssistant(techId, tip)
        setAssistant(execId, tip)
      }
    }
  }

  async function applyLastPlan(projectId: number | null, mode?: string) {
    const assistantId = uid()
    messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: '', createdAt: Date.now() }]
    const plan = pendingApply.value?.plan || lastAgentPlan.value
    if (!plan || !plan.tasks || plan.tasks.length === 0) {
      setAssistant(assistantId, '没有可落地的 Agent 任务。\n\n先输入：`/agent 你的需求`')
      return
    }
    if (!projectId) {
      setAssistant(assistantId, '请先在上方 Context 里选择一个项目，再输入：`/apply`')
      return
    }
    if (mode !== 'confirm') {
      const pk = planKeyOf(plan)
      const la = lastApply.value
      if (la && la.projectId === projectId && la.planKey === pk && Date.now() - la.at < 12 * 60 * 1000) {
        const lines: string[] = []
        lines.push('## 已落地（无需重复）')
        lines.push(`\nContext 项目：${projectId}`)
        lines.push(`\n已创建任务：${la.taskIds.length} 条`)
        if (la.taskIds.length) {
          lines.push(`\n打开任务：`)
          for (const id of la.taskIds.slice(0, 12)) lines.push(`- [#${id}](/projects/${projectId}/tasks/${id})`)
          lines.push(`\n打开看板：[/board?projectId=${projectId}](/board?projectId=${projectId})`)
          lines.push(`打开项目：[/projects/${projectId}](/projects/${projectId})`)
        }
        setAssistant(assistantId, lines.join('\n'))
        return
      }
      pendingApply.value = { projectId, plan }
      persistAgentState()
      setAssistant(assistantId, formatApplyPreview(plan, projectId))
      return
    }
    pendingApply.value = null
    persistAgentState()
    appendAssistant(assistantId, `开始落地到项目 projectId=${projectId} …\n`)
    try {
      const pk = planKeyOf(plan)
      const la = lastApply.value
      if (la && la.projectId === projectId && la.planKey === pk && Date.now() - la.at < 12 * 60 * 1000) {
        appendAssistant(assistantId, `\n已检测到这轮计划刚刚落地过，为避免重复创建，本次不再重复执行。\n`)
        appendAssistant(assistantId, `\n打开看板：[/board?projectId=${projectId}](/board?projectId=${projectId})\n`)
        return
      }
      const res = await aiApi.agentApply({ projectId, plan })
      const ids = Array.isArray(res.taskIds) ? res.taskIds.map((x: any) => Number(x)).filter((x: any) => Number.isFinite(x)) : []
      const applyAt = Date.now()
      lastApply.value = { projectId, planKey: pk, taskIds: ids, at: applyAt }
      persistAgentState()
      appendAssistant(assistantId, `- 回执：后端返回 taskIds=${ids.length} 条\n`)
      if (!ids.length) {
        appendAssistant(assistantId, `\n落地失败：后端未返回任何 taskId（疑似未创建成功）。\n`)
        appendAssistant(assistantId, `\n你可以重新尝试落地，或把后端日志发我定位。\n`)
        return
      }

      const okIds: number[] = []
      const badIds: { id: number; reason: string }[] = []
      for (const id of ids) {
        try {
          const t = await taskApi.get(id)
          if (!t || t.projectId !== projectId) {
            badIds.push({ id, reason: '项目不匹配或任务不存在' })
            continue
          }
          okIds.push(id)
        } catch (e: any) {
          badIds.push({ id, reason: e?.message || '读取失败' })
        }
      }

      appendAssistant(assistantId, `- 二次校验：可读取=${okIds.length}，失败=${badIds.length}\n`)
      if (badIds.length) {
        appendAssistant(assistantId, `\n落地未完全成功（已拒绝口头成功）。\n`)
        appendAssistant(assistantId, `\n失败项：\n`)
        for (const b of badIds.slice(0, 12)) appendAssistant(assistantId, `- #${b.id}：${b.reason}\n`)
        appendAssistant(assistantId, `\n建议：先输入 \`/undo\` 撤销本次落地，再重试。\n`)
      }

      if (okIds.length) {
        appendAssistant(assistantId, `\n打开任务：\n`)
        for (const id of okIds.slice(0, 12)) {
          appendAssistant(assistantId, `- [#${id}](/projects/${projectId}/tasks/${id})\n`)
        }
        autoOpenTask.value = { projectId, taskId: okIds[0] }
        autoOpenNonce.value = Date.now()
      }
      appendAssistant(assistantId, `\n打开看板：[/board?projectId=${projectId}](/board?projectId=${projectId})\n`)
      appendAssistant(assistantId, `打开项目：[/projects/${projectId}](/projects/${projectId})\n`)
      appendAssistant(assistantId, `\n撤销本次落地：输入 \`/undo\`\n`)
      try {
        rt.publishEvent({
          projectId,
          type: 'AI_APPLY_DONE',
          payloadJson: JSON.stringify({ taskIds: okIds.length ? okIds : ids }),
          time: new Date().toISOString()
        })
      } catch {
      }
    } catch (e: any) {
      appendAssistant(assistantId, `\n落地失败：${e?.message || '失败'}\n`)
    }
  }

  function setAssistant(id: string, content: string) {
    messages.value = messages.value.map((m) => (m.id === id ? { ...m, content } : m))
  }

  function appendAssistant(id: string, delta: string) {
    messages.value = messages.value.map((m) => (m.id === id ? { ...m, content: (m.content || '') + delta } : m))
  }

  function formatPlanMarkdown(plan: AiAgentPlanResponse, projectId: number | null) {
    const goal = plan.goal ? String(plan.goal).trim() : ''
    const lines: string[] = []
    lines.push(goal ? `## ${goal}` : '## Agent')
    if (projectId) lines.push(`\nContext 项目：${projectId}\n`)
    const tasks = Array.isArray(plan.tasks) ? plan.tasks : []
    lines.push(`任务：${tasks.length} 条`)
    const top = tasks.slice(0, 5)
    if (top.length) {
      lines.push(`\n**Top 任务**：`)
      top.forEach((t: any, i: number) => {
        const title = String(t?.title || '').trim()
        const pr = String(t?.priority || '').trim()
        if (!title) return
        lines.push(`- ${i + 1}. ${title}${pr ? ` · ${pr}` : ''}`)
      })
    }
    lines.push(`\n---\n发送“确认/确定/好/可以/就这样/开始”即可落地；发送“取消/算了/先不做”取消；也可输入 \`/apply\`。`)
    lines.push(`\n<details><summary>展开全部任务</summary>\n`)
    tasks.slice(0, 20).forEach((t, idx) => {
      const title = String(t?.title || '').trim()
      if (!title) return
      const pr = String(t?.priority || '').trim()
      lines.push(`\n### ${idx + 1}. ${title}${pr ? ` · **${pr}**` : ''}`)
      const desc = String(t?.description || '').trim()
      if (desc) lines.push(`\n> ${desc.replace(/\n+/g, ' ').slice(0, 220)}`)
      const checklist = Array.isArray(t?.checklist) ? t.checklist : []
      if (checklist.length) {
        lines.push(`\n**验收**：`)
        for (const c of checklist.slice(0, 12)) {
          const s = String(c || '').trim()
          if (s) lines.push(`- [ ] ${s}`)
        }
      }
      const dels = Array.isArray(t?.deliverables) ? t.deliverables : []
      if (dels.length) {
        lines.push(`\n**交付物**：`)
        for (const d of dels.slice(0, 8)) {
          const dt = String(d?.title || '').trim()
          if (!dt) continue
          const ty = String(d?.type || '').trim() || 'LINK'
          lines.push(`- ${ty}: ${dt}`)
        }
      }
    })
    if (tasks.length > 20) lines.push(`\n…（已省略 ${tasks.length - 20} 条）`)
    lines.push(`\n</details>`)
    return lines.join('\n')
  }

  function formatApplyPreview(plan: AiAgentPlanResponse, projectId: number) {
    const tasks = Array.isArray(plan.tasks) ? plan.tasks : []
    const titles = tasks
      .slice(0, 5)
      .map((t, idx) => `${idx + 1}. ${String(t?.title || '').trim()}`)
      .filter((s) => s.length > 3)
    const lines: string[] = []
    lines.push(`## 落地预览`)
    lines.push(`\nContext 项目：${projectId}`)
    lines.push(`\n将创建任务：${tasks.length} 条`)
    if (titles.length) {
      lines.push(`\n任务标题：`)
      for (const t of titles) lines.push(`- ${t}`)
    }
    lines.push(`\n---\n发送“确认/确定/好/可以/开始”继续，发送“取消/算了/先不做”终止。`)
    return lines.join('\n')
  }

  function formatStageMarkdown(title: string, body: string) {
    const text = String(body || '').trim()
    const head = text.split('\n').slice(0, 6).join('\n')
    const rest = text.split('\n').slice(6).join('\n')
    const lines: string[] = []
    lines.push(`### ${title}`)
    if (head) lines.push(head)
    if (rest.trim()) {
      lines.push(`\n<details><summary>展开更多</summary>\n\n${rest.trim()}\n\n</details>`)
    }
    return lines.join('\n')
  }

  async function aiOneShot(projectId: number | null, prompt: string) {
    const req = { messages: [{ role: 'user' as any, content: String(prompt || '') }], ...(projectId ? { projectId } : {}) }
    let out = ''
    await aiApi.chatStream(req as any, (d) => {
      out += d
    })
    return out.trim()
  }

  function ymd(d: Date) {
    const yyyy = d.getFullYear()
    const mm = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    return `${yyyy}-${mm}-${dd}`
  }

  function parseId(text: string, key: string) {
    const t = String(text || '')
    const re1 = new RegExp(`${key}\\s*#\\s*(\\d+)`, 'i')
    const re2 = new RegExp(`${key}\\s*id\\s*[:= ]\\s*(\\d+)`, 'i')
    const m = re1.exec(t) || re2.exec(t)
    return m ? Number(m[1]) : null
  }

  async function planWeeklyReport(projectId: number | null) {
    const assistantId = uid()
    messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: '', createdAt: Date.now() }]
    if (!projectId) {
      setAssistant(assistantId, '请先在上方 Context 里选择一个项目，然后再说“生成周报”。')
      return
    }
    try {
      const end = new Date()
      const start = new Date(end.getTime() - 6 * 24 * 60 * 60 * 1000)
      const overview = await dashboardApi.overview({ projectId, startDate: ymd(start), endDate: ymd(end), lite: true })
      const prompt = [
        '你是一个项目周报助手，请基于给定数据输出一份简洁周报，要求：',
        '- 只输出 Markdown',
        '- 总字数尽量控制在 300-600 字',
        '- 结构固定：本周概览 / 完成 / 进行中 / 风险 / 下周计划',
        '- 不要输出免责声明或废话',
        '',
        '数据(JSON)：',
        '```json',
        JSON.stringify(
          {
            projectId,
            range: { start: ymd(start), end: ymd(end) },
            stats: {
              taskTotal: overview.taskTotal,
              doneTaskTotal: overview.doneTaskTotal,
              taskDoneRate: overview.taskDoneRate,
              wipTotal: overview.wipTotal,
              tasksCreatedThisWeek: overview.tasksCreatedThisWeek
            },
            myActions: (overview.myActions || []).slice(0, 8),
            riskTasks: (overview.riskTasks || []).slice(0, 8),
            topDiscussedTasks: (overview.topDiscussedTasks || []).slice(0, 6)
          },
          null,
          2
        ),
        '```'
      ].join('\n')
      const md = await aiOneShot(projectId, prompt)
      const title = `周报 · ${ymd(start)} ~ ${ymd(end)}`
      const out = [
        `## 周报（预览）`,
        ``,
        `Context 项目：${projectId}`,
        ``,
        md,
        ``,
        `---`,
        `发送“确认”保存到知识库；发送“取消”放弃。`
      ].join('\n')
      pendingAction.value = { kind: 'WEEKLY_REPORT', projectId, title, markdown: md }
      setAssistant(assistantId, out)
    } catch (e: any) {
      setAssistant(assistantId, `生成周报失败：${e?.message || '失败'}`)
    }
  }

  async function pickOpenMilestone(projectId: number): Promise<Milestone | null> {
    const list = await milestoneApi.list(projectId, false)
    const open = list.filter((m) => String(m.status || '') === 'OPEN')
    if (!open.length) return null
    open.sort((a, b) => (b.id || 0) - (a.id || 0))
    return open[0]
  }

  async function planReleaseNotesDraft(projectId: number | null, text: string) {
    const assistantId = uid()
    messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: '', createdAt: Date.now() }]
    if (!projectId) {
      setAssistant(assistantId, '请先在上方 Context 里选择一个项目，然后再说“草拟发布说明”。')
      return
    }
    try {
      const mid = parseId(text, 'milestone') || parseId(text, '里程碑')
      const ms = mid ? (await milestoneApi.list(projectId, true)).find((m) => m.id === mid) || null : await pickOpenMilestone(projectId)
      if (!ms) {
        setAssistant(assistantId, '未找到可用里程碑（OPEN）。请先创建里程碑，或指定 `里程碑#123`。')
        return
      }
      const all = await taskApi.listByProject(projectId)
      const done = all.filter((t: any) => Number(t.milestoneId || 0) === ms.id && String(t.status || '') === 'DONE').slice(0, 30)
      const items: any[] = []
      for (const t of done) {
        let dels: any[] = []
        try {
          dels = await taskApi.deliverables(t.id)
        } catch {
        }
        items.push({ id: t.id, title: t.title, priority: t.priority || null, deliverables: dels.slice(0, 8) })
      }
      const prompt = [
        '你是一个 Release Notes 草拟助手，请基于给定里程碑与完成任务输出一份简洁发布说明，要求：',
        '- 只输出 Markdown',
        '- 默认只列出重点（5-12条），其余放到折叠详情',
        '- 结构建议：Highlights / Fixed & Improved / Deliverables / Notes',
        '- 不要输出冗长的过程描述',
        '',
        '数据(JSON)：',
        '```json',
        JSON.stringify(
          {
            projectId,
            milestone: { id: ms.id, name: ms.name },
            doneTasks: items
          },
          null,
          2
        ),
        '```'
      ].join('\n')
      const md = await aiOneShot(projectId, prompt)
      const title = `Release Notes Draft · ${ms.name}`
      const out = [
        `## Release Notes（预览）`,
        ``,
        `里程碑：${ms.name} (#${ms.id})`,
        ``,
        md,
        ``,
        `---`,
        `发送“确认”保存到知识库；发送“取消”放弃。`
      ].join('\n')
      pendingAction.value = { kind: 'RELEASE_NOTES_DRAFT', projectId, milestoneId: ms.id, title, markdown: md }
      setAssistant(assistantId, out)
    } catch (e: any) {
      setAssistant(assistantId, `草拟发布说明失败：${e?.message || '失败'}`)
    }
  }

  async function planGate(projectId: number | null, text: string) {
    const assistantId = uid()
    messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: '', createdAt: Date.now() }]
    if (!projectId) {
      setAssistant(assistantId, '请先在上方 Context 里选择一个项目，然后再说“验收检查/发布检查”。')
      return
    }
    try {
      const mid = parseId(text, 'milestone') || parseId(text, '里程碑')
      const ms = mid ? (await milestoneApi.list(projectId, true)).find((m) => m.id === mid) || null : await pickOpenMilestone(projectId)
      if (!ms) {
        setAssistant(assistantId, '未找到可用里程碑（OPEN）。请先创建里程碑，或指定 `里程碑#123`。')
        return
      }
      const all = await taskApi.listByProject(projectId)
      const inMs = all.filter((t: any) => Number(t.milestoneId || 0) === ms.id)
      const notDone = inMs.filter((t: any) => String(t.status || '') !== 'DONE')
      const needFixTaskIds: number[] = []
      const issues: string[] = []
      issues.push(`里程碑：${ms.name} (#${ms.id})`)
      issues.push(`任务总数：${inMs.length}，未完成：${notDone.length}`)
      if (notDone.length) {
        issues.push(`\n**未完成任务（最多10条）**：`)
        for (const t of notDone.slice(0, 10)) issues.push(`- [#${t.id}](/projects/${projectId}/tasks/${t.id}) ${t.title}`)
      }
      const done = inMs.filter((t: any) => String(t.status || '') === 'DONE').slice(0, 30)
      const missing: string[] = []
      for (const t of done) {
        let cl: any[] = []
        let dels: any[] = []
        try {
          cl = await taskApi.checklist(t.id)
        } catch {
        }
        try {
          dels = await taskApi.deliverables(t.id)
        } catch {
        }
        const miss = []
        if (!cl.length) miss.push('验收清单')
        if (!dels.length) miss.push('交付物')
        if (miss.length) {
          needFixTaskIds.push(t.id)
          missing.push(`- [#${t.id}](/projects/${projectId}/tasks/${t.id}) ${t.title}（缺：${miss.join('、')}）`)
        }
      }
      if (missing.length) {
        issues.push(`\n**已完成但不够“可交付”的任务（最多10条）**：`)
        for (const x of missing.slice(0, 10)) issues.push(x)
      }
      const out = [
        `## Gate 检查（预览）`,
        ``,
        issues.join('\n'),
        ``,
        `---`,
        needFixTaskIds.length
          ? `发送“确认”自动补齐缺失的验收清单/交付物占位（仅对缺失项，且不改已有内容）；发送“取消”放弃。`
          : `没有发现需要自动补齐的项。`
      ].join('\n')
      pendingAction.value = needFixTaskIds.length ? { kind: 'GATE_FIX', projectId, taskIds: needFixTaskIds } : null
      setAssistant(assistantId, out)
    } catch (e: any) {
      setAssistant(assistantId, `Gate 检查失败：${e?.message || '失败'}`)
    }
  }

  async function planInboxTriage(projectId: number | null) {
    const assistantId = uid()
    messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: '', createdAt: Date.now() }]
    try {
      const res = await inboxApi.list({ limit: 40, handled: false, ...(projectId ? { projectId } : {}) })
      const list = (res.list || []).slice(0, 30)
      if (!list.length) {
        setAssistant(assistantId, '收件箱暂无需要处理的消息。')
        return
      }
      const prompt = [
        '你是一个 Inbox 分流助手，请基于给定收件箱条目输出简洁分流结果，要求：',
        '- 只输出 Markdown',
        '- 总字数尽量控制在 250-500 字',
        '- 输出结构：总览 / 建议处理顺序(Top5) / 可一键处理的建议',
        '- 不要输出冗长解释',
        '',
        '数据(JSON)：',
        '```json',
        JSON.stringify(
          list.map((x: InboxItem) => ({
            id: x.id,
            category: x.category,
            title: x.title,
            projectId: x.projectId ?? null,
            taskId: x.taskId ?? null,
            isRead: x.isRead,
            isHandled: x.isHandled,
            createTime: x.createTime ?? null
          })),
          null,
          2
        ),
        '```'
      ].join('\n')
      const md = await aiOneShot(projectId ?? null, prompt)
      const ids = list.map((x) => x.id)
      const out = [
        `## Inbox 分流（预览）`,
        ``,
        md,
        ``,
        `---`,
        `发送“确认”将上述 ${ids.length} 条全部标记为已处理；发送“取消”放弃。`
      ].join('\n')
      pendingAction.value = { kind: 'INBOX_HANDLE', ids }
      setAssistant(assistantId, out)
    } catch (e: any) {
      setAssistant(assistantId, `Inbox 分流失败：${e?.message || '失败'}`)
    }
  }

  async function runPendingActionConfirm() {
    const pa = pendingAction.value
    if (!pa) return
    const assistantId = uid()
    messages.value = [...messages.value, { id: assistantId, role: 'assistant', content: '', createdAt: Date.now() }]
    pendingAction.value = null
    try {
      if (pa.kind === 'WEEKLY_REPORT') {
        const id = await kbApi.createExternalDoc({ projectId: pa.projectId, title: pa.title, content: pa.markdown })
        setAssistant(assistantId, `已保存到知识库：kbDocId=${id}`)
        return
      }
      if (pa.kind === 'RELEASE_NOTES_DRAFT') {
        const id = await kbApi.createExternalDoc({ projectId: pa.projectId, title: pa.title, content: pa.markdown })
        setAssistant(assistantId, `已保存到知识库：kbDocId=${id}`)
        return
      }
      if (pa.kind === 'INBOX_HANDLE') {
        await inboxApi.handleBatch(pa.ids)
        setAssistant(assistantId, `已标记为已处理：${pa.ids.length} 条`)
        return
      }
      if (pa.kind === 'GATE_FIX') {
        let fixed = 0
        for (const tid of pa.taskIds.slice(0, 30)) {
          let cl: any[] = []
          let dels: any[] = []
          try {
            cl = await taskApi.checklist(tid)
          } catch {
          }
          try {
            dels = await taskApi.deliverables(tid)
          } catch {
          }
          if (!cl.length) {
            await taskApi.createChecklistItem(tid, '补齐验收标准（可测）')
            fixed += 1
          }
          if (!dels.length) {
            await taskApi.createDeliverable(tid, { type: 'LINK', title: '补齐交付链接（PR/DOC/演示）', url: null })
            fixed += 1
          }
        }
        setAssistant(assistantId, `已自动补齐缺失项：${fixed} 项（占位）`)
        return
      }
      setAssistant(assistantId, '已完成。')
    } catch (e: any) {
      setAssistant(assistantId, `执行失败：${e?.message || '失败'}`)
    }
  }

  function clear() {
    messages.value = []
    lastAgentPlan.value = null
    pendingApply.value = null
    lastApply.value = null
    historyProjectId.value = null
    pendingIntent.value = null
    pendingAction.value = null
    persistAgentState()
  }

  function pushAssistantMessage(content: string) {
    const c = String(content || '')
    if (!c.trim()) return
    messages.value = [...messages.value, { id: uid(), role: 'assistant', content: c, createdAt: Date.now() }]
  }

  async function loadHistory(projectId?: number | null) {
    const pid = projectId == null ? null : Number(projectId)
    if (historyProjectId.value === pid) return
    historyProjectId.value = pid
    if (!pendingApply.value || pendingApply.value.projectId !== pid) {
      pendingApply.value = null
      persistAgentState()
    }
    try {
      const list = await aiApi.historyList({ projectId: pid ?? undefined, limit: 120 })
      const items = list
        .slice()
        .sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0))
        .slice(-80)
      const restored: ChatMessage[] = []
      for (const h of items) {
        const t = h.createdAt || Date.now()
        const prompt = String(h.prompt || '').trim()
        const resp = String(h.response || '').trim()
        if (prompt) restored.push({ id: uid(), role: 'user', content: prompt, createdAt: t })
        if (resp) restored.push({ id: uid(), role: 'assistant', content: resp, createdAt: t + 1 })
      }
      messages.value = restored
    } catch {
    }
  }

  return {
    messages,
    sending,
    send,
    deleteUserMessage,
    clear,
    lastAgentPlan,
    pendingApply,
    loadHistory,
    autoOpenTask,
    autoOpenNonce,
    pushAssistantMessage,
    setPendingFileContext
  }
})

function shouldRunWeeklyReport(text: string) {
  const s = String(text || '').trim()
  if (!s) return false
  return /(周报|本周进展|本周汇报|weekly report)/i.test(s)
}

function shouldRunReleaseNotesDraft(text: string) {
  const s = String(text || '').trim()
  if (!s) return false
  return /(发布说明|release notes|里程碑.*说明|版本说明|更新说明)/i.test(s)
}

function shouldRunGate(text: string) {
  const s = String(text || '').trim()
  if (!s) return false
  return /(gate|验收.*检查|发布.*检查|交付.*检查|上线.*检查|里程碑.*检查)/i.test(s)
}

function shouldRunInboxTriage(text: string) {
  const s = String(text || '').trim()
  if (!s) return false
  return /(收件箱.*整理|整理收件箱|inbox.*triage|收件箱分流|通知分流|收件箱清理)/i.test(s)
}
