<script setup lang="ts" name="dict-tag">
import { computed } from 'vue'

const props = defineProps({
  options: {
    type: Array as any,
    default: null,
  },
  value: [String, Array],
})

const values = computed(() => {
  if (props.value != null && props.value !== 'undefined') {
    return Array.isArray(props.value) ? props.value : [props.value]
  }
  else {
    return []
  }
})
</script>

<template>
  <view>
    <template v-for="(item, index) in options">
      <template v-if="values.includes(item.value || item)">
        <text :key="index * 2" class="text-22rpx">
          {{ item.label || item }}
        </text>
      </template>
    </template>
  </view>
</template>

<style scoped></style>
