<script setup lang="ts">
import type { DecorationComponent } from '../schema/types';

import { computed, ref, watch } from 'vue';

import { ElEmpty, ElTabPane, ElTabs } from 'element-plus';

import { getComponentDefinition } from '../registry/component-registry';
import { cloneDesignerValue, isSameDesignerValue } from '../schema/clone';

const props = defineProps<{ component?: DecorationComponent }>();
const emit = defineEmits<{
  patch: [value: Record<string, unknown>, groupKey?: string];
}>();

const activeTab = ref('content');
const localProps = ref<Record<string, unknown>>({});
const settingComponent = computed(() =>
  props.component
    ? getComponentDefinition(props.component.type)?.settings
    : undefined,
);

watch(
  () => props.component,
  (component) => {
    const nextProps = component ? cloneDesignerValue(component.props) : {};
    if (!isSameDesignerValue(localProps.value, nextProps)) {
      localProps.value = nextProps;
    }
  },
  { immediate: true },
);

function updateProps(value: Record<string, unknown>) {
  localProps.value = value;
  if (!isSameDesignerValue(value, props.component?.props)) {
    emit('patch', cloneDesignerValue(value), `props:${props.component?.id}`);
  }
}
</script>

<template>
  <div class="property-panel">
    <ElEmpty
      v-if="!component"
      :image-size="64"
      description="选择组件后编辑属性"
    />
    <ElTabs v-else v-model="activeTab" stretch>
      <ElTabPane label="内容" name="content">
        <component
          :is="settingComponent"
          v-if="settingComponent"
          :model-value="localProps"
          @update:model-value="updateProps"
        />
        <ElEmpty v-else :image-size="56" description="该组件暂无可视化设置" />
      </ElTabPane>
      <ElTabPane label="样式" name="style">
        <ElEmpty :image-size="56" description="样式配置由组件设置提供" />
      </ElTabPane>
      <ElTabPane label="高级" name="advanced">
        <ElEmpty :image-size="56" description="暂无高级配置" />
      </ElTabPane>
    </ElTabs>
  </div>
</template>

<style scoped>
.property-panel {
  min-height: 100%;
}

.property-panel :deep(.el-tabs__content) {
  padding: 0 16px 24px;
}
</style>
