<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus';

import type { AgentInfo } from '#/api/message/agent';

import { reactive, ref } from 'vue';

import { useUserStore } from '@vben/stores';

import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import {
  getAgentCandidates,
  getAgentConfig,
  saveAgentConfig,
} from '#/api/message/agent';

const emit = defineEmits<{
  saved: [agent: AgentInfo];
}>();

const userStore = useUserStore();
const formRef = ref<FormInstance>();
const visible = ref(false);
const loading = ref(false);
const configLoading = ref(false);
const candidates = ref<
  Array<{ avatar?: string; deptId?: string; id: string; nickname?: string }>
>([]);
const currentActiveCount = ref(0);
let configRequestId = 0;

const form = reactive({
  autoAccept: true,
  enabled: true,
  maxActiveCount: 10,
  staffId: '',
});

const rules: FormRules<typeof form> = {
  maxActiveCount: [
    { message: '请输入最大同时接待数量', required: true, trigger: 'change' },
  ],
  staffId: [{ message: '请选择客服人员', required: true, trigger: 'change' }],
};

function resetConfig() {
  form.enabled = true;
  form.autoAccept = true;
  form.maxActiveCount = 10;
  currentActiveCount.value = 0;
}

async function loadConfig(staffId: string) {
  const requestId = ++configRequestId;
  if (!staffId) {
    resetConfig();
    return;
  }
  configLoading.value = true;
  try {
    const config = await getAgentConfig(staffId);
    if (requestId !== configRequestId) return;
    if (!config) {
      resetConfig();
      return;
    }
    form.enabled = config.enabled === '1';
    form.autoAccept = config.autoAccept === '1';
    form.maxActiveCount = config.maxActiveCount;
    currentActiveCount.value = config.currentActiveCount;
  } finally {
    if (requestId === configRequestId) configLoading.value = false;
  }
}

async function open() {
  visible.value = true;
  loading.value = true;
  resetConfig();
  form.staffId = '';
  try {
    const page = await getAgentCandidates();
    candidates.value = page.records || [];
    const currentUserId = userStore.userInfo?.userId;
    form.staffId =
      candidates.value.find((candidate) => candidate.id === currentUserId)
        ?.id ||
      candidates.value[0]?.id ||
      '';
    await loadConfig(form.staffId);
  } finally {
    loading.value = false;
  }
}

async function submit() {
  await formRef.value?.validate();
  loading.value = true;
  try {
    const agent = await saveAgentConfig({ ...form });
    ElMessage.success('客服坐席配置已保存');
    emit('saved', agent);
    visible.value = false;
  } finally {
    loading.value = false;
  }
}

defineExpose({ open });
</script>

<template>
  <ElDialog
    v-model="visible"
    class="agent-config-dialog"
    title="客服坐席设置"
    width="520px"
    destroy-on-close
  >
    <ElAlert
      class="qualification-tip"
      :closable="false"
      show-icon
      title="这里只显示角色已获得“客服坐席配置”或“客服主管操作”权限的工作人员。"
      type="info"
    />

    <ElForm
      ref="formRef"
      v-loading="loading || configLoading"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent
    >
      <ElFormItem label="客服人员" prop="staffId">
        <ElSelect
          v-model="form.staffId"
          class="field-control"
          filterable
          placeholder="选择具备客服权限的工作人员"
          @change="loadConfig"
        >
          <ElOption
            v-for="candidate in candidates"
            :key="candidate.id"
            :label="candidate.nickname || candidate.id"
            :value="candidate.id"
          >
            <div class="candidate-option">
              <strong>{{ candidate.nickname || '未设置昵称' }}</strong>
              <span>{{ candidate.id }}</span>
            </div>
          </ElOption>
        </ElSelect>
      </ElFormItem>

      <div class="switch-grid">
        <div class="setting-card">
          <div>
            <strong>启用坐席</strong>
            <span>关闭后不能上线、领取或接收自动分配</span>
          </div>
          <ElSwitch v-model="form.enabled" />
        </div>
        <div class="setting-card">
          <div>
            <strong>自动接待</strong>
            <span>在线且有空余容量时自动接收新会话</span>
          </div>
          <ElSwitch v-model="form.autoAccept" />
        </div>
      </div>

      <ElFormItem label="最大同时接待数" prop="maxActiveCount">
        <ElInputNumber
          v-model="form.maxActiveCount"
          class="field-control"
          :max="100"
          :min="Math.max(1, currentActiveCount)"
          controls-position="right"
        />
        <p class="capacity-note">
          当前活跃会话 {{ currentActiveCount }} 个，接待上限不能低于当前活跃数。
        </p>
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton
        type="primary"
        :disabled="candidates.length === 0"
        :loading="loading"
        @click="submit"
      >
        保存配置
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.qualification-tip {
  margin-bottom: 20px;
}

.field-control {
  width: 100%;
}

.candidate-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.candidate-option strong {
  overflow: hidden;
  text-overflow: ellipsis;
}

.candidate-option span {
  flex: none;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.switch-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 18px;
}

.setting-card {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  min-height: 92px;
  padding: 15px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

.setting-card strong,
.setting-card span {
  display: block;
}

.setting-card strong {
  margin-bottom: 5px;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.setting-card span,
.capacity-note {
  font-size: 12px;
  line-height: 1.55;
  color: var(--el-text-color-secondary);
}

.capacity-note {
  margin-top: 7px;
}

@media (max-width: 640px) {
  .switch-grid {
    grid-template-columns: 1fr;
  }
}
</style>
