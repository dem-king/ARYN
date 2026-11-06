<script setup lang="ts">
import { ElDialog } from 'element-plus';

import { DESK_SHORTCUTS } from './use-desk-shortcuts';

/** 键盘快捷键帮助浮层（只读展示，顺序与 use-desk-shortcuts 的匹配规则一致）。 */
const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{ close: [] }>();
</script>

<template>
  <ElDialog
    :model-value="props.open"
    append-to-body
    title="键盘快捷键"
    width="420px"
    @close="emit('close')"
  >
    <ul class="shortcut-list">
      <li v-for="shortcut in DESK_SHORTCUTS" :key="shortcut.keys">
        <span class="shortcut-desc">{{ shortcut.description }}</span>
        <kbd class="shortcut-keys">{{ shortcut.keys }}</kbd>
      </li>
    </ul>
    <p class="shortcut-hint">
      提示：按 Esc 关闭本浮层；输入框内不响应组合快捷键。
    </p>
  </ElDialog>
</template>

<style scoped>
.shortcut-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  list-style: none;
}

.shortcut-list li {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 8px 4px;
  border-bottom: 1px solid var(--desk-line-soft);
}

.shortcut-list li:last-child {
  border-bottom: 0;
}

.shortcut-desc {
  font-size: 13px;
  color: var(--desk-text);
}

.shortcut-keys {
  padding: 2px 8px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--desk-accent-strong);
  background: var(--desk-canvas);
  border: 1px solid var(--desk-line);
  border-radius: 6px;
}

.shortcut-hint {
  margin-top: 14px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--desk-dim);
}
</style>
