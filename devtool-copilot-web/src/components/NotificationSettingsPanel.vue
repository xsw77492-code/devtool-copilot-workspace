<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NButton, NCheckbox, NSelect, NSpin, useMessage } from 'naive-ui'
import { notificationApi, type NotificationSettingsRequest, type NotificationSettingsResponse } from '../api/notification'
import { NOTIFICATION_TYPE_META } from '../constants/notificationTypes'

const emit = defineEmits<{ saved: [] }>()

const message = useMessage()
const loading = ref(false)
const settings = ref<NotificationSettingsResponse | null>(null)
const dndEnabled = ref(false)
const dndStart = ref(0)
const dndEnd = ref(0)
const typePrefMap = ref<Record<string, boolean>>({})

const minuteOptions = computed(() => {
  const opts: { label: string; value: number }[] = []
  for (let m = 0; m < 24 * 60; m += 30) {
    const hh = String(Math.floor(m / 60)).padStart(2, '0')
    const mm = String(m % 60).padStart(2, '0')
    opts.push({ label: `${hh}:${mm}`, value: m })
  }
  return opts
})

const labelByType = computed(() => {
  const m: Record<string, string> = {}
  for (const it of NOTIFICATION_TYPE_META) m[it.type] = it.label
  return m
})

const knownTypes = computed(() => {
  const ts = NOTIFICATION_TYPE_META.map((x) => x.type)
  return Array.from(new Set(ts))
})

const settingGroups = computed(() => {
  return [
    { key: 'task', title: '任务', types: NOTIFICATION_TYPE_META.filter((x) => x.type.startsWith('TASK_')).map((x) => x.type) },
    { key: 'project', title: '项目', types: NOTIFICATION_TYPE_META.filter((x) => x.type.startsWith('PROJECT_')).map((x) => x.type) },
    { key: 'system', title: '系统', types: ['SYSTEM'] }
  ]
})

async function load() {
  loading.value = true
  try {
    const s = await notificationApi.settings()
    settings.value = s
    dndEnabled.value = Number(s.dndEnabled || 0) === 1
    dndStart.value = Number(s.dndStartMinute || 0)
    dndEnd.value = Number(s.dndEndMinute || 0)
    const map: Record<string, boolean> = {}
    for (const p of s.typePrefs || []) {
      map[p.type] = Number(p.enabled || 0) === 1
    }
    typePrefMap.value = map
  } catch (e: any) {
    message.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function setAllPrefs(v: boolean) {
  const m: Record<string, boolean> = { ...typePrefMap.value }
  for (const t of knownTypes.value) m[t] = v
  typePrefMap.value = m
}

async function save() {
  const payload: NotificationSettingsRequest = {
    dndEnabled: dndEnabled.value ? 1 : 0,
    dndStartMinute: dndStart.value,
    dndEndMinute: dndEnd.value,
    typePrefs: knownTypes.value.map((t) => ({ type: t, enabled: typePrefMap.value[t] === false ? 0 : 1 }))
  }
  try {
    await notificationApi.updateSettings(payload)
    message.success('已保存')
    emit('saved')
  } catch (e: any) {
    message.error(e?.message || '保存失败')
  }
}

onMounted(load)
</script>

<template>
  <n-spin :show="loading">
    <div class="settingsRow">
      <n-checkbox v-model:checked="dndEnabled">免打扰</n-checkbox>
      <div class="settingsTime" v-if="dndEnabled">
        <n-select v-model:value="dndStart" :options="minuteOptions" class="timeSel" />
        <span class="muted">—</span>
        <n-select v-model:value="dndEnd" :options="minuteOptions" class="timeSel" />
      </div>
    </div>

    <div class="settingsTools">
      <n-button tertiary size="small" @click="setAllPrefs(true)">全部开启</n-button>
      <n-button tertiary size="small" @click="setAllPrefs(false)">全部关闭</n-button>
    </div>

    <div class="settingsGroups">
      <div v-for="g in settingGroups" :key="g.key" class="group">
        <div class="groupTitle">{{ g.title }}</div>
        <div class="groupGrid">
          <div v-for="t in g.types" :key="t" class="typeRow">
            <n-checkbox :checked="typePrefMap[t] !== false" @update:checked="(v) => (typePrefMap[t] = v)">{{
              labelByType[t] || t
            }}</n-checkbox>
          </div>
        </div>
      </div>
    </div>

    <div class="settingsActions">
      <n-button tertiary @click="load">重置</n-button>
      <n-button secondary class="accentBtn" @click="save">保存</n-button>
    </div>
  </n-spin>
</template>

<style scoped>
.settingsRow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.settingsTime {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.timeSel {
  width: 120px;
}

.settingsTools {
  margin-top: 12px;
  display: flex;
  gap: 10px;
}

.settingsGroups {
  margin-top: 14px;
  display: grid;
  gap: 12px;
}

.groupTitle {
  font-size: 13px;
  font-weight: 760;
  letter-spacing: 0.1px;
  color: rgba(15, 23, 42, 0.82);
}

.groupGrid {
  margin-top: 8px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px 10px;
}

.typeRow {
  padding: 6px 8px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.02);
}

.settingsActions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.accentBtn {
  background: rgba(20, 184, 166, 0.12) !important;
  border-color: rgba(20, 184, 166, 0.22) !important;
  color: rgba(15, 23, 42, 0.92) !important;
}
</style>

