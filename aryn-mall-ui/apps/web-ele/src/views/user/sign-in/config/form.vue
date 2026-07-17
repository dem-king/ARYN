<script lang="ts" setup>
import { reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElMessage,
  ElRadio,
  ElRadioGroup,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/user/sign-in-config';

const emit = defineEmits(['initPage']);
const visible = ref(false);
const loading = ref(false);
const title = ref('');
const formRef = ref();
const state = reactive({
  form: {
    id: '',
    consecutiveDay: 1,
    rewardPoint: 1,
    sortOrder: 0,
    status: '0',
  },
});

const rules = reactive({
  consecutiveDay: [
    { required: true, message: '请输入连续签到天数', trigger: 'blur' },
  ],
  rewardPoint: [{ required: true, message: '请输入奖励积分', trigger: 'blur' }],
});

const initForm = async (row?: any) => {
  visible.value = true;
  if (row?.id) {
    title.value = '编辑签到配置';
    const res = await getById(row.id);
    Object.assign(state.form, res);
  } else {
    title.value = '新增签到配置';
    state.form = {
      id: '',
      consecutiveDay: 1,
      rewardPoint: 1,
      sortOrder: 0,
      status: '0',
    };
  }
};

const submitForm = async () => {
  await formRef.value.validate();
  loading.value = true;
  try {
    if (state.form.id) {
      await editObj(state.form);
      ElMessage.success('修改成功');
    } else {
      await addObj(state.form);
      ElMessage.success('新增成功');
    }
    visible.value = false;
    emit('initPage');
  } finally {
    loading.value = false;
  }
};

defineExpose({ initForm });
</script>
<template>
  <ElDialog v-model="visible" :title="title" width="500px" draggable>
    <ElForm
      ref="formRef"
      :model="state.form"
      :rules="rules"
      label-width="120px"
    >
      <ElFormItem label="连续签到天数" prop="consecutiveDay">
        <ElInputNumber
          v-model="state.form.consecutiveDay"
          :min="1"
          style="width: 100%"
        />
      </ElFormItem>
      <ElFormItem label="奖励积分" prop="rewardPoint">
        <ElInputNumber
          v-model="state.form.rewardPoint"
          :min="1"
          style="width: 100%"
        />
      </ElFormItem>
      <ElFormItem label="排序号" prop="sortOrder">
        <ElInputNumber
          v-model="state.form.sortOrder"
          :min="0"
          style="width: 100%"
        />
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
      <ElButton type="primary" :loading="loading" @click="submitForm">
        确定
      </ElButton>
    </template>
  </ElDialog>
</template>
