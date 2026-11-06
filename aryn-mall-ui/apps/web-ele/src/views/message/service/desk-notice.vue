<script setup lang="ts">
/**
 * 主区单条上下文提示（纯展示）。
 *
 * <p>页面已把「会话状态横幅/实时连接/presence」按优先级合并成一条，
 * 这里只按 tone 渲染 warning/info 两色，不再自己判断优先级。</p>
 */
const props = defineProps<{ text: string; tone: 'info' | 'warning' }>();
</script>

<template>
  <div class="state-banner" :class="props.tone">{{ props.text }}</div>
</template>

<style scoped>
.state-banner {
  flex: none;
  padding: 9px 24px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--desk-warning);
  background: var(--desk-warning-soft);
  border-bottom: 1px solid var(--desk-warning-line);
}

.state-banner.info {
  color: var(--desk-muted);
  background: var(--desk-hover);
  border-bottom-color: var(--desk-line-soft);
}
</style>
