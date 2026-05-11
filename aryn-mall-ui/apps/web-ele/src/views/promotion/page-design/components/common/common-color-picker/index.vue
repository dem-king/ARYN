<script setup lang="ts">
import { ref, watch } from 'vue';

import { ElColorPicker } from 'element-plus';

interface Props {
  modelValue?: string;
}
const props = defineProps<Props>();
const emit = defineEmits(['update:modelValue']);
const colorValue = ref(props.modelValue);

const changeColor = (value: any) => {
  emit('update:modelValue', value);
};
watch(
  () => props.modelValue,
  (val) => {
    colorValue.value = val;
  },
);
</script>
<template>
  <div>
    <ElColorPicker
      v-model="colorValue"
      color-format="rgb"
      show-alpha
      @change="changeColor"
    />
  </div>
</template>
