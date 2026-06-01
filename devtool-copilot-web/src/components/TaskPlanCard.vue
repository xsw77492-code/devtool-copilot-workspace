<script setup lang="ts">
import { computed } from 'vue'
import { NButton } from 'naive-ui'
import type { TaskPlan } from '../api/ai'

const props = defineProps<{
  plan: TaskPlan
  adding?: boolean
  added?: boolean
  disabled?: boolean
}>()

const emit = defineEmits<{ add: [] }>()

const priorityClass = computed(() => {
  if (props.plan.priority === 'HIGH') return 'prio high'
  if (props.plan.priority === 'LOW') return 'prio low'
  return 'prio medium'
})

const actionLabel = computed(() => {
  if (props.added) return 'Added'
  return 'Add to Project'
})
</script>

<template>
  <div class="card hover-row">
    <div class="top">
      <div class="title">
        <span class="order mono">{{ plan.order }}</span>
        <span class="dot">·</span>
        <span>{{ plan.title }}</span>
      </div>
      <div class="right">
        <span :class="priorityClass">{{ plan.priority }}</span>
        <n-button
          text
          size="small"
          class="action"
          :disabled="disabled || added"
          :loading="adding"
          @click="emit('add')"
        >
          {{ actionLabel }}
        </n-button>
      </div>
    </div>
    <div class="desc muted">{{ plan.description }}</div>
  </div>
</template>

<style scoped>
.card {
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(255, 255, 255, 0.02);
  padding: 12px 12px;
  transition: transform 120ms ease;
}
.card:hover {
  transform: translateY(-1px);
}
.top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}
.title {
  font-weight: 640;
  letter-spacing: -0.1px;
  display: flex;
  align-items: center;
  gap: 8px;
  line-height: 1.4;
}
.order {
  font-size: 12px;
  opacity: 0.85;
}
.dot {
  opacity: 0.4;
}
.right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.prio {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: rgba(250, 250, 250, 0.72);
  white-space: nowrap;
}
.prio.high {
  background: rgba(20, 184, 166, 0.14);
  border-color: rgba(20, 184, 166, 0.26);
  color: rgba(250, 250, 250, 0.92);
}
.prio.medium {
  background: rgba(6, 182, 212, 0.12);
  border-color: rgba(6, 182, 212, 0.22);
  color: rgba(250, 250, 250, 0.88);
}
.prio.low {
  background: rgba(255, 255, 255, 0.03);
  border-color: rgba(255, 255, 255, 0.08);
}
.action {
  height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.05);
}
.desc {
  margin-top: 8px;
  line-height: 1.6;
  font-size: 12px;
}
</style>
