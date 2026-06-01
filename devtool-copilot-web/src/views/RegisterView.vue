<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NButton, NConfigProvider, NForm, NFormItem, NInput, useMessage } from 'naive-ui'
import { authApi } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const message = useMessage()

const username = ref('')
const email = ref('')
const emailCode = ref('')
const password = ref('')
const confirmPassword = ref('')
const loading = ref(false)
const sendLoading = ref(false)
const cooldown = ref(0)
let cooldownTimer: number | null = null

const canSend = computed(() => cooldown.value <= 0 && !!email.value.trim() && !sendLoading.value)

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
  if (password.value !== confirmPassword.value) {
    message.error('两次密码不一致')
    return
  }
  loading.value = true
  try {
    let emailVerifyToken: string | null = null
    if (emailCode.value.trim()) {
      const res = await authApi.confirmEmailVerify({ email: email.value, code: emailCode.value.trim() })
      emailVerifyToken = res.verifyToken
    }
    await authApi.register({
      username: username.value,
      email: email.value,
      password: password.value,
      emailVerifyToken
    })
    await auth.login({ username: username.value, password: password.value })
    const redirect = (route.query.redirect as string | undefined) || '/dashboard'
    router.replace(redirect)
  } catch (e: any) {
    message.error(e?.message || '注册失败')
  } finally {
    loading.value = false
  }
}

async function sendCode() {
  if (!email.value.trim()) {
    message.error('请先填写邮箱')
    return
  }
  if (!canSend.value) return
  sendLoading.value = true
  try {
    await authApi.requestEmailVerify({ email: email.value.trim() })
    message.success('验证码已发送')
    cooldown.value = 60
    if (cooldownTimer) window.clearInterval(cooldownTimer)
    cooldownTimer = window.setInterval(() => {
      cooldown.value -= 1
      if (cooldown.value <= 0 && cooldownTimer) {
        window.clearInterval(cooldownTimer)
        cooldownTimer = null
      }
    }, 1000)
  } catch (e: any) {
    message.error(e?.message || '发送失败')
  } finally {
    sendLoading.value = false
  }
}

onBeforeUnmount(() => {
  if (cooldownTimer) window.clearInterval(cooldownTimer)
})
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
            <div class="heroTitle">创建账号</div>
            <div class="heroDesc">用于登录与找回密码。密码建议使用密码管理器生成强密码。</div>
            <div class="tips">
              <div class="tip">用户名：3–32 位，仅字母/数字/下划线</div>
              <div class="tip">密码：12–64 位，需包含大小写字母、数字、特殊字符</div>
            </div>
          </div>

          <div class="authCol">
            <div class="authCard">
              <div class="authTitle">注册</div>
              <div class="authSub">几秒钟完成注册</div>

              <n-form>
                <n-form-item label="用户名">
                  <n-input v-model:value="username" size="large" placeholder="例如: dev_user" />
                </n-form-item>
                <n-form-item label="邮箱">
                  <n-input v-model:value="email" size="large" placeholder="用于找回密码" />
                </n-form-item>
                <n-form-item label="邮箱验证码">
                  <div class="codeRow">
                    <n-input v-model:value="emailCode" size="large" placeholder="6 位数字（可选）" />
                    <n-button size="large" class="codeBtn" :disabled="!canSend" :loading="sendLoading" @click="sendCode">
                      {{ cooldown > 0 ? `${cooldown}s` : '发送验证码' }}
                    </n-button>
                  </div>
                </n-form-item>
                <n-form-item label="密码">
                  <n-input v-model:value="password" size="large" type="password" placeholder="设置强密码" />
                </n-form-item>
                <n-form-item label="确认密码">
                  <n-input v-model:value="confirmPassword" size="large" type="password" placeholder="再次输入密码" />
                </n-form-item>
                <n-button type="primary" size="large" block :loading="loading" attr-type="button" @click.prevent="submit">
                  创建账号
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

.tips {
  margin-top: 18px;
  display: grid;
  gap: 10px;
  color: rgba(15, 23, 42, 0.72);
  font-size: 13px;
}

.tip {
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 14px;
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

.codeRow {
  width: 100%;
  display: flex;
  gap: 10px;
  align-items: center;
}

.codeBtn {
  min-width: 120px;
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
