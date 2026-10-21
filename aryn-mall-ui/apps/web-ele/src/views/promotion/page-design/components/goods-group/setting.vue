<script setup lang="ts">
import type { GoodsGroupProps } from './types';

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

const props = defineProps<{ modelValue: GoodsGroupProps }>();
const emit = defineEmits<{ 'update:modelValue': [value: GoodsGroupProps] }>();
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
      <ElInput v-model="form.title" maxlength="20" show-word-limit />
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
          <ElOption label="销量优先" value="sales" /><ElOption
            label="新品优先"
            value="create_time"
          /><ElOption label="价格从低到高" value="sales_price" />
        </ElSelect>
      </ElFormItem>
    </template>
    <ElFormItem
      :label="
        form.dataSource.mode === 'manual' ? '最多展示数量' : '每页加载数量'
      "
    >
      <ElInputNumber v-model="form.count" :min="1" :max="20" />
      <div class="retail-setting__hint">
        {{
          form.dataSource.mode === 'manual'
            ? '手选商品超过该数量时，前台只展示前 N 个'
            : '自动规则模式下前台按该数量分批加载，滚动会继续加载'
        }}
      </div>
    </ElFormItem>
    <ElFormItem label="每行列数">
      <ElRadioGroup v-model="form.columns">
        <ElRadio :value="2">2 列</ElRadio><ElRadio :value="3">3 列</ElRadio>
      </ElRadioGroup>
    </ElFormItem>
    <ElFormItem label="显示销量">
      <ElSwitch v-model="form.showSales" />
    </ElFormItem>
    <ElFormItem label="商品原价">
      <ElSwitch v-model="form.showOriginalPrice" />
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

.retail-setting__hint {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}
</style>
