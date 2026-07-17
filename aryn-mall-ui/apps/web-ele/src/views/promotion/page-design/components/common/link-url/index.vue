<script setup lang="ts">
import type {
  DecorationLink,
  DecorationLinkType,
} from '../../../../page-designer/schema/types';

import { computed, ref, watch } from 'vue';

import { ArrowRightBold, CircleClose } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
} from 'element-plus';

import { cloneDesignerValue } from '../../../../page-designer/schema/clone';
import {
  normalizeDecorationLink,
  validateDecorationLink,
} from '../../../../page-designer/utils/link-utils';

const props = defineProps<{
  modelValue?: DecorationLink | null | { name?: string; url?: string };
  placeholder?: string;
}>();
const emit = defineEmits<{
  'update:modelValue': [value: DecorationLink | null];
}>();

const dialogVisible = ref(false);
const form = ref<DecorationLink>(normalizeDecorationLink(props.modelValue));
const paramsText = ref('{}');
const errors = computed(() => validateDecorationLink(form.value));
const needsTarget = computed(() =>
  ['activity', 'category', 'coupon', 'goods', 'page'].includes(form.value.type),
);
const needsPath = computed(() =>
  ['custom', 'mini-program'].includes(form.value.type),
);
const displayValue = computed(
  () =>
    form.value.targetId || form.value.path || props.placeholder || '选择链接',
);

const linkTypes: Array<{ label: string; value: DecorationLinkType }> = [
  { label: '商品', value: 'goods' },
  { label: '分类', value: 'category' },
  { label: '微页面', value: 'page' },
  { label: '优惠券', value: 'coupon' },
  { label: '活动', value: 'activity' },
  { label: '外部小程序', value: 'mini-program' },
  { label: '客服', value: 'customer-service' },
  { label: '自定义路径', value: 'custom' },
];

function open() {
  form.value = normalizeDecorationLink(props.modelValue);
  paramsText.value = JSON.stringify(form.value.params ?? {}, null, 2);
  dialogVisible.value = true;
}

function submit() {
  if (errors.value.length > 0) return;
  try {
    form.value.params = JSON.parse(paramsText.value || '{}') as Record<
      string,
      string
    >;
  } catch {
    return;
  }
  emit('update:modelValue', cloneDesignerValue(form.value));
  dialogVisible.value = false;
}

function clear() {
  emit('update:modelValue', null);
}

watch(
  () => props.modelValue,
  (value) => {
    form.value = normalizeDecorationLink(value);
  },
);
</script>

<template>
  <div class="link-editor">
    <ElInput :model-value="displayValue" readonly @click="open">
      <template #append>
        <ElButton
          v-if="modelValue"
          :icon="CircleClose"
          aria-label="清除链接"
          @click.stop="clear"
        />
        <ElButton
          v-else
          :icon="ArrowRightBold"
          aria-label="选择链接"
          @click="open"
        />
      </template>
    </ElInput>

    <ElDialog
      v-model="dialogVisible"
      append-to-body
      title="链接设置"
      width="520px"
    >
      <ElForm :model="form" label-position="top">
        <ElFormItem label="链接类型">
          <ElSelect v-model="form.type" class="full-width">
            <ElOption
              v-for="item in linkTypes"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem v-if="needsTarget" label="目标 ID" :error="errors[0]">
          <ElInput v-model="form.targetId" clearable />
        </ElFormItem>
        <ElFormItem v-if="needsPath" label="页面路径" :error="errors[0]">
          <ElInput v-model="form.path" clearable />
        </ElFormItem>
        <ElFormItem label="参数">
          <ElInput v-model="paramsText" :rows="4" type="textarea" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton :disabled="errors.length > 0" type="primary" @click="submit">
          确定
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.link-editor,
.full-width {
  width: 100%;
}
</style>
