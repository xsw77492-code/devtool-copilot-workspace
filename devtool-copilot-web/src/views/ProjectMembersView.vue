<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NInput, NSelect, NSpin, useDialog, useMessage } from 'naive-ui'
import {
  projectCollabApi,
  type ProjectInviteItem,
  type ProjectMemberItem,
  type ProjectMemberRole,
  type ProjectMembersResponse
} from '../api/projectCollab'
import { useRealtimeStore } from '../stores/realtime'
import PresenceBar from '../components/PresenceBar.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const rt = useRealtimeStore()

const projectId = computed(() => Number(route.params.id))

const loading = ref(false)
const membersResp = ref<ProjectMembersResponse | null>(null)
const invites = ref<ProjectInviteItem[]>([])

const email = ref('')
const role = ref<ProjectMemberRole>('VIEWER')
const inviting = ref(false)
const exporting = ref(false)

const memberQ = ref('')
const roleFilter = ref<'ALL' | ProjectMemberRole>('ALL')
const statusFilter = ref<'all' | 'active' | 'disabled'>('all')

type InviteStatus = ProjectInviteItem['status']
type InviteStatusFilter = 'ALL' | InviteStatus

const inviteQ = ref('')
const inviteStatus = ref<InviteStatusFilter>('ALL')
const expandedInviteEmail = ref<string | null>(null)
const inviteGroupOpen = ref<Record<InviteStatus, boolean>>({
  PENDING: true,
  ACCEPTED: true,
  REJECTED: false,
  EXPIRED: false,
  CANCELED: false
})

const isOwner = computed(() => membersResp.value?.myRole === 'OWNER')
const myRole = computed(() => membersResp.value?.myRole || null)

const roleOptions = [
  { label: 'VIEWER（只读）', value: 'VIEWER' },
  { label: 'DEVELOPER（可协作）', value: 'DEVELOPER' },
  { label: 'OWNER（项目所有者）', value: 'OWNER' }
]

const memberRoleOptions = [
  { label: 'VIEWER（只读）', value: 'VIEWER' },
  { label: 'DEVELOPER（可协作）', value: 'DEVELOPER' }
]

const roleFilterOptions = [{ label: '全部角色', value: 'ALL' }, ...memberRoleOptions, { label: 'OWNER', value: 'OWNER' }]
const statusFilterOptions = [
  { label: '全部状态', value: 'all' },
  { label: '启用中', value: 'active' },
  { label: '已禁用', value: 'disabled' }
]

const inviteStatusOptions = [
  { label: '全部状态', value: 'ALL' },
  { label: 'PENDING', value: 'PENDING' },
  { label: 'ACCEPTED', value: 'ACCEPTED' },
  { label: 'REJECTED', value: 'REJECTED' },
  { label: 'EXPIRED', value: 'EXPIRED' },
  { label: 'CANCELED', value: 'CANCELED' }
]

const inviteStatusOrder: InviteStatus[] = ['PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CANCELED']

const presenceMap = computed(() => {
  const map = new Map<number, { online: boolean; away: boolean; editing: boolean; lastSeenTime?: string | null }>()
  for (const p of rt.presenceMembers || []) {
    if (!p || !p.userId) continue
    const raw = String(p.lastSeenTime || '')
    const ts = raw ? Date.parse(raw) : NaN
    const away = Number.isFinite(ts) ? Date.now() - ts > 45 * 1000 : false
    map.set(p.userId, { online: p.online !== false, away, editing: p.editing === true, lastSeenTime: p.lastSeenTime })
  }
  return map
})

function presenceClass(userId: number) {
  const p = presenceMap.value.get(userId)
  if (!p) return 'p-unknown'
  if (!p.online) return 'p-off'
  if (p.away) return 'p-away'
  if (p.editing) return 'p-edit'
  return 'p-on'
}

const filteredMembers = computed(() => {
  const list = membersResp.value?.members || []
  const q = memberQ.value.trim().toLowerCase()
  return list.filter((m) => {
    if (!m) return false
    const disabled = Number(m.disabled || 0) === 1
    if (statusFilter.value === 'active' && disabled) return false
    if (statusFilter.value === 'disabled' && !disabled) return false
    if (roleFilter.value !== 'ALL' && m.role !== roleFilter.value) return false
    if (!q) return true
    const s = `${m.username || ''} ${m.email || ''}`.toLowerCase()
    return s.includes(q)
  })
})

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

