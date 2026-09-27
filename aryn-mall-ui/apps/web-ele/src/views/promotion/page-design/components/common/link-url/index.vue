<script setup lang="ts">
import type {
  DecorationLink,
  DecorationLinkType,
} from '../../../../page-designer/schema/types';

import { computed, ref, watch } from 'vue';

import { ArrowRightBold, CircleClose } from '@element-plus/icons-vue';
import {
  ElButton,
  ElCascader,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
} from 'element-plus';

import { getPage as getCategoryTree } from '#/api/product/goods-category';

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

/**
 * 分类树选择器：金刚区入口常只选一级（如「水果」），
 * 故 checkStrictly 允许选任意层级；选中后把一级/二级 id 分别写进 params，
 * 移动端跳转时直接带正确层级，不再事后查树判断。
 */
const categoryTree = ref<any[]>([]);
const categoryCascader = ref<string[]>([]);
const categoryLabel = ref('');
const cascaderProps = {
  label: 'name',
  value: 'id',
  children: 'children',
  checkStrictly: true,
};

const displayValue = computed(() => {
  if (form.value.type === 'category') {
    return (
      categoryLabel.value ||
      form.value.targetId ||
      props.placeholder ||
      '选择链接'
    );
  }
  return (
    form.value.targetId || form.value.path || props.placeholder || '选择链接'
  );
});

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

function loadCategoryTree() {
  if (categoryTree.value.length > 0) return;
  getCategoryTree().then((res) => {
    categoryTree.value = res ?? [];
    // 树回来后用当前已选路径反查展示名（打开弹窗时树可能还没到）
    categoryLabel.value = findCategoryLabel(
      categoryTree.value,
      categoryCascader.value,
    );
  });
}

/** 沿已选 id 路径在树里找出最后一级的名字，用于触发器回显 */
function findCategoryLabel(tree: any[], path: string[]): string {
  if (path.length === 0) return '';
  let nodes = tree;
  let label = '';
  for (const id of path) {
    const node = nodes.find((n: any) => String(n.id) === String(id));
    if (!node) return label;
    label = node.name;
    nodes = node.children ?? [];
  }
  return label;
}

function onCategoryChange(val: any) {
  categoryCascader.value = Array.isArray(val) ? [...val] : [];
  const firstId = categoryCascader.value[0] ?? '';
  const secondId = categoryCascader.value[1] ?? '';
  // targetId 仍保留所选节点 id（二级优先），兼容旧校验与展示
  form.value.targetId = secondId || firstId;
  // 层级写进 params，移动端跳转时直接带正确的 first/second
  form.value.params = {
    ...form.value.params,
    categoryFirstId: firstId,
    categorySecondId: secondId,
  };
  paramsText.value = JSON.stringify(form.value.params ?? {}, null, 2);
  categoryLabel.value = findCategoryLabel(
    categoryTree.value,
    categoryCascader.value,
  );
}

function open() {
  form.value = normalizeDecorationLink(props.modelValue);
  paramsText.value = JSON.stringify(form.value.params ?? {}, null, 2);
  // 回填已有分类选择：params 里带了 first/second 就拼回级联路径
  const p = form.value.params ?? {};
  categoryCascader.value = [p.categoryFirstId, p.categorySecondId].filter(
    Boolean,
  ) as string[];
  categoryLabel.value = findCategoryLabel(
    categoryTree.value,
    categoryCascader.value,
  );
  loadCategoryTree();
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
        <ElFormItem
          v-if="form.type === 'category'"
          label="选择分类"
          :error="errors[0]"
        >
          <ElCascader
            :options="categoryTree"
            v-model="categoryCascader"
            clearable
            :props="cascaderProps"
            class="full-width"
            placeholder="选择分类（可只选一级，如「水果」）"
            @change="onCategoryChange"
          />
        </ElFormItem>
        <ElFormItem v-else-if="needsTarget" label="目标 ID" :error="errors[0]">
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
