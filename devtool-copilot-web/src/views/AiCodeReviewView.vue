<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { NSelect, NSpin, useMessage } from 'naive-ui'
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

const riskInfo = computed(() => {
  const lvl = result.value?.riskLevel
  if (lvl === 'HIGH') return { label: '高风险', cls: 'risk high', tip: '建议立即修复' }
  if (lvl === 'LOW') return { label: '低风险', cls: 'risk low', tip: '整体良好' }
  return { label: '中风险', cls: 'risk medium', tip: '存在改进空间' }
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

function clearInput() {
  code.value = ''
  result.value = null
}

function persist() {
  try {
    localStorage.setItem(REVIEW_STATE_KEY, JSON.stringify({ language: language.value, code: code.value, ts: Date.now() }))
  } catch {}
}

function schedulePersist() {
  if (persistTimer) clearTimeout(persistTimer)
  persistTimer = setTimeout(() => persist(), 240)
}

const charLen = computed(() => code.value.length)

onMounted(() => {
  try {
    const raw = localStorage.getItem(REVIEW_STATE_KEY)
    const obj = raw ? (JSON.parse(raw) as any) : null
    if (obj && typeof obj.language === 'string') language.value = obj.language
    if (obj && typeof obj.code === 'string') code.value = obj.code
  } catch {}
})

watch([language, code], () => schedulePersist(), { deep: true })
</script>

<template>
  <div class="review-root">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <div class="left-tools">
        <n-select v-model:value="language" size="small" :options="langOptions" style="width: 160px" />
        <span class="char-tip">字符 {{ charLen }}</span>
      </div>
      <div class="right-tools">
        <button class="btn" :disabled="!code.trim()" @click="clearInput">清空</button>
        <button class="btn btn-primary" :disabled="reviewing || !code.trim()" @click="review">
          <span v-if="reviewing" class="spin-dot"></span>
          <span v-else>开始审查</span>
        </button>
      </div>
    </div>

    <!-- 输入卡 -->
    <div class="card input-card">
      <div class="card-head">
        <div class="card-title">代码输入</div>
        <div class="card-sub">支持任意语言，粘贴即可</div>
      </div>
      <textarea
        v-model="code"
        class="code-input"
        placeholder="粘贴你要审查的代码…"
        spellcheck="false"
      ></textarea>
    </div>

    <!-- 结果卡 -->
    <div class="card result-card">
      <n-spin :show="reviewing">
        <div v-if="!result && !reviewing" class="empty">
          <div class="empty-title">等待代码审查</div>
          <div class="empty-sub">点击右上方「开始审查」，AI 将从安全、性能、可维护性等维度给出报告</div>
        </div>
        <div v-else-if="result" class="out">
          <div class="out-head">
            <div>
              <div class="out-title">审查报告</div>
              <div class="out-sub">{{ riskInfo.tip }}</div>
            </div>
            <span :class="riskInfo.cls">{{ riskInfo.label }}</span>
          </div>
          <div class="report">
            <markdown-view :content="result.report" />
          </div>
        </div>
      </n-spin>
    </div>
  </div>
</template>

<style scoped>
.review-root {
  padding: 0 0 24px;
  color: #111827;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.left-tools,
.right-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}
.char-tip {
  font-size: 12px;
  color: #9ca3af;
  font-variant-numeric: tabular-nums;
}

.btn {
  height: 30px;
  padding: 0 14px;
  background: #ffffff;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 13px;
  color: #111827;
  cursor: pointer;
  transition: all 0.15s ease;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.btn:hover:not(:disabled) {
  border-color: #111827;
}
.btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.btn-primary {
  background: #111827;
  border-color: #111827;
  color: #ffffff;
}
.btn-primary:hover:not(:disabled) {
  background: #1f2937;
  border-color: #1f2937;
}

.spin-dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

.card {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px 18px;
}

.input-card {
  margin-bottom: 14px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 12px;
}
.card-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}
.card-sub {
  font-size: 12px;
  color: #9ca3af;
}

.code-input {
  width: 100%;
  min-height: 220px;
  padding: 14px 16px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-family: ui-monospace, SFMono-Regular, 'Cascadia Mono', Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #111827;
  resize: vertical;
  outline: none;
  transition: border-color 0.15s ease;
  box-sizing: border-box;
}
.code-input:focus {
  border-color: #111827;
  background: #ffffff;
}
.code-input::placeholder {
  color: #9ca3af;
}

/* 结果区 */
.out-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid #f3f4f6;
}
.out-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}
.out-sub {
  margin-top: 2px;
  font-size: 12px;
  color: #9ca3af;
}

.report {
  font-size: 13px;
  line-height: 1.7;
  color: #111827;
}

/* 风险徽章（黑灰三档） */
.risk {
  font-size: 12px;
  padding: 6px 12px;
  border-radius: 4px;
  font-weight: 600;
  white-space: nowrap;
}
.risk.high {
  background: #111827;
  color: #ffffff;
}
.risk.medium {
  background: #4b5563;
  color: #ffffff;
}
.risk.low {
  background: #f3f4f6;
  border: 1px solid #d1d5db;
  color: #374151;
}

/* 空态 */
.empty {
  padding: 60px 20px;
  text-align: center;
}
.empty-title {
  font-size: 14px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 4px;
}
.empty-sub {
  font-size: 12px;
  color: #9ca3af;
}
</style>