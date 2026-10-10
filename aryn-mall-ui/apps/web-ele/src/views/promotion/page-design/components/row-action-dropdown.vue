<script setup lang="ts">
import type { PageDesignRowAction } from '../list-actions';

import type { PageDesignRecord } from '#/api/promotion/page-design';

import { MoreFilled } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
} from 'element-plus';

import {
  ROW_ACTION_ACCESS,
  ROW_ACTION_LABELS,
  rowActions,
} from '../list-actions';

defineProps<{ page: PageDesignRecord }>();

const emit = defineEmits<{
  action: [action: PageDesignRowAction];
}>();
</script>

<template>
  <ElDropdown
    trigger="click"
    @command="(action: PageDesignRowAction) => emit('action', action)"
  >
    <ElButton :icon="MoreFilled" aria-label="更多操作" circle text />
    <template #dropdown>
      <ElDropdownMenu>
        <ElDropdownItem
          v-for="action in rowActions(page)"
          :key="action"
          v-access:code="ROW_ACTION_ACCESS[action]"
          :class="{ 'danger-item': action === 'delete' }"
          :command="action"
          :divided="action === 'delete'"
        >
          {{ ROW_ACTION_LABELS[action] }}
        </ElDropdownItem>
      </ElDropdownMenu>
    </template>
  </ElDropdown>
</template>

<style scoped>
.danger-item {
  color: var(--el-color-danger);
}
</style>
