<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NSpin } from 'naive-ui'
import { taskApi, type Task, type TaskTimelineItem } from '../api/task'
import TaskLifecycleFilm from '../components/TaskLifecycleFilm.vue'

const route = useRoute()
const router = useRouter()

const taskId = computed(() => Number(route.params.taskId || 0))
const task = ref<Task | null>(null)
const timeline = ref<TaskTimelineItem[]>([])
const loading = ref(false)
const errorMsg = ref('')

function statusLabel(st?: string) {
  const v = String(st || '').toUpperCase()
  if (v === 'DOING') return '进行中'
  if (v === 'DONE') return '已完成'
  return '待办'
}

async function load() {
  if (!Number.isFinite(taskId.value) || taskId.value <= 0) {
    errorMsg.value = '任务 ID 无效'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const [t, list] = await Promise.all([taskApi.get(taskId.value), taskApi.timeline(taskId.value)])
    task.value = t
    timeline.value = list
  } catch (e: any) {
    errorMsg.value = e?.message || '任务加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)

function goBack() {
  router.push({ name: 'lifecycle-center' })
}
</script>

<template>
  <div class="page lcPage">
    <!-- 顶部独立一行：返回按钮（左上顶格，跟随 app-content 的 padding，无双重缩进） -->
    <div class="lcBreadRow">
      <button class="lcBack" type="button" @click="goBack">
        <svg class="lcBackIcon" viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="M12.5 4.5 7 10l5.5 5.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        <span>返回</span>
      </button>
    </div>

    <header class="lcHero">
      <div class="lcHeroLeft">
        <h1>{{ task?.title || '任务生命周期' }}</h1>
        <div v-if="task" class="lcHeroMeta">
          <span class="lcPill" :class="`lcPill-${(task.status || '').toLowerCase()}`">{{ statusLabel(task.status) }}</span>
          <span v-if="task.priority" class="lcPill">优先级 {{ task.priority }}</span>
          <span class="lcMuted">任务 ID #{{ taskId }}</span>
        </div>
      </div>
    </header>

    <n-spin :show="loading">
      <div v-if="errorMsg" class="lcError">{{ errorMsg }}</div>
      <TaskLifecycleFilm v-else-if="task" :task="task" :timeline="timeline" />
      <div v-else-if="!loading" class="lcEmpty">
        <p>任务不存在</p>
        <span>请返回任务列表重试</span>
      </div>
    </n-spin>
  </div>
</template>

<style scoped>
.lcPage {
  max-width: 1280px;
  margin: 0 auto;
  /* 只保留底部 padding，顶部和左右交给 AppLayout 的 .app-content(padding: 24px 28px)，
     否则双重 padding 会让返回按钮缩进 44px/60px，永远顶不了格 */
  padding: 0 0 64px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-x: hidden;
  min-width: 0;
  width: 100%;
  box-sizing: border-box;
}

/* 顶部独立一行：返回按钮（真正左上顶格） */
.lcBreadRow {
  display: flex;
  align-items: center;
  height: 28px;
}
.lcBack {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 0;
  background: transparent;
  color: #374151;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  padding: 5px 10px 5px 6px;
  margin-left: -8px;
  border-radius: 8px;
  cursor: pointer;
  transition: color 0.15s ease, background 0.15s ease;
}
.lcBack:hover { color: #111827; background: rgba(15, 23, 42, 0.05); }
.lcBackIcon {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
}

/* 主标题卡片 */
.lcHero {
  background: #ffffff;
  border: 1px solid #eef0f4;
  border-radius: 14px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  padding: 20px 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.lcHeroLeft { flex: 1; min-width: 0; }
.lcHero h1 {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 600;
  color: #1f2329;
  letter-spacing: -0.2px;
  line-height: 1.35;
}
.lcHeroMeta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.lcPill {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: #1f2329;
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
}
.lcPill-todo { background: #f3f4f6; color: #1f2329; border-color: #e5e7eb; }
.lcPill-doing { background: #1f2329; color: #ffffff; border-color: #1f2329; }
.lcPill-done { background: #ffffff; color: #1f2329; border-color: #1f2329; }
.lcMuted { font-size: 12px; color: #6b7280; }

.lcError {
  padding: 16px;
  border: 1px solid #fecaca;
  background: #fef2f2;
  border-radius: 12px;
  color: #991b1b;
  font-size: 13px;
}

.lcEmpty {
  padding: 60px 16px;
  text-align: center;
  color: #6b7280;
  background: #ffffff;
  border: 1px solid #eef0f4;
  border-radius: 14px;
}
.lcEmpty p {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
}
.lcEmpty span {
  font-size: 12px;
}
</style>
