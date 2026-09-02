<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NDropdown, NForm, NFormItem, NInput, NModal, NSelect, useMessage } from 'naive-ui'
import { useAiChatStore } from '../stores/aiChat'
import MarkdownView from '../components/MarkdownView.vue'
import { useProjectStore } from '../stores/project'
import { kbApi } from '../api/kb'
import { docgenApi, type DocGenSaveTo } from '../api/docgen'
import AiCodeReviewView from './AiCodeReviewView.vue'
import AiHistoryView from './AiHistoryView.vue'
import AiUsageView from './AiUsageView.vue'
import { uploadAiFile } from '../api/aiFile'

const chat = useAiChatStore()
const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const message = useMessage()

const projectId = ref<number | null>(null)
const input = ref('')
const viewport = ref<HTMLDivElement | null>(null)
const streamCursor = computed(() => (chat.sending ? 'typing' : ''))
const planPreviewOpen = ref(false)
const currentPlan = computed(() => chat.lastAgentPlan)
const currentPlanTasks = computed(() => (Array.isArray(currentPlan.value?.tasks) ? currentPlan.value!.tasks : []))
const currentPlanGoal = computed(() => String(currentPlan.value?.goal || '').trim())
const currentPlanTop = computed(() => currentPlanTasks.value.slice(0, 5))
const currentPlanPreview = computed(() => currentPlanTasks.value.slice(0, 2))
const currentPlanHighCount = computed(() =>
  currentPlanTasks.value.filter((task) => String(task?.priority || '').trim().toUpperCase() === 'HIGH').length,
)
const legacyPlanSummaryPattern = /<details>\s*<summary>\s*查看完整任务清单\s*<\/summary>[\s\S]*?<\/details>/gi

type ThinkingCard = {
  state: 'thinking' | 'done' | 'error'
  steps: Array<{ state: string; label: string }>
  reason: string
}

type AiTab = 'chat' | 'review' | 'history' | 'usage'
const tab = ref<AiTab>('chat')

const docOpen = ref(false)
const docTitle = ref('')
const docUrl = ref('')
const docContent = ref('')
const docSaving = ref(false)

const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

type PendingAttachment = {
  id: string
  file: File
  name: string
  type: string
  size: number
  previewUrl: string | null
}
const pendingAttachments = ref<PendingAttachment[]>([])

const voiceOn = ref(false)
const speakingOn = ref(false)
let recog: any = null
let persistTimer: any = null

function localId() {
  return Math.random().toString(16).slice(2) + Date.now().toString(16)
}

const plusOptions = computed(() => [
  {
    key: 'file',
    label: uploading.value
      ? '文件（发送中…）'
      : pendingAttachments.value.length
        ? `文件（已选 ${pendingAttachments.value.length}）`
        : '文件'
  },
  { key: 'voice', label: voiceOn.value ? '语音输入 · 开' : '语音输入' },
  { key: 'speak', label: speakingOn.value ? '语音播报 · 开' : '语音播报' },
  { key: 'doc', label: 'Add Doc' }
])

