<script setup lang="ts">
import type { ComposerState, QuoteTarget } from './service-state';

import { computed, ref } from 'vue';

import { ElButton, ElInput, ElMessage } from 'element-plus';

import { IMAGE_UPLOAD_ACCEPT, validateImageUpload } from './service-state';

/**
 * 回复输入区：草稿输入、引用条、图片入口、发送按钮门禁、快捷回复/快捷键入口。
 *
 * <p>纯展示 + 表单事件：Enter 发送的键盘判定（输入法组合键、Shift+Enter）仍在
 * 页面消息域组合式里，这里只把 keydown 原样 emit 回页面；发送/打开弹窗同理。
 * 图片上传只做前端预校验后把 File 交给页面（接口调用与发送由消息域编排）。</p>
 */
const props = withDefaults(
  defineProps<{
    composer: ComposerState;
    /** 输入区左下角说明（离线/只读/HTTP 发送提示等）。 */
    composerNote: string;
    draft: string;
    messageLoading: boolean;
    /** 当前引用目标（composer 顶部引用条；空=未引用）。 */
    quote?: QuoteTarget;
    /** 图片上传中：禁用图片入口，避免重复提交。 */
    uploading?: boolean;
  }>(),
  { quote: undefined, uploading: false },
);

const emit = defineEmits<{
  cancelQuote: [];
  keydown: [event: KeyboardEvent];
  openHelp: [];
  openQuickReply: [];
  send: [];
  'update:draft': [value: string];
  uploadImage: [file: File];
}>();

const fileInputRef = ref<HTMLInputElement>();

/** 发送门禁：阅读态禁用、空草稿、消息加载中都不允许发送。 */
const sendDisabled = computed(
  () => props.composer.disabled || !props.draft.trim() || props.messageLoading,
);

/** 图片入口门禁：阅读态/上传中禁用（无图片时要发文字仍可用）。 */
const imageDisabled = computed(
  () => props.composer.disabled || props.uploading,
);

function onKeydown(event: Event) {
  if (event instanceof KeyboardEvent) emit('keydown', event);
}

function pickImage() {
  if (imageDisabled.value) return;
  fileInputRef.value?.click();
}

/** 选择图片：前端预校验（MIME/大小，与契约 §17.3 同口径）后交给页面上传。 */
function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  // 清空 input：允许连续选择同一文件重试
  input.value = '';
  if (!file) return;
  const result = validateImageUpload(file);
  if (!result.ok) {
    ElMessage.error(result.error || '图片文件不合法');
    return;
  }
  emit('uploadImage', file);
}
</script>

<template>
  <footer>
    <div v-if="props.composer.hint" class="composer-hint">
      {{ props.composer.hint }}
    </div>
    <!-- 引用条：发送时带 quotedMessageId，可取消 -->
    <div v-if="props.quote" class="quote-bar">
      <span class="quote-bar-copy">
        <span class="quote-bar-sender">引用 {{ props.quote.senderName }}</span>
        <span class="quote-bar-text">{{ props.quote.summary }}</span>
      </span>
      <button
        type="button"
        class="quote-bar-cancel"
        aria-label="取消引用"
        @click="emit('cancelQuote')"
      >
        取消引用
      </button>
    </div>
    <ElInput
      :model-value="props.draft"
      type="textarea"
      :rows="3"
      resize="none"
      :disabled="props.composer.disabled"
      :placeholder="props.composer.placeholder"
      @keydown="onKeydown"
      @update:model-value="emit('update:draft', $event)"
    />
    <input
      ref="fileInputRef"
      class="image-input"
      type="file"
      :accept="IMAGE_UPLOAD_ACCEPT"
      @change="onFileChange"
    />
    <div class="send-row">
      <span class="send-hint">{{ props.composerNote }}</span>
      <span class="send-actions">
        <ElButton
          class="ghost-trigger image-trigger"
          size="small"
          :disabled="imageDisabled"
          :title="props.uploading ? '图片上传中' : '发送图片'"
          aria-label="发送图片"
          @click="pickImage"
        >
          {{ props.uploading ? '上传中…' : '图片' }}
        </ElButton>
        <ElButton
          class="ghost-trigger"
          size="small"
          @click="emit('openQuickReply')"
        >
          快捷回复
        </ElButton>
        <ElButton
          class="ghost-trigger shortcut-entry"
          size="small"
          title="键盘快捷键"
          @click="emit('openHelp')"
        >
          快捷键 ?
        </ElButton>
        <ElButton type="primary" :disabled="sendDisabled" @click="emit('send')">
          发送回复
        </ElButton>
      </span>
    </div>
  </footer>
</template>

<style scoped>
footer {
  padding: 14px 22px 16px;
  background: var(--desk-surface);
  border-top: 1px solid var(--desk-line-faint);
}

.composer-hint {
  margin-bottom: 8px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--desk-warning);
}

/* 引用条：显示被引用消息发送者+摘要，可取消 */
.quote-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  margin-bottom: 8px;
  background: var(--desk-hover);
  border-left: 2px solid var(--desk-accent-line);
  border-radius: 6px;
}

.quote-bar-copy {
  min-width: 0;
}

.quote-bar-sender {
  display: block;
  font-size: 10px;
  color: var(--desk-accent-strong);
}

.quote-bar-text {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  color: var(--desk-muted);
  white-space: nowrap;
}

.quote-bar-cancel {
  flex: none;
  padding: 2px 4px;
  font-size: 10px;
  color: var(--desk-dim);
}

.quote-bar-cancel:hover {
  color: var(--desk-warning);
}

/* 隐藏的原生文件选择器：由「图片」按钮触发 */
.image-input {
  display: none;
}

.image-trigger:disabled {
  color: var(--desk-faint);
  border-color: var(--desk-line-soft);
}

.send-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 10px;
  color: var(--desk-faint);
}

.send-hint {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.send-actions {
  display: flex;
  flex: none;
  gap: 6px;
  align-items: center;
}

.shortcut-entry {
  letter-spacing: 0.02em;
}

.ghost-trigger {
  flex: none;
  color: var(--desk-muted);
  background: transparent;
  border-color: var(--desk-line);
}

.ghost-trigger:hover {
  color: var(--desk-accent-contrast);
  background: var(--desk-accent-soft-hover);
  border-color: var(--desk-accent-line-hover);
}
</style>
