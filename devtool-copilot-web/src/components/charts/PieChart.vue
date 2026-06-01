<script setup lang="ts">
import { computed } from 'vue'

type Slice = { label: string; value: number; color?: string }

const props = defineProps<{
  slices: Slice[]
  size?: number
}>()

const s = computed(() => props.size || 180)
const r = computed(() => s.value / 2 - 10)
const cx = computed(() => s.value / 2)
const cy = computed(() => s.value / 2)

const total = computed(() => props.slices.reduce((sum, x) => sum + Math.max(0, Number(x.value || 0)), 0))

function arcPath(start: number, end: number) {
  const large = end - start > Math.PI ? 1 : 0
  const x1 = cx.value + r.value * Math.cos(start)
  const y1 = cy.value + r.value * Math.sin(start)
  const x2 = cx.value + r.value * Math.cos(end)
  const y2 = cy.value + r.value * Math.sin(end)
  return `M ${cx.value} ${cy.value} L ${x1} ${y1} A ${r.value} ${r.value} 0 ${large} 1 ${x2} ${y2} Z`
}

const palette = ['rgba(20,184,166,0.92)', 'rgba(6,182,212,0.86)', 'rgba(15,23,42,0.26)', 'rgba(15,23,42,0.14)']

const arcs = computed(() => {
  const t = total.value
  if (t <= 0) return []
  let a = -Math.PI / 2
  return props.slices.map((it, idx) => {
    const v = Math.max(0, Number(it.value || 0))
    const da = (v / t) * Math.PI * 2
    const start = a
    const end = a + da
    a = end
    return {
      d: arcPath(start, end),
      color: it.color || palette[idx % palette.length],
      label: it.label,
      value: v
    }
  })
})
</script>

<template>
  <svg class="chart" :viewBox="`0 0 ${s} ${s}`">
    <g v-if="arcs.length">
      <path v-for="(a, i) in arcs" :key="i" :d="a.d" :fill="a.color" />
      <circle :cx="cx" :cy="cy" :r="r * 0.58" fill="#ffffff" opacity="0.92" />
    </g>
    <g v-else>
      <circle :cx="cx" :cy="cy" :r="r" fill="rgba(15,23,42,0.04)" stroke="rgba(15,23,42,0.08)" />
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
