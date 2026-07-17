<script lang="ts" setup>
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

import { addObj, editObj, getById } from '#/api/user/member-level';

const emit = defineEmits(['initPage']);

const SelectMaterial = defineAsyncComponent(
  () => import('#/components/select-material/index.vue'),
);

const visible = ref(false);
const loading = ref(false);
const title = ref('');
const formRef = ref();
const state = reactive({
  form: {
    id: '',
    levelName: '',
    levelIcon: '',
    conditionType: '1',
    conditionValue: 0,
    sortOrder: 0,
    status: '0',
  },
});

const rules = reactive({
  levelName: [{ required: true, message: '请输入等级名称', trigger: 'blur' }],
  conditionType: [
    { required: true, message: '请选择升级条件类型', trigger: 'change' },
  ],
  conditionValue: [
    { required: true, message: '请输入升级条件值', trigger: 'blur' },
  ],
});

const initForm = async (row?: any) => {
  visible.value = true;
  if (row?.id) {
    title.value = '编辑会员等级';
    const res = await getById(row.id);
    Object.assign(state.form, res);
  } else {
    title.value = '新增会员等级';
    state.form = {
      id: '',
      levelName: '',
      levelIcon: '',
      conditionType: '1',
      conditionValue: 0,
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
      <ElFormItem label="等级名称" prop="levelName">
        <ElInput v-model="state.form.levelName" placeholder="请输入等级名称" />
      </ElFormItem>
      <ElFormItem label="等级图标" prop="levelIcon">
        <SelectMaterial v-model="state.form.levelIcon" />
      </ElFormItem>
      <ElFormItem label="升级条件类型" prop="conditionType">
        <ElRadioGroup v-model="state.form.conditionType">
          <ElRadio value="1">累计消费金额</ElRadio>
          <ElRadio value="2">累计积分</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="升级条件值" prop="conditionValue">
        <ElInputNumber
          v-model="state.form.conditionValue"
          :min="0.01"
          :precision="2"
          :step="1"
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
