<script setup lang="ts">
import { computed, onBeforeUnmount, shallowRef, watch } from 'vue';

import { CopyDocument, Link } from '@element-plus/icons-vue';
import { useQRCode } from '@vueuse/integrations/useQRCode';
import { ElButton, ElDialog, ElInput, ElMessage, ElTag } from 'element-plus';

import {
  buildPreviewUrl,
  formatRemainingTime,
  getRemainingSeconds,
} from './preview-utils';

const props = defineProps<{
  expiresAt: number;
  modelValue: boolean;
  pageName: string;
  token: string;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();

const now = shallowRef(Date.now());
let countdownTimer: ReturnType<typeof setInterval> | undefined;

const previewUrl = computed(() =>
  buildPreviewUrl(props.token, window.location.origin),
);
const remainingSeconds = computed(() =>
  getRemainingSeconds(props.expiresAt, now.value),
);
const remainingLabel = computed(() =>
  formatRemainingTime(remainingSeconds.value),
);
const expired = computed(() => remainingSeconds.value === 0);
const qrcode = useQRCode(previewUrl, {
  errorCorrectionLevel: 'M',
  margin: 1,
  width: 220,
});

function stopCountdown() {
  if (countdownTimer) clearInterval(countdownTimer);
  countdownTimer = undefined;
}

watch(
  () => props.modelValue,
  (visible) => {
    stopCountdown();
    now.value = Date.now();
    if (visible) {
      countdownTimer = setInterval(() => {
        now.value = Date.now();
        if (expired.value) stopCountdown();
      }, 1000);
    }
  },
  { immediate: true },
);

onBeforeUnmount(stopCountdown);

async function copyUrl() {
  await navigator.clipboard.writeText(previewUrl.value);
  ElMessage.success('预览链接已复制');
}

function openPreview() {
  window.open(previewUrl.value, '_blank', 'noopener,noreferrer');
}
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :title="`${pageName} · 草稿预览`"
    width="560px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="preview-content">
      <div class="qr-panel">
        <img :src="qrcode" alt="草稿预览二维码" class="qr-code" />
        <ElTag :type="expired ? 'danger' : 'success'" effect="plain">
          {{ expired ? '已过期' : `剩余 ${remainingLabel}` }}
        </ElTag>
      </div>
      <div class="preview-detail">
        <div class="preview-heading">
          <span class="preview-mark" aria-hidden="true"><Link /></span>
          <div>
            <strong>短期预览已生成</strong>
            <p>请使用手机扫码预览当前草稿。</p>
          </div>
        </div>
        <p class="preview-note">
          该入口绑定当前草稿修订和租户，10 分钟后或草稿再次保存后失效。
        </p>
        <ElInput :model-value="previewUrl" readonly>
          <template #append>
            <ElButton
              :icon="CopyDocument"
              aria-label="复制预览链接"
              @click="copyUrl"
            />
          </template>
        </ElInput>
      </div>
    </div>
    <template #footer>
      <ElButton @click="emit('update:modelValue', false)">关闭</ElButton>
      <ElButton :disabled="expired" type="primary" @click="openPreview">
        打开预览
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.preview-content {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 20px;
  align-items: center;
}

.qr-panel {
  display: grid;
  gap: 10px;
  justify-items: center;
}

.qr-code {
  width: 220px;
  height: 220px;
  border: 1px solid var(--el-border-color-lighter);
}

.preview-detail,
.preview-heading {
  display: grid;
  gap: 12px;
}

.preview-heading {
  grid-template-columns: 40px 1fr;
  align-items: start;
}

.preview-heading p,
.preview-note {
  margin: 6px 0 0;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.preview-note {
  margin: 0;
  font-size: 13px;
}

.preview-mark {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 6px;
}

.preview-mark svg {
  width: 20px;
}

@media (max-width: 640px) {
  .preview-content {
    grid-template-columns: 1fr;
  }
}
</style>
