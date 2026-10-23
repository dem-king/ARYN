<script lang="ts" setup>
withDefaults(
  defineProps<{
    /** 是否使用卡片外壳，抽屉/弹窗内可传 false 只保留标题行 */
    bordered?: boolean;
    /** 标题右侧的补充说明，可选 */
    description?: string;
    /** 分区标题 */
    title: string;
  }>(),
  {
    bordered: true,
    description: '',
  },
);
</script>

<template>
  <section class="hx-page-section" :class="{ 'is-plain': !bordered }">
    <header class="hx-page-section__header">
      <div class="hx-page-section__heading">
        <h3 class="hx-page-section__title">{{ title }}</h3>
        <span v-if="description" class="hx-page-section__desc">
          {{ description }}
        </span>
      </div>
      <div class="hx-page-section__extra">
        <slot name="extra"></slot>
      </div>
    </header>
    <div class="hx-page-section__body">
      <slot></slot>
    </div>
  </section>
</template>

<style scoped lang="scss">
.hx-page-section {
  padding: 0 16px;
  margin-bottom: 14px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;

  &.is-plain {
    padding: 0;
    background: transparent;
    border: none;
    border-radius: 0;
  }
}

.hx-page-section__header {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  min-height: 46px;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.is-plain .hx-page-section__header {
  min-height: 38px;
  border-bottom: none;
}

.hx-page-section__heading {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: baseline;
  min-width: 0;
}

.hx-page-section__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
  color: var(--el-text-color-primary);
}

.hx-page-section__desc {
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-secondary);
}

.hx-page-section__extra {
  display: flex;
  flex: none;
  gap: 8px;
  align-items: center;
}

.hx-page-section__body {
  padding: 16px 0;
}

.is-plain .hx-page-section__body {
  padding: 8px 0 4px;
}
</style>
