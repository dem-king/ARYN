<script lang="ts" setup>
import { reactive, ref } from 'vue';

import {
  ElButton, ElDialog, ElForm, ElFormItem, ElInputNumber, ElMessage, ElRadio, ElRadioGroup,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/user/recharge-config';

const visible = ref(false);
const loading = ref(false);
const title = ref('');
const formRef = ref();
const state = reactive({
  form: { id: '', rechargeAmount: 0.01, giftAmount: 0, giftPoint: 0, sortOrder: 0, status: '0' },
});

const rules = reactive({
  rechargeAmount: [{ required: true, message: '请输入充值金额', trigger: 'blur' }],
});

const emit = defineEmits(['init-page']);

const initForm = async (row?: any) => {
  visible.value = true;
  if (row?.id) {
    title.value = '编辑储值配置';
    const res = await getById(row.id);
    Object.assign(state.form, res);
  } else {
    title.value = '新增储值配置';
    state.form = { id: '', rechargeAmount: 0.01, giftAmount: 0, giftPoint: 0, sortOrder: 0, status: '0' };
  }
};

const submitForm = async () => {
  await formRef.value.validate();
  if (state.form.rechargeAmount <= 0) { ElMessage.warning('充值金额必须大于0'); return; }
  loading.value = true;
  try {
    if (state.form.id) { await editObj(state.form); ElMessage.success('修改成功'); }
    else { await addObj(state.form); ElMessage.success('新增成功'); }
    visible.value = false;
    emit('init-page');
  } finally { loading.value = false; }
};

defineExpose({ initForm });
</script>
<template>
  <ElDialog v-model="visible" :title="title" width="500px" draggable>
    <ElForm ref="formRef" :model="state.form" :rules="rules" label-width="100px">
      <ElFormItem label="充值金额" prop="rechargeAmount">
        <ElInputNumber v-model="state.form.rechargeAmount" :min="0.01" :precision="2" :step="1" style="width: 100%" />
      </ElFormItem>
      <ElFormItem label="赠送金额" prop="giftAmount">
        <ElInputNumber v-model="state.form.giftAmount" :min="0" :precision="2" style="width: 100%" />
      </ElFormItem>
      <ElFormItem label="赠送积分" prop="giftPoint">
        <ElInputNumber v-model="state.form.giftPoint" :min="0" style="width: 100%" />
      </ElFormItem>
      <ElFormItem label="排序号" prop="sortOrder">
        <ElInputNumber v-model="state.form.sortOrder" :min="0" style="width: 100%" />
      </ElFormItem>
      <ElFormItem label="状态" prop="status">
        <ElRadioGroup v-model="state.form.status">
          <ElRadio value="0">启用</ElRadio>
          <ElRadio value="1">禁用</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" :loading="loading" @click="submitForm">确定</ElButton>
    </template>
  </ElDialog>
</template>
