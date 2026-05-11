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
  ElRadio,
  ElRadioGroup,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/upms/logistics-company';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);
// 字典
const { status } = useDict('status');
const state = reactive({
  form: {
    id: '',
    code: '',
    name: '',
    status: '',
  },
  rules: {
    code: [
      {
        required: true,
        message: '请输入物流名称',
        trigger: 'change',
      },
    ],
    name: [
      {
        required: true,
        message: '请输入物流编码',
        trigger: 'change',
      },
    ],
  },
  menuList: [],
});
const dialog = ref(false);
const loading = ref(false);
const formRef = ref();
const initForm = (row: any) => {
  dialog.value = true;
  if (row && row.id) {
    getDetail(row.id);
  }
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
      ElMessage.success('操作成功');
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
    :title="state.form.id ? '修改物流' : '添加物流'"
    width="38%"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="120px"
      :rules="state.rules"
    >
      <ElFormItem label="物流名称" prop="name">
        <ElInput v-model="state.form.name" />
      </ElFormItem>
      <ElFormItem label="物流编码" prop="code">
        <ElInput v-model="state.form.code" />
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
<style lang="scss"></style>
