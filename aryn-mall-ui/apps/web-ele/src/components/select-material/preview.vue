<script lang="ts" setup>
import { ElImageViewer } from 'element-plus';

defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  images: {
    type: Array as () => string[],
    default: () => [],
  },
  type: {
    type: String,
    default: 'image',
  },
});
const emit = defineEmits(['update:modelValue']);
const handleClose = () => {
  emit('update:modelValue', false);
};
</script>
<template>
  <div v-if="modelValue">
    <div v-if="type === 'image'">
      <ElImageViewer
        :url-list="images"
        hide-on-click-modal
        @close="handleClose"
      />
    </div>
  </div>
</template>
