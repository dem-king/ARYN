<script setup lang="ts">
import { ref } from 'vue';

import { ElButton, ElDialog, ElMessage } from 'element-plus';

import Material from '#/views/upms/material/image-tab/index.vue';

// Props 定义
const props = defineProps({
  canChooseImagesNum: {
    type: Number,
    default: 1,
  },
});
// Emits 定义
const emit = defineEmits(['changeImg']);
const dialog = ref<boolean>(false);
const chooseImages = ref<any>([]);
const openDialog = () => {
  dialog.value = true;
};

// 确认选择
const submit = () => {
  if (chooseImages.value.length <= 0) {
    ElMessage.error('请选择素材');
    return;
  }
  dialog.value = false;
  emit('changeImg', chooseImages.value);
};
const selectChange = (item: any) => {
  chooseImages.value = item;
};

defineExpose({
  openDialog,
});
</script>

<!-- 保持模板部分不变 -->
<template>
  <ElDialog v-model="dialog" title="素材" width="70%" append-to-body>
    <Material
      :selectable="true"
      :max-select="props.canChooseImagesNum"
      mode="dialog"
      @select-change="selectChange"
    />
    <template #footer>
      <span class="dialog-footer">
        <ElButton @click="dialog = false">关 闭</ElButton>
        <ElButton type="primary" @click="submit">确 认</ElButton>
      </span>
    </template>
  </ElDialog>
</template>
