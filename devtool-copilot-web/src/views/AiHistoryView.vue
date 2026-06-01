<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NCheckbox, NModal, NSelect, NSpin, useDialog, useMessage } from 'naive-ui'
import { aiApi, type AiChatHistoryItem } from '../api/ai'
import { useProjectStore } from '../stores/project'
import MarkdownView from '../components/MarkdownView.vue'

const message = useMessage()
const dialog = useDialog()
const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()

const projectId = ref<number | null>(null)
const loading = ref(false)
const list = ref<AiChatHistoryItem[]>([])
const selected = ref<Set<number>>(new Set())
const detail = ref<AiChatHistoryItem | null>(null)
const showDetail = computed(() => !!detail.value)
const HISTORY_STATE_KEY = 'dtc_ai_history_state_v1'
const pendingDetailId = ref<number | null>(null)
let persistTimer: any = null

const projectOptions = computed(() => projectStore.visibleProjects.map((p) => ({ label: p.name, value: p.id })))

const projectName = computed(() => {
  const map = new Map<number, string>()
  for (const p of projectStore.visibleProjects) map.set(p.id, p.name)
  return map
})

function syncQuery(id: number | null) {
  const q = { ...route.query }
  if (id == null) delete q.projectId
  else q.projectId = String(id)
  router.replace({ query: q })
}

function fmt(ts?: number) {
  if (!ts) return ''
  return new Date(ts).toLocaleString()
}

function titleOf(h: AiChatHistoryItem) {
  const p = (h.prompt || '').trim()
  const line = p.split('\n')[0] || ''
  const t = line.trim() || '（空）'
  return t.length > 72 ? t.slice(0, 72) + '…' : t
}

function snippetOf(h: AiChatHistoryItem) {
  const s = (h.response || '').trim().replace(/\s+/g, ' ')
  if (!s) return '（无回复）'
  return s.length > 120 ? s.slice(0, 120) + '…' : s
}

const selectedCount = computed(() => selected.value.size)

function isSelected(id: number) {
  return selected.value.has(id)
}

function setSelected(id: number, v: boolean) {
  const next = new Set(selected.value)
  if (v) next.add(id)
  else next.delete(id)
  selected.value = next
}

function clearSelected() {
  selected.value = new Set()
}

function openDetail(h: AiChatHistoryItem) {
  detail.value = h
  pendingDetailId.value = h.id
  schedulePersist()
}

function closeDetail() {
  detail.value = null
  pendingDetailId.value = null
  schedulePersist()
}

async function load() {
  loading.value = true
  try {
    list.value = await aiApi.historyList({ projectId: projectId.value, limit: 100 })
    clearSelected()
    if (pendingDetailId.value) {
      const hit = list.value.find((x) => x.id === pendingDetailId.value) || null
      if (hit) detail.value = hit
      else pendingDetailId.value = null
    }
  } catch (e: any) {
    message.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function persist() {
  try {
    localStorage.setItem(
      HISTORY_STATE_KEY,
      JSON.stringify({ projectId: projectId.value ?? null, detailId: pendingDetailId.value ?? null, ts: Date.now() })
    )
  } catch {
  }
}

function schedulePersist() {
  if (persistTimer) clearTimeout(persistTimer)
  persistTimer = setTimeout(() => persist(), 240)
}

async function deleteSelected() {
  const ids = Array.from(selected.value)
  if (!ids.length) return
  await deleteByIds(ids)
}

async function deleteByIds(ids: number[]) {
  if (!ids.length) return
  return new Promise<void>((resolve) => {
    dialog.warning({
      title: '删除历史记录',
      content: ids.length === 1 ? '确认删除这条记录？' : `确认删除选中的 ${ids.length} 条记录？`,
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          await aiApi.historyDelete(ids)
          const set = new Set(ids)
          list.value = list.value.filter((h) => !set.has(h.id))
          if (detail.value && set.has(detail.value.id)) {
            closeDetail()
          }
          selected.value = new Set(Array.from(selected.value).filter((id) => !set.has(id)))
          message.success('已删除')
        } catch (e: any) {
          message.error(e?.message || '删除失败')
        } finally {
          resolve()
        }
      },
      onNegativeClick: () => resolve()
    })
  })
}

async function clearAll() {
  dialog.warning({
    title: '清空历史记录',
    content: projectId.value
      ? '确认清空当前项目下的全部历史记录？'
      : '确认清空全部历史记录？',
    positiveText: '清空',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await aiApi.historyClear(projectId.value)
        list.value = []
        closeDetail()
        clearSelected()
        message.success('已清空')
      } catch (e: any) {
        message.error(e?.message || '清空失败')
      }
    }
  })
}

onMounted(async () => {
  if (!projectStore.projects.length) {
    await projectStore.load()
  }
  try {
    const raw = localStorage.getItem(HISTORY_STATE_KEY)
    const obj = raw ? (JSON.parse(raw) as any) : null
    if (!route.query.projectId && obj && obj.projectId && Number.isFinite(Number(obj.projectId))) {
      projectId.value = Number(obj.projectId)
    }
    if (obj && obj.detailId && Number.isFinite(Number(obj.detailId))) {
      pendingDetailId.value = Number(obj.detailId)
    }
  } catch {
  }
  const qid = route.query.projectId ? Number(route.query.projectId) : null
  if (qid && Number.isFinite(qid)) {
    projectId.value = qid
  } else if (projectId.value == null && projectStore.visibleProjects[0]) {
    projectId.value = projectStore.visibleProjects[0].id
    syncQuery(projectId.value)
  }
  await load()
  schedulePersist()
})

