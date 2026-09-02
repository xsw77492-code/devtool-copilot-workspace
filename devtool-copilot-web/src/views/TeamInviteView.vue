<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NSpin, useMessage } from 'naive-ui'
import { projectCollabApi } from '../api/projectCollab'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const token = computed(() => String(route.query.token || ''))
const loading = ref(false)

async function accept() {
  if (!token.value) {
    message.error('邀请链接无效')
    return
  }
  loading.value = true
  try {
    const groupId = await projectCollabApi.acceptTeamGroupInvite(token.value)
    message.success('已加入团队群组')
    router.replace({ name: 'collab-center', query: { tab: 'groups', groupId: String(groupId) } })
  } catch (error: any) {
    message.error(error?.message || '加入群组失败')
  } finally {
    loading.value = false
  }
}

async function reject() {
  if (!token.value) {
    message.error('邀请链接无效')
    return
  }
  loading.value = true
  try {
    await projectCollabApi.rejectTeamGroupInvite(token.value)
    message.success('已拒绝邀请')
    router.replace({ name: 'board' })
  } catch (error: any) {
    message.error(error?.message || '操作失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="invitePage">
    <section class="invitePanel">
      <div class="brandMark">D</div>
      <div class="eyebrow"><span />团队协作</div>
      <h1>你收到一个群组邀请</h1>
      <p>接受后，你可以查看该群组的成员与协作动态。当前登录账号的邮箱需要与受邀邮箱一致。</p>
      <n-spin :show="loading">
        <div class="actions">
          <button class="primaryButton" type="button" @click="accept">接受并加入</button>
          <button class="secondaryButton" type="button" @click="reject">拒绝</button>
        </div>
      </n-spin>
    </section>
  </main>
</template>

<style scoped>
.invitePage { min-height: 100vh; display: grid; place-items: center; padding: 24px; background: #f7f8f7; color: #1d2b27; }
.invitePanel { width: min(430px, 100%); padding: 30px; border: 1px solid #e0e7e3; border-radius: 8px; background: #fff; box-shadow: 0 18px 42px rgba(30, 55, 46, .08); }
.brandMark { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 7px; background: #1d2b27; color: #fff; font-size: 13px; font-weight: 800; }.eyebrow { display: inline-flex; align-items: center; gap: 7px; margin-top: 28px; color: #67766f; font-size: 12px; font-weight: 700; }.eyebrow span { width: 7px; height: 7px; border-radius: 50%; background: #22a883; }.invitePanel h1 { margin: 8px 0 0; font-size: 23px; font-weight: 720; letter-spacing: 0; }.invitePanel p { margin: 11px 0 0; color: #71807a; font-size: 13px; line-height: 1.7; }.actions { display: grid; grid-template-columns: 1fr 100px; gap: 9px; margin-top: 25px; }.primaryButton, .secondaryButton { min-height: 38px; border: 1px solid transparent; border-radius: 6px; font: inherit; font-size: 13px; font-weight: 680; cursor: pointer; }.primaryButton { background: #137c67; color: #fff; }.primaryButton:hover { background: #0d6d5a; }.secondaryButton { border-color: #d9e2de; background: #fff; color: #52625c; }.secondaryButton:hover { background: #f6f9f7; }
<style scoped>
.primaryButton { background: #17191b !important; }
.primaryButton:hover { background: #34383b !important; }
.secondaryButton { border-color: #d9dfe1 !important; color: #525a60 !important; }
.secondaryButton:hover { background: #f3f4f5 !important; }
</style>
