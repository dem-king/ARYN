<script setup lang="ts">
import type {
  AssignmentForm,
  DeliveryStaff,
  DeliveryTask,
} from '#/api/order/delivery-task';

import { computed, reactive, ref, watch } from 'vue';

import { ElMessage } from 'element-plus';
import { nanoid } from 'nanoid';

import {
  assignDeliveryTask,
  buildAssignmentPayload,
  getDeliveryStaffCandidates,
  reassignDeliveryTask,
} from '#/api/order/delivery-task';

const props = defineProps<{
  mode: 'ASSIGN' | 'REASSIGN';
  task?: DeliveryTask;
  visible: boolean;
}>();
const emit = defineEmits<{
  (event: 'success'): void;
  (event: 'update:visible', value: boolean): void;
}>();

const loading = ref(false);
const staffLoading = ref(false);
const staffOptions = ref<DeliveryStaff[]>([]);
const form = reactive<AssignmentForm>({
  assigneeId: '',
  description: '',
  reasonCode: '',
  remark: '',
  requestId: '',
  version: 0,
});
const title = computed(() =>
  props.mode === 'ASSIGN' ? '派发配送任务' : '改派配送任务',
);

watch(
  () => props.visible,
  async (visible) => {
    if (!visible || !props.task) return;
    Object.assign(form, {
      assigneeId: props.mode === 'REASSIGN' ? props.task.assigneeId || '' : '',
      description: '',
      reasonCode: '',
      remark: '',
      requestId: nanoid(),
      version: props.task.version,
    });
    staffLoading.value = true;
    try {
      const response = await getDeliveryStaffCandidates({ limit: 500 });
      staffOptions.value = response.records || [];
    } finally {
      staffLoading.value = false;
    }
  },
);

const close = () => emit('update:visible', false);
const submit = async () => {
  if (!props.task || !form.assigneeId) {
    ElMessage.warning('请选择配送员');
    return;
  }
  if (props.mode === 'REASSIGN' && !form.reasonCode) {
    ElMessage.warning('请选择改派原因');
    return;
  }
  loading.value = true;
  try {
    const payload = buildAssignmentPayload(props.mode, form) as AssignmentForm;
    await (props.mode === 'ASSIGN'
      ? assignDeliveryTask(props.task.id, payload)
      : reassignDeliveryTask(props.task.id, payload));
    ElMessage.success(props.mode === 'ASSIGN' ? '派单成功' : '改派成功');
    close();
    emit('success');
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <ElDialog :model-value="visible" :title="title" width="520px" @close="close">
    <ElAlert
      class="mb-5"
      :closable="false"
      show-icon
      title="派单后将同时发送站内提醒；已绑定配送微信的员工还会收到订阅消息。"
      type="info"
    />
    <ElForm label-position="top" :model="form">
      <ElFormItem label="配送员" required>
        <ElSelect
          v-model="form.assigneeId"
          class="w-full"
          filterable
          :loading="staffLoading"
          placeholder="按姓名或手机号选择"
        >
          <ElOption
            v-for="staff in staffOptions"
            :key="staff.id"
            :label="`${staff.nickname}${staff.phone ? ` · ${staff.phone}` : ''}`"
            :value="staff.id"
          />
        </ElSelect>
      </ElFormItem>
      <template v-if="mode === 'REASSIGN'">
        <ElFormItem label="改派原因" required>
          <ElSelect
            v-model="form.reasonCode"
            class="w-full"
            placeholder="请选择原因"
          >
            <ElOption label="配送员忙碌" value="STAFF_BUSY" />
            <ElOption label="配送员无法履约" value="STAFF_UNAVAILABLE" />
            <ElOption label="客户协商调整" value="CUSTOMER_REQUEST" />
            <ElOption label="其他" value="OTHER" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="原因说明">
          <ElInput
            v-model="form.description"
            maxlength="500"
            show-word-limit
            type="textarea"
          />
        </ElFormItem>
      </template>
      <ElFormItem v-else label="派单备注">
        <ElInput
          v-model="form.remark"
          maxlength="500"
          show-word-limit
          type="textarea"
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="close">取消</ElButton>
      <ElButton type="primary" :loading="loading" @click="submit">
        确认{{ mode === 'ASSIGN' ? '派单' : '改派' }}
      </ElButton>
    </template>
  </ElDialog>
</template>
