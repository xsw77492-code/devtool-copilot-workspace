<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NButton, NConfigProvider, NForm, NFormItem, NInput, NModal, useMessage } from 'naive-ui'
import { authApi } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const message = useMessage()

const showAuth = ref(false)
const mode = ref<'login' | 'register' | 'forgot'>('login')
const registerStep = ref<1 | 2>(1)
const posterStage = ref<HTMLElement | null>(null)
const username = ref('')
const password = ref('')
const email = ref('')
const confirmPassword = ref('')
const forgotEmail = ref('')
const loading = ref(false)

const themeOverrides = {
  common: {
    primaryColor: '#176b59',
    primaryColorHover: '#0f5d4b',
    primaryColorPressed: '#0b4a3c',
    borderRadius: '6px'
  }
}

const redirect = computed(() => (route.query.redirect as string | undefined) || '/board')

function openAuth(next: 'login' | 'register' | 'forgot') {
  mode.value = next
  if (next === 'register') registerStep.value = 1
  password.value = ''
  confirmPassword.value = ''
  showAuth.value = true
  if (route.query.auth !== next) {
    router.replace({ name: 'login', query: { ...route.query, auth: next } })
  }
}

function continueRegister() {
  if (!username.value.trim() || !email.value.trim()) {
    message.error('请填写用户名和邮箱')
    return
  }
  registerStep.value = 2
}

