<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NDatePicker, NInput, NSelect, NSpin, useDialog, useMessage } from 'naive-ui'
import { projectAuditApi, type ProjectAuditItem } from '../api/projectAudit'
import { useRealtimeStore } from '../stores/realtime'
import PresenceBar from '../components/PresenceBar.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const rt = useRealtimeStore()

const projectId = computed(() => Number(route.params.id))

const loading = ref(false)
const loadingMore = ref(false)
const exporting = ref(false)
const clearing = ref(false)
const deletingId = ref<number | null>(null)

const list = ref<ProjectAuditItem[]>([])
const cursor = ref<number | null>(null)
const hasMore = ref(true)

const action = ref<string>('')
const q = ref('')
const range = ref<[number, number] | null>(null)

const limit = 100

const actionOptions = [
  { label: '全部动作', value: '' },
  { label: '创建任务', value: 'TASK_CREATED' },
  { label: '更新任务', value: 'TASK_UPDATED' },
  { label: '看板移动', value: 'TASK_MOVED' },
  { label: '状态变更', value: 'TASK_STATUS_CHANGED' },
  { label: '发表评论', value: 'TASK_COMMENT_CREATED' },
  { label: '邀请成员', value: 'MEMBER_INVITED' },
  { label: '取消邀请', value: 'MEMBER_INVITE_CANCELED' },
  { label: '更新邀请链接', value: 'MEMBER_INVITE_REISSUED' },
  { label: '移除成员', value: 'MEMBER_REMOVED' },
  { label: '变更成员角色', value: 'MEMBER_ROLE_CHANGED' },
  { label: '禁用/启用成员', value: 'MEMBER_DISABLED' },
  { label: '导出成员', value: 'MEMBERS_EXPORT_CSV' },
  { label: '创建项目', value: 'PROJECT_CREATED' }
]

function fmtTime(v?: string | null) {
  if (!v) return ''
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  return d.toLocaleString()
}

function titleOf(a: ProjectAuditItem) {
  const actor = a.actorUsername || `User#${a.actorUserId}`
  const s = a.summary ? String(a.summary) : ''
  const t = String(a.action || '')
  if (t === 'TASK_CREATED') return `${actor} 创建了任务 · ${s}`
  if (t === 'TASK_UPDATED') return `${actor} 更新了任务 · ${s}`
  if (t === 'TASK_MOVED') return `${actor} 在看板移动了任务 · ${s}`
  if (t === 'TASK_STATUS_CHANGED') return `${actor} 变更了任务状态 · ${s}`
  if (t === 'TASK_COMMENT_CREATED') return `${actor} 评论了任务 · ${s}`
  if (t === 'MEMBER_INVITED') return `${actor} 邀请了成员`
  if (t === 'MEMBER_INVITE_CANCELED') return `${actor} 取消了邀请`
  if (t === 'MEMBER_INVITE_REISSUED') return `${actor} 更新了邀请链接`
  if (t === 'MEMBER_REMOVED') return `${actor} 移除了成员`
  if (t === 'MEMBER_ROLE_CHANGED') return `${actor} 调整了成员角色`
  if (t === 'MEMBER_DISABLED') return `${actor} 禁用了成员`
  if (t === 'MEMBER_ENABLED') return `${actor} 启用了成员`
  if (t === 'MEMBER_OWNER_TRANSFERRED') return `${actor} 转让了所有权`
  if (t === 'MEMBERS_EXPORT_CSV') return `${actor} 导出了成员列表`
  if (t === 'PROJECT_CREATED') return `${actor} 创建了项目 · ${s}`
  return `${actor} · ${t}${s ? ` · ${s}` : ''}`
}

function detailPreview(a: ProjectAuditItem) {
  const raw = String(a.detail || '').trim()
  if (!raw) return ''
  if (raw.length <= 160) return raw
  return raw.slice(0, 160) + '…'
}

