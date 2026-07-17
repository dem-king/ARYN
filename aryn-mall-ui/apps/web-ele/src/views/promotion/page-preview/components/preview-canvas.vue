<script setup lang="ts">
import type { DecorationDocument } from '../../page-designer/schema/types';

import { computed } from 'vue';

import { getComponentDefinition } from '../../page-designer/registry/component-registry';

const props = defineProps<{
  document: DecorationDocument;
  pageName: string;
}>();

const pageStyle = computed(() => ({
  backgroundColor: props.document.page.backgroundColor,
  backgroundImage: props.document.page.backgroundImage
    ? `url(${props.document.page.backgroundImage})`
    : undefined,
}));
</script>

<template>
  <main class="preview-canvas" :style="pageStyle">
    <header
      v-if="document.page.navigation.visible"
      class="preview-navigation"
      :style="{
        backgroundColor: document.page.navigation.backgroundColor,
        color: document.page.navigation.textColor,
      }"
    >
      {{ document.page.navigation.title || pageName }}
    </header>

    <div v-if="document.components.length === 0" class="preview-empty">
      当前草稿还没有组件
    </div>

    <template v-for="component in document.components" :key="component.id">
      <component
        :is="getComponentDefinition(component.type)?.preview"
        v-if="getComponentDefinition(component.type)?.preview"
        :show-data="component.props"
      />
      <div v-else class="preview-unknown">
        暂不支持预览组件：{{ component.type }}
      </div>
    </template>
  </main>
</template>

<style scoped>
.preview-canvas {
  width: min(100%, 375px);
  min-height: 100dvh;
  margin: 0 auto;
  overflow: hidden;
  background-repeat: no-repeat;
  background-position: top center;
  background-size: 100% auto;
  box-shadow: 0 0 32px rgb(15 23 42 / 10%);
}

.preview-navigation {
  display: flex;
  align-items: end;
  justify-content: center;
  min-height: 84px;
  padding: 24px 44px 10px;
  font-size: 16px;
  font-weight: 600;
  text-align: center;
}

.preview-empty,
.preview-unknown {
  padding: 48px 20px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.preview-unknown {
  padding: 16px;
  margin: 12px;
  border: 1px dashed var(--el-border-color);
}

@media (max-width: 375px) {
  .preview-canvas {
    width: 100%;
    box-shadow: none;
  }
}
</style>
