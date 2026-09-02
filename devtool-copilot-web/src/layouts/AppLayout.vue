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
const isChat = computed(() => route.name === 'chat')
const isCollab = computed(() => route.name === 'collab-center')
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
                :collapsed-width="72"
                :width="236"
                class="app-sider"
              >
                <side-nav />
              </n-layout-sider>

              <n-layout class="app-main">
                <n-layout-header class="app-header">
                  <top-bar />
                </n-layout-header>
                <n-layout-content
                  class="app-content"
                  :class="{ 'chat-content': isChat, 'collab-content': isCollab }"
                >
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
  background: var(--bg0);
}
.app-main {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.app-sider {
  background: #fff;
  border-right: 1px solid #e7e9ed;
  transition: width 180ms ease, border-color 180ms ease;
}
.app-header {
  height: 52px;
  display: flex;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #e7e9ed;
}
.app-content {
  padding: 24px 28px 32px;
  background: var(--bg0);
  overflow-y: auto;
  overflow-x: hidden;
  transition: padding 180ms ease;
}
.app-content.chat-content {
  padding: 0;
  overflow: hidden;
}
.app-content.collab-content {
  padding: 0;
  overflow: hidden;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
/* naive-ui 的 n-layout-scroll-container 默认是 block，header/content 无法 flex 分配高度。
   协作页需要 content 撑满 100vh-52px，所以只对这个容器启用 flex column */
.app-main :deep(.n-layout-scroll-container:has(.app-content.collab-content)) {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.app-content.collab-content .view-stage,
.app-content.collab-content .view {
  height: 100%;
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
  opacity: 1;
  filter: none;
}

@media (max-width: 760px) {
  .app-content {
    padding: 16px 14px 24px;
  }
}
</style>
