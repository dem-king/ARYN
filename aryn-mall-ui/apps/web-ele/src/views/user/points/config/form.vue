<script lang="ts" setup>
import { computed, reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/user/points-config';
import PageSection from '#/components/page/page-section.vue';

import { getPointsSceneOption, pointsSceneOptions } from '../scene-options';

const emit = defineEmits(['initPage']);
const visible = ref(false);
const loading = ref(false);
const title = ref('');
const originScene = ref('');
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

const sceneOptions = pointsSceneOptions;

const sceneOption = computed(() =>
  getPointsSceneOption(state.form.triggerScene),
);
const pointValueLabel = computed(() => {
  if (state.form.triggerScene === 'ORDER_COMPLETE') return '每元获得积分';
  if (state.form.triggerScene === 'ORDER_DEDUCT') return '每元所需积分';
  return '积分值';
});

const handleSceneChange = (value: string) => {
  const option = getPointsSceneOption(value);
  if (!option) return;
  state.form.ruleType = option.ruleType;
  if (option.value === 'ORDER_COMPLETE') {
    state.form.pointValue = 1;
  } else if (option.value === 'ORDER_DEDUCT' || option.value === 'REGISTER') {
    state.form.pointValue = 100;
  }
};

const initForm = async (row?: any) => {
  visible.value = true;
  if (row?.id) {
    title.value = '编辑积分配置';
    const res = await getById(row.id);
    Object.assign(state.form, res);
    originScene.value = state.form.triggerScene;
  } else {
    title.value = '新增积分配置';
    originScene.value = '';
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
  // 场景决定发放/抵扣口径：编辑既有规则时改场景会立即改变线上行为，需要二次确认
  if (
    state.form.id &&
    originScene.value &&
    state.form.triggerScene !== originScene.value
  ) {
    try {
      await ElMessageBox.confirm(
        `触发场景将从「${getPointsSceneOption(originScene.value)?.label || originScene.value}」改为「${getPointsSceneOption(state.form.triggerScene)?.label || state.form.triggerScene}」，修改后立即生效，是否继续?`,
        '提示',
        {
          confirmButtonText: '确认',
          cancelButtonText: '取消',
          type: 'warning',
        },
      );
    } catch {
      return;
    }
  }
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
    <ElForm ref="formRef" :model="state.form" :rules="rules" label-width="auto">
      <PageSection title="规则信息" :bordered="false">
        <ElFormItem label="规则名称" prop="ruleName">
          <ElInput v-model="state.form.ruleName" placeholder="请输入规则名称" />
        </ElFormItem>
        <ElFormItem label="规则类型" prop="ruleType">
          <ElRadioGroup v-model="state.form.ruleType" disabled>
            <ElRadio value="1">获取</ElRadio>
            <ElRadio value="2">消耗</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="触发场景" prop="triggerScene">
          <div class="w-full">
            <ElSelect
              v-model="state.form.triggerScene"
              placeholder="请选择触发场景"
              style="width: 100%"
              @change="handleSceneChange"
            >
              <ElOption
                v-for="item in sceneOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
            <div v-if="sceneOption" class="mt-1 text-xs text-gray-400">
              {{ sceneOption.hint }}
            </div>
          </div>
        </ElFormItem>
        <ElFormItem :label="pointValueLabel" prop="pointValue">
          <ElInputNumber
            v-model="state.form.pointValue"
            :min="1"
            style="width: 100%"
          />
        </ElFormItem>
      </PageSection>
      <PageSection title="状态设置" :bordered="false">
        <ElFormItem label="状态" prop="status">
          <ElRadioGroup v-model="state.form.status">
            <ElRadio value="0">启用</ElRadio>
            <ElRadio value="1">禁用</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
      </PageSection>
    </ElForm>
    <template #footer>
      <div class="hx-form-footer">
        <ElButton @click="visible = false">取消</ElButton>
        <ElButton type="primary" :loading="loading" @click="submitForm">
          确定
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>
