<script setup lang="ts">
import type { DeskQuickReply } from './use-desk-quick-replies';

import { computed, ref, watch } from 'vue';

import { useUserStore } from '@vben/stores';

import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElInput,
  ElMessage,
  ElMessageBox,
} from 'element-plus';

import {
  MAX_QUICK_REPLIES,
  MAX_QUICK_REPLY_CONTENT_LENGTH,
  MAX_QUICK_REPLY_TITLE_LENGTH,
  useDeskQuickReplies,
} from './use-desk-quick-replies';

/**
 * 工作台「快捷回复」管理弹窗。
 *
 * 数据全部来自本地组合式（localStorage），不发起任何 API 请求；
 * 列表支持搜索、插入（emit pick 后关闭）和删除确认，底部可直接新增常用语。
 * 深色样式沿用工作台的 `--desk-*` 令牌。
 */
const props = defineProps<{ open: boolean }>();

const emit = defineEmits<{ close: []; pick: [content: string] }>();

const userStore = useUserStore();
// 与工作台页面一致，按当前登录工作人员 ID 隔离常用语；未登录时组合式内部归入 unknown 桶
const staffId = computed(() => userStore.userInfo?.userId);
const { add, remove, replies } = useDeskQuickReplies(staffId);

const keyword = ref('');
const draftTitle = ref('');
const draftContent = ref('');

const filteredReplies = computed(() => {
  const needle = keyword.value.trim().toLowerCase();
  if (!needle) return replies.value;
  return replies.value.filter((reply) => {
    return (
      reply.title.toLowerCase().includes(needle) ||
      reply.content.toLowerCase().includes(needle)
    );
  });
});

/** 每次打开重置搜索与草稿，避免上次的输入残留。 */
watch(
  () => props.open,
  (open) => {
    if (!open) return;
    keyword.value = '';
    draftTitle.value = '';
    draftContent.value = '';
  },
);

/** ElDialog 通过 v-model 回传关闭（右上角 X / ESC / 遮罩）时通知父组件。 */
function handleVisibleChange(visible: boolean) {
  if (!visible) emit('close');
}

function insert(reply: DeskQuickReply) {
  emit('pick', reply.content);
  emit('close');
}

async function removeReply(reply: DeskQuickReply) {
  try {
    await ElMessageBox.confirm(
      `确认删除常用语「${reply.title}」？删除后可重新添加。`,
      '删除常用语',
      {
        cancelButtonText: '取消',
        confirmButtonText: '删除',
        type: 'warning',
      },
    );
  } catch {
    // 取消删除：什么都不做
    return;
  }
  if (remove(reply.id)) {
    ElMessage.success('常用语已删除');
  }
}

function addReply() {
  const title = draftTitle.value.trim();
  const content = draftContent.value.trim();
  if (!title || !content) {
    ElMessage.warning('请填写常用语标题和内容');
    return;
  }
  if (title.length > MAX_QUICK_REPLY_TITLE_LENGTH) {
    ElMessage.warning(`标题最多 ${MAX_QUICK_REPLY_TITLE_LENGTH} 字`);
    return;
  }
  if (content.length > MAX_QUICK_REPLY_CONTENT_LENGTH) {
    ElMessage.warning(`内容最多 ${MAX_QUICK_REPLY_CONTENT_LENGTH} 字`);
    return;
  }
  if (replies.value.length >= MAX_QUICK_REPLIES) {
    ElMessage.warning(
      `最多保存 ${MAX_QUICK_REPLIES} 条常用语，请先删除不用的条目`,
    );
    return;
  }
  if (!add(title, content)) {
    ElMessage.warning('常用语添加失败，请检查标题和内容');
    return;
  }
  draftTitle.value = '';
  draftContent.value = '';
  ElMessage.success('常用语已添加');
}
</script>

<template>
  <ElDialog
    :model-value="props.open"
    append-to-body
    class="quick-reply-dialog"
    destroy-on-close
    title="快捷回复"
    width="560px"
    @update:model-value="handleVisibleChange"
  >
    <ElInput
      v-model="keyword"
      class="quick-reply-search"
      clearable
      placeholder="搜索常用语标题或内容"
    />

    <div class="quick-reply-list">
      <ElEmpty
        v-if="replies.length === 0"
        description="还没有常用语，先在下方添加一条吧"
        :image-size="60"
      />
      <ElEmpty
        v-else-if="filteredReplies.length === 0"
        description="没有匹配的常用语"
        :image-size="60"
      />
      <div
        v-for="reply in filteredReplies"
        :key="reply.id"
        class="quick-reply-item"
      >
        <div class="quick-reply-copy">
          <strong>{{ reply.title }}</strong>
          <p>{{ reply.content }}</p>
        </div>
        <div class="quick-reply-actions">
          <ElButton size="small" type="primary" @click="insert(reply)">
            插入
          </ElButton>
          <ElButton size="small" text type="danger" @click="removeReply(reply)">
            删除
          </ElButton>
        </div>
      </div>
    </div>

    <div class="quick-reply-editor">
      <p class="quick-reply-editor-title">新增常用语</p>
      <ElInput
        v-model="draftTitle"
        class="quick-reply-field"
        :placeholder="`标题，例如：催发货（最多 ${MAX_QUICK_REPLY_TITLE_LENGTH} 字）`"
      />
      <ElInput
        v-model="draftContent"
        class="quick-reply-field"
        :rows="3"
        :placeholder="`内容，插入后会出现在回复输入框（最多 ${MAX_QUICK_REPLY_CONTENT_LENGTH} 字）`"
        type="textarea"
      />
      <div class="quick-reply-editor-actions">
        <ElButton type="primary" @click="addReply">添加</ElButton>
      </div>
    </div>

    <template #footer>
      <ElButton @click="emit('close')">关闭</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.quick-reply-search {
  margin-bottom: 12px;
}

.quick-reply-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 280px;
  overflow: auto;
}

.quick-reply-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 10px 12px;
  background: var(--desk-bubble-bg);
  border: 1px solid var(--desk-bubble-line);
  border-radius: 10px;
}

.quick-reply-copy {
  flex: 1;
  min-width: 0;
}

.quick-reply-copy strong {
  display: block;
  font-size: 13px;
  color: var(--desk-text);
}

.quick-reply-copy p {
  display: -webkit-box;
  margin: 4px 0 0;
  overflow: hidden;
  -webkit-line-clamp: 2;
  font-size: 12px;
  line-height: 1.6;
  color: var(--desk-muted);
  -webkit-box-orient: vertical;
}

.quick-reply-actions {
  display: flex;
  flex: none;
  gap: 4px;
  align-items: center;
}

.quick-reply-editor {
  padding-top: 12px;
  margin-top: 14px;
  border-top: 1px solid var(--desk-line);
}

.quick-reply-editor-title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--desk-text);
}

.quick-reply-field {
  margin-bottom: 8px;
}

.quick-reply-editor-actions {
  display: flex;
  justify-content: flex-end;
}
</style>
