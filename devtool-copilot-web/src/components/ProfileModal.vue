<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NButton, NInput, NModal, NSelect, useMessage } from 'naive-ui'
import { profileApi, type ProfileResponse, type UserStatus } from '../api/profile'
import { useAuthStore } from '../stores/auth'

const props = defineProps<{ show: boolean }>()
const emit = defineEmits<{ (e: 'update:show', v: boolean): void; (e: 'saved'): void }>()

const message = useMessage()
const auth = useAuthStore()

const profile = ref<ProfileResponse | null>(null)
const nickname = ref('')
const signature = ref('')
const status = ref<UserStatus>('ONLINE')
const avatarUrl = ref<string | null>(null)
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)

const initials = computed(() => String(profile.value?.nickname || profile.value?.username || 'U').trim().slice(0, 1).toUpperCase())

const statusOptions = [
  { label: '在线', value: 'ONLINE' },
  { label: '离开', value: 'AWAY' },
  { label: '忙碌', value: 'BUSY' },
  { label: '离线', value: 'OFFLINE' }
]

watch(
  () => props.show,
  (v) => {
    if (!v) return
    load()
  }
)

async function load() {
  loading.value = true
  try {
    const p = await profileApi.getProfile()
    profile.value = p
    nickname.value = p.nickname || ''
    signature.value = p.signature || ''
    status.value = p.status || 'ONLINE'
    avatarUrl.value = p.avatarUrl || null
  } catch (e: any) {
    message.error(e.message || '加载资料失败')
  } finally {
    loading.value = false
  }
}

async function onPickAvatar(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  uploading.value = true
  try {
    avatarUrl.value = await profileApi.uploadAvatar(file)
    message.success('头像已更新，保存后生效')
  } catch (e: any) {
    message.error(e.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await profileApi.updateProfile({
      nickname: nickname.value || null,
      signature: signature.value || null,
      status: status.value
    })
    // 同步全局 me
    if (auth.me && auth.token && auth.refreshToken) {
      const me = {
        ...auth.me,
        nickname: nickname.value || null,
        signature: signature.value || null,
        status: status.value,
        avatarUrl: avatarUrl.value
      }
      auth.setSession(auth.token, auth.refreshToken, me)
    }
    message.success('已保存')
    emit('saved')
    emit('update:show', false)
  } catch (e: any) {
    message.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <n-modal
    :show="props.show"
    preset="card"
    title="个人资料"
    class="profile-modal"
    :style="{ width: '420px' }"
    :bordered="false"
    @update:show="(v: boolean) => emit('update:show', v)"
  >
    <div class="profile-body">
      <div class="avatar-section">
        <span class="big-avatar">
          <img v-if="avatarUrl" :src="avatarUrl" alt="" />
          <template v-else>{{ initials }}</template>
          <span class="avatar-edit" @click="avatarInput?.click()">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
              <path d="M4 20h16M4 20l3.5-11 5 6 2-2.5 5.5 7.5M4 20h16" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </span>
        </span>
        <div class="avatar-hint">
          <span>{{ profile?.username || '' }}</span>
          <span class="muted">支持 jpg / png / gif / webp，最大 5MB</span>
        </div>
        <input ref="avatarInput" type="file" accept="image/*" hidden @change="onPickAvatar" />
      </div>

      <div class="field">
        <label class="field-label">昵称</label>
        <n-input v-model:value="nickname" placeholder="展示给其他成员的名称" :maxlength="32" />
      </div>

      <div class="field">
        <label class="field-label">状态</label>
        <n-select v-model:value="status" :options="statusOptions" />
      </div>

      <div class="field">
        <label class="field-label">签名</label>
        <n-input
          v-model:value="signature"
          type="textarea"
          placeholder="一句话介绍自己"
          :maxlength="120"
          :rows="2"
        />
      </div>

      <div class="profile-foot">
        <n-button quaternary @click="emit('update:show', false)">取消</n-button>
        <n-button type="primary" :loading="saving || uploading" :disabled="loading" @click="save">保存</n-button>
      </div>
    </div>
  </n-modal>
</template>

<style scoped>
.profile-body {
  display: grid;
  gap: 14px;
}
.avatar-section {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 4px;
}
.big-avatar {
  position: relative;
  width: 64px;
  height: 64px;
  flex: 0 0 64px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px solid #e3e5e8;
  color: #303540;
  font-size: 22px;
  font-weight: 700;
  display: grid;
  place-items: center;
  overflow: hidden;
  box-sizing: border-box;
}
.big-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-edit {
  position: absolute;
  right: -4px;
  bottom: -4px;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #fff;
  border: 1px solid #e2e6ea;
  color: #4b5563;
  display: grid;
  place-items: center;
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}
.avatar-edit:hover {
  color: #1769e0;
  border-color: #1769e0;
}
.avatar-hint {
  display: grid;
  gap: 3px;
  font-size: 13px;
  font-weight: 600;
  color: #1f2329;
}
.muted {
  font-size: 11px;
  font-weight: 400;
  color: #98a0aa;
}
.field {
  display: grid;
  gap: 6px;
}
.field-label {
  font-size: 12px;
  font-weight: 600;
  color: #4b5563;
}
.profile-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 4px;
}
</style>
