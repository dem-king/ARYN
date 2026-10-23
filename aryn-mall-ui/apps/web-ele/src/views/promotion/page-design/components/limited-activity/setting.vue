<script setup lang="ts">
import type { LimitedActivityProps } from './types';

import { ref, watch } from 'vue';

import {
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';
import IdPickerField from '../common/id-picker-field/index.vue';

const props = defineProps<{ modelValue: LimitedActivityProps }>();
const emit = defineEmits<{
  'update:modelValue': [value: LimitedActivityProps];
}>();
const form = ref(cloneDesignerValue(props.modelValue));

watch(
  () => props.modelValue,
  (value) => {
    if (!isSameDesignerValue(form.value, value))
      form.value = cloneDesignerValue(value);
  },
  { deep: true },
);
watch(
  form,
  (value) => {
    if (!isSameDesignerValue(value, props.modelValue))
      emit('update:modelValue', cloneDesignerValue(value));
  },
  { deep: true },
);
</script>

<template>
  <ElForm :model="form" label-position="top" class="retail-setting">
    <ElFormItem label="区块标题">
      <ElInput v-model="form.title" maxlength="20" show-word-limit />
    </ElFormItem>
    <ElFormItem label="副标题">
      <ElInput
        v-model="form.subtitle"
        maxlength="20"
        placeholder="留空使用默认：邀好友一起拼 · 人越多越划算"
        show-word-limit
      />
    </ElFormItem>
    <ElFormItem label="展示样式">
      <ElRadioGroup v-model="form.layout">
        <ElRadio value="list">单列横排</ElRadio>
        <ElRadio value="grid">双列货架</ElRadio>
      </ElRadioGroup>
    </ElFormItem>
    <ElFormItem label="数据来源">
      <ElRadioGroup v-model="form.dataSource.mode">
        <ElRadio value="automatic">自动读取进行中拼团</ElRadio>
        <ElRadio value="manual">手动选择</ElRadio>
      </ElRadioGroup>
    </ElFormItem>
    <ElFormItem v-if="form.dataSource.mode === 'manual'" label="拼团活动">
      <IdPickerField
        v-model="form.dataSource.targetIds"
        kind="groupbuy-activity"
        :limit="10"
      />
    </ElFormItem>
    <ElFormItem label="展示数量">
      <ElInputNumber v-model="form.count" :min="1" :max="10" />
    </ElFormItem>
    <ElFormItem label="显示结束时间">
      <ElSwitch v-model="form.showCountdown" />
    </ElFormItem>
    <ElFormItem label="无数据策略">
      <ElSelect v-model="form.emptyStrategy">
        <ElOption label="显示占位" value="placeholder" /><ElOption
          label="隐藏组件"
          value="hide"
        />
      </ElSelect>
    </ElFormItem>
    <ElFormItem label="失效数据策略">
      <ElSelect v-model="form.invalidStrategy">
        <ElOption label="隐藏失效项" value="hide" /><ElOption
          label="显示占位"
          value="placeholder"
        />
      </ElSelect>
    </ElFormItem>
  </ElForm>
</template>

<style scoped>
.retail-setting :deep(.el-select),
.retail-setting :deep(.el-input-number) {
  width: 100%;
}
</style>
