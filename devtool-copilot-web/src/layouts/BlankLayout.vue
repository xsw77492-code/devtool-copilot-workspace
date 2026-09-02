<script setup lang="ts">
import { computed } from 'vue'
import { NConfigProvider, NMessageProvider } from 'naive-ui'
import { buildThemeOverrides } from '../styles/theme'
import { usePreferenceStore } from '../stores/preference'

const pref = usePreferenceStore()
const themeOverrides = computed(() => buildThemeOverrides(pref.accent))
</script>

<template>
  <n-config-provider :theme="null" :theme-overrides="themeOverrides">
    <n-message-provider>
      <router-view />
    </n-message-provider>
  </n-config-provider>
</template>
