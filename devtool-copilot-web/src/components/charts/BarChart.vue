<script setup lang="ts">
import { computed } from 'vue'

type Bar = { label: string; value: number }

const props = defineProps<{
  bars: Bar[]
  height?: number
  interactive?: boolean
  axis?: boolean
  yTicks?: number
}>()

const emit = defineEmits<{
  (e: 'select', bar: Bar, index: number): void
}>()

const w = 520
const h = computed(() => props.height || 180)
const pad = 16
const axisOn = computed(() => !!props.axis)
const padL = computed(() => (axisOn.value ? 42 : pad))
const padR = pad
const padT = pad
const padB = pad
const gap = 8

const maxV = computed(() => Math.max(1, ...props.bars.map((b) => Number(b.value || 0))))

function niceMax(m: number) {
  if (!Number.isFinite(m) || m <= 0) return 1
  const exp = Math.floor(Math.log10(m))
  const base = Math.pow(10, exp)
  const f = m / base
  const nf = f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10
  return nf * base
}

const niceV = computed(() => niceMax(maxV.value))

const ticks = computed(() => {
  const n = Math.max(3, Math.min(6, Number(props.yTicks || 4)))
  const m = niceV.value
  const step = m / (n - 1)
  const out: Array<{ v: number; y: number }> = []
  const innerH = h.value - padT - padB
  for (let i = 0; i < n; i++) {
    const v = step * i
    const y = padT + innerH - (v / m) * innerH
    out.push({ v, y })
  }
  return out
})

function fmtTick(v: number) {
  if (v >= 1000) return `${Math.round(v / 100) / 10}k`
  if (Number.isInteger(v)) return String(v)
  return String(Math.round(v * 10) / 10)
}

const layout = computed(() => {
  const bars = props.bars || []
  const innerW = w - padL.value - padR
  const innerH = h.value - padT - padB
  const bw = bars.length ? (innerW - gap * (bars.length - 1)) / bars.length : innerW
  return bars.map((b, i) => {
    const v = Math.max(0, b.value || 0)
    const bh = (v / niceV.value) * innerH
    const x = padL.value + i * (bw + gap)
    const y = padT + innerH - bh
    return { x, y, bw, bh, label: b.label, value: v, idx: i }
  })
})
</script>

<template>
  <svg class="chart" :viewBox="`0 0 ${w} ${h}`" preserveAspectRatio="none">
    <defs>
      <linearGradient id="bcFill" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stop-color="rgba(var(--accent-rgb),0.86)" />
        <stop offset="1" stop-color="rgba(var(--accent2-rgb),0.74)" />
      </linearGradient>
    </defs>
    <g v-if="axisOn">
      <g v-for="t in ticks" :key="t.y">
        <line :x1="padL" :x2="w - padR" :y1="t.y" :y2="t.y" class="grid" />
        <text :x="padL - 8" :y="t.y + 4" text-anchor="end" class="tick">{{ fmtTick(t.v) }}</text>
      </g>
    </g>
    <g v-for="b in layout" :key="b.x">
      <rect
        :x="b.x"
        :y="b.y"
        :width="b.bw"
        :height="b.bh"
        rx="8"
        fill="url(#bcFill)"
        opacity="0.95"
        :class="{ i: !!props.interactive }"
        @click="props.interactive ? emit('select', { label: b.label, value: b.value }, b.idx) : undefined"
      />
    </g>
  </svg>
</template>

<style scoped>
.chart {
  width: 100%;
  height: 100%;
  display: block;
}

.i {
  cursor: pointer;
}

.grid {
  stroke: rgba(15, 23, 42, 0.08);
  stroke-width: 1;
}

.tick {
  fill: rgba(15, 23, 42, 0.52);
  font-size: 11px;
  font-weight: 700;
}
</style>
