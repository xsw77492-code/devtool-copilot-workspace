<script setup lang="ts">
import { NButton } from 'naive-ui'
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="wrap page">
    <div class="panel box">
      <div class="h1">404</div>
      <div class="muted">页面不存在</div>
      <div class="actions">
        <n-button text size="small" type="primary" class="pill" @click="router.push({ name: 'dashboard' })">
          返回工作台
        </n-button>
        <n-button text size="small" class="pill" @click="router.push({ name: 'login' })">登录</n-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.wrap {
  min-height: 100vh;
  display: grid;
  place-items: center;
}
.box {
  padding: 18px;
  width: min(520px, calc(100vw - 36px));
  text-align: center;
}
.actions {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 14px;
}
.pill {
  height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.05);
}
</style>
