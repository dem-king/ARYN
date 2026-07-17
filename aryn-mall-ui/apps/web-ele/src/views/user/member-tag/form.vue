<script lang="ts" setup>
import { reactive, ref } from 'vue';

import {
  ElButton,
  ElColorPicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElRadio,
  ElRadioGroup,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/user/member-tag';

const emit = defineEmits(['initPage']);
const visible = ref(false);
const loading = ref(false);
const title = ref('');
const formRef = ref();
const state = reactive({
  form: {
    id: '',
    tagName: '',
    tagColor: '',
    sortOrder: 0,
    status: '0',
  },
});

const rules = reactive({
  tagName: [{ required: true, message: '请输入标签名称', trigger: 'blur' }],
});

const initForm = async (row?: any) => {
  visible.value = true;
  if (row?.id) {
    title.value = '编辑会员标签';
    const res = await getById(row.id);
    Object.assign(state.form, res);
  } else {
    title.value = '新增会员标签';
    state.form = {
      id: '',
      tagName: '',
      tagColor: '',
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
      <ElFormItem label="标签名称" prop="tagName">
        <ElInput v-model="state.form.tagName" placeholder="请输入标签名称" />
      </ElFormItem>
      <ElFormItem label="标签颜色" prop="tagColor">
        <ElColorPicker v-model="state.form.tagColor" />
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
