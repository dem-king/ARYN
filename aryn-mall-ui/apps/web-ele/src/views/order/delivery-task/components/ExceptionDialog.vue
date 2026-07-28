<script setup lang="ts">
import type { DeliveryTask } from '#/api/order/delivery-task';

import { reactive, ref, watch } from 'vue';

import { ElMessage } from 'element-plus';
import { nanoid } from 'nanoid';

import {
  closeDeliveryTask,
  confirmDeliveryReturn,
  markDeliveryReturnPending,
} from '#/api/order/delivery-task';

const props = defineProps<{
  action: 'CLOSE' | 'CONFIRM_RETURN' | 'RETURN_PENDING';
  task?: DeliveryTask;
  visible: boolean;
}>();
const emit = defineEmits<{
  (event: 'success'): void;
  (event: 'update:visible', value: boolean): void;
}>();
const loading = ref(false);
const form = reactive({
  description: '',
  reasonCode: '',
  requestId: '',
  version: 0,
});
const titles = {
  CLOSE: '关闭配送任务',
  CONFIRM_RETURN: '确认商品已退回',
  RETURN_PENDING: '标记待退回',
};

watch(
  () => props.visible,
  (visible) => {
    if (!visible || !props.task) return;
    Object.assign(form, {
      description: '',
      reasonCode: '',
      requestId: nanoid(),
      version: props.task.version,
    });
  },
);
const close = () => emit('update:visible', false);
const submit = async () => {
  if (!props.task || !form.reasonCode) {
    ElMessage.warning('请填写操作原因');
    return;
  }
  loading.value = true;
  try {
    const handlers = {
      CLOSE: closeDeliveryTask,
      CONFIRM_RETURN: confirmDeliveryReturn,
      RETURN_PENDING: markDeliveryReturnPending,
    };
    await handlers[props.action](props.task.id, form);
    ElMessage.success('操作成功');
    close();
    emit('success');
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <ElDialog
    :model-value="visible"
    :title="titles[action]"
    width="500px"
    @close="close"
  >
    <ElForm label-position="top" :model="form">
      <ElFormItem label="原因" required>
        <ElInput
          v-model="form.reasonCode"
          maxlength="64"
          placeholder="请输入原因编码或简短原因"
        />
      </ElFormItem>
      <ElFormItem label="补充说明">
        <ElInput
          v-model="form.description"
          maxlength="500"
          show-word-limit
          type="textarea"
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="close">取消</ElButton>
      <ElButton type="primary" :loading="loading" @click="submit"
        >确认</ElButton
      >
    </template>
  </ElDialog>
</template>
