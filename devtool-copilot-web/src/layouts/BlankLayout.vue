<script setup lang="ts">
import { NConfigProvider, NMessageProvider } from 'naive-ui'
import { buildThemeOverrides } from '../styles/theme'

const themeOverrides = buildThemeOverrides('#14b8a6')
</script>

<template>
  <n-config-provider :theme="null" :theme-overrides="themeOverrides">
    <n-message-provider>
      <router-view />
    </n-message-provider>
  </n-config-provider>
</template>
