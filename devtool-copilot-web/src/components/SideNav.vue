<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useChatStore } from '../stores/chat'
import { useRealtimeStore } from '../stores/realtime'

const route = useRoute()

const auth = useAuthStore()
const chat = useChatStore()
const rt = useRealtimeStore()

onMounted(() => {
  if (!auth.isAuthed) return
  chat.loadConversations().catch(() => {})
  rt.subscribeChat()
})

type NavKey =
  | 'workspace'
  | 'collab-center'
  | 'chat'
  | 'board'
  | 'lifecycle'
  | 'inbox'
  | 'ai-chat'
  | 'admin-users'

type NavItem = { key: NavKey; label: string; to: any }
const ORDER_KEY = 'dtc_sidenav_order_v1'
const DEFAULT_ORDER: NavKey[] = ['workspace', 'collab-center', 'chat', 'board', 'lifecycle', 'inbox', 'ai-chat']

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
    workspace: { key: 'workspace', label: '工作台', to: { name: 'workspace' } },
    'collab-center': { key: 'collab-center', label: '团队协作', to: { name: 'collab-center' } },
    chat: { key: 'chat', label: '消息', to: { name: 'chat' } },
    board: { key: 'board', label: '任务看板', to: { name: 'board' } },
    lifecycle: { key: 'lifecycle', label: '生命周期', to: { name: 'lifecycle-center' } },
    inbox: { key: 'inbox', label: '收件箱', to: { name: 'inbox' } },
    'ai-chat': { key: 'ai-chat', label: 'AI 助手', to: { name: 'ai-chat' } }
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
  if (name === 'collab-center') return 'collab-center'
  if (name === 'chat') return 'chat'
  if (name === 'board') return 'board'
  if (name === 'lifecycle-center' || name === 'task-lifecycle') return 'lifecycle'
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
      <div class="mark" aria-hidden="true"><i /><i /></div>
      <div class="btext">
        <div class="bname">DevTool Copilot</div>
        <div class="muted bsub">TEAM WORKSPACE</div>
      </div>
    </div>

    <nav class="nav">
      <div class="nav-caption">工作空间</div>
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
          <svg v-if="it.key === 'collab-center'" width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path
              d="M8 9.2a2.7 2.7 0 1 1 5.4 0 2.7 2.7 0 0 1-5.4 0Z"
              stroke="currentColor"
              stroke-width="1.6"
            />
            <path
              d="M4.8 17.8c.9-2.3 3-3.7 5.9-3.7 1.1 0 2 .2 2.9.5"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
            />
            <path
              d="M15.8 10.1a2.2 2.2 0 1 1 4.4 0 2.2 2.2 0 0 1-4.4 0Z"
              stroke="currentColor"
              stroke-width="1.6"
            />
            <path
              d="M14.9 18.2c.7-1.8 2.3-2.9 4.5-2.9 1.1 0 2 .2 2.8.7"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
            />
          </svg>

          <svg v-else-if="it.key === 'chat'" width="20" height="20" viewBox="0 0 24 24" fill="none">
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

          <svg v-else-if="it.key === 'lifecycle'" width="20" height="20" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="8.4" stroke="currentColor" stroke-width="1.6" />
            <path d="M12 7.6V12l3 1.8" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
            <path d="M3 12h2.2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            <path d="M18.8 12H21" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
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
          <span v-if="it.key === 'chat' && chat.totalUnread > 0" class="nav-badge">
            {{ chat.totalUnread > 99 ? '99+' : chat.totalUnread }}
          </span>
        </span>
      </router-link>
    </nav>

    <div class="spacer" />

    <div class="footer">
      <div class="hint">个人工作区</div>
    </div>
  </div>
</template>

<style scoped>
.shell {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 14px 12px 12px;
  gap: 14px;
  overflow: hidden;
}
.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 3px 6px 10px;
}
.mark {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  background: #1769e0;
}
.mark i {
  display: block;
  width: 3px;
  height: 11px;
  border-radius: 2px;
  background: #fff;
}
.mark i:last-child {
  height: 7px;
  opacity: 0.78;
}
.btext {
  display: grid;
  gap: 1px;
  min-width: 0;
}
.bname {
  font-weight: 650;
  letter-spacing: 0;
  font-size: 13px;
  color: #1f2329;
}
.bsub {
  font-size: 10px;
  letter-spacing: 0.5px;
}
.nav {
  display: grid;
  gap: 2px;
  padding: 4px 0;
  overflow: hidden;
}
.nav-caption {
  padding: 0 10px 7px;
  color: #9197a1;
  font-size: 10px;
  font-weight: 650;
  letter-spacing: 0.6px;
}
.nav-item {
  width: 100%;
  border-radius: 6px;
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 8px 10px;
  color: #646a73;
  border: 1px solid transparent;
  background: transparent;
  transition: background-color 120ms ease, border-color 120ms ease, color 120ms ease;
}
.nav-item:hover {
  background: #f4f6f8;
  border-color: transparent;
  color: #1f2329;
}
.nav-item.active {
  background: #eaf2ff;
  border-color: transparent;
  color: #1769e0;
}
.icon {
  display: grid;
  place-items: center;
  color: #7b818a;
  flex: 0 0 16px;
}
.icon svg {
  width: 16px;
  height: 16px;
}
.nav-item.active .icon {
  color: #1769e0;
}
.txt {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex: 1;
}
.label {
  font-size: 13px;
  font-weight: 520;
  letter-spacing: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.nav-badge {
  min-width: 16px;
  height: 16px;
  padding: 0 5px;
  border-radius: 8px;
  background: #e5484d;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  display: grid;
  place-items: center;
  margin-left: auto;
  flex: 0 0 auto;
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
  height: 34px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 10px;
  border: 1px solid #edf0f2;
  background: #fafbfc;
  color: #8b9199;
}
.hint {
  font-size: 11px;
  letter-spacing: 0;
  font-weight: 550;
}
</style>
