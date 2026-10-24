<script lang="ts" setup>
import type { PointsGlobalConfig } from '#/api/user/points-config';

import { onMounted, reactive, ref } from 'vue';

import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElMessage,
} from 'element-plus';

import { getGlobalConfig, saveGlobalConfig } from '#/api/user/points-config';
import PageSection from '#/components/page/page-section.vue';

interface GlobalConfigForm {
  validityDays: null | number;
  deductMaxRatio: null | number;
  minUseAmount: null | number;
  maxDeductAmount: null | number;
}

const loading = ref(false);
const saving = ref(false);
const formRef = ref();
const configId = ref('');
const state = reactive<{ form: GlobalConfigForm }>({
  form: {
    validityDays: null,
    deductMaxRatio: null,
    minUseAmount: null,
    maxDeductAmount: null,
  },
});

// 抵扣上限比例为百分数口径（20 表示 20%），后端校验 (0,100]，为必填项；
// 其余三项空值提交时统一转 0（0=永久/无门槛/不封顶）。
const rules = reactive({
  deductMaxRatio: [
    { required: true, message: '请输入抵扣上限比例', trigger: 'blur' },
  ],
});

const initForm = async () => {
  loading.value = true;
  try {
    const res: PointsGlobalConfig = await getGlobalConfig();
    configId.value = res?.id ?? '';
    state.form = {
      validityDays: res?.validityDays ?? 0,
      deductMaxRatio: res?.deductMaxRatio ?? 20,
      minUseAmount: res?.minUseAmount ?? 0,
      maxDeductAmount: res?.maxDeductAmount ?? 0,
    };
  } finally {
    loading.value = false;
  }
};

const submitForm = async () => {
  await formRef.value.validate();
  const form = state.form;
  const payload: PointsGlobalConfig = {
    id: configId.value || undefined,
    validityDays: form.validityDays ?? 0,
    deductMaxRatio: form.deductMaxRatio ?? 20,
    minUseAmount: form.minUseAmount ?? 0,
    maxDeductAmount: form.maxDeductAmount ?? 0,
  };
  saving.value = true;
  try {
    await saveGlobalConfig(payload);
    ElMessage.success('保存成功');
    await initForm();
  } finally {
    saving.value = false;
  }
};

onMounted(() => {
  initForm();
});
</script>
<template>
  <div v-loading="loading">
    <PageSection
      title="抵扣与有效期"
      description="积分风控全局参数，对本租户所有积分发放与抵扣即时生效"
    >
      <ElForm
        ref="formRef"
        :model="state.form"
        :rules="rules"
        label-width="130px"
        style="max-width: 520px"
      >
        <ElFormItem label="积分有效天数">
          <div class="w-full">
            <ElInputNumber
              v-model="state.form.validityDays"
              :min="0"
              :max="3650"
              :step="1"
              :precision="0"
              controls-position="right"
              style="width: 100%"
            />
            <div class="field-hint">积分获得后可使用的天数，0 表示永不过期</div>
          </div>
        </ElFormItem>
        <ElFormItem label="抵扣上限比例(%)" prop="deductMaxRatio">
          <div class="w-full">
            <ElInputNumber
              v-model="state.form.deductMaxRatio"
              :min="1"
              :max="100"
              :step="1"
              :precision="0"
              controls-position="right"
              style="width: 100%"
            />
            <div class="field-hint">
              单笔订单积分最高可抵扣金额的比例，如输入 20 表示最多抵 20%
            </div>
          </div>
        </ElFormItem>
        <ElFormItem label="最低使用门槛(元)">
          <div class="w-full">
            <ElInputNumber
              v-model="state.form.minUseAmount"
              :min="0"
              :step="1"
              :precision="2"
              controls-position="right"
              style="width: 100%"
            />
            <div class="field-hint">
              订单金额满 X 元才可使用积分抵扣，0 表示无门槛
            </div>
          </div>
        </ElFormItem>
        <ElFormItem label="单笔封顶(元)">
          <div class="w-full">
            <ElInputNumber
              v-model="state.form.maxDeductAmount"
              :min="0"
              :step="1"
              :precision="2"
              controls-position="right"
              style="width: 100%"
            />
            <div class="field-hint">单笔订单积分抵扣金额上限，0 表示不封顶</div>
          </div>
        </ElFormItem>
        <ElFormItem>
          <ElButton
            type="primary"
            v-access:code="'user:pointsconfig:edit'"
            :loading="saving"
            @click="submitForm"
          >
            保存
          </ElButton>
        </ElFormItem>
      </ElForm>
    </PageSection>
  </div>
</template>

<style scoped>
.field-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}
</style>
