<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NSpin } from 'naive-ui'
import { aiApi, type AiUsageDaily, type AiUsageTypeRow, type AiUsageQuota } from '../api/ai'

const loading = ref(false)
const daily = ref<AiUsageDaily[]>([])
const types = ref<AiUsageTypeRow[]>([])
const quota = ref<AiUsageQuota | null>(null)

const fmt = (n: number | null | undefined) => {
  if (n == null) return '0'
  if (n < 1000) return String(n)
  if (n < 1_000_000) return (n / 1000).toFixed(n < 10000 ? 1 : 0) + 'k'
  return (n / 1_000_000).toFixed(1) + 'M'
}

const fmtDate = (s: string) => (s ? s.slice(5) : '')

const monthPct = computed(() => {
  if (!quota.value || !quota.value.monthlyQuota || quota.value.monthlyQuota <= 0) return 0
  return Math.min(100, Math.round((quota.value.monthlyUsedTokens / quota.value.monthlyQuota) * 100))
})
const dayPct = computed(() => {
  if (!quota.value || !quota.value.dailyCallLimit || quota.value.dailyCallLimit <= 0) return 0
  return Math.min(100, Math.round((quota.value.todayCalls / quota.value.dailyCallLimit) * 100))
})

const totalTokens7d = computed(() => daily.value.reduce((s, d) => s + (d.totalTokens || 0), 0))
const totalCalls7d = computed(() => daily.value.reduce((s, d) => s + (d.calls || 0), 0))
const totalPrompt7d = computed(() => daily.value.reduce((s, d) => s + (d.promptTokens || 0), 0))
const totalCompletion7d = computed(() => daily.value.reduce((s, d) => s + (d.completionTokens || 0), 0))

const maxDayTokens = computed(() => Math.max(1, ...daily.value.map((d) => d.totalTokens || 0)))
const maxTypeTokens = computed(() => Math.max(1, ...types.value.map((t) => t.totalTokens || 0)))

const promptRatio = computed(() => {
  const total = totalPrompt7d.value + totalCompletion7d.value
  if (total <= 0) return 50
  return Math.round((totalPrompt7d.value / total) * 100)
})
const completionRatio = computed(() => 100 - promptRatio.value)

const AVG_MIN_PER_CALL = 4
const savedMinutes = computed(() => totalCalls7d.value * AVG_MIN_PER_CALL)
const savedHours = computed(() => (savedMinutes.value / 60).toFixed(1))

const typeLabel: Record<string, string> = {
  chat: '对话',
  review: '审查',
  plan: '规划',
  insight: '洞察',
  chat_lifecycle: '任务生命周期',
  plan_lifecycle: '任务规划',
  insight_lifecycle: '生命周期洞察',
  root_cause: '任务根因',
  plan_lifecycle_root: '生命周期解读',
}
const getTypeLabel = (k: string) => typeLabel[k] || k