function fmtDate(raw?: string | null) {
  const s = String(raw || '')
  if (!s) return ''
  const ts = Date.parse(s)
  if (!Number.isFinite(ts)) return s
  const d = new Date(ts)
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

type InviteEmailAgg = { email: string; latest: ProjectInviteItem; items: ProjectInviteItem[]; count: number }

const inviteAggs = computed<InviteEmailAgg[]>(() => {
  const map = new Map<string, ProjectInviteItem[]>()
  for (const it of invites.value || []) {
    const em = String(it?.email || '').trim().toLowerCase()
    if (!em) continue
    const arr = map.get(em) || []
    arr.push(it)
    map.set(em, arr)
  }

  const q = inviteQ.value.trim().toLowerCase()
  const out: InviteEmailAgg[] = []
  for (const [em, arr] of map.entries()) {
    arr.sort((a, b) => (b.id || 0) - (a.id || 0))
    const latest = arr[0]
    if (!latest) continue
    if (q && !em.includes(q)) continue
    if (inviteStatus.value !== 'ALL' && latest.status !== inviteStatus.value) continue
    out.push({ email: em, latest, items: arr, count: arr.length })
  }
  out.sort((a, b) => (b.latest.id || 0) - (a.latest.id || 0))
  return out
})

const inviteGroups = computed<Record<InviteStatus, InviteEmailAgg[]>>(() => {
  const groups: Record<InviteStatus, InviteEmailAgg[]> = {
    PENDING: [],
    ACCEPTED: [],
    REJECTED: [],
    EXPIRED: [],
    CANCELED: []
  }
  for (const g of inviteAggs.value) {
    const st = g.latest.status as InviteStatus
    if (groups[st]) groups[st].push(g)
  }
  return groups
})

function toggleInviteEmail(email: string) {
  expandedInviteEmail.value = expandedInviteEmail.value === email ? null : email
}

function toggleInviteGroup(st: InviteStatus) {
  inviteGroupOpen.value = { ...inviteGroupOpen.value, [st]: !inviteGroupOpen.value[st] }
}

async function load() {
  loading.value = true
  try {
    membersResp.value = await projectCollabApi.members(projectId.value)
    if (membersResp.value.myRole === 'OWNER') {
      invites.value = await projectCollabApi.invites(projectId.value)
    } else {
      invites.value = []
    }
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

async function invite() {
  if (!email.value.trim()) {
    message.error('请输入邮箱')
    return
  }
  inviting.value = true
  try {
    const res = await projectCollabApi.invite(projectId.value, { email: email.value.trim(), role: role.value })
    email.value = ''
    role.value = 'VIEWER'
    await load()
    await copyText(res.inviteLink)
    message.success('邀请已创建，邀请链接已复制')
  } catch (e: any) {
    message.error(e?.message || '邀请失败')
  } finally {
    inviting.value = false
  }
}

async function reInvite(it: ProjectInviteItem) {
  inviting.value = true
  try {
    const res = await projectCollabApi.reissueInvite(projectId.value, it.id)
    await load()
    await copyText(res.inviteLink)
    message.success('邀请链接已复制')
  } catch (e: any) {
    message.error(e?.message || '操作失败')
  } finally {
    inviting.value = false
  }
}

function cancelInvite(it: ProjectInviteItem) {
  dialog.warning({
    title: '取消邀请',
    content: `确定取消对 ${it.email} 的邀请吗？`,
    positiveText: '取消邀请',
    negativeText: '返回',
    onPositiveClick: async () => {
      try {
        await projectCollabApi.cancelInvite(projectId.value, it.id)
        await load()
        message.success('已取消邀请')
      } catch (e: any) {
        message.error(e?.message || '操作失败')
      }
    }
  })
}

function removeMember(userId: number, username: string) {
  dialog.warning({
    title: '移除成员',
    content: `确定移除 ${username} 吗？`,
    positiveText: '移除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await projectCollabApi.removeMember(projectId.value, userId)
        await load()
        message.success('已移除')
      } catch (e: any) {
        message.error(e?.message || '移除失败')
      }
    }
  })
}

function updateRole(m: ProjectMemberItem, nextRole: ProjectMemberRole) {
  if (!isOwner.value) return
  if (m.role === 'OWNER') return
  if (nextRole === 'OWNER') return
  if (m.role === nextRole) return
  dialog.warning({
    title: '调整角色',
    content: `确定将 ${m.username} 的角色调整为 ${nextRole} 吗？`,
    positiveText: '确认',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await projectCollabApi.updateMemberRole(projectId.value, m.userId, nextRole)
        await load()
        message.success('已更新角色')
      } catch (e: any) {
        message.error(e?.message || '操作失败')
      }
    }
  })
}

