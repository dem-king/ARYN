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

import { addObj, editObj, getById } from '#/api/upms/sys-role';

const emit = defineEmits(['initPage']);
const state = reactive({
  form: {
    id: '',
    roleName: '',
    roleCode: '',
    roleDesc: '',
  },
  rules: {
    roleName: [
      {
        required: true,
        message: '请输入角色名称',
        trigger: 'change',
      },
    ],
    roleCode: [
      {
        required: true,
        message: '请输入角色编码',
        trigger: 'change',
      },
    ],
    roleDesc: [
      {
        required: true,
        message: '请输入角色描述',
        trigger: 'change',
      },
    ],
  },
  menuList: [],
});
const dialog = ref(false);
const loading = ref(false);
const formRef = ref();
const initForm = (row: any | undefined) => {
  dialog.value = true;
  if (row && row.id) {
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
    :title="state.form.id ? '修改角色' : '添加角色'"
    width="60%"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="120px"
      :rules="state.rules"
    >
      <ElFormItem label="角色名称" prop="roleName">
        <ElInput v-model="state.form.roleName" maxlength="10" show-word-limit />
      </ElFormItem>
      <ElFormItem label="角色编码" prop="roleCode">
        <ElInput
          v-model="state.form.roleCode"
          :disabled="!!state.form.id"
          maxlength="30"
          show-word-limit
        />
      </ElFormItem>
      <ElFormItem label="角色描述" prop="roleDesc">
        <ElInput
          v-model="state.form.roleDesc"
          :rows="2"
          type="textarea"
          maxlength="50"
          show-word-limit
        />
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
<style lang="scss"></style>
