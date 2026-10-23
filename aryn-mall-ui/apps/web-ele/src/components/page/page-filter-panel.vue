<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { ref } from 'vue';

import { Refresh, Search } from '@element-plus/icons-vue';
import { ElButton, ElForm, ElFormItem } from 'element-plus';

withDefaults(
  defineProps<{
    /** 表单数据对象，直接透传给 ElForm */
    model: Record<string, any>;
    /** 与 RightToolbar 的搜索按钮联动 */
    visible?: boolean;
  }>(),
  {
    visible: true,
  },
);

const emit = defineEmits<{
  reset: [];
  search: [];
}>();

const formRef = ref<FormInstance>();

/** 重置后立即触发查询，避免用户还要再点一次搜索 */
const handleReset = () => {
  formRef.value?.resetFields();
  emit('reset');
};
</script>

<template>
  <ElForm
    ref="formRef"
    v-show="visible"
    :inline="true"
    label-width="96px"
    :model="model"
    class="hx-filter-panel"
    @submit.prevent
  >
    <slot></slot>
    <ElFormItem class="hx-filter-panel__actions">
      <ElButton type="primary" :icon="Search" @click="emit('search')">
        搜索
      </ElButton>
      <ElButton :icon="Refresh" @click="handleReset">重置</ElButton>
    </ElFormItem>
  </ElForm>
</template>

<style scoped lang="scss">
.hx-filter-panel {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  padding: 16px 16px 0;
  margin-bottom: 14px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;

  :deep(.el-form-item) {
    flex: none;
    margin-right: 18px;
    margin-bottom: 16px;
  }

  :deep(.el-form-item__label) {
    font-weight: 500;
    color: var(--el-text-color-regular);
    white-space: nowrap;
  }

  /* 统一常规控件宽度，日期区间单独放宽 */
  :deep(.el-form-item__content > .el-input),
  :deep(.el-form-item__content > .el-select) {
    width: 200px;
  }

  :deep(.el-form-item__content > .el-date-editor) {
    width: 360px;
  }
}

.hx-filter-panel__actions {
  margin-right: 0 !important;
  margin-left: auto !important;
}

@media (width <= 1400px) {
  .hx-filter-panel :deep(.el-form-item) {
    margin-right: 12px;
  }
}
</style>
