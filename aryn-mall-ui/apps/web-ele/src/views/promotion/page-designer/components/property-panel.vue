<script setup lang="ts">
import type { DiyCommonStyle } from '@vben/types';

import type { DecorationComponent } from '../schema/types';

import { computed, defineAsyncComponent, ref, watch } from 'vue';

import { ElEmpty, ElTabPane, ElTabs } from 'element-plus';

import { getComponentDefinition } from '../registry/component-registry';
import { cloneDesignerValue, isSameDesignerValue } from '../schema/clone';

const props = defineProps<{ component?: DecorationComponent }>();

const emit = defineEmits<{
  patch: [value: Record<string, unknown>, groupKey?: string];
}>();

const CommonStyle = defineAsyncComponent(
  () => import('../../page-design/components/common/common-style/index.vue'),
);

const activeTab = ref('content');
const localProps = ref<Record<string, unknown>>({});
const definition = computed(() =>
  props.component ? getComponentDefinition(props.component.type) : undefined,
);
const settingComponent = computed(() => definition.value?.settings);
const styleSettingComponent = computed(() => definition.value?.styleSettings);
const hasCommonStyle = computed(() => {
  if (!props.component) return false;
  if ('commonStyle' in props.component.props) return true;
  return 'commonStyle' in (definition.value?.createDefaultProps() ?? {});
});
const hasStyleTab = computed(
  () => Boolean(styleSettingComponent.value) || hasCommonStyle.value,
);
const commonStyleValue = computed(() => {
  const value = localProps.value.commonStyle as
    DiyCommonStyle | null | undefined;
  if (value) return value;
  return definition.value?.createDefaultProps().commonStyle as
    DiyCommonStyle | undefined;
});

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

watch(
  () => props.component?.id,
  () => {
    if (activeTab.value === 'style' && !hasStyleTab.value) {
      activeTab.value = 'content';
    }
  },
);

function updateProps(value: Record<string, unknown>) {
  localProps.value = value;
  if (!isSameDesignerValue(value, props.component?.props)) {
    emit('patch', cloneDesignerValue(value), `props:${props.component?.id}`);
  }
}

function updateCommonStyle(value: DiyCommonStyle) {
  // CommonStyle 就地修改传入对象；保持同一引用，避免 props 监听触发自身回写回路
  localProps.value.commonStyle = value;
  if (!isSameDesignerValue(localProps.value, props.component?.props)) {
    emit(
      'patch',
      cloneDesignerValue(localProps.value),
      `props:${props.component?.id}`,
    );
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
          v-if="activeTab === 'content' && settingComponent"
          :model-value="localProps"
          @update:model-value="updateProps"
        />
        <ElEmpty
          v-else-if="activeTab === 'content'"
          :image-size="56"
          description="该组件暂无可视化设置"
        />
      </ElTabPane>
      <ElTabPane v-if="hasStyleTab" label="样式" name="style">
        <template v-if="activeTab === 'style'">
          <component
            :is="styleSettingComponent"
            v-if="styleSettingComponent"
            :model-value="localProps"
            @update:model-value="updateProps"
          />
          <template v-if="hasCommonStyle">
            <div v-if="styleSettingComponent" class="style-section-title">
              通用样式
            </div>
            <CommonStyle
              :model-value="commonStyleValue"
              @update:model-value="updateCommonStyle"
            />
          </template>
        </template>
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

.style-section-title {
  padding: 12px 0 6px;
  margin-top: 4px;
  font-weight: 600;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
