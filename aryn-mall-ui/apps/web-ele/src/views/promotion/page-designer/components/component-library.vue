<script setup lang="ts">
import { computed, ref } from 'vue';

import { Grid, Search } from '@element-plus/icons-vue';
import { ElCollapse, ElCollapseItem, ElIcon, ElInput } from 'element-plus';

import {
  componentRegistry,
  legacyComponentTypes,
  retailComponentTypes,
} from '../registry/component-registry';

const emit = defineEmits<{ add: [type: string] }>();
const query = ref('');
const activeGroups = ref([
  '基础组件',
  '导航广告',
  '商品经营',
  '营销活动',
  '店铺服务',
]);
const componentTypes = [...legacyComponentTypes, ...retailComponentTypes];

const groupedItems = computed(() => {
  const keyword = query.value.trim().toLowerCase();
  const groups: Record<string, (typeof componentTypes)[number][]> = {};
  for (const type of componentTypes) {
    const definition = componentRegistry[type];
    if (
      keyword &&
      !definition.label.includes(keyword) &&
      !type.toLowerCase().includes(keyword)
    ) {
      continue;
    }
    (groups[definition.category] ??= []).push(type);
  }
  return groups;
});
</script>

<template>
  <div class="component-library">
    <ElInput
      v-model="query"
      :prefix-icon="Search"
      clearable
      placeholder="搜索组件"
    />
    <ElCollapse v-model="activeGroups">
      <ElCollapseItem
        v-for="(group, category) in groupedItems"
        :key="category"
        :name="category"
        :title="category"
      >
        <div class="library-grid">
          <button
            v-for="type in group"
            :key="type"
            class="library-item"
            type="button"
            @click="emit('add', type)"
          >
            <ElIcon><Grid /></ElIcon>
            <span>{{ componentRegistry[type].label }}</span>
          </button>
        </div>
      </ElCollapseItem>
    </ElCollapse>
  </div>
</template>

<style scoped>
.component-library {
  padding: 12px;
}

.component-library :deep(.el-collapse) {
  border: 0;
}

.library-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.library-item {
  display: grid;
  gap: 6px;
  place-items: center;
  min-height: 68px;
  padding: 10px 6px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background: var(--el-fill-color-light);
  border: 1px solid transparent;
  border-radius: 6px;
  transition:
    border-color 180ms ease,
    background-color 180ms ease;
}

.library-item:hover,
.library-item:focus-visible {
  outline: 0;
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
}

.library-item .el-icon {
  font-size: 20px;
}
</style>
