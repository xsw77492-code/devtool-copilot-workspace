<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()

const auth = useAuthStore()

type NavKey =
  | 'dashboard'
  | 'workspace'
  | 'board'
  | 'inbox'
  | 'ai-chat'
  | 'admin-users'

type NavItem = { key: NavKey; label: string; to: any }
const ORDER_KEY = 'dtc_sidenav_order_v1'
const DEFAULT_ORDER: NavKey[] = ['dashboard', 'workspace', 'board', 'inbox', 'ai-chat']

function loadOrder(): NavKey[] {
  try {
    const raw = localStorage.getItem(ORDER_KEY)
    if (!raw) return DEFAULT_ORDER.slice()
    const arr = JSON.parse(raw)
    if (!Array.isArray(arr)) return DEFAULT_ORDER.slice()
    const set = new Set(DEFAULT_ORDER)
    const out: NavKey[] = []
    for (const k of arr) {
      if (set.has(k)) out.push(k)
    }
    for (const k of DEFAULT_ORDER) {
      if (!out.includes(k)) out.push(k)
    }
    return out
  } catch {
    return DEFAULT_ORDER.slice()
  }
}

const order = ref<NavKey[]>(loadOrder())

watch(
  order,
  (v) => {
    try {
      localStorage.setItem(ORDER_KEY, JSON.stringify(v))
    } catch {
    }
  },
  { deep: true }
)

const items = computed(() => {
  const baseByKey: Record<string, NavItem> = {
    dashboard: { key: 'dashboard', label: '数据面板', to: { name: 'dashboard' } },
    workspace: { key: 'workspace', label: '工作台', to: { name: 'workspace' } },
    board: { key: 'board', label: '看板', to: { name: 'board' } },
    inbox: { key: 'inbox', label: '收件箱', to: { name: 'inbox' } },
    'ai-chat': { key: 'ai-chat', label: 'AI', to: { name: 'ai-chat' } }
  }
  const out: NavItem[] = []
  for (const k of order.value) {
    const it = baseByKey[k]
    if (it) out.push(it)
  }
  if (auth.role === 'ADMIN') {
    out.push({ key: 'admin-users', label: '账号管理', to: { name: 'admin-users' } })
  }
  return out
})

const dragging = ref<NavKey | null>(null)

function onDragStart(key: NavKey) {
  if (key === 'admin-users') return
  dragging.value = key
}

function onDrop(overKey: NavKey) {
  const from = dragging.value
  dragging.value = null
  if (!from) return
  if (from === 'admin-users' || overKey === 'admin-users') return
  if (from === overKey) return

  const arr = order.value.slice()
  const fromIdx = arr.indexOf(from)
  const toIdx = arr.indexOf(overKey)
  if (fromIdx < 0 || toIdx < 0) return
  arr.splice(fromIdx, 1)
  arr.splice(toIdx, 0, from)
  order.value = arr
}

const active = computed<NavKey | null>(() => {
  const name = route.name ? String(route.name) : ''
  if (name === 'ai-chat') return 'ai-chat'
  if (name === 'ai-code-review' || name === 'ai-history') return 'ai-chat'
  if (name === 'admin-users') return 'admin-users'
  if (name === 'dashboard') return 'dashboard'
  if (name === 'board') return 'board'
  if (name === 'inbox') return 'inbox'
  if (name === 'workspace' || name === 'project-detail' || name === 'task-detail' || name === 'project-members' || name === 'project-activity') {
    return 'workspace'
  }
  return null
})
</script>

