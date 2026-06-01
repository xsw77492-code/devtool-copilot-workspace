<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NConfigProvider, NForm, NFormItem, NInput, useMessage } from 'naive-ui'
import { authApi } from '../api/auth'

const router = useRouter()
const message = useMessage()

const email = ref('')
const loading = ref(false)

const themeOverrides = {
  common: {
    primaryColor: '#0f172a',
    primaryColorHover: '#111827',
    primaryColorPressed: '#0b1220',
    borderRadius: '16px'
  }
}

async function submit() {
  loading.value = true
  try {
    await authApi.passwordResetRequest({ email: email.value })
    message.success('如果账号存在，将发送重置邮件，请检查收件箱')
    router.replace({ name: 'login' })
  } catch (e: any) {
    message.error(e?.message || '发送失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="wrap">
    <div class="bgBlob bg1" aria-hidden="true" />
    <div class="bgBlob bg2" aria-hidden="true" />
    <n-config-provider :theme="null" :theme-overrides="themeOverrides">
      <div class="shell">
        <main class="main">
          <div class="authCard">
            <div class="authShell">
              <div class="authInner">
                <div class="authTitle">找回密码</div>
                <div class="authSub">输入注册邮箱，我们会发送密码重置链接。</div>

                <div class="authSepRow">
                  <div class="authSepLead" />
                  <div class="authSep" />
                </div>

                <n-form class="authForm">
                  <n-form-item label="邮箱">
                    <n-input v-model:value="email" size="large" placeholder="输入注册邮箱" />
                  </n-form-item>

                  <div class="authLinksRow">
                    <n-button text size="small" class="authTextBtn" @click="router.push({ name: 'login' })">返回登录</n-button>
                  </div>

                  <div class="authActions">
                    <n-button type="primary" size="large" block class="authPrimary" :loading="loading" attr-type="button" @click.prevent="submit">
                      发送邮件
                    </n-button>
                  </div>
                </n-form>
              </div>
            </div>
          </div>
        </main>
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
    radial-gradient(1200px 520px at 50% -10%, rgba(15, 23, 42, 0.06), transparent 62%),
    radial-gradient(1100px 680px at 12% 92%, rgba(15, 23, 42, 0.04), transparent 60%),
    linear-gradient(180deg, #ffffff, #fafafa 55%, #ffffff);
}

.wrap::before {
  content: '';
  position: absolute;
  inset: -2px;
  background:
    linear-gradient(rgba(15, 23, 42, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15, 23, 42, 0.04) 1px, transparent 1px);
  background-size: 64px 64px;
  mask-image: radial-gradient(circle at 50% 22%, rgba(0, 0, 0, 0.78), transparent 68%);
  pointer-events: none;
}

.bgBlob {
  position: absolute;
  width: 760px;
  height: 560px;
  border-radius: 999px;
  filter: blur(78px);
  opacity: 0.14;
  pointer-events: none;
  background: radial-gradient(circle at 30% 30%, rgba(20, 184, 166, 0.22), transparent 64%),
    radial-gradient(circle at 70% 70%, rgba(6, 182, 212, 0.14), transparent 60%);
}

.bg1 {
  top: -220px;
  right: -260px;
  animation: drift1 28s ease-in-out infinite;
}

.bg2 {
  bottom: -260px;
  left: -280px;
  animation: drift2 32s ease-in-out infinite;
}

.shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.main {
  flex: 1;
  display: grid;
  place-items: center;
  padding: 44px 18px 28px;
}

.authCard {
  width: min(568px, calc(100vw - 28px));
}

.authShell {
  position: relative;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.08), 0 6px 18px rgba(15, 23, 42, 0.035), 0 0 0 1px rgba(255, 255, 255, 0.45);
  backdrop-filter: blur(18px);
}

.authShell::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 28px;
  pointer-events: none;
  background:
    radial-gradient(320px 150px at 100% 0%, rgba(15, 23, 42, 0.03), transparent 60%),
    radial-gradient(240px 160px at 0% 100%, rgba(15, 23, 42, 0.025), transparent 64%);
}

.authInner {
  position: relative;
  padding: 30px 30px 30px;
}

.authTitle {
  font-size: 38px;
  line-height: 1.04;
  font-weight: 950;
  letter-spacing: -0.9px;
  color: #0f172a;
}

.authSub {
  margin-top: 12px;
  font-size: 13px;
  line-height: 1.7;
  font-weight: 450;
  letter-spacing: -0.06px;
  color: rgba(15, 23, 42, 0.34);
}

.authSepRow {
  margin-top: 18px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.authSepLead {
  height: 3px;
  width: 32px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(15, 23, 42, 0.86), rgba(15, 23, 42, 0.42));
}

.authSep {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, rgba(15, 23, 42, 0.03), rgba(15, 23, 42, 0.11), rgba(15, 23, 42, 0.03));
}

.authForm {
  margin-top: 28px;
}

.authLinksRow {
  display: flex;
  align-items: flex-start;
  gap: 18px;
  margin-top: 10px;
}

.authActions {
  margin-top: 26px;
}

@media (max-width: 980px) {
  .main {
    padding: 32px 18px 24px;
  }
  .authInner {
    padding: 24px 20px 22px;
  }
  .authTitle {
    font-size: 32px;
  }
}

:deep(.n-form-item-label__text) {
  color: rgba(15, 23, 42, 0.88);
  font-weight: 700;
  letter-spacing: -0.08px;
}

:deep(.n-input) {
  border-radius: 14px;
  --n-text-color: rgba(15, 23, 42, 0.9);
  --n-placeholder-color: rgba(15, 23, 42, 0.3);
  --n-border: 1px solid rgba(15, 23, 42, 0.12);
  --n-border-hover: 1px solid rgba(15, 23, 42, 0.12);
  --n-border-focus: 1px solid rgba(15, 23, 42, 0.28);
  --n-box-shadow-focus: 0 0 0 4px rgba(15, 23, 42, 0.045);
  --n-color: rgba(255, 255, 255, 0.86);
  --n-color-focus: rgba(255, 255, 255, 0.96);
  --n-height: 52px;
}

:deep(.n-input .n-input-wrapper) {
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease,
    background-color 180ms ease;
}

:deep(.n-button) {
  border-radius: 18px;
}

.authPrimary {
  --n-color: #0f172a;
  --n-color-hover: #0b1220;
  --n-color-pressed: #0b1220;
  --n-border: 1px solid rgba(15, 23, 42, 0.1);
  width: 100%;
  font-weight: 800;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.14);
  transition:
    box-shadow 160ms ease,
    background-color 160ms ease;
}

.authPrimary:hover {
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.16);
}

.authTextBtn {
  color: rgba(15, 23, 42, 0.62);
  font-weight: 700;
  font-size: 13px;
  padding-left: 0;
  justify-content: flex-start;
}

@keyframes drift1 {
  0% {
    transform: translate3d(0, 0, 0) scale(1);
  }
  50% {
    transform: translate3d(-22px, 12px, 0) scale(1.02);
  }
  100% {
    transform: translate3d(0, 0, 0) scale(1);
  }
}

@keyframes drift2 {
  0% {
    transform: translate3d(0, 0, 0) scale(1);
  }
  50% {
    transform: translate3d(18px, -14px, 0) scale(1.02);
  }
  100% {
    transform: translate3d(0, 0, 0) scale(1);
  }
}
</style>
