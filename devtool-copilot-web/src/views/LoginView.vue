<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NButton, NConfigProvider, NForm, NFormItem, NInput, NModal, useMessage } from 'naive-ui'
import { authApi } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const message = useMessage()

const showAuth = ref(false)
const mode = ref<'login' | 'register'>('login')

const username = ref('')
const password = ref('')
const email = ref('')
const confirmPassword = ref('')
const loading = ref(false)

const themeOverrides = {
  common: {
    primaryColor: '#0f172a',
    primaryColorHover: '#111827',
    primaryColorPressed: '#0b1220',
    borderRadius: '14px'
  }
}

const redirect = computed(() => (route.query.redirect as string | undefined) || '/dashboard')

function openAuth(next: 'login' | 'register' = 'login') {
  mode.value = next
  showAuth.value = true
}

function clickFooterLink() {
  message.info('即将上线')
}

async function submitLogin() {
  loading.value = true
  try {
    await auth.login({ username: username.value, password: password.value })
    router.replace(redirect.value)
  } catch (e: any) {
    message.error(e?.message || '登录失败')
  } finally {
    loading.value = false
  }
}

async function submitRegister() {
  if (password.value !== confirmPassword.value) {
    message.error('两次密码不一致')
    return
  }
  loading.value = true
  try {
    await authApi.register({ username: username.value, email: email.value, password: password.value })
    await auth.login({ username: username.value, password: password.value })
    router.replace(redirect.value)
  } catch (e: any) {
    message.error(e?.message || '注册失败')
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
          <div class="center">
            <h1 class="title">DevTool Copilot</h1>
            <div class="subBrand">AI 工作台</div>
            <div class="tagline">把需求跑成可追踪的工程闭环</div>
            <div class="desc">任务、协作、实时同步、通知与数据面板，一站式把团队交付跑通。</div>
            <div class="micro">
              <span class="chip">实时协作</span>
              <span class="chip">权限与审计</span>
              <span class="chip">周报导出</span>
            </div>
            <div class="actions">
              <n-button type="primary" size="large" class="cta" @click="openAuth('login')">进入工作台</n-button>
              <n-button tertiary size="large" class="ghost" @click="openAuth('register')">创建账号</n-button>
            </div>
          </div>
        </main>

        <div class="foot" aria-hidden="false">
          <div class="footLeft">
            <button class="footLink" type="button" @click="clickFooterLink">隐私</button>
            <span class="sep" aria-hidden="true" />
            <button class="footLink" type="button" @click="clickFooterLink">条款</button>
          </div>
          <div class="footRight">
            <button class="footLink" type="button" @click="clickFooterLink">帮助</button>
            <span class="sep" aria-hidden="true" />
            <button class="footLink" type="button" @click="clickFooterLink">状态</button>
          </div>
        </div>

        <n-modal
          v-model:show="showAuth"
          :mask-closable="true"
          preset="card"
          class="authModal"
          :bordered="false"
          :style="{ width: 'min(568px, calc(100vw - 28px))' }"
        >
          <div class="authShell">
            <div class="authInner">
              <div class="authHeading">
                <div :class="['authTitle', { register: mode === 'register' }]">{{ mode === 'login' ? '登录' : '创建账号' }}</div>
                <div :class="['authSubtitle', { register: mode === 'register' }]">
                  <template v-if="mode === 'login'">使用你的账号继续进入工作台</template>
                  <template v-else>创建账号，加入协作交付与任务闭环</template>
                </div>
              </div>

              <Transition name="authSwap" mode="out-in">
                <n-form v-if="mode === 'login'" key="login" class="authForm">
                  <n-form-item label="用户名">
                    <n-input v-model:value="username" size="large" placeholder="输入用户名" />
                  </n-form-item>
                  <n-form-item label="密码">
                    <n-input v-model:value="password" size="large" type="password" placeholder="输入密码" />
                  </n-form-item>

                  <div class="authLinksRow">
                    <n-button text size="small" class="authTextBtn" @click="router.push({ name: 'forgot-password' })">忘记密码？</n-button>
                    <n-button text size="small" class="authTextBtn" @click="mode = 'register'">创建账号</n-button>
                  </div>

                  <div class="authActions">
                    <n-button type="primary" size="large" block class="authPrimary" :loading="loading" attr-type="button" @click.prevent="submitLogin">
                      下一步
                    </n-button>
                  </div>
                </n-form>

                <n-form v-else key="register" class="authForm authFormRegister">
                  <n-form-item label="用户名" feedback="3-32 位；仅字母/数字/下划线（例如 dev_user）">
                    <n-input v-model:value="username" size="large" placeholder="例如: dev_user" />
                  </n-form-item>
                  <n-form-item label="邮箱">
                    <n-input v-model:value="email" size="large" placeholder="用于找回密码" />
                  </n-form-item>
                  <n-form-item label="密码" feedback="12-64 位；需包含大写/小写字母、数字、特殊字符（例如 Devtool@2026!）">
                    <n-input v-model:value="password" size="large" type="password" placeholder="例如: Devtool@2026!" />
                  </n-form-item>
                  <n-form-item label="确认密码">
                    <n-input v-model:value="confirmPassword" size="large" type="password" placeholder="再次输入密码" />
                  </n-form-item>

                  <div class="authLinksRow authLinksRegister">
                    <n-button text size="small" class="authTextBtn" @click="mode = 'login'">返回登录</n-button>
                  </div>

                  <div class="authActions">
                    <n-button type="primary" size="large" block class="authPrimary" :loading="loading" attr-type="button" @click.prevent="submitRegister">
                      创建账号
                    </n-button>
                  </div>
                </n-form>
              </Transition>
            </div>
          </div>
        </n-modal>
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

.micro {
  margin-top: 14px;
  display: flex;
  justify-content: center;
  gap: 10px;
  flex-wrap: wrap;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 750;
  letter-spacing: -0.2px;
  color: rgba(15, 23, 42, 0.72);
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.04), rgba(15, 23, 42, 0.02));
  box-shadow: 0 14px 44px rgba(2, 6, 23, 0.06);
}

