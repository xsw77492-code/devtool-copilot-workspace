<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

function openAgent() {
  router.push({ name: 'ai-chat', query: { mode: 'agent' } })
}

const POS_KEY = 'dtc_ai_buddy_pos_v1'
const dragging = ref(false)
const down = ref<{ dx: number; dy: number; id: number } | null>(null)
const pos = ref<{ x: number; y: number } | null>(null)
const moved = ref(false)
const dragStart = ref<{ x: number; y: number } | null>(null)
const lastDragEndAt = ref(0)

const buddyStyle = computed(() => {
  const p = pos.value
  if (!p) return {}
  return { transform: `translate3d(${Math.round(p.x)}px, ${Math.round(p.y)}px, 0)` }
})

function clamp(v: number, min: number, max: number) {
  return Math.max(min, Math.min(max, v))
}

function setPos(x: number, y: number) {
  const w = window.innerWidth
  const h = window.innerHeight
  const bw = 56
  const bh = 56
  const nx = clamp(x, 8, Math.max(8, w - bw - 8))
  const ny = clamp(y, 8, Math.max(8, h - bh - 8))
  pos.value = { x: nx, y: ny }
  try {
    localStorage.setItem(POS_KEY, JSON.stringify(pos.value))
  } catch {
  }
}

function onPointerDown(e: PointerEvent) {
  if (e.button !== 0) return
  const p = pos.value
  if (!p) return
  dragging.value = true
  moved.value = false
  dragStart.value = { x: e.clientX, y: e.clientY }
  down.value = { dx: e.clientX - p.x, dy: e.clientY - p.y, id: e.pointerId }
  try {
    ;(e.currentTarget as HTMLElement | null)?.setPointerCapture?.(e.pointerId)
  } catch {
  }
}

function onPointerMove(e: PointerEvent) {
  if (!dragging.value || !down.value) return
  if (e.pointerId !== down.value.id) return
  if (dragStart.value) {
    const dx = e.clientX - dragStart.value.x
    const dy = e.clientY - dragStart.value.y
    if (dx * dx + dy * dy > 16) moved.value = true
  }
  setPos(e.clientX - down.value.dx, e.clientY - down.value.dy)
}

function onPointerUp(e: PointerEvent) {
  if (!down.value) return
  if (e.pointerId !== down.value.id) return
  dragging.value = false
  down.value = null
  dragStart.value = null
  if (moved.value) lastDragEndAt.value = Date.now()
}

function onClick(e: MouseEvent) {
  if (dragging.value) {
    e.preventDefault()
    e.stopPropagation()
    return
  }
  if (Date.now() - lastDragEndAt.value < 320) {
    e.preventDefault()
    e.stopPropagation()
    return
  }
  openAgent()
}

function initPos() {
  try {
    const raw = localStorage.getItem(POS_KEY)
    const obj = raw ? JSON.parse(raw) : null
    if (obj && Number.isFinite(obj.x) && Number.isFinite(obj.y)) {
      setPos(Number(obj.x), Number(obj.y))
      return
    }
  } catch {
  }
  const x = Math.round(window.innerWidth / 2 - 28)
  const y = 8
  setPos(x, y)
}

function onResize() {
  if (!pos.value) return
  setPos(pos.value.x, pos.value.y)
}

onMounted(() => {
  initPos()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
})
</script>

