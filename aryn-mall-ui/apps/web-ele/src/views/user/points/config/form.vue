<script lang="ts" setup>
import { reactive, ref } from 'vue';

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

import { addObj, editObj, getById } from '#/api/user/points-config';

const emit = defineEmits(['init-page']);
const visible = ref(false);
const loading = ref(false);
const title = ref('');
const formRef = ref();
const state = reactive({
  form: {
    id: '',
    ruleName: '',
    ruleType: '1',
    triggerScene: '',
    pointValue: 1,
    status: '0',
  },
});

const rules = reactive({
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  ruleType: [{ required: true, message: '请选择规则类型', trigger: 'change' }],
  triggerScene: [
    { required: true, message: '请选择触发场景', trigger: 'change' },
  ],
  pointValue: [{ required: true, message: '请输入积分值', trigger: 'blur' }],
});

const sceneOptions = [
  { label: '注册', value: 'REGISTER' },
  { label: '消费', value: 'CONSUME' },
  { label: '签到', value: 'SIGN_IN' },
  { label: '评价', value: 'REVIEW' },
  { label: '兑换', value: 'EXCHANGE' },
];

const initForm = async (row?: any) => {
  visible.value = true;
  if (row?.id) {
    title.value = '编辑积分配置';
    const res = await getById(row.id);
    Object.assign(state.form, res);
  } else {
    title.value = '新增积分配置';
    state.form = {
      id: '',
      ruleName: '',
      ruleType: '1',
      triggerScene: '',
      pointValue: 1,
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
    emit('init-page');
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
      label-width="100px"
    >
      <ElFormItem label="规则名称" prop="ruleName">
        <ElInput v-model="state.form.ruleName" placeholder="请输入规则名称" />
      </ElFormItem>
      <ElFormItem label="规则类型" prop="ruleType">
        <ElRadioGroup v-model="state.form.ruleType">
          <ElRadio value="1">获取</ElRadio>
          <ElRadio value="2">消耗</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="触发场景" prop="triggerScene">
        <ElSelect
          v-model="state.form.triggerScene"
          placeholder="请选择触发场景"
          style="width: 100%"
        >
          <ElOption
            v-for="item in sceneOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="积分值" prop="pointValue">
        <ElInputNumber
          v-model="state.form.pointValue"
          :min="1"
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
