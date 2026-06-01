<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NDropdown, NForm, NFormItem, NInput, NModal, useMessage } from 'naive-ui'
import AiBuddyButton from './AiBuddyButton.vue'
import { useAuthStore } from '../stores/auth'
import { useNotificationStore } from '../stores/notification'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const ns = useNotificationStore()
const message = useMessage()

const showAiBuddy = computed(() => route.name !== 'ai-chat')

const authModalOpen = ref(false)
const oldPassword = ref('')
const newPassword = ref('')
const pwdLoading = ref(false)

const options = computed(() => [
  { key: 'changePwd', label: '修改密码' },
  { key: 'logoutAll', label: '退出所有设备' },
  { key: 'logout', label: '退出登录' }
])

async function onSelect(key: string) {
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
</script>

<template>
  <div class="wrap">
    <div class="left" />
    <div class="mid">
      <ai-buddy-button v-if="showAiBuddy" />
    </div>
    <div class="right">
      <button class="pill notify" type="button" @click="router.push({ name: 'notifications' })">
        <span>通知</span>
        <span v-if="ns.unreadCount > 0" class="dot">{{ ns.unreadCount > 99 ? '99+' : ns.unreadCount }}</span>
      </button>
      <button class="pill iconBtn" type="button" aria-label="设置" @click="router.push({ name: 'settings' })">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path
            d="M12 15.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4Z"
            stroke="currentColor"
            stroke-width="1.6"
          />
          <path
            d="M19.4 13.4v-2.8l-2.1-.7a7.6 7.6 0 0 0-.8-1.9l1-2-2-2-2 1a7.6 7.6 0 0 0-1.9-.8L10.6 2.6H7.8l-.7 2.1a7.6 7.6 0 0 0-1.9.8l-2-1-2 2 1 2a7.6 7.6 0 0 0-.8 1.9l-2.1.7v2.8l2.1.7c.18.66.45 1.3.8 1.9l-1 2 2 2 2-1c.6.35 1.24.62 1.9.8l.7 2.1h2.8l.7-2.1c.66-.18 1.3-.45 1.9-.8l2 1 2-2-1-2c.35-.6.62-1.24.8-1.9l2.1-.7Z"
            stroke="currentColor"
            stroke-width="1.6"
            stroke-linejoin="round"
          />
        </svg>
      </button>
      <n-dropdown :options="options" placement="bottom-end" @select="onSelect">
        <n-button text size="small" class="pill user">{{ auth.me?.username || 'User' }}</n-button>
      </n-dropdown>
    </div>
  </div>

  <n-modal v-model:show="authModalOpen" preset="card" title="修改密码" class="pwdModal">
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
</template>

<style scoped>
.wrap {
  width: 100%;
  padding: 0 22px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.left {
  min-width: 8px;
}
.mid {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  padding-top: 8px;
}
.right {
  display: flex;
  gap: 8px;
  align-items: center;
}
.pill {
  height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid var(--stroke2);
  font-size: 13px;
  font-weight: 850;
  letter-spacing: -0.2px;
}
.pill.user {
  background: rgba(20, 184, 166, 0.10);
  border-color: rgba(20, 184, 166, 0.22);
}

.pill.notify {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  font-weight: 750;
  color: rgba(15, 23, 42, 0.80);
}

.pill.iconBtn {
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.dot {
  height: 18px;
  min-width: 18px;
  padding: 0 6px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 900;
  background: rgba(20, 184, 166, 0.12);
  border: 1px solid rgba(20, 184, 166, 0.22);
  color: rgba(15, 23, 42, 0.88);
}

:global(.pwdModal) {
  width: min(420px, calc(100vw - 28px));
}
</style>
