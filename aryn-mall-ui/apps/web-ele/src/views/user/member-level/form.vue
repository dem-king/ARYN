<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElDivider,
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
    growthValue: 0,
    isPaid: '0',
    price: 0,
    duration: 0,
    exclusiveDiscount: 100,
    birthdayGiftPoints: 0,
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
  growthValue: [
    { required: true, message: '请输入成长值阈值', trigger: 'blur' },
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
      growthValue: 0,
      isPaid: '0',
      price: 0,
      duration: 0,
      exclusiveDiscount: 100,
      birthdayGiftPoints: 0,
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
  <ElDialog v-model="visible" :title="title" width="560px" draggable>
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
      <ElFormItem label="成长值阈值" prop="growthValue">
        <ElInputNumber
          v-model="state.form.growthValue"
          :min="0"
          :precision="0"
          :step="100"
          style="width: 100%"
          placeholder="达到此成长值可升级"
        />
      </ElFormItem>

      <ElDivider content-position="left">付费会员配置</ElDivider>

      <ElFormItem label="是否付费" prop="isPaid">
        <ElRadioGroup v-model="state.form.isPaid">
          <ElRadio value="0">否</ElRadio>
          <ElRadio value="1">是</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem
        v-if="state.form.isPaid === '1'"
        label="开通价格"
        prop="price"
      >
        <ElInputNumber
          v-model="state.form.price"
          :min="0.01"
          :precision="2"
          :step="1"
          style="width: 100%"
          placeholder="付费开通价格（元）"
        />
      </ElFormItem>
      <ElFormItem
        v-if="state.form.isPaid === '1'"
        label="有效期(月)"
        prop="duration"
      >
        <ElInputNumber
          v-model="state.form.duration"
          :min="1"
          :precision="0"
          :step="1"
          style="width: 100%"
          placeholder="付费会员有效期月数"
        />
      </ElFormItem>

      <ElDivider content-position="left">等级权益配置</ElDivider>

      <ElFormItem label="专属折扣(%)" prop="exclusiveDiscount">
        <ElInputNumber
          v-model="state.form.exclusiveDiscount"
          :min="1"
          :max="100"
          :precision="0"
          :step="1"
          style="width: 100%"
          placeholder="1-100，如95表示9.5折"
        />
      </ElFormItem>
      <ElFormItem label="生日礼包积分" prop="birthdayGiftPoints">
        <ElInputNumber
          v-model="state.form.birthdayGiftPoints"
          :min="0"
          :precision="0"
          :step="10"
          style="width: 100%"
          placeholder="生日当月赠送积分"
        />
      </ElFormItem>

      <ElDivider content-position="left">基础配置</ElDivider>

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