.chip::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: rgba(20, 184, 166, 0.42);
}

.foot {
  position: absolute;
  left: 18px;
  right: 18px;
  bottom: 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  color: rgba(15, 23, 42, 0.55);
  font-size: 12px;
  letter-spacing: -0.1px;
}

.footLeft,
.footRight {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.footLink {
  padding: 6px 6px;
  border-radius: 10px;
  background: transparent;
  border: 0;
  color: rgba(15, 23, 42, 0.58);
  font-weight: 700;
  cursor: pointer;
}

.footLink:hover {
  color: rgba(15, 23, 42, 0.82);
  background: rgba(15, 23, 42, 0.03);
}

.sep {
  width: 4px;
  height: 4px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.22);
}

.shell {
  min-height: 100vh;
  max-width: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.main {
  flex: 1;
  display: grid;
  place-items: center;
  padding: 44px 18px 28px;
}

.center {
  text-align: center;
  max-width: 760px;
  margin: 0 auto;
  padding: 0 10px;
  transform: translateY(-22px);
}

.title {
  margin: 0;
  font-size: 68px;
  line-height: 1.02;
  letter-spacing: -1.9px;
  font-weight: 950;
  color: #0f172a;
}

.subBrand {
  margin-top: 10px;
  font-size: 14px;
  font-weight: 750;
  letter-spacing: -0.2px;
  color: rgba(15, 23, 42, 0.62);
}

.tagline {
  margin-top: 18px;
  font-size: 22px;
  font-weight: 850;
  letter-spacing: -0.6px;
  color: rgba(15, 23, 42, 0.86);
}

.desc {
  margin-top: 12px;
  font-size: 15px;
  line-height: 1.8;
  color: rgba(15, 23, 42, 0.62);
}

.actions {
  margin-top: 22px;
  display: flex;
  justify-content: center;
  gap: 12px;
}

.cta {
  min-width: 160px;
  box-shadow: 0 16px 50px rgba(2, 6, 23, 0.16);
}

.ghost {
  min-width: 140px;
}

:global(.authModal) {
  width: min(568px, calc(100vw - 28px)) !important;
  max-height: calc(100vh - 64px);
  position: fixed !important;
  left: 50% !important;
  top: 50% !important;
  transform: translate(-50%, -50%) !important;
  margin: 0 !important;
  animation: authModalIn 220ms cubic-bezier(0.22, 1, 0.36, 1);
}

:global(.authModal .n-card) {
  width: 100% !important;
  border-radius: 28px;
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.08), 0 6px 18px rgba(15, 23, 42, 0.035), 0 0 0 1px rgba(255, 255, 255, 0.45);
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(18px);
  max-height: calc(100vh - 64px);
  overflow: auto;
}

