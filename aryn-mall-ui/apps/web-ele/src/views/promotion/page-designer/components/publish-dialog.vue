<script setup lang="ts">
import type { DecorationDocument } from '../schema/types';

import { computed, ref } from 'vue';

import { ElAlert, ElButton, ElDialog, ElInput } from 'element-plus';

import { getComponentDefinition } from '../registry/component-registry';

const props = defineProps<{
  document: DecorationDocument;
  modelValue: boolean;
  pageName: string;
}>();
const emit = defineEmits<{
  confirm: [remark: string];
  'update:modelValue': [value: boolean];
}>();

const remark = ref('');
const errors = computed(() =>
  props.document.components.flatMap((component, index) => {
    const definition = getComponentDefinition(component.type);
    if (!definition) return [`组件 ${index + 1}：未知类型 ${component.type}`];
    return definition
      .validate(component.props)
      .map((message) => `组件 ${index + 1}：${message}`);
  }),
);
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    title="发布页面"
    width="560px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="publish-summary">
      <strong>{{ pageName }}</strong>
      <span>
        {{ document.components.length }} 个组件 · Schema v{{
          document.schemaVersion
        }}
      </span>
    </div>
    <ElAlert
      v-if="errors.length > 0"
      :closable="false"
      :title="`存在 ${errors.length} 项阻断问题`"
      type="error"
    >
      <ul>
        <li v-for="error in errors" :key="error">{{ error }}</li>
      </ul>
    </ElAlert>
    <ElAlert
      v-else
      :closable="false"
      title="校验通过，可以发布"
      type="success"
    />
    <ElInput
      v-model="remark"
      maxlength="200"
      placeholder="填写发布说明（可选）"
      show-word-limit
      type="textarea"
    />
    <template #footer>
      <ElButton @click="emit('update:modelValue', false)">取消</ElButton>
      <ElButton
        :disabled="errors.length > 0"
        type="primary"
        @click="emit('confirm', remark)"
      >
        确认发布
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.publish-summary {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}

.publish-summary span {
  color: var(--el-text-color-secondary);
}

.el-alert {
  margin-bottom: 16px;
}

.el-alert ul {
  padding-left: 18px;
  margin: 8px 0 0;
}
</style>
