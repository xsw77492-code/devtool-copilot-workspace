<script setup lang="ts">
import { computed } from 'vue'

type Bar = { label: string; value: number }

const props = defineProps<{
  bars: Bar[]
  height?: number
  interactive?: boolean
}>()

const emit = defineEmits<{
  (e: 'select', bar: Bar, index: number): void
}>()

const w = 520
const h = computed(() => props.height || 180)
const pad = 16
const gap = 8

const maxV = computed(() => Math.max(1, ...props.bars.map((b) => Number(b.value || 0))))

const layout = computed(() => {
  const bars = props.bars || []
  const innerW = w - pad * 2
  const innerH = h.value - pad * 2
  const bw = bars.length ? (innerW - gap * (bars.length - 1)) / bars.length : innerW
  return bars.map((b, i) => {
    const v = Math.max(0, b.value || 0)
    const bh = (v / maxV.value) * innerH
    const x = pad + i * (bw + gap)
    const y = pad + innerH - bh
    return { x, y, bw, bh, label: b.label, value: v, idx: i }
  })
})
</script>

<template>
  <svg class="chart" :viewBox="`0 0 ${w} ${h}`" preserveAspectRatio="none">
    <defs>
      <linearGradient id="bcFill" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stop-color="rgba(20,184,166,0.92)" />
        <stop offset="1" stop-color="rgba(6,182,212,0.78)" />
      </linearGradient>
    </defs>
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
</style>
