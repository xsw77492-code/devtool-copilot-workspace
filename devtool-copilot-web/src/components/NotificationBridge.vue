<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useNotification } from 'naive-ui'
import { useAuthStore } from '../stores/auth'
import { useNotificationStore } from '../stores/notification'
import { useRealtimeStore } from '../stores/realtime'
import type { NotificationItem } from '../api/notification'

const router = useRouter()
const auth = useAuthStore()
const ns = useNotificationStore()
const rt = useRealtimeStore()
const notify = useNotification()

let lastKey = ''
let lastAt = 0
function pushOnce(title: string, content: string, duration: number) {
  const key = `${title}||${content}`
  const now = Date.now()
  if (key === lastKey && now - lastAt < 1200) return
  lastKey = key
  lastAt = now
  notify.create({ title, content, duration })
}

onMounted(() => {
  if (auth.isAuthed) {
    ns.refreshUnreadCount()
    ns.connect()
    rt.connect()
  }
})

watch(
  () => auth.isAuthed,
  (v) => {
    if (v) {
      ns.refreshUnreadCount()
      ns.connect()
      rt.connect()
    } else {
      ns.disconnect()
      rt.disconnect()
      router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    }
  }
)

watch(
  () => ns.lastPushed as NotificationItem | null,
  (it) => {
  if (!it) return
  pushOnce(it.title, it.content || '', 4500)
  }
)

watch(
  () => rt.lastToast as { title: string; content?: string } | null,
  (it) => {
  if (!it) return
  pushOnce(it.title, it.content || '', 2600)
  }
)
</script>

<template></template>
