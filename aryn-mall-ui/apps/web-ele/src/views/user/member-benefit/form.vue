<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
} from 'element-plus';

import {
  addObj,
  bindLevels,
  editObj,
  getById,
} from '#/api/user/member-benefit';
import { getList as getLevelList } from '#/api/user/member-level';

const emit = defineEmits(['init-page']);
const visible = ref(false);
const loading = ref(false);
const title = ref('');
const formRef = ref();
const levelList = ref<any[]>([]);
const state = reactive({
  form: {
    id: '',
    benefitName: '',
    benefitType: '1',
    benefitValue: '',
    description: '',
    status: '0',
    levelIds: [] as string[],
  },
});

const rules = reactive({
  benefitName: [{ required: true, message: '请输入权益名称', trigger: 'blur' }],
  benefitType: [
    { required: true, message: '请选择权益类型', trigger: 'change' },
  ],
  benefitValue: [{ required: true, message: '请输入权益值', trigger: 'blur' }],
});

const loadLevelList = async () => {
  try {
    const res = await getLevelList();
    levelList.value = res;
  } catch {
    levelList.value = [];
  }
};

onMounted(() => {
  loadLevelList();
});

const initForm = async (row?: any) => {
  visible.value = true;
  if (row?.id) {
    title.value = '编辑会员权益';
    const res = await getById(row.id);
    Object.assign(state.form, res);
    if (!state.form.levelIds) {
      state.form.levelIds = [];
    }
  } else {
    title.value = '新增会员权益';
    state.form = {
      id: '',
      benefitName: '',
      benefitType: '1',
      benefitValue: '',
      description: '',
      status: '0',
      levelIds: [],
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
    // 绑定等级
    if (state.form.levelIds?.length > 0) {
      await bindLevels({
        benefitId: state.form.id,
        levelIds: state.form.levelIds,
      });
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
      label-width="120px"
    >
      <ElFormItem label="权益名称" prop="benefitName">
        <ElInput
          v-model="state.form.benefitName"
          placeholder="请输入权益名称"
        />
      </ElFormItem>
      <ElFormItem label="权益类型" prop="benefitType">
        <ElRadioGroup v-model="state.form.benefitType">
          <ElRadio value="1">折扣</ElRadio>
          <ElRadio value="2">免运费</ElRadio>
          <ElRadio value="3">专属优惠券</ElRadio>
          <ElRadio value="4">积分倍率</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="权益值" prop="benefitValue">
        <ElInput v-model="state.form.benefitValue" placeholder="请输入权益值" />
      </ElFormItem>
      <ElFormItem label="描述" prop="description">
        <ElInput
          v-model="state.form.description"
          type="textarea"
          placeholder="请输入描述"
        />
      </ElFormItem>
      <ElFormItem label="关联等级" prop="levelIds">
        <ElSelect
          v-model="state.form.levelIds"
          multiple
          placeholder="请选择关联等级"
          style="width: 100%"
        >
          <ElOption
            v-for="item in levelList"
            :key="item.id"
            :label="item.levelName"
            :value="item.id"
          />
        </ElSelect>
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
