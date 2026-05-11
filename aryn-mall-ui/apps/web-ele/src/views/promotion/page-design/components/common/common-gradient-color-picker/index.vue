<script setup lang="ts">
import { ref, watch } from 'vue';

import { MoreFilled } from '@element-plus/icons-vue';
import { ElColorPicker, ElIcon, ElRadio, ElRadioGroup } from 'element-plus';

interface Props {
  startColor?: string;
  endColor?: string;
  direction?: string;
}
const props = defineProps<Props>();
const emit = defineEmits([
  'update:startColor',
  'update:endColor',
  'update:direction',
]);
const startColor = ref(props.startColor);
const endColor = ref(props.endColor);
const direction = ref(props.direction);

const changeStartColor = (value: any) => {
  emit('update:startColor', value);
};

const changeEndColor = (value: any) => {
  emit('update:endColor', value);
};
const directionChange = (value: any) => {
  emit('update:direction', value);
};

watch(
  () => props.startColor,
  (val) => {
    startColor.value = val;
  },
);
watch(
  () => props.endColor,
  (val) => {
    endColor.value = val;
  },
);
watch(
  () => props.direction,
  (val) => {
    direction.value = val;
  },
);
</script>
<template>
  <div>
    <ElColorPicker
      v-model="startColor"
      color-format="rgb"
      show-alpha
      @change="changeStartColor"
    />
    <ElIcon style="margin: 0 10px">
      <MoreFilled />
    </ElIcon>
    <ElColorPicker
      v-model="endColor"
      color-format="rgb"
      show-alpha
      @change="changeEndColor"
    />
    <div>
      <ElRadioGroup v-model="direction" @change="directionChange">
        <ElRadio value="to right">从左到右</ElRadio>
        <ElRadio value="to bottom">从上到下</ElRadio>
      </ElRadioGroup>
    </div>
  </div>
</template>
