<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/order/config';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);
const { status, mq_delay_time_level } = useDict(
  'status',
  'mq_delay_time_level',
);

const state = reactive({
  form: {
    id: '',
    notifyUrl: '',
    status: '',
    orderCancelTimeout: '',
    orderAutoConfirmDays: 1,
    orderAutoCommentDays: 1,
    kuaidi100AppKey: '',
    remark: '',
    wxDeliveryStatus: '1',
  },
  rules: {
    notifyUrl: [
      {
        required: true,
        message: '请输入通知地址',
        trigger: 'change',
      },
    ],
    orderCancelTimeout: [
      {
        required: true,
        message: '请输入订单超时取消时间（分钟）',
        trigger: 'change',
      },
    ],
    orderAutoConfirmDays: [
      {
        required: true,
        message: '请输入订单确认收货时间（天）',
        trigger: 'change',
      },
    ],
    orderAutoCommentDays: [
      {
        required: true,
        message: '请输入订单评价时间（天）',
        trigger: 'change',
      },
    ],
    kuaidi100AppKey: [
      {
        required: true,
        message: '请输入快递100AppKey',
        trigger: 'change',
      },
    ],
    status: [
      {
        required: true,
        message: '请选择状态',
        trigger: 'change',
      },
    ],
    wxDeliveryStatus: [
      {
        required: true,
        message: '请选择微信发货配置',
        trigger: 'change',
      },
    ],
  },
});

const dialog = ref(false);
const loading = ref(false);
const formRef = ref();

const initForm = (row: any) => {
  if (row && row.id) {
    getDetail(row.id);
  }
  dialog.value = true;
};
const getDetail = (id: string) => {
  loading.value = true;
  // 修改
  getById(id)
    .then((response: any) => {
      loading.value = false;
      state.form = response;
    })
    .catch(() => {
      loading.value = false;
    });
};
/**
 * 关闭事件
 */
const handleClose = () => {
  resetForm(formRef.value);
};
/**
 * 重置表单
 */
const resetForm = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  state.form.id = '';
  loading.value = false;
  dialog.value = false;
  formEl.resetFields();
};
/**
 * 提交按钮
 */
const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (valid) {
      loading.value = true;
      if (state.form.id) {
        // 修改
        edit();
      } else {
        // 新增
        add();
      }
    }
  });
};
/**
 * 新增
 */
const add = () => {
  addObj(state.form)
    .then(() => {
      resetForm(formRef.value);
      ElMessage.success('新增成功');
      emit('initPage');
    })
    .catch(() => {
      loading.value = false;
    });
};
/**
 * 修改
 */
const edit = () => {
  editObj(state.form)
    .then(() => {
      resetForm(formRef.value);
      ElMessage.success('修改成功');
      emit('initPage');
    })
    .catch(() => {
      loading.value = false;
    });
};

defineExpose({
  initForm,
});
</script>
<template>
  <ElDialog
    v-model="dialog"
    :title="state.form.id ? '修改订单配置' : '添加订单配置'"
    width="50%"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="240px"
      :rules="state.rules"
    >
      <ElFormItem label="通知地址" prop="notifyUrl">
        <ElInput
          v-model="state.form.notifyUrl"
          maxlength="100"
          style="width: 260px"
          show-word-limit
        />
      </ElFormItem>
      <ElFormItem label="订单超时取消时间（分钟）" prop="orderCancelTimeout">
        <ElSelect v-model="state.form.orderCancelTimeout" style="width: 260px">
          <ElOption
            v-for="item in mq_delay_time_level"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="订单确认收货时间（天）" prop="orderAutoConfirmDays">
        <ElInputNumber
          style="width: 260px"
          v-model="state.form.orderAutoConfirmDays"
          :precision="0"
          controls-position="right"
          :min="1"
        />
      </ElFormItem>
      <ElFormItem label="订单评价时间（天）" prop="orderAutoCommentDays">
        <ElInputNumber
          v-model="state.form.orderAutoCommentDays"
          :precision="0"
          controls-position="right"
          style="width: 260px"
          :min="1"
        />
      </ElFormItem>
      <ElFormItem label="快递100AppKey" prop="kuaidi100AppKey">
        <ElInput
          v-model="state.form.kuaidi100AppKey"
          maxlength="100"
          show-word-limit
          style="width: 260px"
        />
      </ElFormItem>
      <ElFormItem label="微信发货配置" prop="wxDeliveryStatus">
        <ElSwitch
          v-model="state.form.wxDeliveryStatus"
          active-value="0"
          inactive-value="1"
        />
      </ElFormItem>
      <ElFormItem label="状态" prop="status">
        <ElRadioGroup v-model="state.form.status">
          <ElRadio v-for="item in status" :key="item.value" :value="item.value">
            {{ item.label }}
          </ElRadio>
        </ElRadioGroup>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <span class="dialog-footer">
        <ElButton @click="handleClose">关 闭</ElButton>
        <ElButton
          type="primary"
          :loading="loading"
          @click="submitForm(formRef)"
        >
          确 认
        </ElButton>
      </span>
    </template>
  </ElDialog>
</template>
