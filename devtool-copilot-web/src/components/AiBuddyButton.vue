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
  const bw = 64
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
  const x = Math.round(window.innerWidth / 2 - 32)
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
    aria-label="AI 小鲨鱼"
    :class="{ dragging }"
    :style="buddyStyle"
    @click="onClick"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="onPointerUp"
    @pointercancel="onPointerUp"
  >
    <svg class="sprite" width="64" height="56" viewBox="0 0 64 56" fill="none" aria-hidden="true">
      <defs>
        <linearGradient id="dtc_shark_body" x1="12" y1="16" x2="52" y2="42" gradientUnits="userSpaceOnUse">
          <stop stop-color="#466477" />
          <stop offset="1" stop-color="#263d51" />
        </linearGradient>
        <linearGradient id="dtc_shark_belly" x1="25" y1="27" x2="47" y2="42" gradientUnits="userSpaceOnUse">
          <stop stop-color="#f5f8f4" />
          <stop offset="1" stop-color="#d9e6e1" />
        </linearGradient>
      </defs>

      <ellipse class="water-shadow" cx="34" cy="47" rx="19" ry="3" fill="rgba(27, 55, 70, 0.14)" />
      <g class="shark">
        <path class="tail" d="M27 40 32 51 37 40Z" fill="#304b60" />
        <path class="dorsal-fin" d="M27 17 32 8l5 9Z" fill="#36576b" />
        <path class="side-fin side-fin-left" d="M14 32 4 39c-2 2-1 3 1 3l14-4Z" fill="#38596b" />
        <path class="side-fin side-fin-right" d="M50 32 60 39c2 2 1 3-1 3l-14-4Z" fill="#38596b" />
        <path class="body" d="M32 14c13 0 23 7 24 18-1 11-11 18-24 18S9 43 8 32c1-11 11-18 24-18Z" fill="url(#dtc_shark_body)" stroke="#1c3447" stroke-width="1.2" />
        <path class="belly" d="M15 35c5 7 11 10 17 10s12-3 17-10c-3 9-9 13-17 13s-14-4-17-13Z" fill="url(#dtc_shark_belly)" />
        <path class="gill" d="M18 28c2 2 2 5 0 8M22 27c2 3 2 6 0 9M46 28c-2 2-2 5 0 8M42 27c-2 3-2 6 0 9" stroke="#1e3748" stroke-width="1.1" stroke-linecap="round" opacity=".8" />
        <circle class="eye" cx="23" cy="27" r="4" fill="#f4f8f5" />
        <circle class="eye" cx="41" cy="27" r="4" fill="#f4f8f5" />
        <circle class="eye-pupil" cx="23" cy="27" r="1.8" fill="#193348" />
        <circle class="eye-pupil" cx="41" cy="27" r="1.8" fill="#193348" />
        <circle class="eye-glint" cx="23.7" cy="26.2" r=".7" fill="#fff" />
        <circle class="eye-glint" cx="41.7" cy="26.2" r=".7" fill="#fff" />
        <path class="smile" d="M27 35c3 3 7 3 10 0" stroke="#d6eee6" stroke-width="1.3" stroke-linecap="round" />
        <circle class="cheek" cx="17.5" cy="34" r="1.4" fill="#91d1bc" opacity=".75" />
        <circle class="cheek" cx="46.5" cy="34" r="1.4" fill="#91d1bc" opacity=".75" />
      </g>
      <g class="bubbles" fill="#77bda9">
        <circle cx="12" cy="12" r="1.5" />
        <circle cx="8" cy="7" r="1" />
      </g>
    </svg>
    <span class="tip" aria-hidden="true">问问AI</span>
  </button>
</template>

<style scoped>
.buddy {
  position: fixed;
  left: 0;
  top: 0;
  width: 64px;
  height: 56px;
  border-radius: 18px;
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
  filter: drop-shadow(0 14px 28px rgba(28, 55, 70, 0.2));
}

.buddy.dragging {
  filter: drop-shadow(0 18px 34px rgba(28, 55, 70, 0.24));
}

.sprite {
  position: relative;
  z-index: 1;
  width: 64px;
  height: 56px;
  overflow: visible;
  transform-origin: 50% 58%;
  animation: shark-float 4.8s ease-in-out infinite;
}

.buddy:hover .sprite {
  animation-duration: 2.8s;
}

.shark {
  transform-origin: 36px 31px;
  animation: shark-swim 3.2s ease-in-out infinite;
}

.eye {
  animation: eye-blink 6.2s ease-in-out infinite;
}

.tail {
  transform-box: fill-box;
  transform-origin: 72% 50%;
  animation: tail-swish 1.2s ease-in-out infinite;
}

.dorsal-fin,
.side-fin {
  transform-box: fill-box;
  transform-origin: center;
  animation: fin-drift 2.4s ease-in-out infinite;
}

.bubbles {
  transform-origin: center;
  animation: bubbles-rise 3.8s ease-in-out infinite;
}

.water-shadow {
  transform-origin: center;
  animation: shadow-breathe 4.8s ease-in-out infinite;
}

.buddy:hover .tail {
  animation-duration: 560ms;
}

.buddy:hover .shark {
  animation-duration: 1.8s;
}

.buddy:hover .eye {
  animation-duration: 3.8s;
}

.tip {
  position: absolute;
  top: 50%;
  left: calc(100% + 10px);
  transform: translateY(-50%) translateX(-8px);
  opacity: 0;
  pointer-events: none;
  padding: 7px 10px;
  border-radius: 8px;
  background: rgba(28, 51, 68, 0.95);
  color: rgba(255, 255, 255, 0.92);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: -0.2px;
  white-space: nowrap;
  transition: opacity 160ms ease, transform 160ms ease;
  box-shadow: 0 12px 26px rgba(24, 49, 64, 0.2);
}

.buddy:hover .tip {
  opacity: 1;
  transform: translateY(-50%) translateX(0px);
}

@keyframes shark-float {
  0%,
  82%,
  100% {
    transform: translateY(0px) rotate(0deg);
  }
  88% {
    transform: translateY(-1px) rotate(-1deg);
  }
  94% {
    transform: translateY(-2px) rotate(0deg);
  }
}

@keyframes shark-swim {
  0%,
  100% { transform: translateX(0) rotate(0deg); }
  50% { transform: translateX(1px) rotate(-1deg); }
}

@keyframes tail-swish {
  0%,
  100% { transform: rotate(0deg); }
  50% { transform: rotate(8deg); }
}

@keyframes fin-drift {
  0%,
  100% { transform: rotate(0deg); }
  50% { transform: rotate(-4deg); }
}

@keyframes bubbles-rise {
  0%,
  100% { opacity: .25; transform: translate(0, 2px); }
  50% { opacity: .85; transform: translate(1px, -2px); }
}

@keyframes shadow-breathe {
  0%,
  82%,
  100% { transform: scaleX(1); opacity: .14; }
  92% { transform: scaleX(.78); opacity: .08; }
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
  .shark,
  .tail,
  .dorsal-fin,
  .side-fin,
  .bubbles,
  .water-shadow,
  .eye {
    animation: none !important;
  }
}
</style>
