<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';

import { ElCascader } from 'element-plus';

import { getPage as getCategoryTree } from '#/api/product/goods-category';

const props = withDefaults(defineProps<{ modelValue?: string }>(), {
  modelValue: '',
});
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();

const tree = ref<Array<Record<string, any>>>([]);
const cascaderValue = ref<string[]>([]);

function findPath(
  nodes: Array<Record<string, any>>,
  id: string,
): null | string[] {
  for (const node of nodes) {
    if (String(node.id) === id) return [String(node.id)];
    const childPath = findPath(
      (node.children ?? []) as Array<Record<string, any>>,
      id,
    );
    if (childPath) return [String(node.id), ...childPath];
  }
  return null;
}

/** 配置里存的是单个分类 ID，回显时从树里反查完整路径，避免运营看到空选择 */
function syncFromModel() {
  const id = String(props.modelValue ?? '');
  cascaderValue.value = id ? (findPath(tree.value, id) ?? []) : [];
}

onMounted(async () => {
  try {
    tree.value = ((await getCategoryTree()) ?? []) as Array<
      Record<string, any>
    >;
  } catch {
    tree.value = [];
  }
  syncFromModel();
});

watch(() => props.modelValue, syncFromModel);

function onChange(value: unknown) {
  const path = Array.isArray(value) ? value : [];
  emit(
    'update:modelValue',
    path.length > 0 ? String(path[path.length - 1]) : '',
  );
}
</script>

<template>
  <ElCascader
    class="category-picker"
    :model-value="cascaderValue"
    :options="tree"
    :props="{ children: 'children', label: 'name', value: 'id' }"
    clearable
    filterable
    placeholder="留空表示全部分类"
    @update:model-value="onChange"
  />
</template>

<style scoped>
.category-picker {
  width: 100%;
}
</style>
