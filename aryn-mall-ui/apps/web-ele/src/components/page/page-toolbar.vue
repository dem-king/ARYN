<script lang="ts" setup>
defineProps<{
  /** 当前筛选条件下的记录总数，不传则不展示 */
  total?: number;
  /** 计数单位，如「笔订单」「个角色」 */
  totalLabel?: string;
}>();
</script>

<template>
  <div class="hx-page-toolbar">
    <div class="hx-page-toolbar__left">
      <slot name="left"></slot>
    </div>
    <div class="hx-page-toolbar__right">
      <span v-if="typeof total === 'number'" class="hx-page-toolbar__total">
        共 <b>{{ total }}</b> {{ totalLabel }}
      </span>
      <slot name="right"></slot>
    </div>
  </div>
</template>

<style scoped lang="scss">
.hx-page-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.hx-page-toolbar__left,
.hx-page-toolbar__right {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  min-width: 0;
}

.hx-page-toolbar__right {
  margin-left: auto;
}

.hx-page-toolbar__total {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-secondary);

  b {
    font-weight: 600;
    color: var(--el-text-color-primary);
  }
}
</style>