:global(.n-modal-mask) {
  background: rgba(15, 23, 42, 0.26) !important;
  backdrop-filter: blur(10px);
  animation: authMaskIn 260ms ease;
}

:global(.authModal .n-card__header) {
  padding: 6px 12px 0 !important;
  min-height: 0 !important;
}

:global(.authModal .n-card__header-main) {
  padding: 0 !important;
}

:global(.authModal .n-card__content) {
  padding: 0 !important;
}

.authShell {
  position: relative;
  border-radius: 28px;
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

.authHeading {
  margin-top: 0;
}

.authTitle {
  font-size: 38px;
  line-height: 1.04;
  font-weight: 950;
  letter-spacing: -0.9px;
  color: #0f172a;
}

.authTitle.register {
  font-size: 40px;
}

.authSubtitle {
  margin-top: 12px;
  font-size: 13px;
  line-height: 1.7;
  font-weight: 450;
  letter-spacing: -0.06px;
  color: rgba(15, 23, 42, 0.34);
}

.authSubtitle.register {
  font-size: 12px;
  color: rgba(15, 23, 42, 0.3);
}

.authForm {
  margin-top: 28px;
}

.authFormRegister {
  margin-top: 30px;
}

.authLinksRow {
  display: flex;
  align-items: flex-start;
  gap: 18px;
  margin-top: 10px;
}

.authLinksRegister {
  margin-top: 12px;
}

.authActions {
  margin-top: 26px;
}

.authSwap-enter-active,
.authSwap-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}

.authSwap-enter-from,
.authSwap-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

:global(.authModal .n-base-close) {
  color: rgba(15, 23, 42, 0.64) !important;
}

:global(.authModal .n-base-close:hover) {
  color: rgba(15, 23, 42, 0.9) !important;
  background: rgba(15, 23, 42, 0.06) !important;
}

@media (max-width: 980px) {
  .shell {
    padding: 0;
  }
  .title {
    font-size: 46px;
  }
  .center {
    transform: translateY(-10px);
  }
  .tagline {
    font-size: 18px;
  }
  .actions {
    flex-direction: column;
    align-items: stretch;
  }
  .cta,
  .ghost {
    width: 100%;
  }
  .authInner {
    padding: 24px 20px 22px;
  }
  .authTitle {
    font-size: 32px;
  }
}

:global(.authModal) :deep(.n-form-item-label__text) {
  color: rgba(15, 23, 42, 0.88);
  font-weight: 700;
  letter-spacing: -0.08px;
}

:global(.authModal) :deep(.n-form-item-feedback__line) {
  color: rgba(15, 23, 42, 0.44);
  font-size: 12px;
  line-height: 1.6;
  font-weight: 600;
  letter-spacing: -0.08px;
}

:global(.authModal) :deep(.n-form-item) {
  margin-bottom: 6px;
}

.authFormRegister :deep(.n-form-item) {
  margin-bottom: 8px;
}

:global(.authModal) :deep(.n-input) {
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

:global(.authModal) :deep(.n-input .n-input-wrapper) {
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease,
    background-color 180ms ease;
}

:deep(.n-button) {
  border-radius: 18px;
}

.authFormRegister :deep(.n-form-item-feedback-wrapper) {
  min-height: 16px;
  padding-top: 2px;
}

.authFormRegister :deep(.n-form-item-feedback__line) {
  font-size: 11px;
  line-height: 1.45;
  color: rgba(15, 23, 42, 0.3);
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

@keyframes authMaskIn {
  0% {
    opacity: 0;
  }
  100% {
    opacity: 1;
  }
}

@keyframes authModalIn {
  0% {
    opacity: 0;
    transform: translate(-50%, calc(-50% + 14px)) scale(0.985) !important;
  }
  100% {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1) !important;
  }
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