watch(projectId, async (id) => {
  syncQuery(id)
  schedulePersist()
  await load()
})
</script>

<template>
  <div class="page history">
    <div class="toolsbar">
      <n-select
        v-model:value="projectId"
        size="small"
        :options="projectOptions"
        placeholder="All projects"
        clearable
        style="width: 220px"
      />
      <div class="spacer" />
      <n-button size="small" :loading="loading" @click="load">Refresh</n-button>
      <n-button size="small" type="warning" :disabled="selectedCount === 0" @click="deleteSelected">
        Delete ({{ selectedCount }})
      </n-button>
      <n-button size="small" type="error" :disabled="!list.length" @click="clearAll">Clear</n-button>
    </div>

    <section class="panel block">
      <n-spin :show="loading">
        <div v-if="!list.length" class="muted empty">No history yet.</div>
        <div v-else class="rows">
          <button
            v-for="h in list"
            :key="h.id"
            class="item hover-row"
            @click="openDetail(h)"
          >
            <div class="left">
              <n-checkbox
                :checked="isSelected(h.id)"
                size="small"
                @click.stop
                @update:checked="(v) => setSelected(h.id, v)"
              />
            </div>
            <div class="main">
              <div class="title">{{ titleOf(h) }}</div>
              <div class="muted sub">{{ snippetOf(h) }}</div>
              <div class="meta">
                <span class="time mono">{{ fmt(h.createdAt) }}</span>
                <span class="dot">·</span>
                <span class="proj">
                  {{ h.projectId ? projectName.get(h.projectId) || `Project #${h.projectId}` : 'No project' }}
                </span>
              </div>
            </div>
            <div class="right">
              <span class="pill">Open</span>
            </div>
          </button>
        </div>
      </n-spin>
    </section>

    <n-modal
      :show="showDetail"
      preset="card"
      class="detail"
      :bordered="false"
      @update:show="(v) => (v ? null : closeDetail())"
    >
      <template #header>
        <div class="dhead">
          <div class="dtitle">{{ detail ? titleOf(detail) : '' }}</div>
          <div class="dmeta muted">
            <span class="mono">{{ detail ? fmt(detail.createdAt) : '' }}</span>
            <span class="dot">·</span>
            <span>
              {{
                detail && detail.projectId
                  ? projectName.get(detail.projectId) || `Project #${detail.projectId}`
                  : 'No project'
              }}
            </span>
          </div>
        </div>
      </template>
      <template #header-extra>
        <n-button
          size="small"
          type="error"
          :disabled="!detail"
          @click="detail ? deleteByIds([detail.id]) : null"
        >
          Delete
        </n-button>
      </template>

      <div v-if="detail" class="dbody">
        <div class="q">
          <div class="qlabel muted">Prompt</div>
          <div class="qtext">{{ detail.prompt }}</div>
        </div>
        <div class="a">
          <div class="alabel muted">Response</div>
          <div class="atext">
            <markdown-view :content="detail.response" />
          </div>
        </div>
      </div>
    </n-modal>
  </div>
</template>

<style scoped>
.history {
  max-width: 980px;
}
.toolsbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 6px 0 14px;
  flex-wrap: wrap;
}
.spacer {
  flex: 1;
}
.empty {
  padding: 14px 4px;
  line-height: 1.6;
  font-size: 12px;
}
.rows {
  display: grid;
  gap: 8px;
}
.item {
  width: 100%;
  text-align: left;
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.03), rgba(255, 255, 255, 0.015)),
    rgba(255, 255, 255, 0.02);
  padding: 12px 12px;
  display: grid;
  grid-template-columns: 30px 1fr auto;
  gap: 10px;
  align-items: start;
}
.left {
  padding-top: 2px;
}
.main {
  min-width: 0;
}
.title {
  font-weight: 720;
  letter-spacing: -0.2px;
  line-height: 1.35;
  color: rgba(250, 250, 250, 0.96);
}
.sub {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.55;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.meta {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: rgba(250, 250, 250, 0.62);
}
.time {
  opacity: 0.95;
}
.dot {
  opacity: 0.4;
}
.proj {
  opacity: 0.9;
}
.right {
  padding-top: 2px;
}
.pill {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.03);
  color: rgba(250, 250, 250, 0.72);
}
.detail {
  width: min(920px, calc(100vw - 32px));
}
.dhead {
  display: grid;
  gap: 6px;
}
.dtitle {
  font-weight: 760;
  letter-spacing: -0.2px;
}
.dmeta {
  font-size: 12px;
  display: flex;
  gap: 8px;
  align-items: center;
}
.dbody {
  display: grid;
  gap: 14px;
}
.q,
.a {
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(255, 255, 255, 0.02);
  padding: 12px 12px;
}
.qlabel,
.alabel {
  font-size: 12px;
}
.qtext {
  margin-top: 8px;
  white-space: pre-wrap;
  line-height: 1.65;
}
.atext {
  margin-top: 8px;
  border-radius: 12px;
}
@media (max-width: 720px) {
  .tools {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
