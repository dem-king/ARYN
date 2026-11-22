<script setup lang="ts">
import type { QuickTextForm } from './ai-config-form';

import { ref } from 'vue';

import {
  ElAlert,
  ElButton,
  ElCheckbox,
  ElCheckboxGroup,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
} from 'element-plus';

import { quickTextUses } from './ai-config-form';

defineProps<{ busy: boolean; error: string }>();
const emit = defineEmits<{ close: []; save: [form: QuickTextForm] }>();

const preset = ref<'custom' | 'deepseek'>('deepseek');
const form = ref<QuickTextForm>({
  apiKey: '',
  endpoint: 'https://api.deepseek.com',
  model: '',
  name: 'DeepSeek',
  usages: ['PROCUREMENT_PARSE', 'SKU_RERANK'],
});

function changePreset(value: 'custom' | 'deepseek') {
  form.value = {
    ...form.value,
    apiKey: '',
    endpoint: value === 'deepseek' ? 'https://api.deepseek.com' : '',
    model: '',
    name: value === 'deepseek' ? 'DeepSeek' : '',
  };
}
</script>

<template>
  <ElDialog
    :model-value="true"
    title="接入文本模型"
    width="min(36rem, calc(100vw - 2rem))"
    :show-close="!busy"
    :close-on-click-modal="false"
    :close-on-press-escape="!busy"
    @update:model-value="emit('close')"
  >
    <ElAlert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      class="mb-4"
    />
    <ElForm
      id="quick-text-config"
      :disabled="busy"
      label-position="top"
      @submit.prevent="emit('save', form)"
    >
      <ElFormItem label="供应商">
        <ElSelect v-model="preset" class="w-full" @change="changePreset">
          <ElOption label="DeepSeek 官方" value="deepseek" />
          <ElOption label="其他 OpenAI 兼容服务" value="custom" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem v-if="preset === 'custom'" label="供应商名称" required>
        <ElInput v-model="form.name" maxlength="100" />
      </ElFormItem>
      <ElFormItem label="API 基础地址" required>
        <ElInput
          v-model="form.endpoint"
          :readonly="preset === 'deepseek'"
          placeholder="https://api.example.com/v1"
        />
      </ElFormItem>
      <ElFormItem label="API Key" required>
        <ElInput
          v-model="form.apiKey"
          type="password"
          autocomplete="new-password"
          maxlength="4096"
        />
      </ElFormItem>
      <ElFormItem label="模型 ID" required>
        <ElInput
          v-model="form.model"
          placeholder="从供应商的模型列表复制"
          maxlength="200"
        />
      </ElFormItem>
      <ElFormItem label="使用场景" required>
        <ElCheckboxGroup v-model="form.usages">
          <ElCheckbox
            v-for="usage in quickTextUses"
            :key="usage.value"
            :value="usage.value"
          >
            {{ usage.label }}
          </ElCheckbox>
        </ElCheckboxGroup>
        <p class="text-muted-foreground mt-1 leading-normal">
          保存后，所选场景在草稿中使用新模型。测试通过并确认启用后生效。
        </p>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton :disabled="busy" @click="emit('close')">取消</ElButton>
      <ElButton
        form="quick-text-config"
        native-type="submit"
        type="primary"
        :loading="busy"
      >
        保存并继续
      </ElButton>
    </template>
  </ElDialog>
</template>