function detectGenIntent(
  text: string
): { type: 'PPTX' | 'DOCX'; requirement: string; saveTo: DocGenSaveTo; taskId: number | null } | null {
  const t = String(text || '').trim()
  if (!t) return null
  const low = t.toLowerCase()
  const isPpt =
    /生成.*ppt|做.*ppt|输出.*ppt|导出.*ppt|ppt汇报|汇报ppt|路演ppt|pptx/.test(t) || low.includes('ppt')
  const isDoc =
    /生成.*word|做.*word|输出.*word|导出.*word|项目文档|方案文档|需求文档|prd|docx/.test(t) || low.includes('word')
  if (!isPpt && !isDoc) return null
  const type: 'PPTX' | 'DOCX' = isPpt && !isDoc ? 'PPTX' : isDoc && !isPpt ? 'DOCX' : 'DOCX'
  let saveTo: DocGenSaveTo = 'KB'
  if (/仅下载|不保存|只下载/.test(t)) saveTo = 'DOWNLOAD'
  else if (/两者|都保存|知识库.*交付物|交付物.*知识库/.test(t)) saveTo = 'BOTH'
  else if (/交付物/.test(t)) saveTo = 'DELIVERABLE'
  const m = t.match(/taskId\s*[:= ]\s*(\d+)/i) || t.match(/任务\s*#?\s*(\d+)/)
  const taskId = m ? Number(m[1]) : null
  return { type, requirement: t, saveTo, taskId: taskId && Number.isFinite(taskId) ? taskId : null }
}

async function genDirect(intent: { type: 'PPTX' | 'DOCX'; requirement: string; saveTo: DocGenSaveTo; taskId: number | null }) {
  if (!projectId.value) {
    message.error('请先选择 Context 项目')
    return
  }
  const needTask = intent.saveTo === 'DELIVERABLE' || intent.saveTo === 'BOTH'
  if (needTask && !intent.taskId) {
    chat.pushAssistantMessage('保存到交付物需要提供 taskId，例如：`生成PPT ... 保存到交付物 taskId=123`')
    return
  }
  chat.pushAssistantMessage(`正在生成 ${intent.type} …`)
  try {
    const style = inferDocStyle(intent.requirement)
    const req = {
      projectId: projectId.value,
      requirement: intent.requirement,
      title: null,
      style,
      saveTo: intent.saveTo,
      taskId: needTask ? intent.taskId : null
    }
    const res =
      intent.type === 'PPTX' ? await docgenApi.generatePptx(req) : await docgenApi.generateDocx(req)
    const lines: string[] = []
    lines.push(`## 已生成 ${intent.type}`)
    lines.push(`- 文件：${res.filename}`)
    lines.push(`- 预览：[预览](/api/assets/${res.assetId}/preview)`)
    lines.push(`- 下载：[下载](${res.downloadUrl})`)
    if (res.kbDocId) lines.push(`- 知识库：kbDocId=${res.kbDocId}`)
    if (res.deliverableId) lines.push(`- 交付物：deliverableId=${res.deliverableId}`)
    chat.pushAssistantMessage(lines.join('\n'))
    message.success('已生成')
  } catch (e: any) {
    message.error(e?.message || '生成失败')
  }
}

function inferDocStyle(text: string): string {
  const t = String(text || '')
  if (/TECH|技术|架构|研发|接口|方案评审|设计/.test(t)) return 'TECH'
  if (/MINIMAL|极简|简洁|清爽|一页|精简/.test(t)) return 'MINIMAL'
  if (/BUSINESS|商务|商业|汇报|路演|投标|项目汇报/.test(t)) return 'BUSINESS'
  return 'BUSINESS'
}

async function send() {
  if (chat.sending || uploading.value) return
  const raw = input.value
  const text = String(raw || '')
  const hasFiles = pendingAttachments.value.length > 0
  const intent = !hasFiles ? detectGenIntent(text) : null
  if (intent) {
    input.value = ''
    await genDirect(intent)
    return
  }
  if (!text.trim() && !hasFiles) return
  if (hasFiles && !projectId.value) {
    message.error('请先选择 Context 项目')
    return
  }
  const toUpload = pendingAttachments.value.slice()
  const uploaded: { assetId: number; filename: string; extractedText: string }[] = []
  if (toUpload.length && projectId.value) {
    uploading.value = true
    try {
      for (const f of toUpload) {
        const res = await uploadAiFile(projectId.value, f.file)
        uploaded.push({ assetId: res.assetId, filename: res.filename, extractedText: String(res.extractedText || '').trim() })
      }
    } catch (e: any) {
      message.error(e?.message || '上传失败')
      return
    } finally {
      uploading.value = false
    }
  }
  input.value = ''
  for (const f of pendingAttachments.value) {
    try {
      if (f.previewUrl) URL.revokeObjectURL(f.previewUrl)
    } catch {
    }
  }
  pendingAttachments.value = []
  await chat.send(text, projectId.value, uploaded)
}

function openPlanPreview() {
  if (!currentPlan.value) return
  planPreviewOpen.value = true
}

function sanitizeAssistantContent(content: string) {
  return String(content || '').replace(legacyPlanSummaryPattern, '').trim()
}

function parseAssistantSections(content: string): { thinking: ThinkingCard | null; body: string } {
  const text = sanitizeAssistantContent(content)
  if (!text) return { thinking: null, body: '' }
  const match = text.match(/^###\s*AI 思考[\s\S]*?(?=\n---\n|$)/)
  if (!match) return { thinking: null, body: text }
  const thinkingRaw = String(match[0] || '').trim()
  const body = text.slice(match[0].length).replace(/^\n---\n/, '').trim()
  const steps = Array.from(thinkingRaw.matchAll(/^- (已完成|进行中|待处理) · (.+)$/gm)).map((item) => ({
    state: String(item[1] || '').trim(),
    label: String(item[2] || '').trim()
  }))
  const reasonMatch = thinkingRaw.match(/\*\*失败原因\*\*\s*- (.+)/)
  const state: ThinkingCard['state'] = reasonMatch ? 'error' : /\*\*已完成\*\*/.test(thinkingRaw) ? 'done' : 'thinking'
  return {
    thinking: steps.length || reasonMatch ? { state, steps, reason: String(reasonMatch?.[1] || '').trim() } : null,
    body
  }
}

function isPlanMessage(content: string, id: string) {
  if (chat.lastAgentPlanMessageId && chat.lastAgentPlanMessageId === id) return true
  const text = parseAssistantSections(content).body || sanitizeAssistantContent(content)
  if (!text || !currentPlan.value || !currentPlanTasks.value.length) return false
  return /(^|\n)##\s*当前方案\b/.test(text) || /执行分解我已经整理好了/.test(text)
}

function pickFile() {
  if (!projectId.value) {
    message.error('请先选择 Context 项目')
    return
  }
  fileInput.value?.click()
}

async function onFileChange(e: Event) {
  const el = e.target as HTMLInputElement
  const files = el.files ? Array.from(el.files) : []
  el.value = ''
  if (!files.length) return
  const next: PendingAttachment[] = []
  for (const f of files) {
    const isImg = String(f.type || '').startsWith('image/')
    let previewUrl: string | null = null
    if (isImg) {
      try {
        previewUrl = URL.createObjectURL(f)
      } catch {
        previewUrl = null
      }
    }
    next.push({ id: localId(), file: f, name: f.name, type: f.type, size: f.size, previewUrl })
  }
  pendingAttachments.value = [...pendingAttachments.value, ...next]
  schedulePersist()
}

function removePendingAttachment(id: string) {
  const cur = pendingAttachments.value.slice()
  const idx = cur.findIndex((x) => x.id === id)
  if (idx < 0) return
  const item = cur[idx]
  try {
    if (item.previewUrl) URL.revokeObjectURL(item.previewUrl)
  } catch {
  }
  cur.splice(idx, 1)
  pendingAttachments.value = cur
  schedulePersist()
}

function ensureRecognition() {
  if (recog) return recog
  const w = window as any
  const Ctor = w.SpeechRecognition || w.webkitSpeechRecognition
  if (!Ctor) return null
  const r = new Ctor()
  r.continuous = false
  r.interimResults = true
  r.lang = 'zh-CN'
  r.onresult = (ev: any) => {
    try {
      let text = ''
      for (let i = ev.resultIndex; i < ev.results.length; i++) {
        text += String(ev.results[i][0]?.transcript || '')
      }
      if (text) input.value = (input.value ? input.value + ' ' : '') + text.trim()
    } catch {
    }
  }
  r.onend = () => {
    voiceOn.value = false
  }
  r.onerror = () => {
    voiceOn.value = false
  }
  recog = r
  return r
}

function toggleVoice() {
  if (voiceOn.value) {
    voiceOn.value = false
    try {
      recog?.stop?.()
    } catch {
    }
    return
  }
  const r = ensureRecognition()
  if (!r) {
    message.error('当前浏览器不支持语音输入')
    return
  }
  voiceOn.value = true
  try {
    r.start()
  } catch {
    voiceOn.value = false
  }
}

function toggleSpeaking() {
  speakingOn.value = !speakingOn.value
  try {
    localStorage.setItem('dtc_ai_speak', speakingOn.value ? '1' : '0')
  } catch {
  }
  if (!speakingOn.value) {
    try {
      window.speechSynthesis?.cancel?.()
    } catch {
    }
  }
}

function openDoc() {
  docTitle.value = ''
  docUrl.value = ''
  docContent.value = ''
  docOpen.value = true
}

function onPlusSelect(key: string) {
  if (key === 'file') pickFile()
  else if (key === 'voice') toggleVoice()
  else if (key === 'speak') toggleSpeaking()
  else if (key === 'doc') openDoc()
}

async function saveDoc() {
  const title = docTitle.value.trim()
  const content = docContent.value.trim()
  if (!title || !content) {
    message.error('请填写标题与内容')
    return
  }
  docSaving.value = true
  try {
    await kbApi.createExternalDoc({
      projectId: projectId.value,
      title,
      url: docUrl.value.trim() ? docUrl.value.trim() : null,
      content
    })
    docOpen.value = false
    message.success('已保存到知识库')
  } catch (e: any) {
    message.error(e?.message || '保存失败')
  } finally {
    docSaving.value = false
  }
}

async function scrollToBottom() {
  await nextTick()
  const el = viewport.value
  if (!el) return
  el.scrollTop = el.scrollHeight
}

watch(
  () => chat.messages.length,
  () => scrollToBottom()
)

watch(
  () => chat.messages[chat.messages.length - 1]?.content,
  () => scrollToBottom()
)

watch(
  () => chat.messages.length,
  () => {
    if (!speakingOn.value) return
    const last = chat.messages[chat.messages.length - 1]
    if (!last || last.role !== 'assistant') return
    const text = String(last.content || '').replace(/\s+/g, ' ').trim()
    if (!text) return
    const t = text.length > 380 ? text.slice(0, 380) : text
    try {
      const u = new SpeechSynthesisUtterance(t)
      u.lang = 'zh-CN'
      window.speechSynthesis?.cancel?.()
      window.speechSynthesis?.speak?.(u)
    } catch {
    }
  }
)

watch(
  () => chat.autoOpenNonce,
  () => {
    const t = chat.autoOpenTask
    if (!t?.projectId || !t?.taskId) return
    router.push({ name: 'task-detail', params: { projectId: t.projectId, taskId: t.taskId } })
  }
)

function syncQuery(id: number | null) {
  const q = { ...route.query }
  if (id == null) delete q.projectId
  else q.projectId = String(id)
  router.replace({ query: q })
}

function syncTab(t: AiTab) {
  const q = { ...route.query }
  if (t === 'chat') delete q.tab
  else q.tab = t
  router.replace({ query: q })
}

function readTabFromQuery(): AiTab {
  const t = route.query.tab ? String(route.query.tab) : ''
  if (t === 'review') return 'review'
  if (t === 'history') return 'history'
  if (t === 'usage') return 'usage'
  return 'chat'
}

function persistState() {
  try {
    localStorage.setItem('dtc_ai_state', JSON.stringify({ tab: tab.value, projectId: projectId.value ?? null, input: input.value, ts: Date.now() }))
  } catch {
  }
}

function schedulePersist() {
  if (persistTimer) clearTimeout(persistTimer)
  persistTimer = setTimeout(() => persistState(), 240)
}

onMounted(async () => {
  await scrollToBottom()
  if (!projectStore.projects.length) {
    await projectStore.load()
  }
  tab.value = readTabFromQuery()
  try {
    speakingOn.value = localStorage.getItem('dtc_ai_speak') === '1'
  } catch {
  }

  try {
    const raw = localStorage.getItem('dtc_ai_state')
    const obj = raw ? (JSON.parse(raw) as any) : null
    if (!route.query.projectId && obj && obj.projectId && Number.isFinite(Number(obj.projectId))) {
      projectId.value = Number(obj.projectId)
    }
    if (!route.query.tab && obj && obj.tab && (obj.tab === 'review' || obj.tab === 'history' || obj.tab === 'usage')) {
      tab.value = obj.tab
      syncTab(tab.value)
    }
    if (obj && typeof obj.input === 'string' && !input.value) {
      input.value = obj.input
    }
  } catch {
  }

  const qid = route.query.projectId ? Number(route.query.projectId) : null
  if (qid && Number.isFinite(qid)) {
    projectId.value = qid
  } else if (projectId.value == null && projectStore.visibleProjects[0]) {
    projectId.value = projectStore.visibleProjects[0].id
  }
  if (projectId.value != null && !route.query.projectId) {
    syncQuery(projectId.value)
  }
  await chat.loadHistory(projectId.value)
  persistState()
})

watch(
  () => route.query.tab,
  () => {
    tab.value = readTabFromQuery()
    schedulePersist()
  }
)

watch(
  () => projectId.value,
  async (pid, prev) => {
    schedulePersist()
    if (pid === prev) return
    if (pid == null) return
    try {
      await chat.loadHistory(pid)
      await nextTick()
      await scrollToBottom()
    } catch {
    }
  }
)

watch(
  () => input.value,
  () => schedulePersist()
)
</script>

<template>
  <div class="chat-shell">
    <div class="page chat-page">
      <div class="head">
        <div class="tools">
          <div class="tabs">
            <button class="tab" :class="{ on: tab === 'chat' }" @click="syncTab('chat')">对话</button>
            <button class="tab" :class="{ on: tab === 'review' }" @click="syncTab('review')">审查</button>
            <button class="tab" :class="{ on: tab === 'history' }" @click="syncTab('history')">历史</button>
            <button class="tab" :class="{ on: tab === 'usage' }" @click="syncTab('usage')">用量</button>
          </div>
          <n-select
            v-model:value="projectId"
            size="small"
            :options="projectStore.visibleProjects.map((p) => ({ label: p.name, value: p.id }))"
            placeholder="No project"
            clearable
            style="width: 220px"
            @update:value="syncQuery"
          />
        </div>
      </div>

      <div v-if="tab === 'chat'" class="frame">
        <div ref="viewport" class="viewport">
          <div v-for="m in chat.messages" :key="m.id" class="row" :class="m.role">
            <div class="bubble" :class="{ streaming: m.role === 'assistant' && chat.sending && m === chat.messages[chat.messages.length - 1] }">
              <div v-if="m.role === 'assistant'" class="roleMeta assistant">AI</div>
              <div v-if="m.role === 'user' && (m as any).attachments && (m as any).attachments.length" class="attList">
                <a
                  v-for="a in (m as any).attachments"
                  :key="String(a.assetId)"
                  class="attPill"
                  :href="`/api/assets/${a.assetId}/preview`"
                  target="_blank"
                  rel="noreferrer"
                >
                  {{ a.filename }}
                </a>
              </div>
              <template v-if="m.role === 'assistant'">
                <template v-for="section in [parseAssistantSections(m.content)]" :key="`assistant-${m.id}`">
                  <div v-if="section.thinking" class="thinkingCard" :class="`is-${section.thinking.state}`">
                    <div class="thinkingHead">
                      <span class="thinkingTitle">
                        {{
                          section.thinking.state === 'done'
                            ? '已思考'
                            : section.thinking.state === 'error'
                              ? '思考中断'
                              : '思考中'
                        }}
                      </span>
                    </div>
                    <div v-if="section.thinking.steps.length" class="thinkingBody">
                      <div class="thinkingSteps">
                        <div v-for="(step, idx) in section.thinking.steps" :key="`${m.id}-thinking-${idx}`" class="thinkingStep">
                          <span class="thinkingStepState">{{ step.state }}</span>
                          <span class="thinkingStepLabel">{{ step.label }}</span>
                        </div>
                      </div>
                    </div>
                    <div v-if="section.thinking.reason" class="thinkingBody">
                      <div class="thinkingError">{{ section.thinking.reason }}</div>
                    </div>
                  </div>
                  <markdown-view v-if="section.body" :content="section.body" :allow-details="true" />
                </template>
                <div
                  v-if="isPlanMessage(m.content, m.id) && currentPlan && currentPlanTasks.length"
                  class="planInline"
                >
                  <div class="planInlineBar">
                    <div class="planInlineMeta">
                      <span class="planInlineTag">执行分解</span>
                      <span class="planInlineText">{{ currentPlanTasks.length }} 项执行内容</span>
                      <span v-if="currentPlanHighCount" class="planInlineText">{{ currentPlanHighCount }} 项高优先级</span>
                    </div>
                    <button type="button" class="planInlineLink" @click="openPlanPreview">查看完整分解</button>
                  </div>
                  <div v-if="currentPlanPreview.length" class="planInlinePreview">
                    <span
                      v-for="(task, idx) in currentPlanPreview"
                      :key="`${idx}-${task.title}`"
                      class="planInlinePreviewItem"
                    >
                      {{ idx + 1 }}. {{ task.title }}
                    </span>
                  </div>
                </div>
              </template>
              <div v-else class="plain">{{ m.content }}</div>
              <span v-if="m.role === 'assistant' && chat.sending && m === chat.messages[chat.messages.length - 1]" class="stream-cursor" :class="streamCursor" />
            </div>
            <div v-if="m.role === 'user'" class="msgActions">
              <n-button size="tiny" quaternary :disabled="chat.sending || uploading" @click="chat.deleteUserMessage(m.id)">删除</n-button>
            </div>
          </div>
        </div>

        <div class="composer">
          <div class="composer-inner">
            <input ref="fileInput" type="file" multiple class="fileInput" @change="onFileChange" />
            <div class="composerBox">
              <div v-if="pendingAttachments.length" class="pendingFiles">
                <div v-for="f in pendingAttachments" :key="f.id" class="pendingFile">
                  <div v-if="f.previewUrl" class="thumb">
                    <img :src="f.previewUrl" alt="" />
                  </div>
                  <div v-else class="fileMark">
                    {{ f.name.split('.').pop() }}
                  </div>
                  <div class="fileName" :title="f.name">{{ f.name }}</div>
                  <button class="fileRemove" type="button" @click="removePendingAttachment(f.id)">×</button>
                </div>
              </div>

              <n-input
                v-model:value="input"
                class="composerInput"
                type="textarea"
                placeholder="说点什么…  Enter 发送 · Shift+Enter 换行"
                :autosize="{ minRows: 1, maxRows: 5 }"
                @keydown.enter.exact.prevent="send"
              />

              <div class="composerActions">
                <n-dropdown :options="plusOptions" placement="top-start" @select="onPlusSelect">
                  <n-button size="small" secondary class="plusBtn" aria-label="更多">
                    <span class="plus">+</span>
                  </n-button>
                </n-dropdown>
                <n-button size="small" type="primary" class="sendBtn" :loading="chat.sending || uploading" @click="send">Send</n-button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="subpage">
        <ai-code-review-view v-if="tab === 'review'" />
        <ai-history-view v-else-if="tab === 'history'" />
        <ai-usage-view v-else />
      </div>
    </div>
  </div>

  <n-modal v-model:show="docOpen" preset="card" title="外部文档入库" class="docModal">
    <n-form>
      <n-form-item label="标题">
        <n-input v-model:value="docTitle" placeholder="例如：业务规则 / 接口说明 / 会议纪要" />
      </n-form-item>
      <n-form-item label="链接(可选)">
        <n-input v-model:value="docUrl" placeholder="https://..." />
      </n-form-item>
      <n-form-item label="内容">
        <n-input
          v-model:value="docContent"
          type="textarea"
          placeholder="把外部文档的关键内容粘贴到这里（Markdown 或纯文本都可以）"
          :autosize="{ minRows: 8, maxRows: 14 }"
        />
      </n-form-item>
      <n-button type="primary" block :loading="docSaving" @click="saveDoc">保存</n-button>
    </n-form>
  </n-modal>

  <n-modal v-model:show="planPreviewOpen" preset="card" title="执行分解" class="planModal">
    <div class="planPreview">
      <div class="planHero">
        <div class="planIntro">
          <div class="planIntroEyebrow">Execution Plan</div>
          <div class="planIntroTitle">{{ currentPlanGoal || '已整理一版执行方案' }}</div>
          <div class="planIntroMeta">
            <span>{{ currentPlanTasks.length }} 项执行内容</span>
            <span v-if="currentPlanHighCount">· {{ currentPlanHighCount }} 项高优先级</span>
          </div>
        </div>
        <div class="planHeroStats">
          <div class="planHeroStat">
            <div class="planHeroStatLabel">Execution Items</div>
            <div class="planHeroStatValue">{{ currentPlanTasks.length }}</div>
          </div>
          <div class="planHeroStat">
            <div class="planHeroStatLabel">High Priority</div>
            <div class="planHeroStatValue">{{ currentPlanHighCount || 0 }}</div>
          </div>
          <div class="planHeroStat">
            <div class="planHeroStatLabel">Top Focus</div>
            <div class="planHeroStatText">{{ currentPlanTop[0]?.title || '方向已确认' }}</div>
          </div>
        </div>
      </div>

      <div class="planBody">
        <div v-if="currentPlanTop.length" class="planSidebar">
          <div class="planSidebarSection">
            <div class="planSectionTitle">优先推进</div>
            <div class="planTop">
              <div
                v-for="(task, idx) in currentPlanTop"
                :key="`${idx}-${task.title}`"
                class="planTopRow"
                :class="`priority-${String(task.priority || '').trim().toLowerCase()}`"
              >
                <div class="planTopIndex">{{ idx + 1 }}</div>
                <div class="planTopMain">
                  <div class="planTopTitle">{{ task.title }}</div>
                  <div v-if="task.priority" class="planTopMeta">{{ task.priority }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="planMain">
          <div class="planListHead">
            <div class="planSectionTitle">全部执行项</div>
          </div>
          <div class="planList">
            <div
              v-for="(task, idx) in currentPlanTasks"
              :key="`${idx}-${task.title}`"
              class="planItem"
              :class="`priority-${String(task.priority || '').trim().toLowerCase()}`"
            >
              <div class="planItemHead">
                <div class="planItemIndex">{{ idx + 1 }}</div>
                <div class="planItemTitle">{{ task.title }}</div>
                <div v-if="task.priority" class="planItemPriority">{{ task.priority }}</div>
              </div>
              <div v-if="task.description" class="planItemDesc">{{ task.description }}</div>
              <div v-if="Array.isArray(task.checklist) && task.checklist.length" class="planItemBlock planChecklistBlock">
                <div class="planItemLabel">完成标准</div>
                <div class="planItemTags">
                  <span v-for="(item, cIdx) in task.checklist.slice(0, 8)" :key="`${idx}-c-${cIdx}`" class="planTag">{{ item }}</span>
                </div>
              </div>
              <div
                v-if="Array.isArray(task.deliverables) && task.deliverables.length"
                class="planItemBlock planDeliverableBlock"
              >
                <div class="planItemLabel">交付物</div>
                <div class="planItemTags">
                  <span
                    v-for="(item, dIdx) in task.deliverables.slice(0, 6)"
                    :key="`${idx}-d-${dIdx}`"
                    class="planTag"
                  >
                    {{ item.type }} · {{ item.title }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </n-modal>

</template>

<style scoped>
.chat-shell {
  height: calc(100vh - 52px - 18px - 34px);
  min-height: 520px;
  --accent-rgb: 15, 23, 42;
  --accent2-rgb: 15, 23, 42;
  --accent: #0f172a;
  --accent2: #0f172a;
}
.chat-page {
  height: 100%;
  max-width: 1120px;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 10px;
}
.tools {
  display: flex;
  align-items: center;
  gap: 10px;
}
.tabs {
  display: flex;
  gap: 6px;
  padding: 2px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.04);
}
.tab {
  appearance: none;
  border: 0;
  background: transparent;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: rgba(15, 23, 42, 0.72);
  cursor: pointer;
}
.tab.on {
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 1px 0 rgba(15, 23, 42, 0.06);
  color: rgba(15, 23, 42, 0.92);
}
.tools :deep(.n-button.on) {
  border-color: rgba(var(--accent-rgb), 0.14);
  color: rgba(15, 23, 42, 0.92);
}
.frame {
  height: calc(100% - 52px);
  display: grid;
  grid-template-rows: 1fr auto;
  overflow: hidden;
  border-radius: 0;
  border: 0;
  background: transparent;
}
.subpage {
  height: calc(100% - 52px);
  overflow: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}
.subpage::-webkit-scrollbar {
  width: 0;
  height: 0;
}
.viewport {
  padding: 10px 10px 16px;
  overflow: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}
.viewport::-webkit-scrollbar {
  width: 0;
  height: 0;
}
.row {
  display: flex;
  margin: 10px 0;
  gap: 10px;
  align-items: flex-end;
}
.row.user {
  justify-content: flex-end;
}
.row.assistant {
  justify-content: flex-start;
}
.roleMeta {
  margin-bottom: 8px;
  font-size: 11px;
  line-height: 1;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.46);
}
.roleMeta.assistant {
  text-align: left;
  color: rgba(15, 23, 42, 0.34);
}
.msgActions {
  opacity: 0;
  transform: translateY(-2px);
  transition: all 160ms ease;
}
.row.user:hover .msgActions {
  opacity: 1;
  transform: translateY(0);
}
.bubble {
  max-width: min(900px, 92%);
  padding: 0;
  border-radius: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
}
.row.user .bubble {
  max-width: min(420px, 72%);
  padding: 11px 13px;
  border-radius: 16px;
  background: rgba(var(--accent-rgb), 0.07);
  border: 1px solid rgba(var(--accent-rgb), 0.08);
  box-shadow: none;
}
.row.assistant .bubble {
  position: relative;
  border: 0;
  max-width: min(780px, 88%);
  padding: 8px 18px 10px 22px;
  border-radius: 20px;
  background: linear-gradient(90deg, rgba(var(--accent-rgb), 0.045), rgba(var(--accent-rgb), 0.018) 38%, rgba(255, 255, 255, 0));
}
.row.assistant .bubble::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(var(--accent2-rgb), 0.62), rgba(var(--accent-rgb), 0.24));
}
.row.assistant .bubble::after {
  content: '';
  position: absolute;
  left: 8px;
  right: 0;
  top: 10px;
  bottom: 8px;
  background: linear-gradient(90deg, rgba(var(--accent-rgb), 0.045), rgba(var(--accent-rgb), 0.012) 38%, rgba(var(--accent-rgb), 0));
  pointer-events: none;
}
.plain {
  line-height: 1.6;
  font-size: 14px;
  color: rgba(15, 23, 42, 0.92);
  white-space: pre-wrap;
}
.row.user .plain {
  color: rgba(15, 23, 42, 0.92);
  font-weight: 560;
}
.attList {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.attPill {
  display: inline-flex;
  align-items: center;
  max-width: 420px;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  color: rgba(15, 23, 42, 0.86);
  text-decoration: none;
  font-size: 12px;
  line-height: 1;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.06);
}
.attPill:hover {
  background: rgba(255, 255, 255, 0.92);
}
.streaming {
  outline: 2px solid rgba(var(--accent-rgb), 0.08);
  outline-offset: 6px;
}
.stream-cursor {
  display: inline-block;
  width: 8px;
  height: 1.15em;
  margin-left: 2px;
  vertical-align: -2px;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(var(--accent2-rgb), 0.86), rgba(var(--accent-rgb), 0.86));
  opacity: 0.9;
}
.typing {
  animation: blink 1s ease-in-out infinite;
}
.composer {
  border-top: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(10px);
  position: sticky;
  bottom: 0;
}
.planInline {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.07);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.8), rgba(255, 255, 255, 0.7)),
    linear-gradient(90deg, rgba(var(--accent-rgb), 0.04), rgba(255, 255, 255, 0));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
}
.planInlineBar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.planInlineMeta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}
.planInlineTag,
.planInlineText {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 650;
}
.planInlineTag {
  background: rgba(var(--accent-rgb), 0.08);
  color: rgba(15, 23, 42, 0.84);
}
.planInlineText {
  background: rgba(15, 23, 42, 0.045);
  color: rgba(15, 23, 42, 0.62);
}
.planInlineLink {
  appearance: none;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.92);
  padding: 7px 10px;
  border-radius: 999px;
  color: rgba(15, 23, 42, 0.82);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.01em;
  cursor: pointer;
  white-space: nowrap;
}
.planInlineLink:hover {
  background: rgba(15, 23, 42, 0.04);
}
.planInlinePreview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.planInlinePreviewItem {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  padding: 7px 10px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(15, 23, 42, 0.05);
  color: rgba(15, 23, 42, 0.68);
  font-size: 12px;
  line-height: 1.35;
}
.thinkingCard {
  margin: 4px 0 14px;
  padding: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
}
.thinkingHead {
  margin-bottom: 8px;
}
.thinkingTitle {
  display: inline-flex;
  align-items: center;
  font-size: 13px;
  line-height: 1.2;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.72);
  letter-spacing: 0.01em;
}
.thinkingBody {
  margin-left: 7px;
  padding-left: 14px;
  border-left: 2px solid rgba(15, 23, 42, 0.08);
}
.thinkingSteps {
  display: grid;
  gap: 8px;
}
.thinkingStep {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
}
.thinkingStepState {
  font-size: 11px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.4);
}
.thinkingStepLabel {
  font-size: 12px;
  line-height: 1.45;
  color: rgba(15, 23, 42, 0.74);
}
.thinkingError {
  font-size: 12px;
  line-height: 1.55;
  color: rgba(15, 23, 42, 0.62);
}
.thinkingCard.is-error .thinkingBody {
  border-left-color: rgba(15, 23, 42, 0.12);
}
.thinkingCard.is-thinking .thinkingTitle,
.thinkingCard.is-done .thinkingTitle,
.thinkingCard.is-error .thinkingTitle {
  color: rgba(15, 23, 42, 0.72);
}
.thinkingCard.is-error .thinkingError {
  color: rgba(15, 23, 42, 0.62);
}
.thinkingCard.is-thinking .thinkingStepState,
.thinkingCard.is-done .thinkingStepState,
.thinkingCard.is-error .thinkingStepState {
  color: rgba(15, 23, 42, 0.4);
}
.thinkingCard.is-thinking .thinkingStepLabel,
.thinkingCard.is-done .thinkingStepLabel,
.thinkingCard.is-error .thinkingStepLabel {
  color: rgba(15, 23, 42, 0.74);
}
.thinkingCard.is-thinking .thinkingBody,
.thinkingCard.is-done .thinkingBody,
.thinkingCard.is-error .thinkingBody {
  background: transparent;
}
.thinkingError + .thinkingSteps,
.thinkingSteps + .thinkingError {
  margin-top: 8px;
}
.thinkingError {
  font-size: 12px;
  line-height: 1.55;
}
.composer-inner {
  padding: 10px 12px 12px;
}
.fileInput {
  display: none;
}
.composerBox {
  position: relative;
  border-radius: 18px;
  background: rgba(15, 23, 42, 0.04);
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.06);
  padding: 8px 8px 8px;
}
.pendingFiles {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
}
.pendingFile {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: min(420px, 100%);
  padding: 8px 10px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.06);
}
.thumb {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  overflow: hidden;
  flex: 0 0 auto;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.fileMark {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  font-size: 12px;
  font-weight: 760;
  color: rgba(15, 23, 42, 0.82);
  background: linear-gradient(180deg, rgba(var(--accent2-rgb), 0.12), rgba(var(--accent-rgb), 0.10));
}
.fileName {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 12px;
  color: rgba(15, 23, 42, 0.78);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fileRemove {
  border: 0;
  background: transparent;
  padding: 0;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  cursor: pointer;
  color: rgba(15, 23, 42, 0.56);
  line-height: 1;
  font-size: 18px;
}
.fileRemove:hover {
  background: rgba(15, 23, 42, 0.06);
  color: rgba(15, 23, 42, 0.78);
}
.composerActions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
}
.plusBtn {
  width: 30px;
  height: 26px;
  padding: 0;
  border-radius: 9px;
}
.plus {
  font-size: 18px;
  line-height: 1;
  font-weight: 860;
  color: rgba(15, 23, 42, 0.82);
}
.sendBtn {
  border-radius: 12px;
}

.composerBox :deep(.n-input) {
  background: transparent;
}
.composerBox :deep(.n-input__border),
.composerBox :deep(.n-input__state-border) {
  display: none;
}
.composerBox :deep(.n-input__textarea-el) {
  padding: 8px 10px 6px 10px;
  font-size: 14px;
  line-height: 1.55;
}
.row.assistant .bubble :deep(.md) {
  font-size: 15px;
  line-height: 1.75;
  color: rgba(15, 23, 42, 0.92);
  max-width: 760px;
}
.row.assistant .bubble :deep(.md h1),
.row.assistant .bubble :deep(.md h2),
.row.assistant .bubble :deep(.md h3) {
  color: rgba(15, 23, 42, 0.94);
  letter-spacing: -0.25px;
}
.row.assistant .bubble :deep(.md h1) {
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.10);
}
.row.assistant .bubble :deep(.md h2) {
  position: relative;
  padding-left: 12px;
}
.row.assistant .bubble :deep(.md h3) {
  margin-top: 16px;
  font-size: 13px;
  letter-spacing: 0.02em;
}
.row.assistant .bubble :deep(.md hr) {
  margin: 16px 0 14px;
  border: 0;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}
