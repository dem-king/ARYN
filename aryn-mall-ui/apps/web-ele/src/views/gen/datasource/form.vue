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
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/gen/datasource';

const emit = defineEmits(['initPage']);

const state = reactive({
  form: {
    id: '',
    name: '',
    username: '',
    password: '',
    dbName: '',
    port: '',
    host: '',
  },
  rules: {
    name: [
      {
        required: true,
        message: '请输入名称',
        trigger: 'change',
      },
    ],
    username: [
      {
        required: true,
        message: '请输入用户名',
        trigger: 'change',
      },
    ],
    password: [
      {
        required: true,
        message: '请输入密码',
        trigger: 'change',
      },
    ],
    dbName: [
      {
        required: true,
        message: '请输入数据库名称',
        trigger: 'change',
      },
    ],
    port: [
      {
        required: true,
        message: '请输入端口号',
        trigger: 'change',
      },
    ],
    host: [
      {
        required: true,
        message: '请输入主机',
        trigger: 'change',
      },
    ],
  },
});
const dialog = ref(false);
const loading = ref(false);
const formRef = ref();
const isAdd = ref(false);

const initForm = (row: any | undefined) => {
  dialog.value = true;
  isAdd.value = true;
  if (row && row.id) {
    state.form.id = row.id;
    getDetail(row.id);
  }
};
const getDetail = (id: string) => {
  loading.value = true;
  getById(id).then((response) => {
    state.form = response;
    state.form.password = '';
    loading.value = false;
    isAdd.value = false;
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
      if (isAdd.value) {
        // 新增
        add();
      } else {
        // 修改
        edit();
      }
    }
  });
};
/**
 * 新增
 */
const add = async () => {
  addObj(state.form).then(() => {
    resetForm(formRef.value);
    ElMessage.success('新增成功');
    emit('initPage');
  });
};
/**
 * 修改
 */
const edit = async () => {
  editObj(state.form).then(() => {
    resetForm(formRef.value);
    ElMessage.success('修改成功');
    emit('initPage');
  });
};

defineExpose({
  initForm,
});
</script>
<template>
  <div class="system-add-user-container">
    <ElDialog
      v-model="dialog"
      :title="state.form.id ? '修改数据源' : '新增数据源'"
      width="60%"
      :before-close="handleClose"
    >
      <ElForm
        ref="formRef"
        :model="state.form"
        label-width="120px"
        :rules="state.rules"
      >
        <ElFormItem label="名称" prop="name">
          <ElInput v-model="state.form.name" maxlength="128" show-word-limit />
        </ElFormItem>
        <ElFormItem label="数据库名称" prop="dbName">
          <ElInput v-model="state.form.dbName" />
        </ElFormItem>
        <ElFormItem label="主机" prop="host">
          <ElInput v-model="state.form.host" />
        </ElFormItem>
        <ElFormItem label="端口" prop="port">
          <ElInput v-model="state.form.port" />
        </ElFormItem>
        <ElFormItem label="用户名" prop="username">
          <ElInput v-model="state.form.username" />
        </ElFormItem>
        <ElFormItem label="密码" prop="password">
          <ElInput v-model="state.form.password" />
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
