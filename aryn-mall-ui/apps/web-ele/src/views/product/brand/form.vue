<script lang="ts" setup>
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
  ElRadio,
  ElRadioGroup,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/product/brand';

const emit = defineEmits(['initPage']);

const SelectMaterial = defineAsyncComponent(
  () => import('#/components/select-material/index.vue'),
);

interface DataState {
  form: {
    firstLetter: string;
    id: string;
    logo: string;
    name: string;
    sort: number;
    status: string;
  };
  rules: any;
}

const state = reactive<DataState>({
  form: {
    id: '',
    name: '',
    logo: '',
    firstLetter: '',
    sort: 0,
    status: '0',
  },
  rules: {
    name: [
      {
        required: true,
        message: '请输入品牌名称',
        trigger: 'blur',
      },
    ],
    firstLetter: [
      {
        required: true,
        message: '请输入首字母',
        trigger: 'blur',
      },
    ],
    sort: [
      {
        required: true,
        message: '请输入排序号',
        trigger: 'change',
      },
    ],
  },
});

const dialog = ref(false);
const loading = ref(false);
const formRef = ref();

const initForm = async (row?: any) => {
  if (row?.id) {
    loading.value = true;
    try {
      const res = await getById(row.id);
      state.form = res;
    } finally {
      loading.value = false;
    }
  } else {
    state.form = {
      id: '',
      name: '',
      logo: '',
      firstLetter: '',
      sort: 0,
      status: '0',
    };
  }
  dialog.value = true;
};

const handleClose = () => {
  resetForm(formRef.value);
};

const resetForm = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  state.form.id = '';
  loading.value = false;
  dialog.value = false;
  formEl.resetFields();
};

const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (valid) {
      loading.value = true;
      if (state.form.id) {
        edit();
      } else {
        add();
      }
    }
  });
};

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

defineExpose({ initForm });
</script>
<template>
  <ElDialog
    v-model="dialog"
    :title="state.form.id ? '编辑品牌' : '新增品牌'"
    width="500px"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="100px"
      :rules="state.rules"
    >
      <ElFormItem label="品牌名称" prop="name">
        <ElInput v-model="state.form.name" placeholder="请输入品牌名称" />
      </ElFormItem>
      <ElFormItem label="品牌Logo" prop="logo">
        <SelectMaterial v-model="state.form.logo" :can-choose-images-num="1" />
      </ElFormItem>
      <ElFormItem label="首字母" prop="firstLetter">
        <ElInput
          v-model="state.form.firstLetter"
          placeholder="请输入首字母"
          maxlength="1"
        />
      </ElFormItem>
      <ElFormItem label="排序号" prop="sort">
        <ElInputNumber
          v-model="state.form.sort"
          :min="0"
          :max="99999"
          controls-position="right"
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
