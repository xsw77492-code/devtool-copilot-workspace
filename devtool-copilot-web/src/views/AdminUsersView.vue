<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NButton, NInput, NModal, NSpin, useDialog, useMessage } from 'naive-ui'
import { adminApi, type AdminLoginAuditItem, type AdminUserItem } from '../api/admin'

const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const list = ref<AdminUserItem[]>([])
const keyword = ref('')

const showReset = ref(false)
const resetUser = ref<AdminUserItem | null>(null)
const newPassword = ref('')
const resetting = ref(false)

const showAudits = ref(false)
const auditsUser = ref<AdminUserItem | null>(null)
const audits = ref<AdminLoginAuditItem[]>([])
const auditsLoading = ref(false)

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return list.value
  return list.value.filter((u) => u.username.toLowerCase().includes(k) || u.email.toLowerCase().includes(k))
})

function fmtTime(v?: string | null) {
  if (!v) return '-'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  return d.toLocaleString()
}

function statusText(u: AdminUserItem) {
  if (u.disabled === 1) return '已禁用'
  if (u.lockUntil && new Date(u.lockUntil).getTime() > Date.now()) return '已锁定'
  return '正常'
}

function statusClass(u: AdminUserItem) {
  if (u.disabled === 1) return 'pill danger'
  if (u.lockUntil && new Date(u.lockUntil).getTime() > Date.now()) return 'pill warn'
  return 'pill ok'
}

async function load() {
  loading.value = true
  try {
    list.value = await adminApi.listUsers()
  } catch (e: any) {
    message.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function toggleDisabled(u: AdminUserItem) {
  const next = u.disabled !== 1
  const action = next ? '禁用' : '启用'
  dialog.warning({
    title: `${action}账号`,
    content: `确定要${action} ${u.username} 吗？`,
    positiveText: action,
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await adminApi.updateUserStatus(u.id, { disabled: next })
        await load()
        message.success(`${action}成功`)
      } catch (e: any) {
        message.error(e?.message || `${action}失败`)
      }
    }
  })
}

function openReset(u: AdminUserItem) {
  resetUser.value = u
  newPassword.value = ''
  showReset.value = true
}

async function submitReset() {
  if (!resetUser.value) return
  if (!newPassword.value.trim()) {
    message.error('请输入新密码')
    return
  }
  resetting.value = true
  try {
    await adminApi.resetUserPassword(resetUser.value.id, { newPassword: newPassword.value })
    message.success('密码已重置，已注销该用户所有登录端')
    showReset.value = false
  } catch (e: any) {
    message.error(e?.message || '重置失败')
  } finally {
    resetting.value = false
  }
}

function genPassword() {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
  const specials = '!@#$%^&*()-_=+'
  const pick = (s: string) => s[Math.floor(Math.random() * s.length)]
  let out = ''
  out += pick('ABCDEFGHJKLMNPQRSTUVWXYZ')
  out += pick('abcdefghijkmnopqrstuvwxyz')
  out += pick('23456789')
  out += pick(specials)
  for (let i = 0; i < 12; i++) out += pick(alphabet + specials)
  newPassword.value = out
}