<template>
  <div class="shell">
    <div class="brand">
      <div class="mark" aria-hidden="true">
        <div class="dot" />
      </div>
      <div class="btext">
        <div class="bname">DevTool Copilot</div>
        <div class="muted bsub">AI 开发工作台</div>
      </div>
    </div>

    <nav class="nav">
      <router-link
        v-for="it in items"
        :key="it.key"
        class="nav-item"
        :class="{ active: active === it.key }"
        :to="it.to"
        :draggable="it.key !== 'admin-users'"
        @dragstart="onDragStart(it.key)"
        @dragover.prevent
        @drop.prevent="onDrop(it.key)"
      >
        <span class="icon" aria-hidden="true">
          <svg v-if="it.key === 'dashboard'" width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path
              d="M4 7.5C4 6.12 5.12 5 6.5 5h11C18.88 5 20 6.12 20 7.5v9c0 1.38-1.12 2.5-2.5 2.5h-11C5.12 19 4 17.88 4 16.5v-9Z"
              stroke="currentColor"
              stroke-width="1.6"
            />
            <path d="M7.5 9h9" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            <path d="M7.5 12.5h6.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            <path d="M7.5 16h5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
          </svg>

          <svg v-else-if="it.key === 'board'" width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path
              d="M5.5 6.5A2 2 0 0 1 7.5 4.5h9a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2h-9a2 2 0 0 1-2-2v-11Z"
              stroke="currentColor"
              stroke-width="1.6"
            />
            <path d="M12 5v14" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            <path d="M8.2 9.2h2.2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            <path d="M8.2 13h2.2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            <path d="M13.6 11.1h2.2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            <path d="M13.6 14.9h2.2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
          </svg>

          <svg v-else-if="it.key === 'inbox'" width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path
              d="M5 8.2a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2V18a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V8.2Z"
              stroke="currentColor"
              stroke-width="1.6"
            />
            <path
              d="M5 14h4l1.2 1.8a1.8 1.8 0 0 0 1.5.8h.6a1.8 1.8 0 0 0 1.5-.8L16.5 14H21"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linejoin="round"
            />
            <path d="M9 10.2h6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
          </svg>

          <svg v-else-if="it.key === 'ai-chat'" width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path
              d="M20 12c0 4.42-3.58 8-8 8-1.3 0-2.53-.31-3.62-.87L4 20l.9-3.7A7.97 7.97 0 0 1 4 12c0-4.42 3.58-8 8-8s8 3.58 8 8Z"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linejoin="round"
            />
            <path d="M8.3 12h.01" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" />
            <path d="M12 12h.01" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" />
            <path d="M15.7 12h.01" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" />
          </svg>
          <svg v-else-if="it.key === 'admin-users'" width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path
              d="M7.5 7.8a4.5 4.5 0 1 1 9 0 4.5 4.5 0 0 1-9 0Z"
              stroke="currentColor"
              stroke-width="1.6"
            />
            <path
              d="M4 20c1.3-3.4 4.3-5.4 8-5.4s6.7 2 8 5.4"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
            />
          </svg>
          <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path
              d="M8 4h8a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z"
              stroke="currentColor"
              stroke-width="1.6"
            />
            <path
              d="M9 8h6"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
            />
            <path
              d="M9 12h6"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
            />
            <path
              d="M9 16h4"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
            />
          </svg>
        </span>
        <span class="txt">
          <span class="label">{{ it.label }}</span>
        </span>
      </router-link>
    </nav>

    <div class="spacer" />

    <div class="footer">
      <div class="hint">DTC</div>
    </div>
  </div>
</template>

<style scoped>
.shell {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 14px 14px 18px;
  gap: 12px;
  overflow: hidden;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 6px 10px;
}
.mark {
  width: 38px;
  height: 38px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  border: 1px solid var(--stroke);
  background: rgba(15, 23, 42, 0.04);
}
.dot {
  width: 14px;
  height: 14px;
  border-radius: 999px;
  background: var(--accent);
  box-shadow: 0 0 0 5px rgba(20, 184, 166, 0.16);
}
.btext {
  display: grid;
  gap: 2px;
  min-width: 0;
}
.bname {
  font-weight: 760;
  letter-spacing: -0.2px;
  font-size: 13px;
  color: rgba(15, 23, 42, 0.92);
}
.bsub {
  font-size: 12px;
}
.nav {
  display: grid;
  gap: 8px;
  padding: 4px 4px;
  overflow: hidden;
}
.nav-item {
  width: 100%;
  border-radius: 12px;
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 12px 12px;
  color: rgba(15, 23, 42, 0.78);
  border: 1px solid transparent;
  background: transparent;
  transition: background-color 120ms ease, border-color 120ms ease, color 120ms ease;
}
.nav-item:hover {
  background: rgba(15, 23, 42, 0.04);
  border-color: rgba(15, 23, 42, 0.06);
  color: rgba(15, 23, 42, 0.92);
}
.nav-item.active {
  background: rgba(20, 184, 166, 0.10);
  border-color: rgba(20, 184, 166, 0.18);
  color: rgba(15, 23, 42, 0.95);
}
.icon {
  display: grid;
  place-items: center;
  color: rgba(15, 23, 42, 0.72);
}
.nav-item.active .icon {
  color: rgba(15, 23, 42, 0.92);
}
.txt {
  display: flex;
  align-items: center;
  min-width: 0;
}
.label {
  font-size: 16px;
  font-weight: 740;
  letter-spacing: -0.1px;
}
.nav-item[draggable='true'] {
  cursor: grab;
}
.nav-item[draggable='true']:active {
  cursor: grabbing;
}
.spacer {
  flex: 1;
}
.footer {
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: transparent;
  color: rgba(15, 23, 42, 0.52);
}
.hint {
  font-size: 12px;
  letter-spacing: 0.4px;
  font-weight: 650;
}
</style>
