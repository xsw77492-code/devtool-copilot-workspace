<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NDropdown, NForm, NFormItem, NInput, NModal, NTooltip, useMessage } from 'naive-ui'
import AiBuddyButton from './AiBuddyButton.vue'
import ProfileModal from './ProfileModal.vue'
import { useAuthStore } from '../stores/auth'
import { useNotificationStore } from '../stores/notification'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const ns = useNotificationStore()
const message = useMessage()

const showAiBuddy = computed(() => route.name !== 'ai-chat')
const profileOpen = ref(false)
const displayName = computed(() => auth.me?.nickname || auth.me?.username || 'User')
const pageTitle = computed(() => {
  const titleMap: Record<string, string> = {
    workspace: '工作台',
    'collab-center': '团队协作',
    board: '任务看板',
    inbox: '收件箱',
    chat: '消息',
    notifications: '通知',
    settings: '设置',
    'task-search': '搜索',
    'project-detail': '项目详情',
    'task-detail': '任务详情',
    'ai-chat': 'AI 助手',
    'project-audit': '审计日志',
    'project-activity': '项目动态',
    'project-members': '成员管理',
    'lifecycle-center': '生命周期',
    'task-lifecycle': '生命周期',
    'inbox': '收件箱'
  }
  return titleMap[String(route.name || '')] || ''
})
const initials = computed(() => String(auth.me?.username || 'U').trim().slice(0, 1).toUpperCase())

const searchText = ref('')
const authModalOpen = ref(false)
const oldPassword = ref('')
const newPassword = ref('')
const pwdLoading = ref(false)

const options = computed(() => [
  { key: 'profile', label: '个人资料' },
  { key: 'changePwd', label: '修改密码' },
  { key: 'logoutAll', label: '退出所有设备' },
  { key: 'logout', label: '退出登录' }
])

async function onSelect(key: string) {
  if (key === 'profile') {
    profileOpen.value = true
    return
  }
  if (key === 'logout') {
    await auth.logout()
    router.push({ name: 'login' })
    return
  }
  if (key === 'logoutAll') {
    await auth.logoutAllDevices()
    router.push({ name: 'login' })
    return
  }
  if (key === 'changePwd') {
    oldPassword.value = ''
    newPassword.value = ''
    authModalOpen.value = true
  }
}

async function submitChangePwd() {
  if (!oldPassword.value.trim() || !newPassword.value.trim()) {
    message.error('请填写完整')
    return
  }
  pwdLoading.value = true
  try {
    await auth.changePassword({ oldPassword: oldPassword.value, newPassword: newPassword.value })
    authModalOpen.value = false
    router.push({ name: 'login' })
  } catch (e: any) {
    message.error(e?.message || '修改失败')
  } finally {
    pwdLoading.value = false
  }
}

function goSearch() {
  const text = searchText.value.trim()
  const query: any = route.name === 'task-search' ? { ...route.query } : {}
  query.q = text || undefined
  query.page = undefined
  router.push({ name: 'task-search', query })
}
</script>

