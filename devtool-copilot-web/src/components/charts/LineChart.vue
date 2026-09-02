<script setup lang="ts">
import { computed } from 'vue'

type Point = { label: string; value: number }

const props = defineProps<{
  points: Point[]
  height?: number
  axis?: boolean
  yTicks?: number
}>()

const w = 520
const h = computed(() => props.height || 180)
const pad = 18
const axisOn = computed(() => !!props.axis)
const padL = computed(() => (axisOn.value ? 42 : pad))
const padR = pad
const padT = pad
const padB = pad

const maxV = computed(() => Math.max(1, ...props.points.map((p) => Number(p.value || 0))))

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

const path = computed(() => {
  const pts = props.points || []
  if (!pts.length) return ''
  const innerW = w - padL.value - padR
  const innerH = h.value - padT - padB
  const step = pts.length <= 1 ? innerW : innerW / (pts.length - 1)
  const toY = (v: number) => padT + innerH - (Math.max(0, v) / niceV.value) * innerH
  return pts
    .map((p, i) => {
      const x = padL.value + step * i
      const y = toY(p.value)
      return `${i === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`
    })
    .join(' ')
})

const dots = computed(() => {
  const pts = props.points || []
  const innerW = w - padL.value - padR
  const innerH = h.value - padT - padB
  const step = pts.length <= 1 ? innerW : innerW / (pts.length - 1)
  const toY = (v: number) => padT + innerH - (Math.max(0, v) / niceV.value) * innerH
  return pts.map((p, i) => ({
    x: padL.value + step * i,
    y: toY(p.value),
    v: p.value,
    label: p.label
  }))
})
</script>

<template>
  <svg class="chart" :viewBox="`0 0 ${w} ${h}`" preserveAspectRatio="none">
    <defs>
      <linearGradient id="lcStroke" x1="0" y1="0" x2="1" y2="0">
        <stop offset="0" stop-color="rgba(var(--accent-rgb),0.86)" />
        <stop offset="1" stop-color="rgba(var(--accent2-rgb),0.82)" />
      </linearGradient>
      <linearGradient id="lcFill" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stop-color="rgba(var(--accent2-rgb),0.14)" />
        <stop offset="1" stop-color="rgba(var(--accent2-rgb),0.00)" />
      </linearGradient>
    </defs>

    <g v-if="axisOn">
      <g v-for="t in ticks" :key="t.y">
        <line :x1="padL" :x2="w - padR" :y1="t.y" :y2="t.y" class="grid" />
        <text :x="padL - 8" :y="t.y + 4" text-anchor="end" class="tick">{{ fmtTick(t.v) }}</text>
      </g>
    </g>

    <path
      v-if="path"
      :d="path + ` L ${w - padR} ${h - padB} L ${padL} ${h - padB} Z`"
      fill="url(#lcFill)"
    />
    <path v-if="path" :d="path" fill="none" stroke="url(#lcStroke)" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" />

    <g v-for="d in dots" :key="d.x">
      <circle :cx="d.x" :cy="d.y" r="3.2" fill="#ffffff" stroke="rgba(var(--accent-rgb),0.86)" stroke-width="2" />
    </g>
  </svg>
</template>

<style scoped>
.chart {
  width: 100%;
  height: 100%;
  display: block;
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
