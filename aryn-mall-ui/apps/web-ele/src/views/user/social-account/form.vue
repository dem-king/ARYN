<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/user/social-account';

const emit = defineEmits(['initPage']);

const state = reactive({
  form: {
    id: null,
    type: 'WX_MA',
    appId: null,
    appSecret: null,
    createTime: null,
    updateTime: null,
    delFlag: null,
    createBy: null,
    updateBy: null,
  },
  rules: {
    type: [{ required: true, message: '请选择账号类型', trigger: 'change' }],
    appId: [{ required: true, message: '请输入账号', trigger: 'change' }],
    appSecret: [{ required: true, message: '请输入密钥', trigger: 'change' }],
  },
});
const dialog = ref(false);
const loading = ref(false);
const formRef = ref();

const initForm = (row: any | undefined) => {
  dialog.value = true;
  if (row && row.id) {
    state.form.id = row.id;
    getDetail(row.id);
  }
};
const getDetail = (id: string) => {
  loading.value = true;
  getById(id).then((response) => {
    state.form = response;
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
  state.form.id = null;
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
const add = async () => {
  try {
    await addObj(state.form);
    resetForm(formRef.value);
    ElMessage.success('新增成功');
    emit('initPage');
  } finally {
    loading.value = false;
  }
};
/**
 * 修改
 */
const edit = async () => {
  try {
    await editObj(state.form);
    resetForm(formRef.value);
    ElMessage.success('修改成功');
    emit('initPage');
  } finally {
    loading.value = false;
  }
};

defineExpose({
  initForm,
});
</script>
<template>
  <div>
    <ElDialog
      v-model="dialog"
      :title="state.form.id ? '修改三方平台账号' : '新增三方平台账号'"
      width="60%"
      :before-close="handleClose"
    >
      <ElForm
        ref="formRef"
        :model="state.form"
        label-width="120px"
        :rules="state.rules"
      >
        <ElFormItem label="账号类型" prop="type">
          <ElSelect v-model="state.form.type" placeholder="请选择账号类型">
            <ElOption value="WX_MA" label="微信小程序" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="账号" prop="appId">
          <ElInput v-model="state.form.appId" placeholder="请输入账号" />
        </ElFormItem>

        <ElFormItem label="密钥" prop="appSecret">
          <ElInput v-model="state.form.appSecret" placeholder="请输入密钥" />
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
  </div>
</template>