<template>
  <div class="wrap">
    <div class="left">
      <div class="page-title">{{ pageTitle }}</div>
    </div>

    <div class="mid">
      <div class="mid-inner">
        <div class="search-box">
          <svg class="search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" stroke="currentColor" stroke-width="1.6" />
            <path d="M16.2 16.2 21 21" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
          </svg>
          <input v-model="searchText" class="input" placeholder="搜索任务" @keydown.enter.prevent="goSearch" />
        </div>
        <ai-buddy-button v-if="showAiBuddy" />
      </div>
    </div>

    <div class="right">
      <n-tooltip trigger="hover">
        <template #trigger>
          <button class="icon-button notification-button" type="button" aria-label="通知" @click="router.push({ name: 'notifications' })">
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M18 10a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" />
              <path d="M10 22h4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            </svg>
            <span v-if="ns.unreadCount > 0" class="dot">{{ ns.unreadCount > 99 ? '99+' : ns.unreadCount }}</span>
          </button>
        </template>
        通知
      </n-tooltip>

      <n-tooltip trigger="hover">
        <template #trigger>
          <button class="icon-button" type="button" aria-label="设置" @click="router.push({ name: 'settings' })">
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M12 15.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4Z" stroke="currentColor" stroke-width="1.6" />
              <path d="M19.4 13.4v-2.8l-2.1-.7a7.6 7.6 0 0 0-.8-1.9l1-2-2-2-2 1a7.6 7.6 0 0 0-1.9-.8L10.6 2.6H7.8l-.7 2.1a7.6 7.6 0 0 0-1.9.8l-2-1-2 2 1 2a7.6 7.6 0 0 0-.8 1.9l-2.1.7v2.8l2.1.7c.18.66.45 1.3.8 1.9l-1 2 2 2 2-1c.6.35 1.24.62 1.9.8l.7 2.1h2.8l.7-2.1c.66-.18 1.3-.45 1.9-.8l2 1 2-2-1-2c.35-.6.62-1.24.8-1.9l2.1-.7Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" />
            </svg>
          </button>
        </template>
        设置
      </n-tooltip>

      <n-dropdown :options="options" placement="bottom-end" @select="onSelect">
        <button class="user-menu" type="button">
          <span class="avatar" aria-hidden="true">
            <img v-if="auth.me?.avatarUrl" :src="auth.me.avatarUrl" alt="" />
            <template v-else>{{ initials }}</template>
            <span class="avatar-status" :class="'st-' + (auth.me?.status || 'ONLINE')" />
          </span>
          <span class="user-name">{{ displayName }}</span>
        </button>
      </n-dropdown>
    </div>
  </div>

  <n-modal v-model:show="authModalOpen" preset="card" title="修改密码" class="pwd-modal">
    <n-form>
      <n-form-item label="原密码">
        <n-input v-model:value="oldPassword" type="password" placeholder="请输入原密码" />
      </n-form-item>
      <n-form-item label="新密码">
        <n-input v-model:value="newPassword" type="password" placeholder="至少12位，含大小写、数字、特殊字符" />
      </n-form-item>
      <n-button type="primary" block :loading="pwdLoading" @click="submitChangePwd">确认修改</n-button>
    </n-form>
  </n-modal>

  <profile-modal v-model:show="profileOpen" />
</template>

<style scoped>
.wrap {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 22px 0 24px;
}
.left {
  display: flex;
  align-items: center;
  min-width: 220px;
}
.page-title {
  color: #1f2329;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.02em;
}
.mid {
  flex: 1;
  display: flex;
  justify-content: center;
  min-width: 0;
}
.mid-inner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-width: 0;
}
.search-box {
  width: min(460px, 38vw);
  min-width: 240px;
  height: 32px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  border: 1px solid #e7e9ed;
  border-radius: 6px;
  background: #f7f8fa;
}
.search-icon {
  flex: 0 0 auto;
  color: #8b9199;
}
.input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: #3c4149;
  font-size: 12px;
}
.input::placeholder {
  color: #9197a1;
}
.right {
  display: flex;
  align-items: center;
  gap: 6px;
}
.icon-button {
  width: 32px;
  height: 32px;
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #646a73;
  cursor: pointer;
}
.icon-button:hover {
  border-color: #e4e6eb;
  background: #f7f8fa;
  color: #1f2329;
}
.dot {
  min-width: 15px;
  height: 15px;
  position: absolute;
  top: -3px;
  right: -4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
  border-radius: 8px;
  background: #e5484d;
  color: #fff;
  font-size: 10px;
  font-weight: 650;
}
.user-menu {
  height: 32px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0 6px 0 3px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #3c4149;
  cursor: pointer;
}
.user-menu:hover {
  border-color: #e4e6eb;
  background: #f7f8fa;
}
.avatar {
  position: relative;
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #ffffff;
  border: 1px solid #e3e5e8;
  color: #303540;
  font-size: 11px;
  font-weight: 650;
  overflow: hidden;
  box-sizing: border-box;
}
.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-status {
  position: absolute;
  right: -1px;
  bottom: -1px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 2px solid #fff;
  background: #0e9f6e;
}
.avatar-status.st-AWAY {
  background: #d97706;
}
.avatar-status.st-BUSY {
  background: #db2777;
}
.avatar-status.st-OFFLINE {
  background: #c2c8d0;
}
.user-name {
  max-width: 112px;
  overflow: hidden;
  font-size: 12px;
  font-weight: 550;
  text-overflow: ellipsis;
  white-space: nowrap;
}
:global(.pwd-modal) {
  width: min(420px, calc(100vw - 28px));
}
@media (max-width: 900px) {
  .wrap {
    padding: 0 14px;
  }
  .page-caption,
  .user-name {
    display: none;
  }
  .left {
    min-width: 106px;
  }
  .search-box {
    width: min(340px, 40vw);
    min-width: 160px;
  }
}
</style>
