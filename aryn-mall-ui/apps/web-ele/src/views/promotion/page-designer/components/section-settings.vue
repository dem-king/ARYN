<script setup lang="ts">
import type { DecorationSection, SectionStyle } from '../schema/types';

import { reactive, watch } from 'vue';

import {
  ElColorPicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import { cloneDesignerValue } from '../schema/clone';

const props = defineProps<{ section?: DecorationSection }>();

const emit = defineEmits<{
  patch: [
    patch: {
      name?: string;
      style?: Partial<SectionStyle>;
    },
    groupKey?: string,
  ];
}>();

const form = reactive({
  backgroundColor: '',
  backgroundImage: '',
  condition: 'always' as SectionStyle['condition'],
  horizontalScroll: false,
  name: '',
  paddingY: 0,
  sticky: false,
});

watch(
  () => props.section,
  (section) => {
    if (!section) return;
    form.backgroundColor = section.style.backgroundColor;
    form.backgroundImage = section.style.backgroundImage;
    form.condition = section.style.condition;
    form.horizontalScroll = section.style.horizontalScroll;
    form.name = section.name ?? '';
    form.paddingY = section.style.paddingY;
    form.sticky = section.style.sticky;
  },
  { immediate: true, deep: true },
);

function emitPatch(groupKey: string) {
  const style: Partial<SectionStyle> = cloneDesignerValue({
    backgroundColor: form.backgroundColor,
    backgroundImage: form.backgroundImage,
    condition: form.condition,
    horizontalScroll: form.horizontalScroll,
    paddingY: form.paddingY,
    sticky: form.sticky,
  });
  emit(
    'patch',
    {
      name: form.name,
      style,
    },
    groupKey,
  );
}
</script>

<template>
  <div v-if="section" class="section-settings">
    <ElForm label-position="top" @submit.prevent>
      <ElFormItem label="区块名称">
        <ElInput
          v-model="form.name"
          maxlength="20"
          placeholder="默认区块"
          @update:model-value="emitPatch('section-name')"
        />
      </ElFormItem>
      <ElFormItem label="区块背景色">
        <ElColorPicker
          v-model="form.backgroundColor"
          show-alpha
          @update:model-value="emitPatch('section-background')"
        />
      </ElFormItem>
      <ElFormItem label="区块背景图">
        <ElInput
          v-model="form.backgroundImage"
          clearable
          placeholder="图片地址"
          @update:model-value="emitPatch('section-background')"
        />
      </ElFormItem>
      <ElFormItem label="上下内边距（px）">
        <ElInputNumber
          v-model="form.paddingY"
          :max="60"
          :min="0"
          controls-position="right"
          @update:model-value="emitPatch('section-padding')"
        />
      </ElFormItem>
      <ElFormItem label="横向滚动（组件横滑排列）">
        <ElSwitch
          v-model="form.horizontalScroll"
          @update:model-value="emitPatch('section-scroll')"
        />
      </ElFormItem>
      <ElFormItem label="吸顶">
        <ElSwitch
          v-model="form.sticky"
          @update:model-value="emitPatch('section-sticky')"
        />
      </ElFormItem>
      <ElFormItem label="条件显示">
        <ElSelect
          v-model="form.condition"
          @update:model-value="emitPatch('section-condition')"
        >
          <ElOption label="全部用户" value="always" />
          <ElOption label="仅登录用户" value="login" />
          <ElOption label="仅游客" value="guest" />
        </ElSelect>
      </ElFormItem>
    </ElForm>
  </div>
  <div v-else class="section-empty">请选择一个区块</div>
</template>

<style scoped>
.section-empty {
  padding: 24px 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}
</style>
