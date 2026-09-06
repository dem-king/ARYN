<script setup lang="ts">
import ExtensionSettings from '../extension-settings.vue';

const props = defineProps<{ modelValue: Record<string, unknown> }>();
const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>];
}>();
</script>

<template>
  <ExtensionSettings
    :model-value="props.modelValue"
    type="goods-waterfall"
    @update:model-value="emit('update:modelValue', $event)"
  />
</template>
