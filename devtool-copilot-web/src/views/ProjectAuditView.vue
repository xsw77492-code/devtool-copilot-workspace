<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NConfigProvider, NDatePicker, NDropdown, NInput, NSelect, NSpin, dateZhCN, useDialog, useMessage, zhCN } from 'naive-ui'
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
const loadError = ref('')

const action = ref<string>('')
const q = ref('')
const range = ref<[number, number] | null>(null)

const headMoreOptions = computed(() => [
  { key: 'back', label: '返回项目' },
  { key: 'refresh', label: '刷新', disabled: loading.value || loadingMore.value },
  { key: 'clear', label: '清空', disabled: clearing.value }
])

function onHeadMoreSelect(key: string | number) {
  if (key === 'back') router.push({ name: 'project-detail', params: { id: projectId.value } })
  if (key === 'refresh') void load(true)
  if (key === 'clear') void clearAll()
}

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

const actionLabels: Record<string, string> = Object.fromEntries(
  actionOptions.filter((o) => o.value).map((o) => [o.value, o.label])
) as Record<string, string>

actionLabels.MEMBER_ENABLED = '启用成员'
actionLabels.MEMBER_OWNER_TRANSFERRED = '转让所有权'

function actionLabelOf(action: string) {
  return actionLabels[action] || action
}

function fmtTime(v?: string | null) {
  if (!v) return ''
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  return d.toLocaleString()
}

function fmtRelativeTime(v?: string | null) {
  if (!v) return ''
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  const diff = Date.now() - d.getTime()
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min} 分钟前`
  const h = Math.floor(min / 60)
  if (h < 24) return `${h} 小时前`
  const now = new Date()
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const startOfDay = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  if (startOfDay === startOfToday) return `今天 ${fmtHM(d)}`
  if (startOfDay === startOfToday - 86400000) return `昨天 ${fmtHM(d)}`
  if (h < 24 * 7) return `${Math.floor(h / 24)} 天前`
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
}

function fmtHM(d: Date) {
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
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
    loadError.value = e?.message || '加载失败'
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
    <section class="panel lightPanel">
      <div class="panelHead">
        <div class="filters">
          <n-select v-model:value="action" :options="actionOptions" size="small" class="fSel" />
          <n-input v-model:value="q" size="small" placeholder="关键词（摘要/详情）" class="fInput" @keyup.enter="load(true)" />
          <n-config-provider :locale="zhCN" :date-locale="dateZhCN">
            <n-date-picker v-model:value="range" type="datetimerange" clearable size="small" class="fDate" />
          </n-config-provider>
          <n-button size="small" secondary class="accentBtn" @click="load(true)">筛选</n-button>
        </div>
        <div class="headActions">
          <presence-bar :project-id="projectId" />
          <n-button secondary class="accentBtn" :loading="exporting" @click="exportCsv">导出 CSV</n-button>
          <n-dropdown :options="headMoreOptions" trigger="click" placement="bottom-end" @select="onHeadMoreSelect">
            <n-button tertiary>更多</n-button>
          </n-dropdown>
        </div>
      </div>

      <div class="statLine" v-if="list.length || loading">
        <span>已加载 {{ list.length }} 条记录</span>
      </div>

      <n-spin :show="loading">
        <div v-if="loadError && !loading" class="emptyState err">{{ loadError }}</div>
        <div v-else-if="!list.length && !loading" class="emptyState">
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
                <span :title="fmtTime(a.createTime)">{{ fmtRelativeTime(a.createTime) }}</span>
                <span v-if="a.ip"> · {{ a.ip }}</span>
                <span v-if="a.action"> · {{ actionLabelOf(a.action) }}</span>
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

        <div v-if="list.length && !hasMore && !loading" class="end">已加载全部</div>
      </n-spin>
    </section>
  </div>
</template>

<style scoped>
.lightPage {
  background: transparent;
  color: #0f172a;
}

.lightPanel {
  background: transparent;
  border: 0;
  box-shadow: none;
}

.panelHead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.accentBtn {
  background: #fff !important;
  border-color: rgba(15, 23, 42, 0.16) !important;
  color: #0f172a !important;
  transition: transform 160ms ease, filter 160ms ease;
}

.accentBtn:hover {
  background: rgba(15, 23, 42, 0.04) !important;
  border-color: rgba(15, 23, 42, 0.28) !important;
  color: #0f172a !important;
  transform: translateY(-1px);
}

.filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  flex: 1 1 auto;
  min-width: 0;
}

.headActions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
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

.statLine {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 6px 0;
  font-size: 12px;
  color: rgba(15, 23, 42, 0.45);
}

.timeline {
  margin-top: 6px;
  display: grid;
  gap: 0;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}

.item {
  display: grid;
  grid-template-columns: 22px 1fr auto;
  gap: 12px;
  padding: 14px 6px;
  border-radius: 0;
  border: 0;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  background: transparent;
  transition: background 140ms ease;
}

.item:hover {
  background: rgba(15, 23, 42, 0.03);
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
  background: rgba(15, 23, 42, 0.55);
  margin-top: 18px;
  box-shadow: 0 0 0 6px rgba(15, 23, 42, 0.06);
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

.end {
  display: flex;
  justify-content: center;
  padding-top: 14px;
  font-size: 12px;
  color: rgba(15, 23, 42, 0.4);
}

.emptyState {
  padding: 48px 0 24px;
  text-align: center;
  color: rgba(15, 23, 42, 0.55);
  font-size: 13px;
}

.emptyState.err {
  color: rgba(220, 38, 38, 0.85);
}
</style>
