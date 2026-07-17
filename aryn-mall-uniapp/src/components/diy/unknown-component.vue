<script setup lang="ts">
const props = defineProps<{
  componentType?: string
}>()

if (import.meta.env.DEV) {
  console.warn('[decoration] unknown component skipped', {
    componentType: props.componentType || 'unknown',
  })
}
</script>

<template>
  <view v-if="false" />
</template>
