<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, type RouteLocationNormalizedLoaded } from 'vue-router'
import {
  NConfigProvider,
  NLayout,
  NLayoutSider,
  NLayoutHeader,
  NLayoutContent,
  NMessageProvider,
  NDialogProvider,
  NNotificationProvider,
  NLoadingBarProvider
} from 'naive-ui'
import NotificationBridge from '../components/NotificationBridge.vue'
import SideNav from '../components/SideNav.vue'
import TopBar from '../components/TopBar.vue'
import { buildThemeOverrides } from '../styles/theme'
import { usePreferenceStore } from '../stores/preference'

const route = useRoute()
const pref = usePreferenceStore()
const switching = ref(false)
let switchTimer: ReturnType<typeof setTimeout> | null = null

const themeOverrides = computed(() => buildThemeOverrides(pref.accent))

function viewKey(r: RouteLocationNormalizedLoaded) {
  return JSON.stringify({
    name: r.name ? String(r.name) : '',
    params: r.params ?? {}
  })
}

onMounted(async () => {
  pref.apply()
  try {
    await pref.load()
  } catch {
  }
})

watch(
  () => viewKey(route),
  async () => {
    if (switchTimer) clearTimeout(switchTimer)
    switching.value = true
    await nextTick()
    switchTimer = setTimeout(() => {
      switching.value = false
    }, 90)
  },
  { flush: 'post' }
)
</script>

<template>
  <n-config-provider :theme="null" :theme-overrides="themeOverrides">
    <n-message-provider>
      <n-dialog-provider>
        <n-notification-provider>
          <notification-bridge />
          <n-loading-bar-provider>
            <n-layout class="app-shell" has-sider>
              <n-layout-sider
                collapse-mode="width"
                :collapsed-width="84"
                :width="260"
                class="app-sider"
              >
                <side-nav />
              </n-layout-sider>

              <n-layout>
                <n-layout-header class="app-header">
                  <top-bar />
                </n-layout-header>
                <n-layout-content class="app-content">
                  <div class="view-stage" :class="{ switching }">
                    <router-view v-slot="{ Component, route }">
                      <component :is="Component" :key="viewKey(route)" class="view" />
                    </router-view>
                  </div>
                </n-layout-content>
              </n-layout>
            </n-layout>
          </n-loading-bar-provider>
        </n-notification-provider>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<style scoped>
.app-shell {
  height: 100vh;
  background: transparent;
}
.app-sider {
  background: transparent;
  border-right: 1px solid var(--stroke);
  transition: width 180ms ease, border-color 180ms ease;
}
.app-header {
  height: 52px;
  display: flex;
  align-items: center;
  background: transparent;
}
.app-content {
  padding: 18px 28px 34px;
  overflow-y: auto;
  overflow-x: hidden;
  transition: padding 180ms ease;
}

.app-sider :deep(.n-layout-sider-scroll-container) {
  overflow: hidden !important;
  scrollbar-width: none;
  -ms-overflow-style: none;
}
.app-sider :deep(.n-layout-sider-scroll-container::-webkit-scrollbar) {
  width: 0;
  height: 0;
}

.view-stage {
  position: relative;
  transition: opacity 90ms ease, filter 90ms ease;
}
:deep(.view) {
  width: 100%;
}
.view-stage.switching {
  opacity: 0.975;
  filter: saturate(0.99);
}
</style>
