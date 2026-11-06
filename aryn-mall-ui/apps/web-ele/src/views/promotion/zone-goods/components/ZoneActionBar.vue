<script lang="ts" setup>
import {
  Check,
  Delete,
  Plus,
  Refresh,
  Setting,
  Tickets,
} from '@element-plus/icons-vue';
import { ElButton, ElIcon, ElTooltip } from 'element-plus';

/**
 * 列表操作条。
 *
 * 同一个容器在「普通态（添加商品/批次记录）」与「选择态（批量操作）」之间切换，
 * 两态高度一致（padding + 最小高度只在背景/边框上变化），
 * 首次勾选不会把表格整体往下推，避免连续勾选时点错行。
 */
withDefaults(
  defineProps<{
    /** 当前筛选结果总数 */
    filterTotal?: number;
    /** 是否正在请求「全选筛选结果」 */
    selectAllLoading?: boolean;
    /** 已选商品数 */
    selectedCount?: number;
    /** 已选文案（跨页选择 / 全选筛选结果） */
    selectionText?: string;
    /** 是否展示「选择全部筛选结果」入口 */
    showSelectAll?: boolean;
  }>(),
  {
    filterTotal: 0,
    selectedCount: 0,
    selectAllLoading: false,
    selectionText: '',
    showSelectAll: false,
  },
);

const emit = defineEmits<{
  add: [];
  clear: [];
  records: [];
  refresh: [];
  remove: [];
  rules: [];
  selectAll: [];
}>();
</script>

<template>
  <div
    class="zone-actionbar"
    :class="{ 'is-selecting': selectedCount > 0 }"
    role="region"
    aria-label="商品操作与批量操作"
  >
    <div v-if="selectedCount === 0" class="zone-actionbar__group">
      <ElButton
        v-access:code="'product:goodsspu:edit'"
        type="primary"
        :icon="Plus"
        @click="emit('add')"
      >
        添加商品
      </ElButton>
      <ElButton :icon="Tickets" @click="emit('records')">批次记录</ElButton>
    </div>
    <div
      v-if="selectedCount === 0"
      class="zone-actionbar__group zone-actionbar__group--end"
    >
      <ElButton
        v-if="filterTotal > 0"
        link
        type="primary"
        :loading="selectAllLoading"
        @click="emit('selectAll')"
      >
        全选筛选结果
      </ElButton>
      <ElTooltip content="刷新列表与专区状态" placement="top">
        <ElButton
          :icon="Refresh"
          circle
          aria-label="刷新列表与专区状态"
          @click="emit('refresh')"
        />
      </ElTooltip>
    </div>

    <template v-if="selectedCount > 0">
      <div class="zone-actionbar__group">
        <span class="zone-actionbar__check" aria-hidden="true">
          <ElIcon><Check /></ElIcon>
        </span>
        <span class="zone-actionbar__text" role="status" aria-live="polite">
          {{ selectionText }}
        </span>
        <ElButton
          v-if="showSelectAll"
          link
          type="primary"
          :loading="selectAllLoading"
          @click="emit('selectAll')"
        >
          选择全部 {{ filterTotal }} 件筛选结果
        </ElButton>
      </div>
      <div class="zone-actionbar__group zone-actionbar__group--end">
        <ElButton
          v-access:code="'product:goodsspu:edit'"
          :icon="Setting"
          @click="emit('rules')"
        >
          批量设置规则
        </ElButton>
        <ElButton
          v-access:code="'product:goodsspu:edit'"
          type="danger"
          plain
          :icon="Delete"
          @click="emit('remove')"
        >
          移出专区
        </ElButton>
        <ElButton link @click="emit('clear')">清空选择</ElButton>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.zone-actionbar {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  gap: 8px 16px;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  padding: 7px 14px;
  margin-bottom: var(--zone-gap, 12px);
  background: transparent;
  border: 1px solid transparent;
  border-radius: 10px;
  transition:
    background-color 180ms ease,
    border-color 180ms ease;

  /* 选择态只换底色/边框，不改变盒模型尺寸，避免表格跳动 */
  &.is-selecting {
    background: var(--el-color-primary-light-9);
    border-color: var(--el-color-primary-light-7);
  }
}

.zone-actionbar__group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  min-height: 32px;
}

.zone-actionbar__group--end {
  margin-left: auto;

  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}

.zone-actionbar__check {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  font-size: 12px;
  color: #fff;
  background: var(--el-color-primary);
  border-radius: 50%;
}

.zone-actionbar__text {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

@media (prefers-reduced-motion: reduce) {
  .zone-actionbar {
    transition: none;
  }
}
</style>
