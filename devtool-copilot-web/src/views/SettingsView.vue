<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NButton, NCard, NCheckbox, NInput, NSelect, NSpin, useDialog, useMessage } from 'naive-ui'
import { useAuthStore } from '../stores/auth'
import { authApi, type UserSessionItem } from '../api/auth'
import NotificationSettingsPanel from '../components/NotificationSettingsPanel.vue'
import { ACCENT_PALETTE } from '../styles/accent'
import { usePreferenceStore } from '../stores/preference'

const auth = useAuthStore()
const pref = usePreferenceStore()
const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const saving = ref(false)

const accentKey = ref(pref.pref?.accentKey || 'teal')
const timezone = ref(pref.pref?.timezone || 'Asia/Shanghai')
const weekStart = ref<number>(pref.pref?.weekStart ?? 1)
const reduceMotion = ref((pref.pref?.reduceMotion || 0) === 1)

const tzOptions = [
  { label: 'Asia/Shanghai', value: 'Asia/Shanghai' },
  { label: 'Asia/Hong_Kong', value: 'Asia/Hong_Kong' },
  { label: 'Asia/Tokyo', value: 'Asia/Tokyo' },
  { label: 'UTC', value: 'UTC' },
  { label: 'America/Los_Angeles', value: 'America/Los_Angeles' }
]

const weekStartOptions = [
  { label: '周一', value: 1 },
  { label: '周日', value: 0 }
]

const accentOptions = computed(() => {
  return ACCENT_PALETTE.map((x) => ({ label: x.label, value: x.key }))
})

async function load() {
  loading.value = true
  try {
    const p = await pref.load()
    accentKey.value = p.accentKey
    timezone.value = p.timezone
    weekStart.value = p.weekStart
    reduceMotion.value = (p.reduceMotion || 0) === 1
  } catch (e: any) {
    message.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await pref.update({
      accentKey: accentKey.value as any,
      timezone: timezone.value,
      weekStart: weekStart.value,
      reduceMotion: reduceMotion.value ? 1 : 0
    })
    message.success('已保存')
  } catch (e: any) {
    message.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const sessionsLoading = ref(false)
const sessions = ref<UserSessionItem[]>([])

async function loadSessions() {
  sessionsLoading.value = true
  try {
    const res = await authApi.sessions()
    sessions.value = res.sessions || []
  } catch (e: any) {
    message.error(e?.message || '加载失败')
  } finally {
    sessionsLoading.value = false
  }
}

async function revokeSession(id: number) {
  dialog.warning({
    title: '撤销会话',
    content: '确认撤销该设备会话？该设备将需要重新登录。',
    positiveText: '撤销',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await authApi.revokeSession(id)
        message.success('已撤销')
        await loadSessions()
      } catch (e: any) {
        message.error(e?.message || '操作失败')
      }
    }
  })
}

const pwOld = ref('')
const pwNew = ref('')
const pwNew2 = ref('')
const changing = ref(false)

async function changePassword() {
  if (!pwOld.value || !pwNew.value) return message.error('请输入旧密码与新密码')
  if (pwNew.value.length < 6) return message.error('新密码至少 6 位')
  if (pwNew.value !== pwNew2.value) return message.error('两次输入的新密码不一致')

  dialog.warning({
    title: '修改密码',
    content: '修改密码后会退出当前登录，需要重新登录。',
    positiveText: '确认修改',
    negativeText: '取消',
    onPositiveClick: async () => {
      changing.value = true
      try {
        await auth.changePassword({ oldPassword: pwOld.value, newPassword: pwNew.value })
      } catch (e: any) {
        message.error(e?.message || '修改失败')
      } finally {
        changing.value = false
      }
    }
  })
}

function logoutAll() {
  dialog.warning({
    title: '退出所有设备',
    content: '将撤销所有登录会话（包括当前设备）。确认继续？',
    positiveText: '退出全部',
    negativeText: '取消',
    onPositiveClick: async () => {
      await auth.logoutAllDevices()
    }
  })
}

onMounted(async () => {
  await load()
  await loadSessions()
})
</script>

