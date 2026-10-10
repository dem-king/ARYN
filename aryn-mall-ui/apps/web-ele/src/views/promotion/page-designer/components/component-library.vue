<script setup lang="ts">
import type { PageDesignType } from '#/api/promotion/page-design';

import { computed, ref } from 'vue';

import { Grid, Search } from '@element-plus/icons-vue';
import { ElCollapse, ElCollapseItem, ElIcon, ElInput } from 'element-plus';

import {
  componentRegistry,
  extensionComponentTypes,
  getAllowedComponentsForPageType,
  legacyComponentTypes,
  retailComponentTypes,
} from '../registry/component-registry';

const props = withDefaults(defineProps<{ pageType?: PageDesignType }>(), {
  pageType: '0',
});

const emit = defineEmits<{ add: [type: string] }>();
const query = ref('');
const activeGroups = ref([
  '基础组件',
  '导航广告',
  '内容',
  '商品经营',
  '营销活动',
  '店铺服务',
]);
// 扩展组件（瀑布流/商品推荐/优惠券组合/图片魔方等）同样进入组件库，
// 此前只列 legacy+retail 导致已注册的 extension 组件在面板里拖不到
const componentTypes = [
  ...legacyComponentTypes,
  ...retailComponentTypes,
  ...extensionComponentTypes,
];

const groupedItems = computed(() => {
  const keyword = query.value.trim().toLowerCase();
  // 商品详情页/分类页/个人中心页仅展示各自白名单内的组件
  const allowed = getAllowedComponentsForPageType(props.pageType);
  const groups: Record<string, (typeof componentTypes)[number][]> = {};
  for (const type of componentTypes) {
    if (allowed && !allowed.has(type)) {
      continue;
    }
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