function toggleDisabled(m: ProjectMemberItem, disabled: boolean) {
  if (!isOwner.value) return
  if (m.role === 'OWNER') return
  dialog.warning({
    title: disabled ? '禁用成员' : '启用成员',
    content: disabled ? `禁用后 ${m.username} 将无法访问该项目。确定继续吗？` : `确定启用 ${m.username} 吗？`,
    positiveText: disabled ? '禁用' : '启用',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await projectCollabApi.setMemberDisabled(projectId.value, m.userId, disabled)
        await load()
        message.success(disabled ? '已禁用' : '已启用')
      } catch (e: any) {
        message.error(e?.message || '操作失败')
      }
    }
  })
}

function transferOwner(m: ProjectMemberItem) {
  if (!isOwner.value) return
  if (m.userId === 0 || m.role === 'OWNER') return
  dialog.warning({
    title: '转让所有权',
    content: `确定将项目 OWNER 转让给 ${m.username} 吗？转让后你将不再是 OWNER。`,
    positiveText: '确认转让',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await projectCollabApi.transferOwner(projectId.value, m.userId)
        await load()
        message.success('已转让')
      } catch (e: any) {
        message.error(e?.message || '操作失败')
      }
    }
  })
}

function leaveProject() {
  if (isOwner.value) return
  dialog.warning({
    title: '退出项目',
    content: '确定退出该项目吗？退出后需要重新邀请才能加入。',
    positiveText: '退出',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await projectCollabApi.leaveProject(projectId.value)
        message.success('已退出项目')
        router.push({ name: 'workspace' })
      } catch (e: any) {
        message.error(e?.message || '操作失败')
      }
    }
  })
}