function downloadText(filename: string, content: string) {
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

function buildQuery() {
  const fromTime = range.value ? range.value[0] : undefined
  const toTime = range.value ? range.value[1] : undefined
  return {
    action: action.value || undefined,
    q: q.value.trim() || undefined,
    fromTime,
    toTime
  }
}

async function load(reset?: boolean) {
  if (reset) {
    cursor.value = null
    list.value = []
    hasMore.value = true
  }
  if (!hasMore.value) return

  const isFirst = !list.value.length
  if (isFirst) loading.value = true
  else loadingMore.value = true
  try {
    const res = await projectAuditApi.list(projectId.value, {
      cursor: cursor.value ?? undefined,
      limit,
      ...buildQuery()
    })
    const newList = res?.list || []
    list.value = reset ? newList : list.value.concat(newList)
    cursor.value = (res?.nextCursor as any) ?? null
    hasMore.value = newList.length >= limit
  } catch (e: any) {
    const msg = String(e?.message || '')
    if (msg.includes('权限不足')) {
      message.error('仅项目 OWNER 可查看审计日志')
      router.replace({ name: 'project-detail', params: { id: projectId.value } })
      return
    }
    if (msg.includes('成员已被禁用')) {
      message.error('你已被该项目禁用')
      router.replace({ name: 'workspace' })
      return
    }
    message.error(e?.message || '加载失败')
    hasMore.value = false
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function exportCsv() {
  exporting.value = true
  try {
    const res = await projectAuditApi.exportCsv(projectId.value, buildQuery())
    downloadText(res.filename || `project-audit-${projectId.value}.csv`, res.content || '')
    message.success('已导出')
  } catch (e: any) {
    message.error(e?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

function removeOne(a: ProjectAuditItem) {
  if (!a?.id) return
  dialog.warning({
    title: '删除审计记录',
    content: '删除后不可恢复。',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        deletingId.value = a.id
        await projectAuditApi.deleteOne(projectId.value, a.id)
        list.value = list.value.filter((x) => x.id !== a.id)
        message.success('已删除')
      } catch (e: any) {
        message.error(e?.message || '删除失败')
      } finally {
        deletingId.value = null
      }
    }
  })
}

function clearAll() {
  dialog.warning({
    title: '清空审计日志',
    content: '将删除当前筛选条件下的全部审计记录，删除后不可恢复。',
    positiveText: '清空',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        clearing.value = true
        await projectAuditApi.clear(projectId.value, buildQuery())
        await load(true)
        message.success('已清空')
      } catch (e: any) {
        message.error(e?.message || '清空失败')
      } finally {
        clearing.value = false
      }
    }
  })
}

onMounted(() => {
  rt.subscribe(projectId.value, 'AUDIT', projectId.value)
  load(true)
})

watch(projectId, (id) => {
  const pid = Number(id)
  rt.subscribe(pid, 'AUDIT', pid)
  load(true)
})

onUnmounted(() => {
  rt.subscribe(null)
})

watch(
  () => rt.seq,
  () => {
    const ev = rt.lastEvent
    const pid = Number(ev?.projectId || 0)
    if (!pid || pid !== projectId.value) return
    const t = String(ev?.type || '')
    if (t.startsWith('TASK_') || t.startsWith('MEMBER_') || t.startsWith('AI_')) {
      load(true)
    }
  }
)
</script>

<template>
  <div class="page lightPage">
    <div class="head">
      <div class="left">
        <h1 class="h1">审计日志</h1>
      </div>
      <div class="right">
        <presence-bar :project-id="projectId" />
        <n-button tertiary @click="router.push({ name: 'project-detail', params: { id: projectId } })">返回项目</n-button>
        <button class="btnTealSm" :disabled="exporting" @click="exportCsv">
          <span>导出 CSV</span>
          <span v-if="exporting" class="spinSm" />
        </button>
        <button class="btnGhostSm" :disabled="loading || loadingMore" @click="load(true)">刷新</button>
        <button class="btnGhostSm dangerSm" :disabled="clearing" @click="clearAll">
          <span>清空</span>
          <span v-if="clearing" class="spinSm" />
        </button>
      </div>
    </div>

    <section class="panel lightPanel">
      <div class="topbar">
        <div class="muted">已加载 {{ list.length }} 条</div>
        <div class="filters">
          <n-select v-model:value="action" :options="actionOptions" size="small" class="fSel" />
          <n-input v-model:value="q" size="small" placeholder="关键词（summary/detail）" class="fInput" @keyup.enter="load(true)" />
          <n-date-picker v-model:value="range" type="datetimerange" clearable size="small" class="fDate" />
          <button class="btnTealSm" @click="load(true)">筛选</button>
        </div>
      </div>

      <n-spin :show="loading">
        <div v-if="!list.length && !loading" class="emptyState">
          <div class="emptyTitle">暂无审计记录</div>
        </div>

        <div v-else class="timeline">
          <div v-for="a in list" :key="a.id" class="item">
            <div class="rail" aria-hidden="true">
              <div class="dot" />
            </div>
            <div class="content">
              <div class="t">{{ titleOf(a) }}</div>
              <div class="s muted">
                <span>{{ fmtTime(a.createTime) }}</span>
                <span v-if="a.ip"> · {{ a.ip }}</span>
                <span v-if="a.action"> · {{ a.action }}</span>
              </div>
              <div v-if="detailPreview(a)" class="d muted">{{ detailPreview(a) }}</div>
            </div>
            <div class="actions">
              <n-button size="tiny" quaternary :loading="deletingId === a.id" @click="removeOne(a)">删除</n-button>
            </div>
          </div>
        </div>

        <div v-if="list.length && hasMore" class="more">
          <n-button :loading="loadingMore" @click="load(false)">加载更多</n-button>
        </div>
      </n-spin>
    </section>
  </div>
</template>

<style scoped>
.lightPage {
  background: #ffffff;
  color: #0f172a;
}

.lightPanel {
  background:
    radial-gradient(900px 420px at 12% 0%, rgba(6, 182, 212, 0.10), transparent 58%),
    #ffffff;
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 16px 45px rgba(2, 6, 23, 0.06);
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.fSel {
  width: 180px;
}

.fInput {
  width: 220px;
}

.fDate {
  width: 320px;
}

.btnTealSm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 32px;
  padding: 0 12px;
  border-radius: 12px;
  background: rgba(20, 184, 166, 0.92);
  color: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(13, 148, 136, 0.22);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: -0.2px;
  transition: transform 140ms ease, box-shadow 140ms ease, background 140ms ease;
}

.btnTealSm:hover {
  transform: translateY(-1px);
  background: rgba(13, 148, 136, 0.98);
  box-shadow: 0 14px 38px rgba(2, 6, 23, 0.10);
}

.btnTealSm:disabled {
  opacity: 0.58;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.btnGhostSm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  padding: 0 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.92);
  color: rgba(15, 23, 42, 0.88);
  border: 1px solid rgba(20, 184, 166, 0.22);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: -0.2px;
  transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease, background 140ms ease;
}

.btnGhostSm:hover {
  transform: translateY(-1px);
  border-color: rgba(20, 184, 166, 0.30);
  background: rgba(20, 184, 166, 0.06);
  box-shadow: 0 14px 38px rgba(2, 6, 23, 0.06);
}

.btnGhostSm:disabled {
  opacity: 0.58;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.spinSm {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 999px;
  border: 2px solid rgba(255, 255, 255, 0.38);
  border-top-color: rgba(255, 255, 255, 0.96);
  animation: spin 900ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.timeline {
  margin-top: 14px;
  display: grid;
  gap: 10px;
}

.item {
  display: grid;
  grid-template-columns: 22px 1fr auto;
  gap: 12px;
  padding: 14px 14px;
  border-radius: 18px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.92);
  transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
}

.item:hover {
  transform: translateY(-1px);
  border-color: rgba(20, 184, 166, 0.22);
  box-shadow: 0 14px 40px rgba(2, 6, 23, 0.08);
}

.actions {
  display: flex;
  align-items: flex-start;
  opacity: 0;
  transition: opacity 140ms ease;
}

.item:hover .actions {
  opacity: 1;
}

.dangerSm {
  color: rgba(239, 68, 68, 0.92);
}

.dangerSm:hover {
  background: rgba(239, 68, 68, 0.10);
}

.rail {
  position: relative;
  display: flex;
  justify-content: center;
}

.rail::before {
  content: '';
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  top: 14px;
  bottom: 14px;
  width: 1px;
  background: rgba(15, 23, 42, 0.08);
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: rgba(20, 184, 166, 0.95);
  margin-top: 18px;
  box-shadow: 0 0 0 6px rgba(20, 184, 166, 0.12);
}

.t {
  font-weight: 650;
  color: #0f172a;
  line-height: 1.25;
}

.d {
  margin-top: 6px;
  white-space: pre-wrap;
  word-break: break-word;
}

.more {
  display: flex;
  justify-content: center;
  padding-top: 14px;
}
</style>