function movePoster(event: PointerEvent) {
  const stage = posterStage.value
  if (!stage || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  const bounds = stage.getBoundingClientRect()
  const x = (event.clientX - bounds.left) / bounds.width - 0.5
  const y = (event.clientY - bounds.top) / bounds.height - 0.5
  stage.style.setProperty('--poster-x', `${x * 3.2}deg`)
  stage.style.setProperty('--poster-y', `${y * -2.4}deg`)
}

function resetPoster() {
  posterStage.value?.style.setProperty('--poster-x', '0deg')
  posterStage.value?.style.setProperty('--poster-y', '0deg')
}

function setAuthVisibility(value: boolean) {
  showAuth.value = value
  if (!value && route.query.auth) {
    const { auth: _auth, ...query } = route.query
    router.replace({ name: 'login', query })
  }
}

watch(
  () => route.query.auth,
  (requested) => {
    if (requested === 'login' || requested === 'register' || requested === 'forgot') {
      mode.value = requested
      showAuth.value = true
    }
  },
  { immediate: true }
)

async function submitLogin() {
  if (!username.value.trim() || !password.value) {
    message.error('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login({ username: username.value.trim(), password: password.value })
    router.replace(redirect.value)
  } catch (e: any) {
    message.error(e?.message || '登录失败')
  } finally {
    loading.value = false
  }
}

async function submitRegister() {
  if (!username.value.trim() || !email.value.trim() || !password.value) {
    message.error('请填写完整')
    return
  }
  if (password.value !== confirmPassword.value) {
    message.error('两次密码不一致')
    return
  }
  loading.value = true
  try {
    await authApi.register({ username: username.value.trim(), email: email.value.trim(), password: password.value })
    await auth.login({ username: username.value.trim(), password: password.value })
    router.replace(redirect.value)
  } catch (e: any) {
    message.error(e?.message || '注册失败')
  } finally {
    loading.value = false
  }
}

async function submitForgotPassword() {
  if (!forgotEmail.value.trim()) {
    message.error('请输入注册邮箱')
    return
  }
  loading.value = true
  try {
    await authApi.passwordResetRequest({ email: forgotEmail.value.trim() })
    message.success('如果账号存在，重置链接已发送至邮箱')
    setAuthVisibility(false)
  } catch (e: any) {
    message.error(e?.message || '发送失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <n-config-provider :theme="null" :theme-overrides="themeOverrides">
    <div class="access-page">
      <header class="access-header">
        <div class="brand" aria-label="DevTool Copilot">
          <span class="brand-mark" aria-hidden="true"><i /><i /></span>
          <span class="brand-name">DevTool Copilot</span>
          <span class="brand-tag">WORKSPACE</span>
        </div>
        <div class="header-actions">
          <span>已有账号？</span>
          <button type="button" class="quiet-link" @click="openAuth('login')">登录</button>
        </div>
      </header>

      <main class="access-main">
        <section class="entry-copy" aria-labelledby="product-name">
          <div class="entry-status"><i aria-hidden="true" />团队空间已就绪</div>
          <h1 id="product-name">DevTool Copilot</h1>
          <p class="entry-headline">让每一次推进，<br />都有清晰的下一步。</p>
          <p class="entry-description">从需求落点到任务交付，团队正在同一个工作节奏里协作。</p>
          <div class="entry-actions">
            <n-button type="primary" size="large" @click="openAuth('login')">
              进入工作台
              <span class="button-arrow" aria-hidden="true">→</span>
            </n-button>
            <button type="button" class="secondary-action" @click="openAuth('register')">创建账号</button>
          </div>
          <div class="entry-signals" aria-label="工作台状态">
            <span><i class="signal-dot success" />项目持续同步</span>
            <span><i class="signal-dot neutral" />决策留有记录</span>
          </div>
        </section>

        <div ref="posterStage" class="poster-stage" @pointermove="movePoster" @pointerleave="resetPoster">
          <div class="poster-parallax">
            <span class="poster-sheet poster-sheet-back" aria-hidden="true" />
            <span class="poster-sheet poster-sheet-side" aria-hidden="true" />
            <div class="poster-ai-pulse"><span>AI</span><i /><i /><i /></div>
            <div class="scene-stage">
              <div class="scene-sync-note" aria-label="同步状态"><i /><span>已同步</span></div>
              <div class="scene-presence-note"><span class="presence-avatar">L</span><span>正在编辑</span><i /></div>
              <section class="application-scene" aria-label="DevTool Copilot 工作台预览">
          <div class="scene-chrome">
            <div class="scene-brand"><span class="scene-mark" /><span>DevTool Copilot</span></div>
            <div class="scene-center"><i /><span>支付中心改造</span></div>
            <div class="scene-person"><span>LW</span><i /></div>
          </div>

          <div class="scene-body">
            <aside class="scene-sidebar">
              <div class="workspace-switcher"><span class="switcher-mark">D</span><span>研发团队</span><b>⌄</b></div>
              <nav aria-label="工作台预览导航">
                <div class="scene-nav active"><span class="nav-overview" />概览</div>
                <div class="scene-nav"><span class="nav-project" />项目</div>
                <div class="scene-nav"><span class="nav-board" />任务看板</div>
                <div class="scene-nav"><span class="nav-inbox" />收件箱<span class="nav-badge">2</span></div>
              </nav>
              <div class="scene-sidebar-foot"><span class="avatar-stack"><i>L</i><i>Q</i><i>J</i></span><span>8 位成员在线</span></div>
            </aside>

            <div class="scene-content">
              <div class="project-path"><span>项目</span><b>/</b><span>支付中心改造</span><em>进行中</em></div>
              <div class="project-title-row">
                <div>
                  <h2>支付中心改造</h2>
                  <p>第 3 周 · 交付窗口 8 月 16 日</p>
                </div>
                <button type="button" class="scene-add">+ 新建任务</button>
              </div>

              <div class="project-progress">
                <div class="progress-copy"><span>本周目标</span><strong>72%</strong></div>
                <div class="progress-track"><i /></div>
                <div class="progress-note"><span><i class="live-dot" />刚刚同步</span><span>12 / 17 已完成</span></div>
              </div>

              <div class="scene-grid">
                <section class="task-panel">
                  <div class="panel-heading"><span>正在推进</span><button type="button">查看全部</button></div>
                  <div class="task-list">
                    <article class="task-row high-priority">
                      <span class="task-check" />
                      <div><strong>确认 AI 计划中的接口边界</strong><small>后端 · 今天</small></div>
                      <span class="task-avatar">J</span>
                      <em>进行中</em>
                    </article>
                    <article class="task-row">
                      <span class="task-check" />
                      <div><strong>生成任务验收条件</strong><small>前端 · 明天</small></div>
                      <span class="task-avatar avatar-rose">Q</span>
                      <em>待处理</em>
                    </article>
                    <article class="task-row activity-row">
                      <span class="task-check checked">✓</span>
                      <div><strong>计划确认记录已归档</strong><small>审计 · 刚刚更新</small></div>
                      <span class="task-avatar avatar-sand">M</span>
                      <em>已完成</em>
                    </article>
                  </div>
                </section>

                <aside class="activity-panel">
                  <div class="panel-heading"><span>项目动态</span><button type="button">•••</button></div>
                  <div class="activity-item">
                    <span class="activity-icon check-icon">✓</span>
                    <p><b>Jian</b> 确认了 AI 计划<small>2 分钟前</small></p>
                  </div>
                  <div class="activity-item">
                    <span class="activity-icon note-icon" />
                    <p><b>Lin</b> 添加了验收条件<small>18 分钟前</small></p>
                  </div>
                </aside>
              </div>

              <div class="ai-dock">
                <span class="ai-dock-mark">AI</span>
                <div class="ai-dock-copy"><span>正在生成可确认的任务草案</span><i><b /><b /><b /></i></div>
                <button type="button">查看</button>
              </div>
            </div>
          </div>
              </section>
            </div>
          </div>
        </div>
      </main>

      <footer class="access-footer"><span>© 2026 DevTool Copilot</span><span>团队交付工作台</span></footer>

      <n-modal
        :show="showAuth"
        @update:show="setAuthVisibility"
        preset="card"
        :bordered="false"
        :mask-closable="true"
        :style="{ width: 'min(476px, calc(100vw - 32px))' }"
        class="auth-modal"
      >
        <div class="auth-surface">
          <header class="auth-surface-top">
            <div class="auth-identity"><span class="auth-mark" aria-hidden="true"><i /><i /></span><span>DevTool Copilot</span></div>
            <span class="auth-security"><i />安全登录</span>
          </header>
          <section class="auth-sheet">
            <div v-if="mode !== 'forgot'" class="auth-mode-switch" aria-label="认证方式">
              <button type="button" :class="{ active: mode === 'login' }" @click="openAuth('login')">登录</button>
              <button type="button" :class="{ active: mode === 'register' }" @click="openAuth('register')">注册</button>
            </div>
            <div v-else class="auth-recovery-title"><strong>找回密码</strong><button type="button" @click="openAuth('login')">返回登录</button></div>

            <n-form v-if="mode === 'login'" class="auth-form" @submit.prevent="submitLogin">
              <n-form-item label="用户名">
                <n-input v-model:value="username" size="large" placeholder="输入用户名" autocomplete="username" />
              </n-form-item>
              <n-form-item label="密码">
                <n-input v-model:value="password" size="large" type="password" placeholder="输入密码" autocomplete="current-password" @keyup.enter="submitLogin" />
              </n-form-item>
              <div class="auth-links"><n-button text size="small" @click="openAuth('forgot')">忘记密码</n-button></div>
              <n-button type="primary" size="large" block :loading="loading" attr-type="submit">
                登录并继续
                <span class="button-arrow" aria-hidden="true">→</span>
              </n-button>
            </n-form>

            <n-form v-else-if="mode === 'register'" class="auth-form" @submit.prevent="submitRegister">
              <div class="register-steps" aria-label="注册步骤"><span :class="{ active: registerStep === 1 }">1</span><i :class="{ active: registerStep === 2 }" /><span :class="{ active: registerStep === 2 }">2</span></div>
              <template v-if="registerStep === 1">
                <n-form-item label="用户名"><n-input v-model:value="username" size="large" placeholder="用户名" autocomplete="username" /></n-form-item>
                <n-form-item label="邮箱"><n-input v-model:value="email" size="large" placeholder="邮箱" autocomplete="email" @keyup.enter="continueRegister" /></n-form-item>
                <n-button type="primary" size="large" block attr-type="button" @click="continueRegister">继续<span class="button-arrow" aria-hidden="true">→</span></n-button>
              </template>
              <template v-else>
                <n-form-item label="密码"><n-input v-model:value="password" size="large" type="password" placeholder="密码" autocomplete="new-password" /></n-form-item>
                <n-form-item label="确认密码"><n-input v-model:value="confirmPassword" size="large" type="password" placeholder="确认密码" autocomplete="new-password" @keyup.enter="submitRegister" /></n-form-item>
                <div class="register-back"><button type="button" @click="registerStep = 1">上一步</button></div>
                <n-button type="primary" size="large" block :loading="loading" attr-type="submit">创建账号<span class="button-arrow" aria-hidden="true">→</span></n-button>
              </template>
            </n-form>

            <n-form v-else class="auth-form" @submit.prevent="submitForgotPassword">
              <n-form-item label="注册邮箱">
                <n-input v-model:value="forgotEmail" size="large" placeholder="输入注册邮箱" autocomplete="email" @keyup.enter="submitForgotPassword" />
              </n-form-item>
              <n-button type="primary" size="large" block :loading="loading" attr-type="submit">
                发送重置链接
                <span class="button-arrow" aria-hidden="true">→</span>
              </n-button>
            </n-form>
          </section>
        </div>
      </n-modal>
    </div>
  </n-config-provider>
</template>

<style scoped>
.access-page {
  min-height: 100svh;
  position: relative;
  display: grid;
  grid-template-rows: 72px 1fr 44px;
  overflow: hidden;
  isolation: isolate;
  background: #f7f7f5;
  color: #1c2420;
}

.access-header,
.brand,
.header-actions,
.entry-status,
.entry-actions,
.entry-signals,
.scene-chrome,
.scene-brand,
.scene-center,
.scene-person,
.workspace-switcher,
.scene-nav,
.scene-sidebar-foot,
.project-path,
.project-title-row,
.progress-copy,
.progress-note,
.panel-heading,
.task-row,
.activity-item,
.ai-dock,
.auth-context-brand,
.auth-context-card,
.auth-links {
  display: flex;
  align-items: center;
}

.access-header {
  justify-content: space-between;
  padding: 0 48px;
  border-bottom: 1px solid #e4e5e0;
  background: rgba(247, 247, 245, 0.9);
}

.brand { gap: 10px; }

.brand-mark,
.auth-mark {
  width: 26px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  border-radius: 7px;
  background: #1c2420;
}

.brand-mark i,
.auth-mark i {
  width: 3px;
  height: 12px;
  display: block;
  border-radius: 2px;
  background: #eef0e9;
}

.brand-mark i:last-child,
.auth-mark i:last-child { height: 7px; opacity: 0.7; }

.brand-name { font-size: 14px; font-weight: 680; }
.brand-tag { color: #8a908a; font-size: 10px; font-weight: 650; letter-spacing: 0.8px; }
.header-actions { gap: 11px; color: #777d78; font-size: 13px; }

.quiet-link,
.secondary-action {
  border: 0;
  background: transparent;
  color: #1c2420;
  font: inherit;
  cursor: pointer;
}

.quiet-link { padding: 7px 0; font-weight: 650; }
.quiet-link:hover { color: #176b59; }

.access-main {
  position: relative;
  width: min(1280px, calc(100% - 112px));
  display: grid;
  grid-template-columns: minmax(360px, 0.88fr) minmax(630px, 1.12fr);
  align-items: center;
  gap: 52px;
  margin: 0 auto;
  padding: 38px 0;
}

.entry-copy,
.scene-stage { position: relative; z-index: 1; }
.delivery-trace { position: absolute; z-index: 0; top: 24%; left: 41.5%; width: 1px; height: 250px; border-left: 1px solid #dbe6df; pointer-events: none; }
.delivery-trace::before { position: absolute; top: 20px; left: -1px; width: 44px; height: 1px; content: ''; background: #e4ece7; }.delivery-trace span { position: absolute; right: -23px; bottom: 28px; width: 45px; height: 1px; background: #e4ece7; }.delivery-trace i { position: absolute; left: -4px; width: 7px; height: 7px; border: 1px solid #f7f7f5; border-radius: 2px; background: #1fa67a; box-shadow: 0 0 0 1px #cde1d6; }.delivery-trace i:nth-child(1) { top: 17px; }.delivery-trace i:nth-child(2) { top: 116px; background: #dfa440; box-shadow: 0 0 0 1px #f0d4a7; }.delivery-trace i:nth-child(3) { bottom: 25px; background: #d17a70; box-shadow: 0 0 0 1px #edc4bf; }
.activity-rail { position: absolute; z-index: 2; bottom: 68px; left: clamp(32px, 10vw, 160px); display: flex; align-items: center; gap: 26px; color: #858c86; font-size: 11px; }.rail-line { width: 36px; height: 1px; background: #d7e2dc; }.rail-event { display: inline-flex; align-items: center; gap: 7px; white-space: nowrap; }.rail-event em { color: #afb5af; font-size: 10px; font-style: normal; }.rail-dot { width: 7px; height: 7px; display: inline-block; border-radius: 2px; }.rail-dot.ready { background: #1fa67a; }.rail-dot.progress { background: #dfa440; }.rail-dot.plan { background: #8aa696; }.rail-event.current { color: #506057; font-weight: 650; }.rail-event.current .rail-dot { animation: rail-breathe 2.5s ease-in-out infinite; }

.entry-copy { align-self: center; }
.entry-status { gap: 7px; color: #5b625d; font-size: 12px; font-weight: 560; animation: enter-up 560ms 60ms both; }
.entry-status i { width: 7px; height: 7px; border-radius: 50%; background: #1fa67a; box-shadow: 0 0 0 4px rgba(31, 166, 122, 0.1); }

.entry-copy h1 {
  margin: 20px 0 0;
  color: #1c2420;
  font-size: 15px;
  font-weight: 690;
  line-height: 1.25;
  letter-spacing: 0.1px;
  animation: enter-up 560ms 120ms both;
}

.entry-headline {
  margin: 10px 0 0;
  color: #1c2420;
  font-size: clamp(34px, 3.15vw, 48px);
  font-weight: 690;
  line-height: 1.13;
  letter-spacing: 0;
  animation: enter-up 560ms 180ms both;
}

.entry-description {
  max-width: 365px;
  margin: 18px 0 0;
  color: #69706b;
  font-size: 15px;
  line-height: 1.75;
  animation: enter-up 560ms 240ms both;
}

.entry-actions { gap: 18px; margin-top: 30px; animation: enter-up 560ms 300ms both; }
.entry-actions :deep(.n-button) { min-width: 144px; height: 44px; padding: 0 16px 0 18px; font-weight: 650; }
.entry-actions :deep(.n-button__content) { gap: 20px; }
.button-arrow { font-size: 17px; font-weight: 450; line-height: 1; }
.secondary-action { padding: 10px 0; color: #4e5751; font-size: 14px; font-weight: 620; }
.secondary-action:hover { color: #176b59; }

.entry-signals { gap: 18px; margin-top: 42px; color: #858b86; font-size: 12px; animation: enter-up 560ms 360ms both; }
.entry-signals span { display: inline-flex; align-items: center; gap: 6px; }
.signal-dot { width: 5px; height: 5px; display: inline-block; border-radius: 50%; }
.signal-dot.success { background: #1fa67a; }
.signal-dot.neutral { background: #a7aca6; }

.application-scene {
  min-width: 0;
  overflow: hidden;
  border: 1px solid #d6d9d4;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 28px 62px rgba(35, 42, 37, 0.13), 0 4px 12px rgba(35, 42, 37, 0.05);
  transform-origin: center right;
  transition: transform 260ms ease, box-shadow 260ms ease;
  animation: scene-in 680ms 180ms both;
}

.scene-stage { min-width: 0; position: relative; padding: 18px 14px 0 0; }
.scene-stage:hover .application-scene { transform: translateY(-3px); box-shadow: 0 34px 70px rgba(35, 42, 37, 0.15), 0 5px 13px rgba(35, 42, 37, 0.05); }
.scene-sync-note { position: absolute; z-index: 2; top: 0; right: 0; display: inline-flex; align-items: center; gap: 6px; min-height: 26px; padding: 0 10px; border: 1px solid #d9e5dd; border-radius: 5px; background: #f7faf7; color: #5e6d63; box-shadow: 0 8px 18px rgba(31, 48, 38, 0.08); font-size: 10px; font-weight: 600; animation: note-in 600ms 760ms both; }
.scene-sync-note i { width: 6px; height: 6px; border-radius: 50%; background: #1fa67a; box-shadow: 0 0 0 3px rgba(31, 166, 122, 0.12); animation: live-breathe 2.2s ease-in-out infinite; }
.scene-presence-note { position: absolute; z-index: 2; bottom: 50px; left: -42px; display: inline-flex; align-items: center; gap: 6px; min-height: 27px; padding: 0 9px 0 5px; border: 1px solid #e2e6e1; border-radius: 5px; background: #fff; box-shadow: 0 9px 20px rgba(31, 48, 38, 0.1); color: #68736b; font-size: 10px; font-weight: 600; animation: note-in 620ms 920ms both; }.presence-avatar { width: 19px; height: 19px; display: grid; place-items: center; border-radius: 50%; background: #efe0dc; color: #a64b42; font-size: 8px; font-weight: 700; }.scene-presence-note > i { width: 5px; height: 5px; margin-left: 1px; border-radius: 50%; background: #1fa67a; animation: live-breathe 2.4s ease-in-out infinite; }
.scene-ai-note { position: absolute; z-index: 2; right: -25px; bottom: 118px; display: inline-flex; align-items: center; gap: 3px; min-height: 25px; padding: 0 8px; border: 1px solid #dce9e1; border-radius: 5px; background: #f5faf6; color: #293b31; box-shadow: 0 8px 18px rgba(31, 48, 38, 0.08); font-size: 8px; font-weight: 720; animation: note-in 620ms 1.04s both; }.scene-ai-note > i { width: 3px; height: 3px; border-radius: 50%; background: #7fa58e; animation: dot-step 1.5s ease-in-out infinite; }.scene-ai-note > i:nth-of-type(2) { animation-delay: 120ms; }.scene-ai-note > i:nth-of-type(3) { animation-delay: 240ms; }

.scene-chrome {
  height: 50px;
  gap: 18px;
  padding: 0 17px;
  border-bottom: 1px solid #e8e9e6;
  color: #69706b;
  font-size: 11px;
}

.scene-brand { flex: 0 0 auto; align-items: center; gap: 7px; color: #39413c; font-weight: 660; }
.scene-mark { width: 15px; height: 15px; display: block; border-radius: 4px; background: #1c2420; }
.scene-center { gap: 7px; margin: 0 auto; color: #8a908a; }
.scene-center i { width: 5px; height: 5px; border-radius: 50%; background: #1fa67a; }
.scene-person { gap: 7px; color: #86908a; }
.scene-person > span { width: 24px; height: 24px; display: grid; place-items: center; border-radius: 50%; background: #dce7df; color: #176b59; font-size: 9px; font-weight: 700; }
.scene-person > i { width: 5px; height: 5px; margin-left: -11px; margin-bottom: -13px; border: 1px solid #fff; border-radius: 50%; background: #1fa67a; }

.scene-body { min-height: 468px; display: grid; grid-template-columns: 172px 1fr; }
.scene-sidebar { display: flex; flex-direction: column; padding: 15px 10px 12px; border-right: 1px solid #e8e9e6; background: #fbfbfa; color: #68706a; font-size: 11px; }
.workspace-switcher { gap: 7px; height: 31px; padding: 0 8px; margin-bottom: 14px; color: #374039; font-size: 11px; font-weight: 640; }
.switcher-mark { width: 18px; height: 18px; display: grid; place-items: center; border-radius: 5px; background: #dce7df; color: #176b59; font-size: 9px; }
.workspace-switcher b { margin-left: auto; color: #9ba19c; font-size: 12px; font-weight: 500; }
.scene-sidebar nav { display: grid; gap: 3px; }
.scene-nav { position: relative; gap: 8px; height: 32px; padding: 0 9px; border-radius: 5px; }
button.scene-nav { width: 100%; border: 0; background: transparent; color: inherit; font: inherit; text-align: left; cursor: pointer; transition: color 150ms ease, background-color 150ms ease; }
.scene-nav:not(.active):hover { color: #45594c; background: #f2f5f2; }
.scene-nav.active { color: #176b59; background: #e9f1ed; font-weight: 650; }
.scene-nav > span:not(.nav-badge) { width: 13px; height: 13px; display: block; box-sizing: border-box; border: 1.4px solid currentColor; border-radius: 3px; opacity: 0.78; }
.scene-nav .nav-overview { border-radius: 50%; }
.scene-nav .nav-board { height: 10px; border-width: 1.4px 0; border-radius: 0; }
.scene-nav .nav-inbox { border-radius: 3px 3px 5px 5px; }
.nav-badge { min-width: 15px; height: 15px; display: grid; place-items: center; margin-left: auto; border-radius: 50%; background: #ececea; color: #727a74; font-size: 9px; }
.scene-sidebar-foot { gap: 8px; padding: 10px 8px 0; margin-top: auto; border-top: 1px solid #ebece9; color: #969c97; font-size: 10px; }
.avatar-stack { display: inline-flex; padding-left: 3px; }
.avatar-stack i { width: 17px; height: 17px; display: grid; place-items: center; margin-left: -3px; border: 1px solid #fbfbfa; border-radius: 50%; background: #dce7df; color: #176b59; font-size: 7px; font-style: normal; font-weight: 700; }
.avatar-stack i:nth-child(2) { background: #efe0dc; color: #a64b42; }
.avatar-stack i:nth-child(3) { background: #e8e0c9; color: #8b6c1d; }

.scene-content { min-width: 0; padding: 25px 26px 20px; background: #fff; }
.project-path { gap: 7px; color: #9ba19c; font-size: 10px; }
.project-path b { color: #c2c6c2; font-weight: 400; }
.project-path em { padding: 3px 6px; margin-left: 4px; border-radius: 3px; background: #e9f1ed; color: #176b59; font-size: 9px; font-style: normal; font-weight: 650; }
.project-title-row { justify-content: space-between; gap: 16px; margin-top: 10px; }
.project-title-row h2 { margin: 0; color: #252d28; font-size: 21px; font-weight: 680; line-height: 1.25; }
.project-title-row p { margin: 6px 0 0; color: #959c96; font-size: 11px; }
.scene-add { height: 29px; flex: 0 0 auto; padding: 0 9px; border: 1px solid #d8dcd7; border-radius: 4px; background: #fff; color: #445048; font-size: 10px; font-weight: 620; }

.project-progress { margin-top: 22px; padding: 13px 14px 11px; border: 1px solid #e4e7e2; border-radius: 6px; background: #fbfcfa; }
.progress-copy { justify-content: space-between; color: #69706b; font-size: 11px; }
.progress-copy strong { color: #176b59; font-size: 12px; }
.progress-track { height: 5px; overflow: hidden; margin-top: 10px; border-radius: 5px; background: #e6e9e4; }
.progress-track i { width: 72%; height: 100%; display: block; border-radius: inherit; background: #1fa67a; transform-origin: left; animation: grow-progress 1.1s 850ms both; }
.progress-note { justify-content: space-between; margin-top: 8px; color: #9aa09b; font-size: 9px; }
.progress-note span { display: inline-flex; align-items: center; gap: 5px; }
.live-dot { width: 5px; height: 5px; display: block; border-radius: 50%; background: #1fa67a; animation: live-breathe 2.2s ease-in-out infinite; }

.scene-grid { display: grid; grid-template-columns: minmax(0, 1.48fr) minmax(164px, 0.78fr); gap: 12px; margin-top: 13px; }
.task-panel,.activity-panel { overflow: hidden; border: 1px solid #e5e7e3; border-radius: 6px; background: #fff; }
.panel-heading { height: 37px; justify-content: space-between; padding: 0 12px; border-bottom: 1px solid #eef0ed; color: #49534c; font-size: 11px; font-weight: 650; }
.panel-heading button { padding: 0; border: 0; background: transparent; color: #8c938d; font: inherit; font-size: 10px; cursor: pointer; }
.panel-heading button:hover { color: #176b59; }
.task-row { min-height: 46px; gap: 8px; padding: 7px 10px; border-bottom: 1px solid #f0f1ef; }
.task-row:last-child { border-bottom: 0; }
.task-row > div { min-width: 0; flex: 1; }
.task-row strong,.task-row small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.task-row strong { color: #38423c; font-size: 10px; font-weight: 620; }
.task-row small { margin-top: 3px; color: #9da29e; font-size: 9px; }
.task-check { width: 13px; height: 13px; flex: 0 0 auto; box-sizing: border-box; border: 1px solid #cbd0cb; border-radius: 4px; color: #fff; font-size: 9px; line-height: 11px; text-align: center; }
.high-priority .task-check { border-color: #dfa440; background: #fffaf0; }
.task-check.checked { border-color: #1fa67a; background: #1fa67a; }
.task-avatar { width: 18px; height: 18px; display: grid; flex: 0 0 auto; place-items: center; border-radius: 50%; background: #dce7df; color: #176b59; font-size: 8px; font-weight: 700; }
.task-avatar.avatar-rose { background: #efe0dc; color: #a64b42; }.task-avatar.avatar-sand { background: #e8e0c9; color: #8b6c1d; }
.task-row em { min-width: 31px; color: #8d938e; font-size: 9px; font-style: normal; text-align: right; }.high-priority em { color: #176b59; }.activity-row em { color: #1a906a; }
.activity-row { animation: row-arrive 700ms 1.15s both; }

.activity-panel { padding-bottom: 4px; background: #fbfcfa; }
.activity-panel .panel-heading { background: #fff; }
.activity-item { align-items: flex-start; gap: 7px; padding: 11px 10px 5px; }
.activity-icon { width: 16px; height: 16px; display: grid; flex: 0 0 auto; place-items: center; border-radius: 4px; background: #e9f1ed; color: #176b59; font-size: 9px; font-weight: 700; }
.note-icon { position: relative; box-sizing: border-box; border: 1px solid #c6d3ca; background: #f4f8f4; }.note-icon::after { width: 7px; height: 1px; content: ''; background: #7da28d; box-shadow: 0 3px 0 #7da28d; }
.activity-item p { min-width: 0; margin: 0; color: #68716b; font-size: 9px; line-height: 1.4; }.activity-item b { color: #3b453e; font-weight: 650; }.activity-item small { display: block; margin-top: 3px; color: #a0a59f; font-size: 9px; }

.ai-dock { gap: 9px; min-height: 36px; padding: 0 10px; margin-top: 12px; border: 1px solid #dfe9e1; border-radius: 6px; background: #f6faf7; }
.ai-dock-mark { width: 19px; height: 19px; display: grid; flex: 0 0 auto; place-items: center; border-radius: 5px; background: #1c2420; color: #f4f6f1; font-size: 8px; font-weight: 700; }
.ai-dock-copy { min-width: 0; flex: 1; color: #647069; font-size: 10px; }.ai-dock-copy > span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.ai-dock-copy i { display: flex; gap: 2px; margin-top: 3px; }.ai-dock-copy b { width: 16px; height: 2px; display: block; border-radius: 2px; background: #acc2b4; animation: stream 1.6s ease-in-out infinite; }.ai-dock-copy b:nth-child(2) { width: 25px; animation-delay: 140ms; }.ai-dock-copy b:nth-child(3) { width: 11px; animation-delay: 280ms; }
.ai-dock button { padding: 0; border: 0; background: transparent; color: #176b59; font-size: 10px; font-weight: 650; cursor: pointer; }

.access-footer { justify-content: space-between; padding: 0 48px; color: #969c97; font-size: 11px; }.access-footer span:last-child { color: #b1b5b1; }

/* A single surface keeps login, registration, and recovery in one product flow. */
.auth-surface { overflow: hidden; background: #fff; }
.auth-surface-top { height: 62px; display: flex; align-items: center; justify-content: space-between; padding: 0 26px; border-bottom: 1px solid #eceeea; }
.auth-identity { display: flex; align-items: center; gap: 9px; color: #29332d; font-size: 13px; font-weight: 680; }.auth-identity .auth-mark { width: 23px; height: 23px; border-radius: 6px; }.auth-identity .auth-mark i { height: 10px; }.auth-identity .auth-mark i:last-child { height: 6px; }
.auth-security { display: inline-flex; align-items: center; gap: 6px; color: #89908b; font-size: 10px; font-weight: 620; }.auth-security i { width: 6px; height: 6px; border-radius: 50%; background: #1fa67a; }
.auth-workline { display: flex; align-items: center; gap: 9px; min-height: 62px; padding: 0 26px; border-bottom: 1px solid #e9ede9; background: #fafcf9; }.workline-avatar { width: 25px; height: 25px; display: grid; flex: 0 0 auto; place-items: center; border-radius: 6px; background: #e2ede6; color: #176b59; font-size: 10px; font-weight: 700; }.auth-workline > div { min-width: 0; flex: 1; }.auth-workline small,.auth-workline strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.auth-workline small { color: #9aa19b; font-size: 9px; }.auth-workline strong { margin-top: 2px; color: #536057; font-size: 10px; font-weight: 630; }.auth-workline strong i { color: #aeb6af; font-style: normal; }.workline-members { display: inline-flex; padding-left: 3px; }.workline-members i { width: 18px; height: 18px; display: grid; place-items: center; margin-left: -3px; border: 1px solid #fafcf9; border-radius: 50%; background: #dce7df; color: #176b59; font-size: 7px; font-style: normal; font-weight: 700; }.workline-members i:nth-child(2) { background: #efe0dc; color: #a64b42; }.workline-members i:nth-child(3) { background: #e8e0c9; color: #8b6c1d; }
.auth-sheet { padding: 31px 26px 27px; }.auth-sheet .auth-heading h2 { margin-top: 8px; color: #1f2a24; font-size: 24px; }.auth-sheet .auth-heading p { margin-top: 7px; }.auth-sheet .auth-form { margin-top: 24px; }.auth-sheet .auth-links { margin-top: 0; }.auth-sheet .auth-links-single { margin-top: -2px; }.auth-recovery-note { display: flex; align-items: center; gap: 6px; margin: -4px 0 17px; color: #89918b; font-size: 11px; }.auth-recovery-note i { width: 5px; height: 5px; border-radius: 50%; background: #1fa67a; }
.auth-sheet { padding: 24px 26px 27px; }.auth-mode-switch { display: grid; grid-template-columns: 1fr 1fr; gap: 3px; padding: 3px; border-radius: 6px; background: #f2f4f1; }.auth-mode-switch button { height: 32px; border: 0; border-radius: 4px; background: transparent; color: #858c86; font-size: 13px; font-weight: 630; cursor: pointer; transition: color 160ms ease, background-color 160ms ease, box-shadow 160ms ease; }.auth-mode-switch button.active { background: #fff; color: #1f2a24; box-shadow: 0 1px 3px rgba(31, 42, 36, 0.11); }.auth-mode-switch button:not(.active):hover { color: #176b59; }.auth-recovery-title { display: flex; align-items: center; justify-content: space-between; min-height: 32px; }.auth-recovery-title strong { color: #1f2a24; font-size: 16px; font-weight: 680; }.auth-recovery-title button { padding: 4px 0; border: 0; background: transparent; color: #176b59; font-size: 12px; font-weight: 630; cursor: pointer; }.auth-sheet .auth-form { margin-top: 20px; }.auth-sheet .auth-links { min-height: 22px; justify-content: flex-start; margin: -3px 0 15px; }.auth-sheet .auth-links :deep(.n-button) { font-size: 12px; }.register-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 12px; }.register-grid :deep(.n-form-item) { min-width: 0; margin-bottom: 13px; }.register-grid :deep(.n-form-item-feedback-wrapper) { display: none; }
.register-steps { display: flex; align-items: center; justify-content: center; gap: 9px; height: 20px; margin: 0 0 17px; }.register-steps span { width: 18px; height: 18px; display: grid; place-items: center; border: 1px solid #cbd3cd; border-radius: 50%; color: #9aa19b; font-size: 10px; font-weight: 680; }.register-steps span.active { border-color: #176b59; background: #176b59; color: #fff; }.register-steps i { width: 30px; height: 1px; background: #dce1dc; }.register-steps i.active { background: #75af98; }.register-back { min-height: 19px; margin: -4px 0 11px; }.register-back button { padding: 0; border: 0; background: transparent; color: #7b837d; font-size: 12px; cursor: pointer; }.register-back button:hover { color: #176b59; }

:global(.auth-modal .n-card) { max-height: calc(100vh - 32px); overflow: auto; padding: 0; border: 1px solid #d9ddd8; border-radius: 8px; box-shadow: 0 28px 70px rgba(20, 28, 23, 0.24); }
:global(.auth-modal .n-card__header) { display: none; }
:global(.auth-modal .n-card__content) { padding: 0; }
.auth-layout { display: grid; grid-template-columns: minmax(235px, 0.78fr) minmax(350px, 1.22fr); min-height: 462px; }
.auth-context { display: flex; flex-direction: column; padding: 30px; background: #1c2420; color: #eef0e9; }.auth-context-brand { gap: 9px; font-size: 13px; font-weight: 650; }.auth-context .auth-mark { background: #f0f2eb; }.auth-context .auth-mark i { background: #1c2420; }.auth-context-copy { margin-top: auto; }.auth-context-copy > span { color: #aeb8b0; font-size: 10px; font-weight: 650; letter-spacing: 0.7px; }.auth-context-copy h2 { margin: 11px 0 0; color: #fff; font-size: 25px; font-weight: 670; line-height: 1.25; }.auth-context-copy p { margin: 8px 0 0; color: #bac3bb; font-size: 12px; }
.auth-context-card { flex-wrap: wrap; gap: 8px; padding: 13px; margin-top: 24px; border: 1px solid #47514a; border-radius: 6px; background: #28322d; color: #c8d0c9; font-size: 10px; }.auth-context-card > div { width: 100%; display: flex; align-items: center; gap: 6px; }.context-card-dot { width: 5px; height: 5px; border-radius: 50%; background: #1fa67a; }.auth-context-card strong { width: 100%; color: #f6f7f3; font-size: 12px; font-weight: 620; }.auth-context-card small { display: flex; align-items: center; gap: 7px; color: #adb8af; font-size: 10px; }.mini-avatars { display: inline-flex; }.mini-avatars i { width: 15px; height: 15px; display: grid; place-items: center; margin-left: -3px; border: 1px solid #28322d; border-radius: 50%; background: #dce7df; color: #176b59; font-size: 7px; font-style: normal; font-weight: 700; }.mini-avatars i:last-child { background: #e8e0c9; color: #8b6c1d; }

.auth-content { padding: 37px 34px 30px; background: #fff; }.auth-eyebrow { color: #79827b; font-size: 10px; font-weight: 680; letter-spacing: 0.75px; }.auth-heading h2 { margin: 10px 0 0; color: #1c2420; font-size: 23px; font-weight: 680; }.auth-heading p { margin: 7px 0 0; color: #7c837d; font-size: 13px; }.auth-form { margin-top: 27px; }.auth-form :deep(.n-form-item) { margin-bottom: 15px; }.auth-form :deep(.n-form-item-label__text) { color: #465049; font-size: 12px; font-weight: 620; }.auth-form :deep(.n-form-item-feedback__line) { color: #8c948e; font-size: 11px; }.auth-form :deep(.n-input) { --n-height: 40px; --n-border: 1px solid #d7dcd7; --n-border-hover: 1px solid #9bac9f; --n-border-focus: 1px solid #176b59; --n-box-shadow-focus: 0 0 0 3px rgba(23, 107, 89, 0.11); --n-border-radius: 6px; }.auth-form :deep(.n-button) { --n-height: 42px; font-weight: 650; }.auth-form :deep(.n-button__content) { gap: 20px; }.auth-links { min-height: 26px; justify-content: space-between; gap: 12px; margin: -2px 0 17px; color: #7f867f; font-size: 12px; }.auth-links-single { justify-content: flex-end; }.auth-links button { padding: 0; border: 0; background: transparent; color: #176b59; font: inherit; font-weight: 620; cursor: pointer; }

@keyframes enter-up { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: translateY(0); } }
@keyframes scene-in { from { opacity: 0; transform: translate(18px, 10px) scale(0.985); } to { opacity: 1; transform: translate(0) scale(1); } }
@keyframes grow-progress { from { transform: scaleX(0); } to { transform: scaleX(1); } }
@keyframes live-breathe { 0%,100% { box-shadow: 0 0 0 0 rgba(31,166,122,0.25); } 50% { box-shadow: 0 0 0 4px rgba(31,166,122,0); } }
@keyframes row-arrive { from { opacity: 0; transform: translateX(-6px); } to { opacity: 1; transform: translateX(0); } }
@keyframes stream { 0%,100% { opacity: 0.35; transform: scaleX(0.72); } 50% { opacity: 1; transform: scaleX(1); } }
@keyframes note-in { from { opacity: 0; transform: translateY(-6px); } to { opacity: 1; transform: translateY(0); } }
@keyframes dot-step { 0%, 100% { opacity: 0.3; transform: translateY(0); } 50% { opacity: 1; transform: translateY(-1px); } }
@keyframes rail-breathe { 0%,100% { box-shadow: 0 0 0 0 rgba(223, 164, 64, 0.26); } 50% { box-shadow: 0 0 0 4px rgba(223, 164, 64, 0); } }

@media (prefers-reduced-motion: reduce) { .entry-status,.entry-copy h1,.entry-headline,.entry-description,.entry-actions,.entry-signals,.application-scene,.scene-sync-note,.scene-sync-note i,.scene-presence-note,.scene-presence-note > i,.scene-ai-note,.scene-ai-note > i,.rail-event.current .rail-dot,.progress-track i,.live-dot,.activity-row,.ai-dock-copy b { animation: none; } }

@media (max-width: 1050px) { .access-main { width: min(800px, calc(100% - 56px)); grid-template-columns: 1fr; gap: 34px; padding: 34px 0 40px; }.entry-copy { text-align: center; }.entry-status,.entry-actions,.entry-signals { justify-content: center; }.entry-description { margin-right: auto; margin-left: auto; }.delivery-trace,.activity-rail { display: none; }.scene-stage { max-width: 800px; width: 100%; margin: 0 auto; }.application-scene { width: 100%; margin: 0 auto; } }

@media (max-width: 640px) { .access-page { grid-template-rows: 62px 1fr 38px; overflow: clip; }.access-header { padding: 0 20px; }.brand-tag,.header-actions > span { display: none; }.access-main { width: min(100% - 32px, 520px); gap: 30px; padding: 32px 0; }.entry-headline { font-size: 37px; }.entry-description { max-width: 330px; font-size: 14px; }.entry-signals { gap: 12px; margin-top: 32px; font-size: 11px; }.scene-stage { padding: 15px 8px 0 0; }.scene-sync-note { right: 0; }.scene-presence-note,.scene-ai-note { display: none; }.application-scene { border-radius: 7px; }.scene-chrome { height: 43px; padding: 0 11px; }.scene-brand span:last-child,.scene-center { display: none; }.scene-body { min-height: 360px; grid-template-columns: 45px 1fr; }.scene-sidebar { padding: 9px 6px; }.workspace-switcher { justify-content: center; padding: 0; margin-bottom: 10px; }.workspace-switcher > span:not(.switcher-mark),.workspace-switcher b,.scene-nav { font-size: 0; }.scene-nav { justify-content: center; padding: 0; }.scene-nav > span:not(.nav-badge) { width: 14px; height: 14px; }.nav-badge,.scene-sidebar-foot { display: none; }.scene-content { padding: 17px 14px; }.project-title-row h2 { font-size: 17px; }.project-title-row p { font-size: 10px; }.scene-add { display: none; }.project-progress { margin-top: 16px; }.scene-grid { grid-template-columns: 1fr; }.activity-panel { display: none; }.task-row { min-height: 43px; }.access-footer { padding: 0 20px; }.access-footer span:last-child { display: none; }.auth-surface-top { padding: 0 20px; }.auth-sheet { padding: 22px 20px 23px; }.auth-sheet .auth-form { margin-top: 18px; } }
/* Poster composition: the interface is the hero, surrounding layers only create depth. */
.access-page { background: #f6f7f4; }
.access-header { border-bottom-color: #e3e5e0; background: #f6f7f4; }
.access-main { width: min(1320px, calc(100% - 96px)); grid-template-columns: minmax(340px, 0.72fr) minmax(650px, 1.28fr); gap: 38px; }
.entry-copy { max-width: 410px; }
.entry-headline { font-size: clamp(40px, 3.5vw, 56px); line-height: 1.08; }
.entry-description { max-width: 330px; }
.poster-stage { --poster-x: 0deg; --poster-y: 0deg; min-width: 0; height: min(620px, calc(100vh - 160px)); min-height: 500px; display: grid; place-items: center; cursor: default; }
.poster-parallax { position: relative; width: min(760px, 100%); height: 100%; transform: perspective(1500px) rotateY(var(--poster-x)) rotateX(var(--poster-y)); transform-style: preserve-3d; transition: transform 420ms cubic-bezier(.2,.8,.2,1); }
.poster-sheet { position: absolute; display: block; box-sizing: border-box; border: 1px solid #d9ded8; border-radius: 8px; background: #eef1ed; box-shadow: 0 20px 42px rgba(38, 48, 41, 0.06); }
.poster-sheet-back { top: 34px; right: 50px; bottom: 42px; left: 46px; opacity: 0.7; transform: translateZ(-60px) rotate(-2.2deg); animation: sheet-back-float 9s ease-in-out infinite; }
.poster-sheet-side { top: 84px; right: 8px; bottom: 78px; left: 114px; background: #f0f2ef; opacity: 0.84; transform: translateZ(-30px) rotate(1.6deg); animation: sheet-side-float 11s ease-in-out infinite; }
.poster-rule { position: absolute; z-index: 1; width: 58px; height: 58px; border-color: #c9d9cf; border-style: solid; opacity: 0.9; }.poster-rule-top { top: 0; left: 12px; border-width: 1px 0 0 1px; }.poster-rule-bottom { right: 0; bottom: 10px; border-width: 0 1px 1px 0; }
.poster-stage .scene-stage { position: absolute; z-index: 3; top: 58px; right: 28px; bottom: 62px; left: 64px; min-width: 0; padding: 0; }.poster-stage .application-scene { height: 100%; border-color: #d6dcd6; box-shadow: 0 32px 66px rgba(34, 43, 37, 0.15), 0 5px 12px rgba(34, 43, 37, 0.06); animation: workspace-float 7.6s ease-in-out infinite; }.poster-stage .scene-body { min-height: 100%; }.poster-stage .scene-sync-note { top: -17px; right: -19px; background: #fbfdfb; }.poster-stage .scene-presence-note { bottom: 36px; left: -45px; }
.poster-avatar { position: absolute; z-index: 4; display: grid; place-items: center; width: 39px; height: 39px; border: 1px solid #e2e5e1; border-radius: 50%; background: #fff; box-shadow: 0 12px 24px rgba(31, 48, 38, 0.12); }.poster-avatar span { width: 27px; height: 27px; display: grid; place-items: center; border-radius: 50%; font-size: 10px; font-weight: 700; }.poster-avatar i { position: absolute; width: 7px; height: 7px; right: 3px; bottom: 3px; border: 1px solid #fff; border-radius: 50%; background: #1fa67a; }.poster-avatar-left { left: 16px; bottom: 126px; animation: avatar-drift-one 6.4s ease-in-out infinite; }.poster-avatar-left span { background: #e2ede6; color: #176b59; }.poster-avatar-right { top: 72px; right: -4px; animation: avatar-drift-two 7.2s ease-in-out infinite; }.poster-avatar-right span { background: #efe0dc; color: #a64b42; }
.poster-ai-pulse { position: absolute; z-index: 4; right: -18px; bottom: 150px; display: flex; align-items: center; gap: 3px; min-height: 27px; padding: 0 9px; border: 1px solid #d8e5dc; border-radius: 5px; background: #f8fbf8; box-shadow: 0 10px 22px rgba(31, 48, 38, 0.09); color: #314237; font-size: 8px; font-weight: 720; animation: ai-drift 8s ease-in-out infinite; }.poster-ai-pulse > i { width: 3px; height: 3px; border-radius: 50%; background: #83a892; animation: dot-step 1.6s ease-in-out infinite; }.poster-ai-pulse > i:nth-of-type(2) { animation-delay: 140ms; }.poster-ai-pulse > i:nth-of-type(3) { animation-delay: 280ms; }
.scene-nav { transition: color 180ms ease, background-color 180ms ease; }.scene-nav:not(.active) { opacity: 0.8; }.scene-nav.active { box-shadow: inset 2px 0 0 #1fa67a; }
.task-row { transition: transform 180ms ease, background-color 180ms ease; }.task-row:hover { transform: translateX(3px); background: #fbfcfa; }.activity-item { transition: transform 180ms ease; }.activity-item:hover { transform: translateX(2px); }

:global(.auth-modal .n-card) { border-color: #dfe3de; border-radius: 8px; box-shadow: 0 28px 76px rgba(30, 40, 33, 0.23), 0 4px 14px rgba(30, 40, 33, 0.08); }
.auth-surface { position: relative; background: #fff; }.auth-surface::before { position: absolute; top: 0; left: 26px; width: 48px; height: 2px; content: ''; background: #1fa67a; }.auth-surface-top { height: 68px; padding: 0 28px; }.auth-sheet { padding: 24px 28px 30px; }.auth-mode-switch { margin: 0; background: #eff2ee; }.auth-mode-switch button { height: 34px; }.auth-sheet .auth-form { margin-top: 22px; }.auth-form :deep(.n-form-item) { margin-bottom: 17px; }.auth-form :deep(.n-input) { --n-height: 43px; --n-border: 1px solid #d9ded9; --n-color: #fdfefd; }.auth-form :deep(.n-button) { --n-height: 43px; }.register-steps { margin-bottom: 19px; }.register-back { margin-top: -2px; }

@keyframes workspace-float { 0%,100% { transform: translateY(0) rotateX(0.2deg); } 50% { transform: translateY(-8px) rotateX(-0.3deg); } }
@keyframes sheet-back-float { 0%,100% { transform: translateZ(-60px) rotate(-2.2deg) translateY(0); } 50% { transform: translateZ(-60px) rotate(-2.2deg) translateY(8px); } }
@keyframes sheet-side-float { 0%,100% { transform: translateZ(-30px) rotate(1.6deg) translateY(0); } 50% { transform: translateZ(-30px) rotate(1.6deg) translateY(-6px); } }
@keyframes avatar-drift-one { 0%,100% { transform: translateY(0) rotate(0deg); } 50% { transform: translateY(-10px) rotate(-3deg); } }
@keyframes avatar-drift-two { 0%,100% { transform: translateY(0) rotate(0deg); } 50% { transform: translateY(8px) rotate(3deg); } }
@keyframes ai-drift { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-7px); } }

@media (max-width: 1050px) { .access-main { width: min(800px, calc(100% - 48px)); grid-template-columns: 1fr; padding-bottom: 54px; }.entry-copy { max-width: none; }.poster-stage { height: 560px; }.poster-parallax { width: min(720px, 100%); } }
@media (max-width: 640px) { .access-main { width: min(100% - 30px, 520px); }.poster-stage { min-height: 380px; height: 440px; }.poster-sheet-back { top: 19px; right: 24px; bottom: 28px; left: 18px; }.poster-sheet-side { top: 52px; right: 0; bottom: 43px; left: 48px; }.poster-stage .scene-stage { top: 37px; right: 9px; bottom: 42px; left: 27px; }.poster-avatar,.poster-ai-pulse,.poster-rule,.poster-stage .scene-presence-note { display: none; }.poster-stage .scene-sync-note { right: -4px; }.auth-sheet { padding: 22px 20px 24px; }.auth-surface-top { padding: 0 20px; }.entry-headline { font-size: 38px; } }
@media (prefers-reduced-motion: reduce) { .poster-parallax,.poster-sheet-back,.poster-sheet-side,.poster-stage .application-scene,.poster-avatar-left,.poster-avatar-right,.poster-ai-pulse { animation: none; transition: none; transform: none; } }

/* Final composition pass: fewer signals, a single visual axis, slower movement. */
.access-main { width: min(1280px, calc(100% - 112px)); grid-template-columns: minmax(400px, 0.76fr) minmax(650px, 1.24fr); gap: 62px; }
.entry-copy { max-width: 430px; }.entry-headline { max-width: 420px; font-size: clamp(40px, 3vw, 46px); line-height: 1.12; }.entry-description { max-width: 320px; margin-top: 20px; }.entry-signals { margin-top: 38px; }
.poster-stage { height: min(592px, calc(100vh - 176px)); min-height: 500px; }.poster-parallax { width: min(746px, 100%); }.poster-sheet-back { top: 42px; right: 42px; bottom: 46px; left: 42px; opacity: 0.58; }.poster-sheet-side { top: 76px; right: 2px; bottom: 74px; left: 104px; opacity: 0.65; }.poster-stage .scene-stage { top: 61px; right: 24px; bottom: 60px; left: 60px; animation: poster-enter 720ms cubic-bezier(.2,.8,.2,1) both; }.poster-stage .application-scene { animation: workspace-float 8.4s 720ms ease-in-out infinite; }.poster-stage .scene-sync-note { top: -15px; right: -15px; }.poster-stage .scene-presence-note { left: -38px; bottom: 38px; }.poster-ai-pulse { right: -8px; bottom: 142px; }
.auth-surface::before { width: 36px; }.auth-surface-top { height: 64px; }.auth-sheet { padding: 22px 28px 28px; }.auth-mode-switch button { height: 33px; }.auth-form :deep(.n-form-item) { margin-bottom: 15px; }.register-steps { margin-bottom: 17px; }

@keyframes poster-enter { from { opacity: 0; transform: translateY(18px) scale(0.985); } to { opacity: 1; transform: translateY(0) scale(1); } }

@media (max-width: 1050px) { .access-main { width: min(800px, calc(100% - 48px)); grid-template-columns: 1fr; }.entry-copy { max-width: none; }.entry-headline { max-width: none; }.poster-stage { height: 560px; }.poster-parallax { width: min(720px, 100%); } }
@media (max-width: 640px) { .access-main { width: min(100% - 30px, 520px); }.entry-headline { font-size: 38px; }.poster-stage { min-height: 380px; height: 440px; }.poster-stage .scene-stage { top: 37px; right: 9px; bottom: 42px; left: 27px; }.poster-rule,.poster-ai-pulse,.poster-stage .scene-presence-note { display: none; } }
@media (prefers-reduced-motion: reduce) { .poster-stage .scene-stage { animation: none; }.poster-stage .application-scene { animation: none; } }

/* Final fit and contrast calibration. */
.poster-stage { height: clamp(530px, 63vh, 640px); min-height: 530px; }.poster-sheet-back { top: 36px; right: 48px; bottom: 34px; left: 48px; background: #ebefea; opacity: 0.76; box-shadow: 0 18px 36px rgba(38, 48, 41, 0.05); }.poster-sheet-side { top: 64px; right: 8px; bottom: 58px; left: 96px; background: #f1f3f0; opacity: 0.84; }.poster-stage .scene-stage { top: 44px; right: 28px; bottom: 28px; left: 60px; }.poster-stage .application-scene { box-shadow: 0 26px 50px rgba(34, 43, 37, 0.13), 0 3px 8px rgba(34, 43, 37, 0.06); }.poster-stage .scene-body { min-height: 0; height: calc(100% - 50px); }.poster-stage .scene-content { padding: 20px 22px 14px; }.poster-stage .project-title-row { margin-top: 8px; }.poster-stage .project-progress { margin-top: 16px; padding: 11px 12px 9px; }.poster-stage .scene-grid { gap: 10px; margin-top: 10px; }.poster-stage .panel-heading { height: 34px; }.poster-stage .task-row { min-height: 41px; padding: 6px 9px; }.poster-stage .ai-dock { min-height: 32px; margin-top: 10px; }.poster-stage .project-progress + .scene-grid { margin-top: 10px; }.poster-stage .scene-sync-note { top: -13px; right: -13px; }.poster-stage .scene-presence-note { bottom: 27px; left: -32px; }.poster-ai-pulse { right: -5px; bottom: 132px; }
@media (max-height: 820px) and (min-width: 1051px) { .poster-stage { height: 530px; }.poster-stage .scene-stage { top: 38px; right: 24px; bottom: 20px; left: 52px; }.poster-stage .scene-content { padding: 17px 19px 12px; }.poster-stage .project-progress { margin-top: 13px; }.poster-stage .task-row { min-height: 38px; }.poster-stage .ai-dock { display: none; } }
</style>
