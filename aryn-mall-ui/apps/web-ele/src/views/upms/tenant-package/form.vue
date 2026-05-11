<script setup lang="ts">
import type { FormInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

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
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/upms/tenant-package';
import { useDict } from '#/utils/dict';

interface DataState {
  form: {
    appKey: string;
    description: string;
    id: string | undefined;
    name: string;
    originalPrice: number;
    salesPrice: number;
    status: string;
    subTitle: string;
  };
  rules: any;
}
const emit = defineEmits(['initPage']);
const Editor = defineAsyncComponent(
  () => import('#/components/editor/index.vue'),
);
// 字典
const { application_key, status } = useDict('application_key', 'status');
const state = reactive<DataState>({
  form: {
    id: undefined,
    status: '',
    name: '',
    subTitle: '',
    originalPrice: 0,
    salesPrice: 0,
    appKey: '',
    description: '',
  },
  rules: {
    name: [
      {
        required: true,
        message: '请输入套餐名称',
        trigger: 'change',
      },
    ],
    originalPrice: [
      {
        required: true,
        message: '请输入原价（元）',
        trigger: 'change',
      },
    ],
    salesPrice: [
      {
        required: true,
        message: '请输入销售价格（元）',
        trigger: 'change',
      },
    ],
    appKey: [
      {
        required: true,
        message: '请选择应用系统',
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
  },
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
  state.form.id = undefined;
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
    :title="state.form.id ? '修改租户套餐' : '添加租户套餐'"
    width="60%"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="120px"
      :rules="state.rules"
      status-icon
    >
      <ElFormItem label="套餐名称" prop="name">
        <ElInput v-model="state.form.name" show-word-limit maxlength="50" />
      </ElFormItem>
      <ElFormItem label="子标题" prop="subTitle">
        <ElInput v-model="state.form.subTitle" />
      </ElFormItem>
      <ElFormItem label="原价（元）" prop="originalPrice">
        <ElInputNumber
          :min="0.01"
          :precision="2"
          v-model="state.form.originalPrice"
        />
      </ElFormItem>
      <ElFormItem label="销售价格（元）" prop="salesPrice">
        <ElInputNumber
          :min="0.01"
          :precision="2"
          v-model="state.form.salesPrice"
        />
      </ElFormItem>
      <ElFormItem label="应用系统" prop="appKey">
        <ElSelect v-model="state.form.appKey" multiple>
          <ElOption
            v-for="item in application_key"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="状态" prop="status">
        <ElRadioGroup v-model="state.form.status">
          <ElRadio v-for="item in status" :key="item.value" :value="item.value">
            {{ item.label }}
          </ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="描述" prop="description">
        <Editor
          v-model:get-html="state.form.description"
          height="500px"
          :disable="false"
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <span class="dialog-footer">
        <ElButton @click="handleClose">关 闭</ElButton>
        <ElButton type="primary" @click="submitForm(formRef)">确 认</ElButton>
      </span>
    </template>
  </ElDialog>
</template>
