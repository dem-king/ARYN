<script setup lang="ts">
import type { GoodsScrollProps } from './types';

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
import CategoryPicker from '../common/category-picker/index.vue';
import IdPickerField from '../common/id-picker-field/index.vue';

const props = defineProps<{ modelValue: GoodsScrollProps }>();
const emit = defineEmits<{ 'update:modelValue': [value: GoodsScrollProps] }>();
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
    if (!isSameDesignerValue(value, props.modelValue)) {
      emit('update:modelValue', cloneDesignerValue(value));
    }
  },
  { deep: true },
);
</script>

<template>
  <ElForm :model="form" label-position="top" class="retail-setting">
    <ElFormItem label="区块标题">
      <ElInput v-model="form.title" maxlength="10" show-word-limit />
    </ElFormItem>
    <ElFormItem label="副标题">
      <ElInput v-model="form.subtitle" maxlength="12" show-word-limit />
    </ElFormItem>
    <ElFormItem label="展示方式">
      <ElRadioGroup v-model="form.displayMode">
        <ElRadio value="scroll">手动横滑</ElRadio>
        <ElRadio value="pager">自动轮播</ElRadio>
      </ElRadioGroup>
    </ElFormItem>
    <ElFormItem label="一屏展示几个">
      <ElRadioGroup v-model="form.perView">
        <ElRadio :value="3">3 个</ElRadio>
        <ElRadio :value="4">4 个</ElRadio>
      </ElRadioGroup>
    </ElFormItem>
    <ElFormItem v-if="form.displayMode === 'pager'" label="轮播间隔(毫秒)">
      <ElInputNumber
        v-model="form.interval"
        :min="1500"
        :max="10000"
        :step="500"
      />
    </ElFormItem>
    <ElFormItem label="数据来源">
      <ElRadioGroup v-model="form.dataSource.mode">
        <ElRadio value="rule">规则选择</ElRadio>
        <ElRadio value="manual">手动选择</ElRadio>
      </ElRadioGroup>
    </ElFormItem>
    <ElFormItem v-if="form.dataSource.mode === 'manual'" label="手选商品">
      <IdPickerField
        v-model="form.dataSource.targetIds"
        kind="goods"
        :limit="20"
      />
    </ElFormItem>
    <template v-else>
      <ElFormItem label="分类">
        <CategoryPicker v-model="form.dataSource.categoryId" />
      </ElFormItem>
      <ElFormItem label="排序">
        <ElSelect v-model="form.dataSource.sort">
          <ElOption label="销量优先" value="sales" />
          <ElOption label="新品优先" value="create_time" />
          <ElOption label="价格从低到高" value="sales_price" />
        </ElSelect>
      </ElFormItem>
    </template>
    <ElFormItem label="展示数量">
      <ElInputNumber v-model="form.count" :min="1" :max="20" />
    </ElFormItem>
    <ElFormItem label="商品原价">
      <ElSwitch v-model="form.showOriginalPrice" />
    </ElFormItem>
    <ElFormItem label="无数据策略">
      <ElSelect v-model="form.emptyStrategy">
        <ElOption label="显示占位" value="placeholder" />
        <ElOption label="隐藏组件" value="hide" />
      </ElSelect>
    </ElFormItem>
    <ElFormItem label="失效数据策略">
      <ElSelect v-model="form.invalidStrategy">
        <ElOption label="隐藏失效项" value="hide" />
        <ElOption label="显示占位" value="placeholder" />
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
