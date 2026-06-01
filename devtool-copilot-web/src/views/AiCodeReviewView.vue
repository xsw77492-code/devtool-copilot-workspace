<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { NButton, NInput, NSelect, NSpin, useMessage } from 'naive-ui'
import { aiApi, type AiRiskLevel } from '../api/ai'
import MarkdownView from '../components/MarkdownView.vue'

const message = useMessage()

const language = ref<string>('TypeScript')
const code = ref<string>('')

const reviewing = ref(false)
const result = ref<{ riskLevel: AiRiskLevel; report: string } | null>(null)
const REVIEW_STATE_KEY = 'dtc_ai_review_state_v1'
let persistTimer: any = null

const langOptions = [
  'Java',
  'TypeScript',
  'JavaScript',
  'Python',
  'Go',
  'SQL',
  'Bash',
  'Other'
].map((v) => ({ label: v, value: v }))

const riskClass = computed(() => {
  const lvl = result.value?.riskLevel
  if (lvl === 'HIGH') return 'risk high'
  if (lvl === 'LOW') return 'risk low'
  return 'risk medium'
})

async function review() {
  const text = code.value.trim()
  if (!text) {
    message.warning('请先粘贴代码')
    return
  }
  reviewing.value = true
  result.value = null
  try {
    result.value = await aiApi.codeReview({ language: language.value, code: text })
  } catch (e: any) {
    message.error(e?.message || '审查失败')
  } finally {
    reviewing.value = false
  }
}

function persist() {
  try {
    localStorage.setItem(REVIEW_STATE_KEY, JSON.stringify({ language: language.value, code: code.value, ts: Date.now() }))
  } catch {
  }
}

function schedulePersist() {
  if (persistTimer) clearTimeout(persistTimer)
  persistTimer = setTimeout(() => persist(), 240)
}

onMounted(() => {
  try {
    const raw = localStorage.getItem(REVIEW_STATE_KEY)
    const obj = raw ? (JSON.parse(raw) as any) : null
    if (obj && typeof obj.language === 'string') language.value = obj.language
    if (obj && typeof obj.code === 'string') code.value = obj.code
  } catch {
  }
})

watch([language, code], () => schedulePersist(), { deep: true })
</script>

<template>
  <div class="page review">
    <div class="toolsbar">
      <n-select v-model:value="language" size="small" :options="langOptions" style="width: 220px" />
      <n-button type="primary" size="small" :loading="reviewing" @click="review">Review</n-button>
    </div>

    <section class="panel input">
      <n-input
        v-model:value="code"
        type="textarea"
        placeholder="Paste your code here…"
        :autosize="{ minRows: 10, maxRows: 18 }"
      />
    </section>

    <section class="panel result">
      <n-spin :show="reviewing">
        <div v-if="!result" class="empty muted">No result yet.</div>
        <div v-else class="out">
          <div class="out-head">
            <div class="h2">Report</div>
            <span :class="riskClass">{{ result.riskLevel }}</span>
          </div>
          <markdown-view :content="result.report" />
        </div>
      </n-spin>
    </section>
  </div>
</template>

<style scoped>
.review {
  max-width: 980px;
}
.toolsbar {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: flex-end;
  margin: 6px 0 14px;
}
.input {
  padding: 14px 14px;
}
.result {
  margin-top: 16px;
  padding: 14px 14px;
}
.empty {
  padding: 12px 2px;
  font-size: 12px;
}
.out-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 10px;
}
.risk {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: rgba(250, 250, 250, 0.8);
  background: rgba(255, 255, 255, 0.03);
}
.risk.high {
  background: rgba(248, 113, 113, 0.14);
  border-color: rgba(248, 113, 113, 0.22);
  color: rgba(254, 226, 226, 0.95);
}
.risk.medium {
  background: rgba(251, 191, 36, 0.14);
  border-color: rgba(251, 191, 36, 0.22);
  color: rgba(254, 243, 199, 0.95);
}
.risk.low {
  background: rgba(34, 197, 94, 0.12);
  border-color: rgba(34, 197, 94, 0.2);
  color: rgba(220, 252, 231, 0.95);
}
</style>
