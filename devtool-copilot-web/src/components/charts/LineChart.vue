<script setup lang="ts">
import { computed } from 'vue'

type Point = { label: string; value: number }

const props = defineProps<{
  points: Point[]
  height?: number
}>()

const w = 520
const h = computed(() => props.height || 180)
const pad = 18

const maxV = computed(() => Math.max(1, ...props.points.map((p) => Number(p.value || 0))))

const path = computed(() => {
  const pts = props.points || []
  if (!pts.length) return ''
  const innerW = w - pad * 2
  const innerH = h.value - pad * 2
  const step = pts.length <= 1 ? innerW : innerW / (pts.length - 1)
  const toY = (v: number) => pad + innerH - (Math.max(0, v) / maxV.value) * innerH
  return pts
    .map((p, i) => {
      const x = pad + step * i
      const y = toY(p.value)
      return `${i === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`
    })
    .join(' ')
})

const dots = computed(() => {
  const pts = props.points || []
  const innerW = w - pad * 2
  const innerH = h.value - pad * 2
  const step = pts.length <= 1 ? innerW : innerW / (pts.length - 1)
  const toY = (v: number) => pad + innerH - (Math.max(0, v) / maxV.value) * innerH
  return pts.map((p, i) => ({
    x: pad + step * i,
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
        <stop offset="0" stop-color="rgba(20,184,166,0.92)" />
        <stop offset="1" stop-color="rgba(6,182,212,0.86)" />
      </linearGradient>
      <linearGradient id="lcFill" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stop-color="rgba(20,184,166,0.16)" />
        <stop offset="1" stop-color="rgba(20,184,166,0.00)" />
      </linearGradient>
    </defs>

    <path v-if="path" :d="path + ` L ${w - pad} ${h - pad} L ${pad} ${h - pad} Z`" fill="url(#lcFill)" />
    <path v-if="path" :d="path" fill="none" stroke="url(#lcStroke)" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" />

    <g v-for="d in dots" :key="d.x">
      <circle :cx="d.x" :cy="d.y" r="3.2" fill="#ffffff" stroke="rgba(20,184,166,0.92)" stroke-width="2" />
    </g>
  </svg>
</template>

<style scoped>
.chart {
  width: 100%;
  height: 100%;
  display: block;
}
</style>
