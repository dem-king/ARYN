<script setup lang="ts">
import type { DecorationDocument } from '../schema/types';

import type {
  PageDesignValidationIssue,
  PageDesignValidationResult,
} from '#/api/promotion/page-design';

import { computed, ref, watch } from 'vue';

import { ElAlert, ElButton, ElDialog, ElInput, ElTag } from 'element-plus';

import { validatePage } from '#/api/promotion/page-design';

import { getComponentDefinition } from '../registry/component-registry';

const props = defineProps<{
  document: DecorationDocument;
  modelValue: boolean;
  pageId: string;
  pageName: string;
}>();
const emit = defineEmits<{
  confirm: [remark: string];
  locate: [componentId: string | undefined];
  'update:modelValue': [value: boolean];
}>();

const remark = ref('');
const validating = ref(false);
const serverResult = ref<null | PageDesignValidationResult>(null);

const clientErrors = computed(() =>
  props.document.sections
    .flatMap((section) => section.components)
    .flatMap((component, index) => {
      const definition = getComponentDefinition(component.type);
      if (!definition) return [`组件 ${index + 1}：未知类型 ${component.type}`];
      return definition
        .validate(component.props)
        .map((message) => `组件 ${index + 1}：${message}`);
    }),
);

const blockingCount = computed(
  () => clientErrors.value.length + (serverResult.value?.errors.length ?? 0),
);

const warnings = computed(() => serverResult.value?.warnings ?? []);

const budget = computed(() => serverResult.value?.performance);

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return;
    remark.value = '';
    serverResult.value = null;
    if (!props.pageId) return;
    validating.value = true;
    validatePage(props.pageId)
      .then((result) => {
        serverResult.value = result;
      })
      .catch(() => {
        // 服务端校验失败（如草稿刚创建未同步）不阻断本地校验提示
        serverResult.value = null;
      })
      .finally(() => {
        validating.value = false;
      });
  },
);

function issueLabel(issue: PageDesignValidationIssue) {
  const location = issue.componentType
    ? `组件（${issue.componentType}${issue.componentId ? ` #${issue.componentId}` : ''}）`
    : '页面';
  return `${location}：${issue.message}`;
}
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    title="发布页面"
    width="600px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="publish-summary">
      <strong>{{ pageName }}</strong>
      <span>
        {{ document.sections.flatMap((section) => section.components).length }}
        个组件 · {{ document.sections.length }} 个区块 · 提交后按 schema v3
        契约发布
      </span>
    </div>

    <ElAlert
      v-if="validating"
      :closable="false"
      title="正在进行服务端发布检查…"
      type="info"
    />
    <ElAlert
      v-else-if="blockingCount > 0"
      :closable="false"
      :title="`存在 ${blockingCount} 项阻断问题，请修复后再提交`"
      type="error"
    >
      <ul class="issue-list">
        <li v-for="error in clientErrors" :key="`local-${error}`">
          {{ error }}
        </li>
        <li
          v-for="issue in serverResult?.errors ?? []"
          :key="issue.code + (issue.field ?? '')"
          class="issue-locate"
          @click="emit('locate', issue.componentId)"
        >
          {{ issueLabel(issue) }}
          <span v-if="issue.componentId" class="issue-hint">点击定位</span>
        </li>
      </ul>
    </ElAlert>
    <ElAlert
      v-else
      :closable="false"
      title="校验通过，提交后将进入发布流程"
      type="success"
    />

    <ElAlert
      v-if="warnings.length > 0"
      :closable="false"
      :title="` ${warnings.length} 项警告，建议处理`"
      type="warning"
    >
      <ul class="issue-list">
        <li v-for="issue in warnings" :key="issue.code + (issue.field ?? '')">
          {{ issueLabel(issue) }}
        </li>
      </ul>
    </ElAlert>

    <div v-if="budget" class="budget-bar">
      <ElTag effect="plain" size="small" type="info">
        组件 {{ budget.componentCount }}
      </ElTag>
      <ElTag effect="plain" size="small" type="info">
        图片 {{ budget.imageCount }}
      </ElTag>
      <ElTag effect="plain" size="small" type="info">
        接口请求 {{ budget.requestCount }}
      </ElTag>
      <ElTag effect="plain" size="small" type="info">
        内容 {{ Math.ceil(budget.contentBytes / 1024) }}KB
      </ElTag>
    </div>

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
        :disabled="blockingCount > 0 || validating"
        type="primary"
        @click="emit('confirm', remark)"
      >
        提交发布
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

.issue-list {
  padding-left: 18px;
  margin: 8px 0 0;
}

.issue-locate {
  cursor: pointer;
}

.issue-locate:hover {
  color: var(--el-color-primary);
}

.issue-hint {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-color-primary);
}

.budget-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
</style>