<template>
  <button
    class="buddy"
    type="button"
    aria-label="AI 小精灵"
    :class="{ dragging }"
    :style="buddyStyle"
    @click="onClick"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="onPointerUp"
    @pointercancel="onPointerUp"
  >
    <svg class="sprite" width="56" height="56" viewBox="0 0 46 46" fill="none" aria-hidden="true">
      <defs>
        <linearGradient id="dtc_ai_g" x1="8" y1="8" x2="38" y2="38" gradientUnits="userSpaceOnUse">
          <stop stop-color="var(--accent2)" stop-opacity="0.92" />
          <stop offset="1" stop-color="var(--accent)" stop-opacity="0.92" />
        </linearGradient>
        <linearGradient id="dtc_ai_shade" x1="12" y1="12" x2="40" y2="40" gradientUnits="userSpaceOnUse">
          <stop stop-color="rgba(255,255,255,0.55)" />
          <stop offset="1" stop-color="rgba(255,255,255,0.00)" />
        </linearGradient>
      </defs>

      <path
        d="M14.2 9.4c4.8-3.2 12.8-3.2 17.6 0 4.4 2.9 6.6 7 6.6 12.4v3.4c0 5.4-2.2 9.5-6.6 12.4-4.8 3.2-12.8 3.2-17.6 0-4.4-2.9-6.6-7-6.6-12.4v-3.4c0-5.4 2.2-9.5 6.6-12.4Z"
        fill="url(#dtc_ai_g)"
        stroke="rgba(15,23,42,0.08)"
        stroke-width="1"
        stroke-linejoin="round"
        opacity="0.92"
      />

      <path
        d="M16.2 16.6c1.7-1.1 3.9-1.7 6.8-1.7 2.9 0 5.1.6 6.8 1.7 1.8 1.2 2.7 2.9 2.7 5.0v4.8c0 2.1-.9 3.8-2.7 5-1.7 1.1-3.9 1.7-6.8 1.7-2.9 0-5.1-.6-6.8-1.7-1.8-1.2-2.7-2.9-2.7-5v-4.8c0-2.1.9-3.8 2.7-5Z"
        fill="rgba(255,255,255,0.90)"
        stroke="rgba(15,23,42,0.10)"
        stroke-width="1"
        stroke-linejoin="round"
      />

      <path
        d="M14.8 20.2c2.2-2.8 4.9-4.2 8.2-4.2s6 1.4 8.2 4.2c-1.2.2-2.4.0-3.6-.5-1.5-.7-3-1-4.6-1-1.6 0-3.2.3-4.6 1-1.2.5-2.4.7-3.6.5Z"
        fill="rgba(15,23,42,0.68)"
      />

      <path
        d="M18.0 23.6c1.1-.8 2.5-1.2 4.0-1.2 1.5 0 2.9.4 4.0 1.2"
        stroke="rgba(15,23,42,0.22)"
        stroke-width="1"
        stroke-linecap="round"
      />

      <ellipse class="eye e1" cx="19.7" cy="26.2" rx="1.35" ry="1.35" fill="url(#dtc_ai_g)" />
      <ellipse class="eye e2" cx="26.3" cy="26.2" rx="1.35" ry="1.35" fill="url(#dtc_ai_g)" />

      <path
        d="M20.3 30.2c.9.9 1.8 1.3 2.7 1.3 1.1 0 2.1-.4 2.9-1.3"
        stroke="url(#dtc_ai_g)"
        stroke-width="1.8"
        stroke-linecap="round"
      />

      <path
        d="M16.4 29.2c1.2.7 2.4 1 3.6 1"
        stroke="rgba(6,182,212,0.20)"
        stroke-width="2.2"
        stroke-linecap="round"
      />
      <path
        d="M29.6 29.2c-1.2.7-2.4 1-3.6 1"
        stroke="rgba(20,184,166,0.20)"
        stroke-width="2.2"
        stroke-linecap="round"
      />

      <path
        d="M14.2 9.4c4.8-3.2 12.8-3.2 17.6 0 4.4 2.9 6.6 7 6.6 12.4v3.4c0 5.4-2.2 9.5-6.6 12.4-4.8 3.2-12.8 3.2-17.6 0-4.4-2.9-6.6-7-6.6-12.4v-3.4c0-5.4 2.2-9.5 6.6-12.4Z"
        fill="url(#dtc_ai_shade)"
        opacity="0.9"
      />

      <path
        class="spark"
        d="M33.6 13.4l.9-2 1 2 2 .9-2 1-.9 2-1-2-2-1 2-.9Z"
        fill="url(#dtc_ai_g)"
        opacity="0.65"
      />
    </svg>
    <span class="tip" aria-hidden="true">问 AI</span>
  </button>
</template>

<style scoped>
.buddy {
  position: fixed;
  left: 0;
  top: 0;
  width: 56px;
  height: 56px;
  border-radius: 16px;
  border: 1px solid transparent;
  background: transparent;
  display: grid;
  place-items: center;
  cursor: pointer;
  padding: 0;
  touch-action: none;
  transition: filter 180ms ease;
  z-index: 60;
}

.buddy:hover {
  filter: drop-shadow(0 18px 48px rgba(15, 23, 42, 0.14));
}

.buddy.dragging {
  filter: drop-shadow(0 22px 56px rgba(15, 23, 42, 0.18));
}

.sprite {
  position: relative;
  z-index: 1;
  transform-origin: 50% 60%;
  animation: sprite-float 4.4s ease-in-out infinite;
}

.buddy:hover .sprite {
  animation-duration: 2.2s;
}

.buddy:hover .spark {
  opacity: 0.9;
  animation: spark-pop 2.6s ease-in-out infinite;
}

.eye {
  transform-box: fill-box;
  transform-origin: center;
  animation: eye-blink 6.2s ease-in-out infinite;
}

.spark {
  animation: spark-pop 7.4s ease-in-out infinite;
  transform-origin: 50% 50%;
}

.buddy:hover .eye {
  animation-duration: 3.8s;
}

.tip {
  position: absolute;
  top: 50%;
  right: calc(100% + 10px);
  transform: translateY(-50%) translateX(8px);
  opacity: 0;
  pointer-events: none;
  padding: 7px 10px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.92);
  color: rgba(255, 255, 255, 0.92);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: -0.2px;
  white-space: nowrap;
  transition: opacity 160ms ease, transform 160ms ease;
  box-shadow: 0 14px 44px rgba(2, 6, 23, 0.24);
}

.buddy:hover .tip {
  opacity: 1;
  transform: translateY(-50%) translateX(0px);
}

@keyframes spark-pop {
  0%,
  88%,
  100% {
    opacity: 0.25;
    transform: scale(0.92);
  }
  92% {
    opacity: 0.9;
    transform: scale(1.06);
  }
}

@keyframes sprite-float {
  0%,
  82%,
  100% {
    transform: translateY(0px) rotate(0deg) scale(1);
  }
  88% {
    transform: translateY(-1px) rotate(-2deg) scale(1.015);
  }
  94% {
    transform: translateY(-2px) rotate(0deg) scale(1.02);
  }
}

@keyframes eye-blink {
  0%,
  92%,
  100% {
    transform: scaleY(1);
  }
  95% {
    transform: scaleY(0.2);
  }
  97% {
    transform: scaleY(1);
  }
}

@keyframes eye-glint {
  0%,
  100% {
    opacity: 0.0;
    transform: translateY(0px);
  }
  40% {
    opacity: 0.0;
  }
  55% {
    opacity: 0.55;
    transform: translateY(-0.5px);
  }
  70% {
    opacity: 0.0;
    transform: translateY(0px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .buddy,
  .sprite,
  .spark,
  .eye {
    animation: none !important;
  }
}
</style>
