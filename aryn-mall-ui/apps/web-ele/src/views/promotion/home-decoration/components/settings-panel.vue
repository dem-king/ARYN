<script setup lang="ts">
import type { DecorationComponent } from '../use-home-decoration';

import { ElButton } from 'element-plus';

import { componentsMap } from '../../page-design/componentsMap';

defineProps<{
  component?: DecorationComponent;
  showSetting: boolean;
}>();

const emit = defineEmits<{
  clear: [];
  updateFormData: [formData: Record<string, any>];
}>();
</script>

<template>
  <div class="right-panel">
    <div class="settings-content">
      <div class="preview-page-config">
        <ElButton @click="emit('clear')">清空组件</ElButton>
      </div>
      <div v-if="component" class="component-settings">
        <component
          :is="componentsMap[`${component.type}-setting`]"
          v-if="showSetting"
          :model-value="component.formData"
          @update:model-value="emit('updateFormData', $event)"
        />
      </div>
      <div v-else class="empty-text">请选择一个组件进行设置</div>
    </div>
  </div>
</template>
