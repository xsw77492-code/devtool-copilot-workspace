<script setup lang="ts">
import { computed } from 'vue'
import { useRealtimeStore, type PresenceMember } from '../stores/realtime'

const props = defineProps<{
  projectId: number
}>()

const rt = useRealtimeStore()

const members = computed(() => {
  if (!props.projectId) return []
  return rt.presenceMembers || []
})

const onlineCount = computed(() => {
  return members.value.filter((m) => m.online !== false).length
})

function initialOf(m: PresenceMember) {
  const s = String(m.username || 'U')
  return s.slice(0, 1).toUpperCase()
}

function lastLabel(m: PresenceMember) {
  const raw = String(m.lastSeenTime || '')
  if (!raw) return ''
  const ts = Date.parse(raw)
  if (!Number.isFinite(ts)) return ''
  const diff = Math.max(0, Date.now() - ts)
  const sec = Math.floor(diff / 1000)
  if (sec < 60) return `${sec}s`
  const min = Math.floor(sec / 60)
  if (min < 60) return `${min}m`
  const h = Math.floor(min / 60)
  return `${h}h`
}

function isAway(m: PresenceMember) {
  if (m.online === false) return false
  const raw = String(m.lastSeenTime || '')
  const ts = Date.parse(raw)
  if (!Number.isFinite(ts)) return false
  return Date.now() - ts > 45 * 1000
}

function viewLabel(m: PresenceMember) {
  const t = String(m.viewType || '')
  const id = m.viewId
  if (t === 'TASK') return id ? `任务 #${id}` : '任务'
  if (t === 'MEMBERS') return '成员页'
  if (t === 'ACTIVITY') return '动态页'
  if (t === 'PROJECT') return '项目页'
  return '在线'
}
</script>

<template>
  <div v-if="members.length" class="presence">
    <div class="label">
      <span class="liveDot" aria-hidden="true" />
      在线 {{ onlineCount }}
    </div>
    <div class="list">
      <div
        v-for="m in members"
        :key="m.userId"
        class="av"
        :class="{ offline: m.online === false, away: isAway(m) }"
        :title="`${m.username || 'User'} · ${m.online === false ? '离线' : isAway(m) ? '离开' : viewLabel(m)}${lastLabel(m) ? ` · ${lastLabel(m)}前` : ''}`"
      >
        {{ initialOf(m) }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.presence {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid rgba(20, 184, 166, 0.18);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.8);
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.06);
  backdrop-filter: blur(8px);
}

.label {
  font-size: 12px;
  font-weight: 900;
  color: rgba(15, 23, 42, 0.82);
  white-space: nowrap;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.liveDot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--accent);
  box-shadow: 0 0 0 4px rgba(20, 184, 166, 0.12);
}

.list {
  display: flex;
  align-items: center;
  gap: 6px;
}

.av {
  width: 26px;
  height: 26px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: #ffffff;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.85);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.av.offline {
  opacity: 0.42;
  filter: grayscale(0.8);
}

.av.away {
  opacity: 0.75;
}

.av:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.12);
}
</style>