<template>
  <div class="page lightPage">
    <div class="head">
      <div class="left">
        <h1 class="h1">设置</h1>
      </div>
      <div class="right">
        <n-button tertiary :loading="loading" @click="load">刷新</n-button>
        <n-button secondary class="accentBtn" :loading="saving" @click="save">保存</n-button>
      </div>
    </div>

    <div class="grid">
      <n-card class="panel lightPanel" title="外观与偏好">
        <n-spin :show="loading">
          <div class="form">
            <div class="row">
              <div class="k">
                <div class="t">强调色</div>
              </div>
              <div class="v">
                <n-select v-model:value="accentKey" :options="accentOptions" class="sel" />
              </div>
            </div>

            <div class="row">
              <div class="k">
                <div class="t">时区</div>
              </div>
              <div class="v">
                <n-select v-model:value="timezone" filterable :options="tzOptions" class="sel" />
              </div>
            </div>

            <div class="row">
              <div class="k">
                <div class="t">周起始日</div>
              </div>
              <div class="v">
                <n-select v-model:value="weekStart" :options="weekStartOptions" class="sel" />
              </div>
            </div>

            <div class="row">
              <div class="k">
                <div class="t">减少动效</div>
              </div>
              <div class="v">
                <n-checkbox v-model:checked="reduceMotion">开启</n-checkbox>
              </div>
            </div>
          </div>
        </n-spin>
      </n-card>

      <n-card class="panel lightPanel" title="通知设置">
        <notification-settings-panel />
      </n-card>

      <n-card class="panel lightPanel" title="安全与会话">
        <div class="sec">
          <div class="secTitle">修改密码</div>
          <div class="secGrid">
            <n-input v-model:value="pwOld" type="password" placeholder="旧密码" />
            <n-input v-model:value="pwNew" type="password" placeholder="新密码（至少 6 位）" />
            <n-input v-model:value="pwNew2" type="password" placeholder="重复新密码" />
          </div>
          <div class="secActions">
            <n-button secondary class="accentBtn" :loading="changing" @click="changePassword">修改密码</n-button>
            <n-button tertiary @click="logoutAll">退出所有设备</n-button>
          </div>
        </div>

        <div class="subtle-sep sep" />

        <div class="sec">
          <div class="secHead">
            <div class="secTitle">会话列表</div>
            <n-button tertiary size="small" :loading="sessionsLoading" @click="loadSessions">刷新</n-button>
          </div>
          <n-spin :show="sessionsLoading">
            <div v-if="!sessions.length" class="empty">
              <div class="emptyTitle">暂无记录</div>
            </div>
            <div v-else class="sessList">
              <div v-for="s in sessions" :key="s.id" class="sess">
                <div class="sessMain">
                  <div class="sessTop">
                    <div class="sessName">{{ s.deviceName || '未知设备' }}</div>
                    <div class="muted sessTime">{{ s.lastUseTime || s.createTime }}</div>
                  </div>
                  <div class="muted sessMeta">
                    <span v-if="s.ip">{{ s.ip }}</span>
                    <span v-if="s.userAgent"> · {{ s.userAgent }}</span>
                  </div>
                </div>
                <div class="sessAct">
                  <n-button tertiary size="small" @click="revokeSession(s.id)">撤销</n-button>
                </div>
              </div>
            </div>
          </n-spin>
        </div>
      </n-card>
    </div>
  </div>
</template>

<style scoped>
.lightPage {
  background: #ffffff;
  color: #0f172a;
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.sub {
  margin-top: 6px;
  font-size: 13px;
}

.right {
  display: inline-flex;
  gap: 10px;
}

.grid {
  margin-top: 14px;
  display: grid;
  gap: 14px;
}

.form {
  display: grid;
  gap: 14px;
}

.row {
  display: grid;
  grid-template-columns: 1fr 280px;
  gap: 14px;
  align-items: center;
}

.k {
  min-width: 0;
}

.t {
  font-weight: 760;
  letter-spacing: -0.1px;
}

.d {
  margin-top: 4px;
  font-size: 12px;
}

.sel {
  border-radius: 14px;
}

.accentBtn {
  background: rgba(20, 184, 166, 0.12) !important;
  border-color: rgba(20, 184, 166, 0.22) !important;
  color: rgba(15, 23, 42, 0.92) !important;
}

.sec {
  display: grid;
  gap: 10px;
}

.secHead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.secTitle {
  font-weight: 760;
  letter-spacing: -0.1px;
}

.secGrid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.secActions {
  display: flex;
  gap: 10px;
}

.sep {
  margin: 14px 0;
}

.sessList {
  display: grid;
  gap: 10px;
}

.sess {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.02);
}

.sessTop {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.sessName {
  font-weight: 740;
}

.sessTime {
  font-size: 12px;
  white-space: nowrap;
}

.sessMeta {
  margin-top: 4px;
  font-size: 12px;
}

.empty {
  padding: 18px 6px;
  text-align: center;
}

.emptyTitle {
  font-weight: 760;
}

.emptyDesc {
  margin-top: 6px;
  font-size: 12px;
}

@media (max-width: 980px) {
  .row {
    grid-template-columns: 1fr;
  }
  .secGrid {
    grid-template-columns: 1fr;
  }
}
</style>