async function exportMembers() {
  exporting.value = true
  try {
    const res = await projectCollabApi.exportMembers(projectId.value)
    downloadText(res.filename || `members_project_${projectId.value}.csv`, res.content || '')
    message.success('已导出')
  } catch (e: any) {
    message.error(e?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

function downloadText(filename: string, content: string) {
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

async function copyText(t: string) {
  try {
    await navigator.clipboard.writeText(t)
  } catch {
    const el = document.createElement('textarea')
    el.value = t
    el.style.position = 'fixed'
    el.style.left = '-9999px'
    document.body.appendChild(el)
    el.focus()
    el.select()
    document.execCommand('copy')
    document.body.removeChild(el)
  }
}

onMounted(() => {
  rt.subscribe(projectId.value, 'MEMBERS', projectId.value)
  load()
})

watch(projectId, (id) => {
  const pid = Number(id)
  rt.subscribe(pid, 'MEMBERS', pid)
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
    if (t.startsWith('MEMBER_')) load()
    if (t === 'PROJECT_INVITE_ACCEPTED' || t === 'PROJECT_INVITE_REJECTED') load()
  }
)
</script>

<template>
  <div class="page lightPage">
    <div class="head">
      <div class="left">
        <h1 class="h1">成员管理</h1>
        <div class="sub">
          <span v-if="myRole" class="chip">我的角色：{{ myRole }}</span>
        </div>
      </div>
      <div class="right">
        <presence-bar :project-id="projectId" />
        <n-button tertiary @click="router.push({ name: 'project-detail', params: { id: projectId } })">返回项目</n-button>
        <n-button v-if="myRole" tertiary :loading="exporting" @click="exportMembers">导出成员</n-button>
        <n-button v-if="myRole && !isOwner" tertiary @click="leaveProject">退出项目</n-button>
        <n-button tertiary @click="load">刷新</n-button>
      </div>
    </div>

    <n-spin :show="loading">
      <div class="grid">
        <section class="panel lightPanel">
          <div class="block-head">
            <div class="h2">成员列表</div>
            <div class="muted meta">{{ filteredMembers.length }}</div>
          </div>

          <div class="filters">
            <n-input v-model:value="memberQ" placeholder="搜索成员（用户名/邮箱）" class="fInput" />
            <n-select v-model:value="roleFilter" :options="roleFilterOptions" class="fSel" />
            <n-select v-model:value="statusFilter" :options="statusFilterOptions" class="fSel" />
          </div>

          <div class="memberList">
            <div v-for="m in filteredMembers" :key="m.userId" class="memberRow" :class="{ disabledRow: Number(m.disabled || 0) === 1 }">
              <div class="avatarWrap" aria-hidden="true">
                <div class="avatar">{{ (m.username || 'U').slice(0, 1).toUpperCase() }}</div>
                <span class="pDot" :class="presenceClass(m.userId)" />
              </div>
              <div class="mMain">
                <div class="mName">{{ m.username }}</div>
                <div class="muted mEmail">{{ m.email }}</div>
                <div v-if="Number(m.disabled || 0) === 1 && m.disabledTime" class="muted tiny">禁用时间：{{ fmtDate(m.disabledTime) }}</div>
              </div>
              <div class="mMeta">
                <span class="tag" :class="`role-${m.role.toLowerCase()}`">{{ m.role }}</span>
                <span v-if="Number(m.disabled || 0) === 1" class="tag tagDisabled">DISABLED</span>
              </div>
              <div class="mActions">
                <n-select
                  v-if="isOwner && m.role !== 'OWNER'"
                  size="small"
                  class="roleSel"
                  :value="m.role"
                  :options="memberRoleOptions"
                  :disabled="Number(m.disabled || 0) === 1"
                  @update:value="(v) => updateRole(m, v as any)"
                />
                <n-button
                  v-if="isOwner && m.role !== 'OWNER' && Number(m.disabled || 0) === 0"
                  size="small"
                  secondary
                  class="ghostBtn"
                  @click="toggleDisabled(m, true)"
                >
                  禁用
                </n-button>
                <n-button
                  v-if="isOwner && m.role !== 'OWNER' && Number(m.disabled || 0) === 1"
                  size="small"
                  secondary
                  class="ghostBtn"
                  @click="toggleDisabled(m, false)"
                >
                  启用
                </n-button>
                <n-button
                  v-if="isOwner && m.role !== 'OWNER' && Number(m.disabled || 0) === 0"
                  size="small"
                  secondary
                  class="ghostBtn"
                  @click="transferOwner(m)"
                >
                  转让Owner
                </n-button>
                <n-button
                  v-if="isOwner && m.role !== 'OWNER'"
                  size="small"
                  secondary
                  class="ghostBtn"
                  @click="removeMember(m.userId, m.username)"
                >
                  移除成员
                </n-button>
              </div>
            </div>
            <div v-if="!filteredMembers.length" class="emptyState">
              <div class="emptyTitle">暂无成员</div>
            </div>
          </div>
        </section>

        <div v-if="isOwner" class="side">
          <section class="panel lightPanel invitePanel">
            <div class="block-head">
              <div class="h2">邀请成员</div>
            </div>

            <div class="inviteForm">
              <n-input v-model:value="email" placeholder="输入成员邮箱" class="email" />
              <n-select v-model:value="role" :options="roleOptions" class="role" />
              <n-button :loading="inviting" class="ghostBtn" @click="invite">发送邀请</n-button>
            </div>
          </section>

          <section class="panel lightPanel">
            <div class="block-head">
              <div class="h2">邀请记录</div>
              <div class="muted meta">{{ inviteAggs.length }}</div>
            </div>

            <div class="filters inviteFilters">
              <n-input v-model:value="inviteQ" placeholder="搜索邀请邮箱" class="fInput" />
              <n-select v-model:value="inviteStatus" :options="inviteStatusOptions" class="fSel" />
            </div>

            <div class="inviteGroups">
              <div v-for="st in inviteStatusOrder" :key="st" class="group">
                <div class="groupHead" @click="toggleInviteGroup(st)">
                  <div class="ghLeft">
                    <span class="statusChip" :class="`st-${st.toLowerCase()}`">{{ st }}</span>
                    <span class="muted tiny">{{ inviteGroups[st].length }} 个邮箱</span>
                  </div>
                  <div class="muted tiny">{{ inviteGroupOpen[st] ? '收起' : '展开' }}</div>
                </div>

                <div v-if="inviteGroupOpen[st]" class="groupBody">
                  <div v-for="g in inviteGroups[st]" :key="g.email" class="emailBlock">
                    <div class="inviteRow emailRow" :class="{ open: expandedInviteEmail === g.email }" @click="toggleInviteEmail(g.email)">
                      <div class="iMain">
                        <div class="iEmail">{{ g.email }}</div>
                        <div class="muted tiny">最近 {{ fmtDate(g.latest.createTime) }} · {{ g.count }} 次</div>
                      </div>
                      <div class="iMeta">
                        <span class="statusChip" :class="`st-${g.latest.status.toLowerCase()}`">{{ g.latest.status }}</span>
                      </div>
                      <div class="iActions" @click.stop>
                        <n-button
                          v-if="g.latest.status === 'PENDING'"
                          size="small"
                          secondary
                          class="ghostBtn"
                          :loading="inviting"
                          @click="reInvite(g.latest)"
                        >
                          复制链接
                        </n-button>
                        <n-button v-if="g.latest.status === 'PENDING'" size="small" secondary class="ghostBtn" @click="cancelInvite(g.latest)"
                          >取消</n-button
                        >
                      </div>
                    </div>

                    <div v-if="expandedInviteEmail === g.email" class="inviteHistory">
                      <div v-for="it in g.items" :key="it.id" class="histRow">
                        <div class="histLeft">
                          <div class="tiny">{{ fmtDate(it.createTime) }}</div>
                          <div class="muted tiny">role={{ it.role }} · expire={{ fmtDate(it.expireTime) || '-' }}</div>
                        </div>
                        <div class="histRight">
                          <span class="statusChip" :class="`st-${it.status.toLowerCase()}`">{{ it.status }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                  <div v-if="!inviteGroups[st].length" class="muted empty">暂无</div>
                </div>
              </div>
            </div>
          </section>
        </div>
      </div>
    </n-spin>
  </div>
</template>

<style scoped>
.lightPage {
  background: #ffffff;
  color: #0f172a;
}

.sub {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
  flex-wrap: wrap;
}

.chip {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(20, 184, 166, 0.08);
  border: 1px solid rgba(20, 184, 166, 0.18);
  color: rgba(15, 23, 42, 0.92);
  font-weight: 760;
  font-size: 12px;
}

.grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 14px;
  align-items: start;
}

.side {
  display: grid;
  gap: 14px;
}

.lightPanel {
  background:
    radial-gradient(900px 420px at 10% 0%, rgba(6, 182, 212, 0.10), transparent 58%),
    #ffffff;
  border: none;
  box-shadow: 0 18px 55px rgba(2, 6, 23, 0.06);
}

.memberList,
.inviteList {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.filters {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr 0.8fr;
  gap: 10px;
  margin-top: 12px;
  align-items: center;
}

.inviteFilters {
  grid-template-columns: 1.3fr 0.7fr;
}

.roleSel {
  min-width: 150px;
}

.memberRow,
.inviteRow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 16px;
  border: none;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 10px 30px rgba(2, 6, 23, 0.06);
  transition: transform 140ms ease, box-shadow 140ms ease;
}

.memberRow:hover,
.inviteRow:hover {
  transform: translateY(-1px);
  box-shadow: 0 16px 46px rgba(2, 6, 23, 0.10);
}

.disabledRow {
  opacity: 0.72;
  filter: grayscale(0.25);
}

.avatarWrap {
  position: relative;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  font-weight: 820;
  color: rgba(15, 23, 42, 0.86);
  background: rgba(20, 184, 166, 0.10);
  border: 1px solid rgba(20, 184, 166, 0.16);
}

.pDot {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 10px;
  height: 10px;
  border-radius: 999px;
  border: 2px solid rgba(255, 255, 255, 0.95);
  background: rgba(100, 116, 139, 0.55);
  box-shadow: 0 6px 16px rgba(2, 6, 23, 0.10);
}

.p-on {
  background: rgba(16, 185, 129, 0.95);
}

.p-away {
  background: rgba(245, 158, 11, 0.90);
}

.p-edit {
  background: rgba(20, 184, 166, 0.95);
}

.p-off {
  background: rgba(100, 116, 139, 0.55);
}

.p-unknown {
  background: rgba(100, 116, 139, 0.35);
}

.mMain {
  min-width: 0;
  flex: 1;
}

.mMeta,
.mActions,
.iMeta,
.iActions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mName {
  font-weight: 860;
  letter-spacing: -0.1px;
}

.mEmail,
.iEmail {
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 520px;
}

.tag {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 820;
  border: none;
  background: rgba(15, 23, 42, 0.04);
  color: rgba(15, 23, 42, 0.82);
}

.role-owner {
  background: rgba(20, 184, 166, 0.10);
  border-color: rgba(20, 184, 166, 0.20);
  color: rgba(15, 23, 42, 0.90);
}

.role-developer {
  background: rgba(14, 165, 233, 0.10);
  border-color: rgba(14, 165, 233, 0.18);
  color: rgba(2, 132, 199, 0.92);
}

.role-viewer {
  background: rgba(100, 116, 139, 0.10);
  border-color: rgba(100, 116, 139, 0.18);
  color: rgba(51, 65, 85, 0.92);
}

.tagDisabled {
  background: rgba(100, 116, 139, 0.10);
  border-color: rgba(100, 116, 139, 0.18);
  color: rgba(51, 65, 85, 0.92);
}

.inviteForm {
  display: grid;
  grid-template-columns: 1.2fr 1fr auto;
  gap: 10px;
  margin-top: 12px;
  align-items: center;
}

.invitePanel .inviteForm {
  margin-top: 14px;
}

:deep(.invitePanel .n-input),
:deep(.invitePanel .n-base-selection) {
  background: rgba(255, 255, 255, 0.78);
  border-color: transparent;
  box-shadow: 0 10px 28px rgba(2, 6, 23, 0.05);
  border-radius: 14px;
}

:deep(.invitePanel .n-input:hover),
:deep(.invitePanel .n-base-selection:hover) {
  border-color: transparent;
}

:deep(.invitePanel .n-input--focus),
:deep(.invitePanel .n-base-selection--active) {
  border-color: transparent;
  box-shadow: 0 0 0 4px rgba(20, 184, 166, 0.10), 0 12px 34px rgba(2, 6, 23, 0.06);
}

.tip {
  margin-top: 10px;
  line-height: 1.6;
}

.tiny {
  font-size: 12px;
}

.empty {
  padding: 6px 0;
}

.emptyState {
  padding: 18px 14px;
  border-radius: 16px;
  border: none;
  background: rgba(15, 23, 42, 0.02);
}

.emptyTitle {
  font-weight: 860;
}

.emptyDesc {
  margin-top: 6px;
  line-height: 1.6;
}

.status,
.statusChip {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 840;
  border: none;
  background: rgba(15, 23, 42, 0.04);
  color: rgba(15, 23, 42, 0.72);
}

.st-pending {
  background: rgba(20, 184, 166, 0.10);
  border-color: rgba(20, 184, 166, 0.20);
  color: rgba(15, 23, 42, 0.90);
}

.st-accepted {
  background: rgba(20, 184, 166, 0.14);
  border-color: rgba(20, 184, 166, 0.24);
  color: rgba(15, 23, 42, 0.90);
}

.st-rejected,
.st-expired,
.st-canceled {
  background: rgba(100, 116, 139, 0.10);
  border-color: rgba(100, 116, 139, 0.18);
  color: rgba(51, 65, 85, 0.92);
}

.inviteGroups {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.groupHead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.55);
  border: none;
  box-shadow: 0 10px 30px rgba(2, 6, 23, 0.05);
  cursor: pointer;
  user-select: none;
  transition: transform 140ms ease, box-shadow 140ms ease;
}

.groupHead:hover {
  transform: translateY(-1px);
  box-shadow: 0 16px 46px rgba(2, 6, 23, 0.10);
}

.ghLeft {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.groupBody {
  display: grid;
  gap: 8px;
  margin-top: 8px;
}

.emailBlock {
  display: grid;
  gap: 8px;
}

.emailRow.open {
  box-shadow: 0 18px 52px rgba(2, 6, 23, 0.12);
}

.inviteHistory {
  display: grid;
  gap: 6px;
  padding-left: 10px;
}

.histRow {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 14px;
  border: none;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 10px 28px rgba(2, 6, 23, 0.05);
}

.histLeft {
  min-width: 0;
}

.histRight {
  flex: 0 0 auto;
}

@media (max-width: 980px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .filters {
    grid-template-columns: 1fr;
  }
  .inviteForm {
    grid-template-columns: 1fr;
  }
  .mEmail,
  .iEmail {
    max-width: 220px;
  }
}

:deep(.n-input),
:deep(.n-base-selection) {
  background: rgba(255, 255, 255, 0.95);
  border-color: rgba(15, 23, 42, 0.12);
  color: rgba(15, 23, 42, 0.92);
}

:deep(.n-input:hover),
:deep(.n-base-selection:hover) {
  border-color: rgba(20, 184, 166, 0.28);
}

:deep(.n-input--focus),
:deep(.n-base-selection--active) {
  border-color: rgba(20, 184, 166, 0.42);
  box-shadow: 0 0 0 4px rgba(20, 184, 166, 0.12);
}

:deep(.n-button.ghostBtn) {
  border-radius: 12px;
}
</style>
