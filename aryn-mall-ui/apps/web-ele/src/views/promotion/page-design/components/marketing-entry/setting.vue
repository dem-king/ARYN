<script setup lang="ts">
import type { MarketingEntryProps } from './types';

import { ref, watch } from 'vue';

import { Delete, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElTooltip,
} from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../page-designer/schema/clone';
import CommonStyle from '../common/common-style/index.vue';
import LinkUrl from '../common/link-url/index.vue';

const props = defineProps<{ modelValue: MarketingEntryProps }>();
const emit = defineEmits<{
  'update:modelValue': [value: MarketingEntryProps];
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

function addEntry() {
  form.value.entries.push({
    iconUrl: '',
    id: crypto.randomUUID(),
    link: { params: {}, path: '', type: 'customer-service' },
    title: '新入口',
  });
}

function removeEntry(index: number) {
  form.value.entries.splice(index, 1);
}
</script>

<template>
  <ElForm :model="form" label-position="top" class="retail-setting">
    <ElFormItem label="展示数量">
      <ElInputNumber v-model="form.count" :min="1" :max="10" />
    </ElFormItem>
    <ElFormItem label="每行列数">
      <ElRadioGroup v-model="form.columns">
        <ElRadio :value="4">4 列</ElRadio><ElRadio :value="5">5 列</ElRadio>
      </ElRadioGroup>
    </ElFormItem>
    <div class="entry-list">
      <section
        v-for="(entry, index) in form.entries"
        :key="entry.id"
        class="entry-item"
      >
        <header>
          <strong>入口 {{ index + 1 }}</strong>
          <ElTooltip content="删除入口">
            <ElButton
              :icon="Delete"
              aria-label="删除入口"
              circle
              size="small"
              type="danger"
              @click="removeEntry(index)"
            />
          </ElTooltip>
        </header>
        <ElFormItem label="标题">
          <ElInput v-model="entry.title" maxlength="10" show-word-limit />
        </ElFormItem>
        <ElFormItem label="图标地址">
          <ElInput
            v-model="entry.iconUrl"
            clearable
            placeholder="可留空使用默认图标"
          />
        </ElFormItem>
        <ElFormItem label="跳转链接">
          <LinkUrl v-model="entry.link" />
        </ElFormItem>
      </section>
    </div>
    <ElButton
      :disabled="form.entries.length >= 10"
      class="add-entry"
      @click="addEntry"
    >
      <ElIcon><Plus /></ElIcon>添加入口
    </ElButton>
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
.retail-setting :deep(.el-input-number),
.add-entry {
  width: 100%;
}

.entry-list {
  display: grid;
  gap: 10px;
  margin-bottom: 10px;
}

.entry-item {
  padding: 10px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
}

.entry-item header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.retail-setting__section {
  padding: 12px 0 6px;
  margin-top: 12px;
  font-weight: 600;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