async function openAudits(u: AdminUserItem) {
  auditsUser.value = u
  audits.value = []
  showAudits.value = true
  auditsLoading.value = true
  try {
    audits.value = await adminApi.getLoginAudits(u.id, 50)
  } catch (e: any) {
    message.error(e?.message || '加载失败')
  } finally {
    auditsLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="head">
      <div class="left">
        <h1 class="h1">账号管理</h1>
        <div class="muted">禁用账号 / 重置密码 / 查看最近登录</div>
      </div>
      <div class="right">
        <n-input v-model:value="keyword" placeholder="搜索用户名或邮箱" class="search" />
        <n-button tertiary @click="load">刷新</n-button>
      </div>
    </div>

    <section class="panel block">
      <div class="block-head">
        <div class="h2">Users</div>
        <div class="muted meta">{{ filtered.length }}</div>
      </div>

      <n-spin :show="loading">
        <div class="table">
          <div class="row headRow">
            <div class="c user">用户</div>
            <div class="c role">角色</div>
            <div class="c status">状态</div>
            <div class="c last">最近登录</div>
            <div class="c action">操作</div>
          </div>
          <div v-for="u in filtered" :key="u.id" class="row bodyRow">
            <div class="c user">
              <div class="uName">{{ u.username }}</div>
              <div class="uEmail muted">{{ u.email }}</div>
            </div>
            <div class="c role">
              <span class="pill neutral">{{ u.role }}</span>
            </div>
            <div class="c status">
              <span :class="statusClass(u)">{{ statusText(u) }}</span>
              <div v-if="u.lockUntil" class="muted tiny">锁定至 {{ fmtTime(u.lockUntil) }}</div>
              <div v-else class="muted tiny">失败次数 {{ u.failedLoginAttempts || 0 }}</div>
            </div>
            <div class="c last">
              <div class="l1">{{ fmtTime(u.lastLoginTime) }}</div>
              <div class="muted tiny">{{ u.lastLoginIp || '-' }}</div>
            </div>
            <div class="c action">
              <n-button size="small" tertiary @click="openAudits(u)">最近登录</n-button>
              <n-button size="small" tertiary @click="openReset(u)">重置密码</n-button>
              <n-button size="small" :type="u.disabled === 1 ? 'primary' : 'error'" secondary @click="toggleDisabled(u)">
                {{ u.disabled === 1 ? '启用' : '禁用' }}
              </n-button>
            </div>
          </div>
        </div>
      </n-spin>
    </section>

    <n-modal v-model:show="showReset" preset="card" title="重置密码" class="modal">
      <div class="modalBody">
        <div class="muted mb">
          {{ resetUser ? `为 ${resetUser.username} 设置新密码（将注销该用户所有 refresh token）` : '' }}
        </div>
        <n-input v-model:value="newPassword" type="password" placeholder="输入强密码" />
        <div class="modalActions">
          <n-button tertiary @click="genPassword">生成强密码</n-button>
          <div class="spacer" />
          <n-button tertiary @click="showReset = false">取消</n-button>
          <n-button type="primary" :loading="resetting" @click="submitReset">确认重置</n-button>
        </div>
        <div class="muted tiny mt">密码建议：12 位以上，包含大小写、数字与特殊字符</div>
      </div>
    </n-modal>

    <n-modal v-model:show="showAudits" preset="card" title="最近登录" class="modalWide">
      <div class="modalBody">
        <div class="muted mb">
          {{ auditsUser ? `用户：${auditsUser.username}` : '' }}
        </div>
        <n-spin :show="auditsLoading">
          <div class="auditList">
            <div v-for="a in audits" :key="a.id" class="auditRow">
              <div class="aLeft">
                <div class="aTime">{{ fmtTime(a.createTime) }}</div>
                <div class="muted tiny">{{ a.ip || '-' }}</div>
              </div>
              <div class="aRight">
                <span :class="a.success === 1 ? 'pill ok' : 'pill danger'">{{ a.success === 1 ? '成功' : '失败' }}</span>
                <span class="muted tiny">{{ a.failReason || '' }}</span>
                <div class="muted tiny ua">{{ a.userAgent || '' }}</div>
              </div>
            </div>
            <div v-if="!audits.length && !auditsLoading" class="muted empty">暂无记录</div>
          </div>
        </n-spin>
      </div>
    </n-modal>
  </div>
</template>

<style scoped>
.search {
  width: 260px;
}

.table {
  display: grid;
  gap: 10px;
  margin-top: 10px;
}

.row {
  display: grid;
  grid-template-columns: 1.3fr 0.6fr 0.9fr 0.9fr 1.2fr;
  gap: 12px;
  align-items: center;
}

.headRow {
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.03);
  color: rgba(15, 23, 42, 0.62);
  font-size: 12px;
  font-weight: 700;
}

.bodyRow {
  padding: 12px 12px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.7);
}

.uName {
  font-weight: 800;
  color: rgba(15, 23, 42, 0.9);
}

.uEmail {
  margin-top: 2px;
}

.pill {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(15, 23, 42, 0.03);
  color: rgba(15, 23, 42, 0.72);
}

.pill.ok {
  background: rgba(16, 185, 129, 0.1);
  border-color: rgba(16, 185, 129, 0.22);
  color: rgba(6, 95, 70, 0.96);
}

.pill.warn {
  background: rgba(245, 158, 11, 0.12);
  border-color: rgba(245, 158, 11, 0.24);
  color: rgba(146, 64, 14, 0.96);
}

.pill.danger {
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.22);
  color: rgba(153, 27, 27, 0.96);
}

.pill.neutral {
  background: rgba(6, 182, 212, 0.10);
  border-color: rgba(6, 182, 212, 0.20);
  color: rgba(15, 23, 42, 0.90);
}

.tiny {
  font-size: 12px;
}

.modal {
  width: min(520px, calc(100vw - 32px));
}

.modalWide {
  width: min(760px, calc(100vw - 32px));
}

.modalBody {
  display: grid;
  gap: 12px;
}

.modalActions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.spacer {
  flex: 1;
}

.mb {
  margin-bottom: 2px;
}

.mt {
  margin-top: 6px;
}

.auditList {
  display: grid;
  gap: 10px;
}

.auditRow {
  display: grid;
  grid-template-columns: 0.55fr 1fr;
  gap: 12px;
  padding: 12px 12px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.72);
}

.aTime {
  font-weight: 750;
}

.aRight {
  display: grid;
  gap: 6px;
}

.ua {
  word-break: break-all;
}

.empty {
  padding: 12px 0;
}

@media (max-width: 980px) {
  .row {
    grid-template-columns: 1fr;
  }
  .headRow {
    display: none;
  }
  .bodyRow {
    gap: 10px;
  }
  .search {
    width: 180px;
  }
}
</style>