const load = async () => {
  loading.value = true
  try {
    const [d, t, q] = await Promise.all([
      aiApi.usageDaily(7),
      aiApi.usageTypes(),
      aiApi.usageQuota(),
    ])
    daily.value = d || []
    types.value = t || []
    quota.value = q || null
  } catch (e) {
    console.error('[usage] load failed', e)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="usage-root">
    <div class="usage-toolbar">
      <div class="muted-tip">数据基于你账号下所有 AI 调用，最近 7 天</div>
      <button class="refresh-btn" :disabled="loading" @click="load">
        <span v-if="loading" class="spin-dot"></span>
        <span v-else>刷新</span>
      </button>
    </div>

    <n-spin :show="loading" :delay="120">
      <!-- 三张配额卡 -->
      <div class="quota-row">
        <div class="quota-card">
          <div class="quota-head">
            <div class="quota-title">本月 Token 用量</div>
            <div class="quota-sub">自然月重置</div>
          </div>
          <div class="quota-main">
            <span class="big-num">{{ fmt(quota?.monthlyUsedTokens) }}</span>
            <span class="big-divider">/</span>
            <span class="big-cap">{{ quota && quota.monthlyQuota > 0 ? fmt(quota.monthlyQuota) : '不限' }}</span>
          </div>
          <div class="bar">
            <div class="bar-fill" :style="{ width: monthPct + '%' }"></div>
          </div>
          <div class="quota-foot">
            <span v-if="quota && quota.monthlyQuota > 0">已使用 {{ monthPct }}%</span>
            <span v-else>未设置月度上限</span>
            <span>近 7 天共 {{ totalCalls7d }} 次</span>
          </div>
        </div>

        <div class="quota-card">
          <div class="quota-head">
            <div class="quota-title">今日调用次数</div>
            <div class="quota-sub">每日 00:00 重置</div>
          </div>
          <div class="quota-main">
            <span class="big-num">{{ quota?.todayCalls ?? 0 }}</span>
            <span class="big-divider">/</span>
            <span class="big-cap">{{ quota && quota.dailyCallLimit > 0 ? quota.dailyCallLimit : '不限' }}</span>
          </div>
          <div class="bar">
            <div class="bar-fill" :style="{ width: dayPct + '%' }"></div>
          </div>
          <div class="quota-foot">
            <span v-if="quota && quota.dailyCallLimit > 0">已使用 {{ dayPct }}%</span>
            <span v-else>未设置每日上限</span>
            <span>本月累计 {{ fmt(quota?.monthlyUsedTokens) }} tokens</span>
          </div>
        </div>

        <div class="quota-card">
          <div class="quota-head">
            <div class="quota-title">配额状态</div>
            <div class="quota-sub">实时计算</div>
          </div>
          <div class="quota-main">
            <span class="status-badge" :class="quota?.monthlyExceeded ? 'bad' : 'ok'">
              {{ quota?.monthlyExceeded ? '已超额' : '正常' }}
            </span>
          </div>
          <div class="quota-foot quota-foot-block">
            <div v-if="quota?.monthlyExceeded" class="warn-line">
              本月 token 额度已达上限，请等待下月重置或联系管理员扩容。
            </div>
            <div v-else class="ok-line">
              近 7 天累计消耗 {{ fmt(totalTokens7d) }} tokens，{{ totalCalls7d }} 次调用。
            </div>
          </div>
        </div>
      </div>

      <!-- 两列：7 天明细 + 类型分布 -->
      <div class="content-row">
        <div class="card">
          <div class="card-head">
            <div class="card-title">最近 7 天明细</div>
            <div class="card-sub">按调用日聚合</div>
          </div>
          <table class="t">
            <thead>
              <tr>
                <th>日期</th>
                <th class="ta-right">调用</th>
                <th class="ta-right">输入</th>
                <th class="ta-right">输出</th>
                <th class="ta-right">合计 Token</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="daily.length === 0">
                <td colspan="5" class="empty">还没有调用记录</td>
              </tr>
              <tr v-for="d in daily" :key="d.date">
                <td>{{ fmtDate(d.date) }}</td>
                <td class="ta-right">{{ d.calls }}</td>
                <td class="ta-right">{{ fmt(d.promptTokens) }}</td>
                <td class="ta-right">{{ fmt(d.completionTokens) }}</td>
                <td class="ta-right strong">{{ fmt(d.totalTokens) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="card">
          <div class="card-head">
            <div class="card-title">按功能类型分布</div>
            <div class="card-sub">近 7 天累计</div>
          </div>
          <div v-if="types.length === 0" class="empty-block">还没有调用记录</div>
          <div v-else class="type-list">
            <div v-for="t in types" :key="t.type" class="type-row">
              <div class="type-label">{{ getTypeLabel(t.type) }}</div>
              <div class="type-bar">
                <div class="type-bar-fill" :style="{ width: Math.max(2, ((t.totalTokens || 0) / maxTypeTokens) * 100) + '%' }"></div>
              </div>
              <div class="type-val">
                <span class="strong">{{ fmt(t.totalTokens) }}</span>
                <span class="muted-xs">tokens · {{ t.calls }} 次</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 趋势图（近 7 天） -->
      <div class="card trend-card">
        <div class="card-head">
          <div>
            <div class="card-title">近 7 天 Token 趋势</div>
            <div class="card-sub">每日总 token 消耗</div>
          </div>
          <div class="trend-total">
            <span class="trend-num">{{ fmt(totalTokens7d) }}</span>
            <span class="trend-unit">tokens / 7 天</span>
          </div>
        </div>
        <div v-if="daily.length === 0" class="empty-block">还没有调用记录</div>
        <div v-else class="bars">
          <div v-for="d in daily" :key="d.date" class="bar-col">
            <div class="bar-stack" :title="`${d.date} · ${d.totalTokens} tokens · ${d.calls} 次`">
              <div
                class="bar-fill-dark"
                :style="{ height: Math.max(4, ((d.totalTokens || 0) / maxDayTokens) * 100) + '%' }"
              ></div>
            </div>
            <div class="bar-meta">
              <div class="bar-val">{{ fmt(d.totalTokens) }}</div>
              <div class="bar-date">{{ fmtDate(d.date) }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 效率三列 -->
      <div class="efficiency-row">
        <div class="card">
          <div class="card-head">
            <div class="card-title">输入 vs 输出 Token</div>
            <div class="card-sub">近 7 天累计</div>
          </div>
          <div class="io-bar">
            <div class="io-input" :style="{ width: promptRatio + '%' }">
              <span v-if="promptRatio > 12">输入 {{ promptRatio }}%</span>
            </div>
            <div class="io-output" :style="{ width: completionRatio + '%' }">
              <span v-if="completionRatio > 12">输出 {{ completionRatio }}%</span>
            </div>
          </div>
          <div class="io-legend">
            <div class="legend-item">
              <span class="dot dot-in"></span>
              <span class="legend-label">输入</span>
              <span class="legend-val">{{ fmt(totalPrompt7d) }} tokens</span>
            </div>
            <div class="legend-item">
              <span class="dot dot-out"></span>
              <span class="legend-label">输出</span>
              <span class="legend-val">{{ fmt(totalCompletion7d) }} tokens</span>
            </div>
          </div>
        </div>

        <div class="card eff-card">
          <div class="card-head">
            <div class="card-title">效率估算</div>
            <div class="card-sub">基于近 7 天调用次数</div>
          </div>
          <div class="eff-main">
            <div class="eff-num">{{ savedHours }}</div>
            <div class="eff-unit">小时</div>
          </div>
          <div class="eff-foot">
            假设每次 AI 调用平均节省 {{ AVG_MIN_PER_CALL }} 分钟人工时间，{{ totalCalls7d }} 次调用共节省约 {{ savedMinutes }} 分钟。
          </div>
        </div>

        <div class="card">
          <div class="card-head">
            <div class="card-title">调用类型排行</div>
            <div class="card-sub">按 token 降序</div>
          </div>
          <div v-if="types.length === 0" class="empty-block">还没有调用记录</div>
          <ol v-else class="rank-list">
            <li v-for="(t, i) in types" :key="t.type">
              <span class="rank-no">{{ i + 1 }}</span>
              <span class="rank-name">{{ getTypeLabel(t.type) }}</span>
              <span class="rank-bar">
                <span class="rank-bar-fill" :style="{ width: Math.max(4, ((t.totalTokens || 0) / maxTypeTokens) * 100) + '%' }"></span>
              </span>
              <span class="rank-val">{{ fmt(t.totalTokens) }}</span>
            </li>
          </ol>
        </div>
      </div>
    </n-spin>
  </div>
</template>

<style scoped>
.usage-root {
  padding: 8px 4px 24px;
  color: #111827;
}

.usage-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 2px 14px;
}
.muted-tip {
  font-size: 12px;
  color: #9ca3af;
}
.refresh-btn {
  height: 30px;
  padding: 0 14px;
  border: 1px solid #d1d5db;
  background: #ffffff;
  border-radius: 6px;
  font-size: 13px;
  color: #111827;
  cursor: pointer;
  transition: all 0.15s ease;
}
.refresh-btn:hover:not(:disabled) {
  border-color: #111827;
}
.refresh-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.spin-dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border: 2px solid #e5e7eb;
  border-top-color: #111827;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  vertical-align: middle;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 三张配额卡 */
.quota-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-bottom: 14px;
}
.quota-card {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px 18px;
  transition: border-color 0.15s ease;
}
.quota-card:hover {
  border-color: #d1d5db;
}
.quota-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 12px;
}
.quota-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}
.quota-sub {
  font-size: 12px;
  color: #9ca3af;
}
.quota-main {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 12px;
}
.big-num {
  font-size: 24px;
  font-weight: 650;
  color: #111827;
  line-height: 1;
}
.big-divider {
  font-size: 16px;
  color: #d1d5db;
  margin: 0 2px;
}
.big-cap {
  font-size: 14px;
  color: #6b7280;
}
.bar {
  height: 5px;
  background: #f3f4f6;
  border-radius: 999px;
  overflow: hidden;
  margin-bottom: 10px;
}
.bar-fill {
  height: 100%;
  background: #111827;
  border-radius: 999px;
  transition: width 0.4s ease;
}
.quota-foot {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #9ca3af;
}
.quota-foot-block {
  display: block;
  line-height: 1.6;
}
.warn-line {
  color: #111827;
  background: #f3f4f6;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 12px;
  border: 1px solid #e5e7eb;
}
.ok-line {
  color: #4b5563;
  font-size: 12px;
}
.status-badge {
  display: inline-block;
  padding: 5px 14px;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 600;
}
.status-badge.ok {
  background: #f3f4f6;
  color: #111827;
  border: 1px solid #d1d5db;
}
.status-badge.bad {
  background: #111827;
  color: #ffffff;
}

/* 内容双列 */
.content-row {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 14px;
  margin-bottom: 14px;
}
.card {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px 18px;
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

/* 表格 */
.t {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.t th {
  text-align: left;
  font-weight: 600;
  color: #6b7280;
  font-size: 12px;
  padding: 10px 8px;
  border-bottom: 1px solid #e5e7eb;
  background: #f9fafb;
}
.t td {
  padding: 12px 8px;
  color: #111827;
  border-bottom: 1px solid #f3f4f6;
}
.t tbody tr:last-child td {
  border-bottom: none;
}
.t tbody tr:hover {
  background: #f9fafb;
}
.ta-right {
  text-align: right;
}
.strong {
  font-weight: 600;
  color: #111827;
}
.empty {
  text-align: center;
  color: #9ca3af;
  padding: 24px 0 !important;
  font-size: 13px;
}
.empty-block {
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
  padding: 40px 0;
}

/* 类型分布 */
.type-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.type-row {
  display: grid;
  grid-template-columns: 110px 1fr 130px;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}
.type-label {
  color: #111827;
}
.type-bar {
  height: 7px;
  background: #f3f4f6;
  border-radius: 999px;
  overflow: hidden;
}
.type-bar-fill {
  height: 100%;
  background: #111827;
  border-radius: 999px;
  transition: width 0.4s ease;
}
.type-val {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.3;
}
.muted-xs {
  font-size: 11px;
  color: #9ca3af;
}

/* 趋势柱状图 */
.trend-card {
  margin-bottom: 14px;
}
.trend-total {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.trend-num {
  font-size: 18px;
  font-weight: 700;
  color: #111827;
  font-variant-numeric: tabular-nums;
}
.trend-unit {
  font-size: 11px;
  color: #9ca3af;
}

.bars {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 14px;
  height: 180px;
  align-items: end;
  padding: 8px 0 0;
}
.bar-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}
.bar-stack {
  width: 100%;
  max-width: 56px;
  flex: 1;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.bar-fill-dark {
  width: 100%;
  background: #111827;
  border-radius: 3px 3px 0 0;
  min-height: 4px;
  transition: height 0.5s ease;
}
.bar-fill-dark:hover {
  opacity: 0.85;
}
.bar-meta {
  margin-top: 8px;
  text-align: center;
}
.bar-val {
  font-size: 12px;
  font-weight: 600;
  color: #111827;
  font-variant-numeric: tabular-nums;
}
.bar-date {
  margin-top: 2px;
  font-size: 11px;
  color: #9ca3af;
}

/* 效率三列 */
.efficiency-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1.2fr;
  gap: 14px;
}
.eff-card {
  display: flex;
  flex-direction: column;
}
.eff-main {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 12px;
}
.eff-num {
  font-size: 28px;
  font-weight: 650;
  color: #111827;
  line-height: 1;
}
.eff-unit {
  font-size: 14px;
  color: #6b7280;
}
.eff-foot {
  font-size: 12px;
  color: #9ca3af;
  line-height: 1.6;
  margin-top: auto;
}

/* 输入输出比例 */
.io-bar {
  height: 26px;
  display: flex;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 14px;
  background: #f3f4f6;
}
.io-input,
.io-output {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  color: #ffffff;
  white-space: nowrap;
  transition: width 0.5s ease;
  min-width: 0;
}
.io-input {
  background: #111827;
}
.io-output {
  background: #9ca3af;
  color: #ffffff;
}

.io-legend {
  display: flex;
  gap: 20px;
  font-size: 12px;
  color: #4b5563;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 999px;
}
.dot-in {
  background: #111827;
}
.dot-out {
  background: #9ca3af;
}
.legend-label {
  color: #6b7280;
}
.legend-val {
  font-weight: 600;
  color: #111827;
  font-variant-numeric: tabular-nums;
}

/* 排行 */
.rank-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.rank-list li {
  display: grid;
  grid-template-columns: 22px 100px 1fr 70px;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}
.rank-no {
  width: 22px;
  height: 22px;
  border-radius: 4px;
  background: #f3f4f6;
  color: #6b7280;
  font-weight: 600;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.rank-list li:nth-child(1) .rank-no {
  background: #111827;
  color: #ffffff;
}
.rank-name {
  color: #111827;
  font-weight: 500;
}
.rank-bar {
  height: 6px;
  background: #f3f4f6;
  border-radius: 999px;
  overflow: hidden;
}
.rank-bar-fill {
  display: block;
  height: 100%;
  background: #111827;
  border-radius: 999px;
  transition: width 0.4s ease;
}
.rank-val {
  text-align: right;
  font-weight: 600;
  color: #111827;
  font-variant-numeric: tabular-nums;
  font-size: 12px;
}

/* 响应式 */
@media (max-width: 1100px) {
  .efficiency-row {
    grid-template-columns: 1fr 1fr;
  }
  .efficiency-row > .card:last-child {
    grid-column: span 2;
  }
}
@media (max-width: 960px) {
  .quota-row,
  .content-row {
    grid-template-columns: 1fr;
  }
  .type-row {
    grid-template-columns: 90px 1fr 110px;
  }
  .efficiency-row {
    grid-template-columns: 1fr;
  }
  .efficiency-row > .card:last-child {
    grid-column: span 1;
  }
  .bars {
    height: 150px;
  }
}
</style>