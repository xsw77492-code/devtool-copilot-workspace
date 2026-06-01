<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NDropdown, NForm, NFormItem, NInput, NModal, NSelect, useMessage } from 'naive-ui'
import { useChatStore } from '../stores/chat'
import MarkdownView from '../components/MarkdownView.vue'
import { useProjectStore } from '../stores/project'
import { kbApi } from '../api/kb'
import { docgenApi, type DocGenSaveTo } from '../api/docgen'
import AiCodeReviewView from './AiCodeReviewView.vue'
import AiHistoryView from './AiHistoryView.vue'
import { uploadAiFile } from '../api/aiFile'

const chat = useChatStore()
const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const message = useMessage()

const projectId = ref<number | null>(null)
const input = ref('')
const viewport = ref<HTMLDivElement | null>(null)
const streamCursor = computed(() => (chat.sending ? 'typing' : ''))

type AiTab = 'chat' | 'review' | 'history'
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
    if (!route.query.tab && obj && obj.tab && (obj.tab === 'review' || obj.tab === 'history')) {
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
        <div>
          <h1 class="h1">AI</h1>
        </div>
        <div class="tools">
          <div class="tabs">
            <button class="tab" :class="{ on: tab === 'chat' }" @click="syncTab('chat')">对话</button>
            <button class="tab" :class="{ on: tab === 'review' }" @click="syncTab('review')">审查</button>
            <button class="tab" :class="{ on: tab === 'history' }" @click="syncTab('history')">历史</button>
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
              <markdown-view v-if="m.role === 'assistant'" :content="m.content" :allow-details="true" />
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
        <ai-history-view v-else />
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

</template>

<style scoped>
.chat-shell {
  height: calc(100vh - 52px - 18px - 34px);
  min-height: 520px;
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
  border-color: rgba(20, 184, 166, 0.25);
  color: rgba(13, 148, 136, 0.95);
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
  max-width: min(920px, 96%);
  padding: 0;
  border-radius: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
}
.row.user .bubble {
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(20, 184, 166, 0.10);
  border-color: rgba(20, 184, 166, 0.18);
  box-shadow: none;
}
.row.assistant .bubble {
  position: relative;
  border: 0;
}
.plain {
  line-height: 1.75;
  font-size: 15px;
  color: rgba(15, 23, 42, 0.92);
  white-space: pre-wrap;
}
.row.user .plain {
  color: rgba(15, 23, 42, 0.92);
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
  color: rgba(13, 148, 136, 0.95);
  text-decoration: none;
  font-size: 12px;
  line-height: 1;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.06);
}
.attPill:hover {
  background: rgba(255, 255, 255, 0.92);
}
.streaming {
  outline: 2px solid rgba(20, 184, 166, 0.12);
  outline-offset: 8px;
}
.stream-cursor {
  display: inline-block;
  width: 8px;
  height: 1.15em;
  margin-left: 2px;
  vertical-align: -2px;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(6, 182, 212, 0.92), rgba(20, 184, 166, 0.92));
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
  background: linear-gradient(180deg, rgba(6, 182, 212, 0.18), rgba(20, 184, 166, 0.16));
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
.row.assistant .bubble :deep(.md h2)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 4px;
  bottom: 4px;
  width: 4px;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(6, 182, 212, 0.92), rgba(20, 184, 166, 0.92));
}
.row.assistant .bubble :deep(.md strong) {
  color: rgba(13, 148, 136, 0.95);
  font-weight: 760;
}
.row.assistant .bubble :deep(.md em) {
  color: rgba(6, 182, 212, 0.86);
}
.row.assistant .bubble :deep(.md ul li::marker),
.row.assistant .bubble :deep(.md ol li::marker) {
  color: rgba(20, 184, 166, 0.75);
}
.row.assistant .bubble :deep(.md blockquote) {
  margin: 10px 0;
  padding: 10px 12px;
  border-left: 3px solid rgba(20, 184, 166, 0.35);
  border-radius: 0 12px 12px 0;
  background: rgba(20, 184, 166, 0.06);
  color: rgba(15, 23, 42, 0.80);
}
.row.assistant .bubble :deep(.md p code),
.row.assistant .bubble :deep(.md li code) {
  color: rgba(13, 148, 136, 0.95);
  background: rgba(15, 23, 42, 0.06);
  border-color: rgba(15, 23, 42, 0.10);
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
