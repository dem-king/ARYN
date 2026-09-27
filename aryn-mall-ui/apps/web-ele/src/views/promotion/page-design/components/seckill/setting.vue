<script setup lang="ts">
import type { SeckillProps } from './types';

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
import CommonStyle from '../common/common-style/index.vue';

const props = defineProps<{ modelValue: SeckillProps }>();
const emit = defineEmits<{
  'update:modelValue': [value: SeckillProps];
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
    <ElFormItem label="数据来源">
      <ElRadioGroup v-model="form.dataSource.mode">
        <ElRadio value="automatic">自动读取进行中/即将开始场次</ElRadio>
        <ElRadio value="manual">手动选择</ElRadio>
      </ElRadioGroup>
    </ElFormItem>
    <ElFormItem v-if="form.dataSource.mode === 'manual'" label="秒杀场次 ID">
      <ElSelect
        v-model="form.dataSource.targetIds"
        allow-create
        default-first-option
        filterable
        multiple
        placeholder="输入场次 ID 后回车"
      />
    </ElFormItem>
    <ElFormItem label="展示场次数">
      <ElInputNumber v-model="form.count" :min="1" :max="5" />
    </ElFormItem>
    <ElFormItem label="显示场次倒计时">
      <ElSwitch v-model="form.showCountdown" />
    </ElFormItem>
    <ElFormItem label="显示已售进度">
      <ElSwitch v-model="form.showProgress" />
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
    <div class="retail-setting__section">通用样式</div>
    <CommonStyle v-model="form.commonStyle" />
  </ElForm>
</template>

<style scoped>
.retail-setting :deep(.el-select),
.retail-setting :deep(.el-input-number) {
  width: 100%;
}

.retail-setting__section {
  padding: 12px 0 6px;
  font-weight: 600;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
