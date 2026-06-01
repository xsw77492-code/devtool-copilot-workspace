<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from './layouts/AppLayout.vue'
import BlankLayout from './layouts/BlankLayout.vue'

const route = useRoute()

const layout = computed(() => {
  const name = (route.meta.layout as string | undefined) ?? 'app'
  if (name === 'blank') return BlankLayout
  return AppLayout
})
</script>

<template>
  <component :is="layout" />
</template>
