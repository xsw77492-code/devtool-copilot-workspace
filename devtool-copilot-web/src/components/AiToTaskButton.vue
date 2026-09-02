<script setup lang="ts">
import { ref } from 'vue'
import { NButton, NInput, NModal, useMessage } from 'naive-ui'
import { taskApi } from '../api/task'

const props = defineProps<{
  projectId?: number | null
  content?: string
  disabled?: boolean
  size?: 'small' | 'tiny'
}>()

const message = useMessage()
const show = ref(false)
const title = ref('')
const desc = ref('')
const creating = ref(false)

function pickTitle(raw: string) {
  const lines = raw
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
  if (!lines.length) return 'AI 建议'
  // 优先找「建议/行动/改进」开头或编号项
  const item = lines.find(
    (l) => /^[-–—]\s*(建议|行动|改进|落地|执行)/.test(l) || /^\d+[.、]\s*/.test(l)
  )
  const base = item ? item.replace(/^[-–—]\s*/, '').replace(/^\d+[.、]\s*/, '') : lines[0]
  return base.replace(/[#*`]/g, '').slice(0, 50) || 'AI 建议'
}

function open() {
  const raw = (props.content || '').trim()
  title.value = pickTitle(raw)
  desc.value = raw.slice(0, 2000)
  show.value = true
}

async function submit() {
  const t = title.value.trim()
  if (!t || !props.projectId) return
  creating.value = true
  try {
    await taskApi.create(Number(props.projectId), t, {
      description: desc.value?.trim() || null,
      source: 'AI_建议'
    })
    message.success('任务已创建，可在任务看板中查看')
    show.value = false
  } catch (e: any) {
    message.error(e?.message || '创建失败')
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <n-button
    size="small"
    class="ai-to-task"
    :disabled="disabled || !projectId"
    :loading="creating"
    @click="open"
  >
    落地为任务
  </n-button>

  <n-modal
    v-model:show="show"
    preset="card"
    title="AI 建议 → 项目任务"
    style="max-width: 520px"
    :mask-closable="!creating"
  >
    <div class="att-form">
      <label class="att-label">任务标题</label>
      <n-input v-model:value="title" size="small" placeholder="输入任务标题" :maxlength="120" />
      <label class="att-label">任务描述（来自 AI 输出，可编辑）</label>
      <n-input
        v-model:value="desc"
        type="textarea"
        size="small"
        :autosize="{ minRows: 4, maxRows: 10 }"
        placeholder="输入任务描述"
        :maxlength="4000"
      />
    </div>
    <template #footer>
      <div class="att-foot">
        <n-button size="small" :disabled="creating" @click="show = false">取消</n-button>
        <n-button size="small" type="primary" :disabled="!title.trim()" :loading="creating" @click="submit">
          创建任务
        </n-button>
      </div>
    </template>
  </n-modal>
</template>

<style scoped>
.ai-to-task {
  margin-left: 8px;
}
.att-form {
  display: grid;
  gap: 8px;
}
.att-label {
  font-size: 12px;
  color: #94a3b8;
}
.att-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
