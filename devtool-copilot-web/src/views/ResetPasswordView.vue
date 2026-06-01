<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NButton, NConfigProvider, NForm, NFormItem, NInput, useMessage } from 'naive-ui'
import { authApi } from '../api/auth'

const router = useRouter()
const route = useRoute()
const message = useMessage()

const token = computed(() => String(route.query.token || ''))
const newPassword = ref('')
const confirmPassword = ref('')
const loading = ref(false)

const logoUrl = new URL('../assets/logo.png.png', import.meta.url).href
const themeOverrides = {
  common: {
    primaryColor: '#4f46e5',
    primaryColorHover: '#4338ca',
    primaryColorPressed: '#3730a3',
    borderRadius: '14px'
  }
}

async function submit() {
  if (!token.value) {
    message.error('缺少 token')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    message.error('两次密码不一致')
    return
  }
  loading.value = true
  try {
    await authApi.passwordResetConfirm({ token: token.value, newPassword: newPassword.value })
    message.success('密码已重置，请重新登录')
    router.replace({ name: 'login' })
  } catch (e: any) {
    message.error(e?.message || '重置失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="wrap">
    <n-config-provider :theme="null" :theme-overrides="themeOverrides">
      <div class="page shell">
        <div class="content">
          <div class="hero">
            <div class="heroTop">
              <img class="logo" :src="logoUrl" alt="DevTool Copilot" />
              <div class="heroBrand">
                <div class="heroName">DevTool Copilot</div>
                <div class="heroSub">智能开发者工作台</div>
              </div>
            </div>
            <div class="heroTitle">重置密码</div>
            <div class="heroDesc">设置一个新的强密码（12 位以上，包含大小写、数字与特殊字符）。</div>
          </div>

          <div class="authCol">
            <div class="authCard">
              <div class="authTitle">设置新密码</div>
              <div class="authSub">完成后需要重新登录</div>

              <n-form>
                <n-form-item label="新密码">
                  <n-input v-model:value="newPassword" size="large" type="password" placeholder="输入新密码" />
                </n-form-item>
                <n-form-item label="确认密码">
                  <n-input v-model:value="confirmPassword" size="large" type="password" placeholder="再次输入新密码" />
                </n-form-item>
                <n-button type="primary" size="large" block :loading="loading" attr-type="button" @click.prevent="submit">
                  重置密码
                </n-button>
              </n-form>

              <div class="authLinks">
                <n-button text size="small" class="linkBtn" @click="router.push({ name: 'login' })">返回登录</n-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </n-config-provider>
  </div>
</template>

<style scoped>
.wrap {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  color: #0b1220;
  background:
    radial-gradient(1100px 520px at 18% 26%, rgba(6, 182, 212, 0.24), transparent 60%),
    radial-gradient(900px 460px at 74% 18%, rgba(34, 211, 238, 0.22), transparent 58%),
    radial-gradient(1200px 760px at 50% 100%, rgba(20, 184, 166, 0.14), transparent 55%),
    linear-gradient(180deg, #f7fbff, #eef5ff 48%, #f8fafc);
}

.wrap::before {
  content: '';
  position: absolute;
  inset: -2px;
  background:
    linear-gradient(rgba(15, 23, 42, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15, 23, 42, 0.04) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(circle at 50% 24%, rgba(0, 0, 0, 0.92), transparent 66%);
  pointer-events: none;
}

.shell {
  padding: 72px 0;
  max-width: 1440px;
}

.content {
  display: grid;
  grid-template-columns: 1.45fr 1fr;
  gap: 56px;
  align-items: center;
  padding: 0 44px;
  min-height: calc(100vh - 144px);
}

.hero {
  position: relative;
  padding: 26px 0;
}

.heroTop {
  display: flex;
  gap: 18px;
  align-items: center;
}

.logo {
  height: 88px;
  width: auto;
  max-width: 520px;
  object-fit: contain;
  filter: drop-shadow(0 18px 44px rgba(20, 184, 166, 0.18));
}

.heroName {
  font-size: 20px;
  letter-spacing: -0.4px;
  font-weight: 850;
}

.heroSub {
  font-size: 14px;
  margin-top: 2px;
  color: rgba(15, 23, 42, 0.62);
  font-weight: 650;
}

.heroTitle {
  margin-top: 26px;
  font-size: 42px;
  line-height: 1.06;
  letter-spacing: -1px;
  font-weight: 850;
  background: linear-gradient(90deg, #0f172a, #1e293b 35%, #1d4ed8);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.heroDesc {
  margin-top: 14px;
  font-size: 16px;
  line-height: 1.75;
  color: rgba(15, 23, 42, 0.68);
  max-width: 760px;
}

.authCol {
  display: flex;
  justify-content: center;
}

.authCard {
  width: min(520px, calc(100vw - 44px));
  padding: 26px 26px 22px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid rgba(15, 23, 42, 0.1);
  box-shadow: 0 28px 90px rgba(15, 23, 42, 0.18);
  backdrop-filter: blur(10px);
}

.authTitle {
  font-weight: 850;
  letter-spacing: -0.3px;
  font-size: 20px;
}

.authSub {
  margin-top: 6px;
  font-size: 14px;
  color: rgba(15, 23, 42, 0.6);
  font-weight: 600;
}

.authLinks {
  margin-top: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: rgba(15, 23, 42, 0.62);
}

.linkBtn {
  font-weight: 650;
  color: rgba(15, 23, 42, 0.72);
}

@media (max-width: 980px) {
  .content {
    grid-template-columns: 1fr;
    gap: 22px;
    padding: 0 18px;
    min-height: unset;
  }
  .shell {
    padding: 28px 0 40px;
  }
  .heroTitle {
    font-size: 30px;
  }
  .logo {
    height: 72px;
  }
}

:deep(.n-form-item-label__text) {
  color: rgba(15, 23, 42, 0.72);
  font-weight: 650;
}

:deep(.n-input) {
  border-radius: 14px;
}

:deep(.n-button) {
  border-radius: 14px;
}
</style>
