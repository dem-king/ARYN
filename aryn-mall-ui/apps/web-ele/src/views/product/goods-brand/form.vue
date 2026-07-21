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
  ElRadio,
  ElRadioGroup,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/product/goods-brand';

const emit = defineEmits(['refresh']);
const visible = ref(false);
const loading = ref(false);
const formRef = ref<FormInstance>();
const form = reactive({
  id: '',
  name: '',
  logoUrl: '',
  description: '',
  status: '0',
  sort: 0,
});
const rules = {
  name: [{ required: true, message: '请输入品牌名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择品牌状态', trigger: 'change' }],
};

function reset() {
  Object.assign(form, {
    id: '',
    name: '',
    logoUrl: '',
    description: '',
    status: '0',
    sort: 0,
  });
  formRef.value?.clearValidate();
}

async function open(row?: any) {
  reset();
  visible.value = true;
  if (row?.id) Object.assign(form, await getById(row.id));
}

async function submit() {
  await formRef.value?.validate();
  loading.value = true;
  try {
    await (form.id ? editObj(form) : addObj(form));
    ElMessage.success(form.id ? '修改成功' : '新增成功');
    visible.value = false;
    emit('refresh');
  } finally {
    loading.value = false;
  }
}

defineExpose({ open });
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="form.id ? '修改品牌' : '新增品牌'"
    width="560px"
  >
    <ElForm ref="formRef" :model="form" :rules="rules" label-width="100px">
      <ElFormItem label="品牌名称" prop="name">
        <ElInput v-model="form.name" maxlength="50" show-word-limit />
      </ElFormItem>
      <ElFormItem label="Logo 地址" prop="logoUrl">
        <ElInput v-model="form.logoUrl" placeholder="请输入品牌 Logo 地址" />
      </ElFormItem>
      <ElFormItem label="品牌描述" prop="description">
        <ElInput
          v-model="form.description"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
        />
      </ElFormItem>
      <ElFormItem label="状态" prop="status">
        <ElRadioGroup v-model="form.status">
          <ElRadio value="0">启用</ElRadio>
          <ElRadio value="1">停用</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="排序" prop="sort">
        <ElInputNumber
          v-model="form.sort"
          :min="0"
          :max="9999"
          controls-position="right"
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" :loading="loading" @click="submit">
        确认
      </ElButton>
    </template>
  </ElDialog>
</template>