.row.assistant .bubble :deep(.md h2)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 4px;
  bottom: 4px;
  width: 4px;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(var(--accent2-rgb), 0.86), rgba(var(--accent-rgb), 0.86));
}
.row.assistant .bubble :deep(.md strong) {
  color: rgba(15, 23, 42, 0.92);
  font-weight: 760;
}
.row.assistant .bubble :deep(.md em) {
  color: rgba(15, 23, 42, 0.78);
}
.row.assistant .bubble :deep(.md ul li::marker),
.row.assistant .bubble :deep(.md ol li::marker) {
  color: rgba(15, 23, 42, 0.62);
}
.row.assistant .bubble :deep(.md blockquote) {
  margin: 10px 0;
  padding: 10px 12px;
  border-left: 3px solid rgba(var(--accent-rgb), 0.18);
  border-radius: 0 12px 12px 0;
  background: rgba(var(--accent-rgb), 0.04);
  color: rgba(15, 23, 42, 0.80);
}
.row.assistant .bubble :deep(.md p code),
.row.assistant .bubble :deep(.md li code) {
  color: rgba(15, 23, 42, 0.92);
  background: rgba(15, 23, 42, 0.06);
  border-color: rgba(15, 23, 42, 0.10);
}
.planModal {
  width: min(1100px, calc(100vw - 28px));
  --accent-rgb: 15, 23, 42;
  --accent2-rgb: 15, 23, 42;
  --accent: #0f172a;
  --accent2: #0f172a;
}
.planModal :deep(.n-card) {
  border-radius: 24px;
  box-shadow: 0 32px 90px rgba(15, 23, 42, 0.18);
}
.planModal :deep(.n-card-header) {
  padding: 18px 22px 0;
}
.planModal :deep(.n-card__content) {
  padding: 14px 22px 22px;
}
.planPreview {
  display: grid;
  gap: 22px;
}
.planHero {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(280px, 0.8fr);
  gap: 18px;
  padding: 18px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 20px;
  background:
    linear-gradient(135deg, rgba(var(--accent-rgb), 0.08), rgba(15, 23, 42, 0.04) 44%, rgba(255, 255, 255, 0.92));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
}
.planIntro {
  min-width: 0;
}
.planIntroEyebrow {
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.42);
}
.planIntroTitle {
  margin-top: 6px;
  font-size: 20px;
  line-height: 1.35;
  font-weight: 760;
  color: rgba(15, 23, 42, 0.94);
}
.planIntroMeta {
  margin-top: 4px;
  font-size: 13px;
  color: rgba(15, 23, 42, 0.6);
}
.planHeroStats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.planHeroStat {
  min-width: 0;
  padding: 14px 14px 12px;
  border-radius: 16px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(255, 255, 255, 0.74));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
}
.planHeroStat:nth-child(1) {
  border-color: rgba(15, 23, 42, 0.12);
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.05), rgba(255, 255, 255, 0.8));
}
.planHeroStat:nth-child(2) {
  border-color: rgba(15, 23, 42, 0.12);
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.05), rgba(255, 255, 255, 0.8));
}
.planHeroStat:nth-child(3) {
  border-color: rgba(15, 23, 42, 0.12);
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.05), rgba(255, 255, 255, 0.8));
}
.planHeroStatLabel {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.42);
}
.planHeroStatValue {
  margin-top: 10px;
  font-size: 24px;
  line-height: 1;
  font-weight: 760;
  color: rgba(15, 23, 42, 0.94);
}
.planHeroStatText {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.5;
  font-weight: 650;
  color: rgba(15, 23, 42, 0.82);
}
.planBody {
  display: grid;
  grid-template-columns: minmax(220px, 280px) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}
