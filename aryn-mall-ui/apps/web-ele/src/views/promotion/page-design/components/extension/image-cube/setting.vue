<script setup lang="ts">
import type { ImageCubeItem, ImageCubeLayout } from '../types';

import { ref, watch } from 'vue';

import { Delete } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElRadio,
  ElRadioGroup,
  ElTooltip,
} from 'element-plus';

import {
  cloneDesignerValue,
  isSameDesignerValue,
} from '../../../../page-designer/schema/clone';
import CommonStyle from '../../common/common-style/index.vue';
import LinkUrl from '../../common/link-url/index.vue';
import { IMAGE_CUBE_LAYOUT_CELLS } from '../types';

const props = defineProps<{ modelValue: Record<string, unknown> }>();
const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>];
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

const layoutOptions = [
  { label: '单图', value: '1' },
  { label: '横排两图', value: '2v' },
  { label: '竖排两图', value: '2h' },
  { label: '四宫格', value: '4' },
  { label: '一左二右', value: '1+2' },
  { label: '一左三右', value: '1+3' },
] as const;

/** 切换布局时把 items 补齐/裁剪到目标格数，已填的内容尽量保留 */
function onLayoutChange(layout: ImageCubeLayout) {
  form.value.layout = layout;
  const items = (form.value.items ?? []) as ImageCubeItem[];
  const target = IMAGE_CUBE_LAYOUT_CELLS[layout];
  const next = [...items];
  while (next.length < target) {
    next.push({
      id: `cube-${next.length + 1}`,
      link: { params: {}, path: '', type: 'custom' },
      url: '',
    });
  }
  form.value.items = next.slice(0, target);
}

function items() {
  return (form.value.items ?? []) as ImageCubeItem[];
}

/** 手动删格：只允许删到当前布局最少需要的格数由校验兜底，这里不做下限 */
function onRemoveItem(index: number) {
  items().splice(index, 1);
}
</script>

<template>
  <ElForm :model="form" label-position="top" class="image-cube-setting">
    <ElFormItem label="布局模板">
      <ElRadioGroup
        :model-value="form.layout as ImageCubeLayout"
        @update:model-value="onLayoutChange($event as ImageCubeLayout)"
      >
        <ElRadio
          v-for="option in layoutOptions"
          :key="option.value"
          :value="option.value"
        >
          {{ option.label }}
        </ElRadio>
      </ElRadioGroup>
    </ElFormItem>
    <div class="cube-list">
      <section
        v-for="(item, index) in items()"
        :key="item.id"
        class="cube-item"
      >
        <header>
          <strong>图片 {{ index + 1 }}</strong>
          <ElTooltip v-if="items().length > 1" content="删除图片">
            <ElButton
              :icon="Delete"
              aria-label="删除图片"
              circle
              size="small"
              type="danger"
              @click="onRemoveItem(index)"
            />
          </ElTooltip>
        </header>
        <ElFormItem label="图片地址">
          <ElInput
            v-model="item.url"
            clearable
            placeholder="建议尺寸与格位比例接近"
          />
        </ElFormItem>
        <ElFormItem label="跳转链接">
          <LinkUrl v-model="item.link" />
        </ElFormItem>
      </section>
    </div>
    <div class="retail-setting__section">通用样式</div>
    <CommonStyle
      :model-value="form.commonStyle as never"
      @update:model-value="form.commonStyle = $event"
    />
  </ElForm>
</template>

<style scoped>
.image-cube-setting :deep(.el-radio-group) {
  flex-wrap: wrap;
}

.cube-list {
  display: grid;
  gap: 10px;
  margin-bottom: 10px;
}

.cube-item {
  padding: 10px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
}

.cube-item header {
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
