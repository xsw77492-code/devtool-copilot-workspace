<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NConfigProvider, NSpin, useMessage } from 'naive-ui'
import { projectCollabApi } from '../api/projectCollab'

const route = useRoute()
const router = useRouter()
const message = useMessage()

const token = computed(() => String(route.query.token || ''))
const loading = ref(false)

const themeOverrides = {
  common: {
    primaryColor: '#4f46e5',
    primaryColorHover: '#4338ca',
    primaryColorPressed: '#3730a3',
    borderRadius: '14px'
  }
}

async function accept() {
  if (!token.value) {
    message.error('缺少 token')
    return
  }
  loading.value = true
  try {
    const projectId = await projectCollabApi.acceptInvite(token.value)
    message.success('已加入项目')
    router.replace({ name: 'project-detail', params: { id: projectId } })
  } catch (e: any) {
    message.error(e?.message || '加入失败')
  } finally {
    loading.value = false
  }
}

async function reject() {
  if (!token.value) {
    message.error('缺少 token')
    return
  }
  loading.value = true
  try {
    await projectCollabApi.rejectInvite(token.value)
    message.success('已拒绝邀请')
    router.replace({ name: 'dashboard' })
  } catch (e: any) {
    message.error(e?.message || '操作失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="wrap">
    <n-config-provider :theme="null" :theme-overrides="themeOverrides">
      <div class="shell">
        <div class="card">
          <div class="title">项目邀请</div>
          <div class="desc muted">你已登录后才能接受邀请，并且账号邮箱需要与邀请邮箱一致。</div>

          <n-spin :show="loading">
            <div class="actions">
              <n-button type="primary" size="large" @click="accept">接受并加入</n-button>
              <n-button tertiary size="large" @click="reject">拒绝</n-button>
            </div>
          </n-spin>
        </div>
      </div>
    </n-config-provider>
  </div>
</template>

<style scoped>
.wrap {
  min-height: 100vh;
  display: grid;
  place-items: center;
  color: rgba(255, 255, 255, 0.92);
  background:
    radial-gradient(1100px 520px at 18% 26%, rgba(6, 182, 212, 0.22), transparent 60%),
    radial-gradient(900px 460px at 74% 18%, rgba(34, 211, 238, 0.18), transparent 58%),
    linear-gradient(180deg, #0b1220, #0b1220);
}

.shell {
  width: min(620px, calc(100vw - 32px));
}

.card {
  padding: 26px 26px 22px;
  border-radius: 22px;
  background: rgba(10, 15, 24, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.14);
  box-shadow: 0 30px 90px rgba(0, 0, 0, 0.42);
}

.title {
  font-weight: 850;
  letter-spacing: -0.4px;
  font-size: 22px;
}

.desc {
  margin-top: 10px;
  line-height: 1.7;
}

.actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 18px;
}
</style>