.planSidebar {
  position: sticky;
  top: 0;
}
.planSidebarSection {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.04), rgba(15, 23, 42, 0.02));
}
.planSectionTitle {
  margin-bottom: 12px;
  font-size: 12px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.44);
}
.planTop {
  display: grid;
  gap: 10px;
}
.planTopRow,
.planItemHead {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.planTopRow + .planTopRow {
  margin-top: 10px;
}
.planTopRow {
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.78);
}
.planTopIndex,
.planItemIndex {
  flex: 0 0 20px;
  font-size: 12px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.46);
}
.planTopMain,
.planItemTitle {
  min-width: 0;
}
.planTopTitle,
.planItemTitle {
  font-size: 14px;
  font-weight: 650;
  color: rgba(15, 23, 42, 0.92);
}
.planTopMeta,
.planItemPriority {
  flex: 0 0 auto;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.5);
}
.planList {
  display: grid;
  gap: 14px;
  max-height: min(58vh, 720px);
  overflow: auto;
  padding-right: 6px;
}
.planMain {
  min-width: 0;
}
.planListHead {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.planList::-webkit-scrollbar {
  width: 8px;
}
.planList::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.12);
}
.planList::-webkit-scrollbar-track {
  background: transparent;
}
.planItem {
  padding: 16px 18px 18px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 18px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(255, 255, 255, 0.76)),
    linear-gradient(90deg, rgba(var(--accent-rgb), 0.025), rgba(255, 255, 255, 0));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72), 0 14px 30px rgba(15, 23, 42, 0.04);
}
.planTopRow.priority-high,
.planItem.priority-high {
  border-color: rgba(15, 23, 42, 0.16);
  background:
    linear-gradient(180deg, rgba(15, 23, 42, 0.06), rgba(255, 255, 255, 0.82)),
    linear-gradient(90deg, rgba(15, 23, 42, 0.03), rgba(255, 255, 255, 0));
}
.planTopRow.priority-medium,
.planItem.priority-medium {
  border-color: rgba(15, 23, 42, 0.14);
  background:
    linear-gradient(180deg, rgba(15, 23, 42, 0.05), rgba(255, 255, 255, 0.82)),
    linear-gradient(90deg, rgba(15, 23, 42, 0.03), rgba(255, 255, 255, 0));
}
.planTopRow.priority-low,
.planItem.priority-low {
  border-color: rgba(15, 23, 42, 0.12);
  background:
    linear-gradient(180deg, rgba(15, 23, 42, 0.04), rgba(255, 255, 255, 0.82)),
    linear-gradient(90deg, rgba(15, 23, 42, 0.02), rgba(255, 255, 255, 0));
}
.planItemHead {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr) auto;
  align-items: flex-start;
  gap: 10px;
}
.planItemDesc {
  margin: 6px 0 0 30px;
  font-size: 13px;
  line-height: 1.65;
  color: rgba(15, 23, 42, 0.72);
}
.planItemBlock {
  margin: 10px 0 0 30px;
}
.planItemLabel {
  margin-bottom: 6px;
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: rgba(15, 23, 42, 0.42);
}
.planItemTags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.planChecklistBlock .planTag {
  border-color: rgba(15, 23, 42, 0.12);
  background: rgba(15, 23, 42, 0.05);
}
.planDeliverableBlock .planTag {
  border-color: rgba(15, 23, 42, 0.12);
  background: rgba(15, 23, 42, 0.05);
}
.planTag {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.84);
  color: rgba(15, 23, 42, 0.74);
  font-size: 12px;
}

@media (max-width: 960px) {
  .planHero,
  .planBody {
    grid-template-columns: 1fr;
  }

  .planHeroStats {
    grid-template-columns: 1fr;
  }

  .planInlineBar,
  .planInlineLink {
    width: 100%;
  }

  .planInlineBar {
    align-items: flex-start;
    flex-direction: column;
  }
}

.row.assistant .bubble :deep(pre.hljs) {
  background: rgba(15, 23, 42, 0.06);
  border: 1px solid rgba(15, 23, 42, 0.10);
  border-radius: 14px;
}
@keyframes blink {
  0%,
  100% {
    opacity: 0.2;
  }
  50% {
    opacity: 1;
  }
}

:global(.docModal) {
  width: min(560px, calc(100vw - 28px));
}
</style>
