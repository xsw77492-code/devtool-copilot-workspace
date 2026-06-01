<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NSpin, useDialog, useMessage } from 'naive-ui'
import { projectCollabApi, type ProjectActivityItem } from '../api/projectCollab'
import { useRealtimeStore } from '../stores/realtime'
import PresenceBar from '../components/PresenceBar.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const rt = useRealtimeStore()

const projectId = computed(() => Number(route.params.id))
const loading = ref(false)
const list = ref<ProjectActivityItem[]>([])
const clearing = ref(false)
const deletingId = ref<number | null>(null)

function fmtTime(v?: string) {
  if (!v) return ''
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return v
  return d.toLocaleString()
}

function titleOf(a: ProjectActivityItem) {
  const actor = a.actorUsername || 'System'
  if (a.type === 'TASK_CREATED') return `${actor} 创建了任务`
  if (a.type === 'TASK_DONE') return `${actor} 完成了任务`
  if (a.type === 'AI_GENERATE_TASK') return `${actor} 通过 AI 生成了任务`
  if (a.type === 'TASK_COMMENT_CREATED') return `${actor} 评论了任务`
  if (a.type === 'MEMBER_JOINED') return `${actor} 加入了项目`
  if (a.type === 'MEMBER_REMOVED') return `${actor} 移除了成员`
  if (a.type === 'MEMBER_INVITED') return `${actor} 邀请了成员`
  if (a.type === 'MEMBER_INVITE_CANCELED') return `${actor} 取消了邀请`
  if (a.type === 'MEMBER_INVITE_REISSUED') return `${actor} 更新了邀请链接`
  if (a.type === 'MEMBER_ROLE_CHANGED') return `${actor} 调整了成员角色`
  if (a.type === 'MEMBER_DISABLED') return `${actor} 禁用了成员`
  if (a.type === 'MEMBER_ENABLED') return `${actor} 启用了成员`
  if (a.type === 'MEMBER_OWNER_TRANSFERRED') return `${actor} 转让了所有权`
  if (a.type === 'MEMBERS_EXPORT_CSV') return `${actor} 导出了成员列表`
  return `${actor} · ${a.type}`
}

function detailOf(a: ProjectActivityItem) {
  const raw = String(a.detail || '').trim()
  if (!raw) return ''
  let obj: any = null
  if (raw.startsWith('{') && raw.endsWith('}')) {
    try {
      obj = JSON.parse(raw)
    } catch {
      obj = null
    }
  }
  const email = obj && typeof obj.email === 'string' ? obj.email : null
  const role = obj && typeof obj.role === 'string' ? obj.role : null
  const inviteId = obj && (typeof obj.inviteId === 'number' || typeof obj.inviteId === 'string') ? obj.inviteId : null
  const userId = obj && (typeof obj.userId === 'number' || typeof obj.userId === 'string') ? obj.userId : null
  const newOwnerUserId =
    obj && (typeof obj.newOwnerUserId === 'number' || typeof obj.newOwnerUserId === 'string') ? obj.newOwnerUserId : null

  if (a.type === 'MEMBER_INVITED') return `${email || ''}${role ? ` · ${role}` : ''}`.trim()
  if (a.type === 'MEMBER_INVITE_CANCELED') return `${email || ''}${inviteId ? ` · inviteId=${inviteId}` : ''}`.trim()
  if (a.type === 'MEMBER_INVITE_REISSUED') return `${email || ''}${inviteId ? ` · inviteId=${inviteId}` : ''}`.trim()
  if (a.type === 'MEMBER_ROLE_CHANGED') return `${userId ? `userId=${userId}` : ''}${role ? ` · ${role}` : ''}`.trim()
  if (a.type === 'MEMBER_DISABLED' || a.type === 'MEMBER_ENABLED') return userId ? `userId=${userId}` : ''
  if (a.type === 'MEMBER_OWNER_TRANSFERRED') return newOwnerUserId ? `newOwnerUserId=${newOwnerUserId}` : ''
  if (a.type === 'MEMBERS_EXPORT_CSV') return 'CSV'

  return raw
}

async function load() {
  loading.value = true
  try {
    list.value = await projectCollabApi.activities(projectId.value, 200)
  } catch (e: any) {
    const msg = String(e?.message || '')
    if (msg.includes('成员已被禁用')) {
      message.error('你已被该项目禁用')
      router.replace({ name: 'workspace' })
      return
    }
    message.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function removeOne(a: ProjectActivityItem) {
  if (!a?.id) return
  dialog.warning({
    title: '删除动态',
    content: '删除后不可恢复。',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        deletingId.value = a.id
        await projectCollabApi.deleteActivity(projectId.value, a.id)
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
    title: '清空动态',
    content: '将删除该项目的全部动态记录，删除后不可恢复。',
    positiveText: '清空',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        clearing.value = true
        await projectCollabApi.clearActivities(projectId.value)
        list.value = []
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
  rt.subscribe(projectId.value, 'ACTIVITY', projectId.value)
  load()
})

watch(projectId, (id) => {
  const pid = Number(id)
  rt.subscribe(pid, 'ACTIVITY', pid)
  load()
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
    if (t.startsWith('TASK_') || t.startsWith('MEMBER_') || t === 'AI_GENERATE_TASK') {
      load()
    }
  }
)
</script>

<template>
  <div class="page lightPage">
    <div class="head">
      <div class="left">
        <h1 class="h1">项目动态</h1>
      </div>
      <div class="right">
        <presence-bar :project-id="projectId" />
        <n-button tertiary @click="router.push({ name: 'project-detail', params: { id: projectId } })">返回项目</n-button>
        <n-button tertiary @click="load">刷新</n-button>
        <n-button secondary :loading="clearing" @click="clearAll">清空</n-button>
      </div>
    </div>

    <section class="panel lightPanel">
      <div class="topbar">
        <div class="muted">共 {{ list.length }} 条</div>
      </div>

      <n-spin :show="loading">
        <div v-if="!list.length && !loading" class="emptyState">
          <div class="emptyTitle">还没有动态</div>
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
                <span v-if="detailOf(a)"> · {{ detailOf(a) }}</span>
              </div>
            </div>
            <div class="actions">
              <n-button size="tiny" quaternary :loading="deletingId === a.id" @click="removeOne(a)">删除</n-button>
            </div>
          </div>
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
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
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
  width: 2px;
  border-radius: 2px;
  background: rgba(20, 184, 166, 0.12);
}

.dot {
  width: 12px;
  height: 12px;
  border-radius: 999px;
  margin-top: 4px;
  background: rgba(20, 184, 166, 0.86);
  box-shadow: 0 0 0 4px rgba(20, 184, 166, 0.12);
}

.content {
  min-width: 0;
}

.t {
  font-weight: 900;
  letter-spacing: -0.2px;
  font-size: 15px;
}

.s {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
}

.emptyState {
  padding: 26px 14px 34px;
  border-radius: 18px;
  border: 1px dashed rgba(15, 23, 42, 0.14);
  background: rgba(15, 23, 42, 0.02);
  margin-top: 14px;
}

.emptyTitle {
  font-weight: 900;
  font-size: 16px;
}

.emptyDesc {
  margin-top: 8px;
  line-height: 1.7;
}
</style>
