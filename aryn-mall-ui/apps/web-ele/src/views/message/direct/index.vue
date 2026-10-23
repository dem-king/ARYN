<script setup lang="ts">
import type { ViewChatMessage } from '../message-state';

import type { Conversation } from '#/api/message/types';
import type { MessagePushSignal } from '#/store/message';

import { nextTick, onMounted, ref } from 'vue';

import { ElButton, ElEmpty, ElInput, ElMessage, ElTag } from 'element-plus';
import { nanoid } from 'nanoid';

import {
  createStaffDirect,
  getConversationMessages,
  getStaffConversations,
  markConversationRead,
  sendConversationMessage,
} from '#/api/message/conversation';
import { getPage as getStaffPage } from '#/api/upms/user';

import ChatMessageCard from '../chat-message-card.vue';
import {
  isStructuredMessage,
  latestServerSequence,
  markMessageFailed,
  mergeCursorMessages,
  mergeServerMessage,
} from '../message-state';
import { useConversationPush } from '../use-conversation-push';

interface StaffOption {
  avatar?: string;
  id: string;
  nickname?: string;
  username?: string;
}

const conversations = ref<Conversation[]>([]);
const active = ref<Conversation>();
const messages = ref<ViewChatMessage[]>([]);
const staffRecords = ref<StaffOption[]>([]);
const keyword = ref('');
const draft = ref('');
const timeline = ref<HTMLElement>();

async function loadConversations() {
  const page = await getStaffConversations({ limit: 100 });
  conversations.value = page.records.filter(
    (item) => item.conversationType === 'STAFF_DIRECT',
  );
}

async function searchStaff() {
  const page = await getStaffPage({
    current: 1,
    size: 12,
    username: keyword.value || undefined,
  });
  staffRecords.value = page.records || [];
}

async function startDirect(staff: StaffOption) {
  const conversation = await createStaffDirect(staff.id);
  await loadConversations();
  await openConversation(conversation);
  ElMessage.success(
    `已打开与 ${staff.nickname || staff.username || staff.id} 的私信`,
  );
}

async function openConversation(conversation: Conversation) {
  active.value = conversation;
  const page = await getConversationMessages(conversation.id, { limit: 100 });
  messages.value = [...page.records].reverse();
  if (conversation.lastSeq > conversation.lastReadSeq) {
    await markConversationRead(conversation.id, conversation.lastSeq);
    await loadConversations();
  }
  await nextTick();
  timeline.value?.scrollTo({ top: timeline.value.scrollHeight });
}

/** 推送到达后按 afterSeq 增量补拉当前私信，并推进已读游标。 */
async function refreshActiveConversation() {
  const conversation = active.value;
  if (!conversation) return;
  const afterSeq = latestServerSequence(messages.value);
  const page = await getConversationMessages(conversation.id, {
    afterSeq,
    limit: 100,
  });
  if (active.value?.id !== conversation.id) return;
  if (page.records.length > 0) {
    messages.value = mergeCursorMessages(messages.value, page.records);
    await nextTick();
    timeline.value?.scrollTo({ top: timeline.value.scrollHeight });
  }
  const latest = latestServerSequence(messages.value);
  if (latest > (conversation.lastReadSeq || 0)) {
    await markConversationRead(conversation.id, latest);
  }
}

/** 推送到达后刷新私信列表；当前会话有新消息时再按游标补拉。 */
async function refreshOnPush(signal: MessagePushSignal) {
  await loadConversations();
  if (!signal.conversationId || signal.conversationId === active.value?.id) {
    await refreshActiveConversation();
  }
}

async function send() {
  const content = draft.value.trim();
  if (!active.value || !content) return;
  const clientMessageId = nanoid();
  const conversationId = active.value.id;
  draft.value = '';
  messages.value.push({
    clientMessageId,
    content,
    conversationId,
    createTime: new Date().toISOString(),
    id: `local:${clientMessageId}`,
    messageType: 'TEXT',
    sendState: 'sending',
    seqNo: Number.MAX_SAFE_INTEGER,
    senderId: 'current',
    senderName: '我',
    senderType: 'SYS_USER',
  });
  try {
    const serverMessage = await sendConversationMessage(conversationId, {
      clientMessageId,
      content,
      messageType: 'TEXT',
    });
    messages.value = mergeServerMessage(messages.value, serverMessage);
  } catch (error) {
    messages.value = markMessageFailed(messages.value, clientMessageId);
    throw error;
  }
}

useConversationPush(refreshOnPush);

onMounted(async () => {
  await Promise.all([loadConversations(), searchStaff()]);
});
</script>

<template>
  <main class="direct-page">
    <aside class="direct-sidebar">
      <div class="sidebar-heading">
        <p>ONE TO ONE / INTERNAL</p>
        <h1>工作人员私信</h1>
        <span>仅当前租户内一对一沟通，不创建群聊。</span>
      </div>
      <ElInput
        v-model="keyword"
        clearable
        placeholder="按账号搜索工作人员"
        @keyup.enter="searchStaff"
      >
        <template #append>
          <ElButton @click="searchStaff">搜索</ElButton>
        </template>
      </ElInput>
      <div class="staff-grid">
        <button
          v-for="staff in staffRecords"
          :key="staff.id"
          class="staff-chip"
          @click="startDirect(staff)"
        >
          <span>{{
            (staff.nickname || staff.username || '同').slice(0, 1)
          }}</span>
          <div>
            <strong>{{ staff.nickname || staff.username }}</strong
            ><small>{{ staff.id }}</small>
          </div>
        </button>
      </div>
      <div class="conversation-heading">最近私信</div>
      <ElEmpty
        v-if="conversations.length === 0"
        class="rail-empty"
        description="暂无最近私信"
        :image-size="60"
      />
      <button
        v-for="item in conversations"
        :key="item.id"
        class="direct-item"
        :class="{ active: active?.id === item.id }"
        @click="openConversation(item)"
      >
        <span class="direct-avatar">内</span>
        <span class="direct-copy"
          ><strong>内部会话</strong
          ><small>{{ item.lastMessageSummary || '尚无消息' }}</small></span
        >
        <ElTag v-if="item.unreadCount" round size="small" type="danger">
          {{ item.unreadCount }}
        </ElTag>
      </button>
    </aside>

    <section v-if="active" class="direct-chat">
      <header>
        <p>PRIVATE CHANNEL</p>
        <h2>一对一私信</h2>
      </header>
      <div ref="timeline" class="direct-timeline">
        <ElEmpty
          v-if="messages.length === 0"
          class="timeline-empty"
          description="还没有消息"
          :image-size="70"
        />
        <article
          v-for="message in messages"
          :key="message.id"
          :class="{ mine: message.senderType === 'SYS_USER' }"
        >
          <div class="direct-bubble">
            <small>{{ message.senderName || message.senderType }}</small>
            <ChatMessageCard
              v-if="isStructuredMessage(message)"
              :message="message"
            />
            <p v-else>{{ message.content || '[系统消息]' }}</p>
            <time>{{
              message.sendState === 'sending'
                ? '发送中…'
                : message.sendState === 'failed'
                  ? '发送失败'
                  : `#${message.seqNo} · ${message.createTime}`
            }}</time>
          </div>
        </article>
      </div>
      <footer>
        <ElInput
          v-model="draft"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="输入内部消息"
          @keydown.enter.exact.prevent="send"
        />
        <div>
          <span>私信不进入客服池，也不占用接待名额</span
          ><ElButton type="primary" @click="send">发送</ElButton>
        </div>
      </footer>
    </section>
    <section v-else class="direct-empty">
      <ElEmpty description="搜索工作人员或选择最近私信" />
    </section>
  </main>
</template>

<style scoped>
.direct-page {
  /* 本地语义变量统一指向 Element 令牌，页面其余样式自动跟随 */
  --direct-ink: var(--el-text-color-primary);
  --direct-muted: var(--el-text-color-secondary);
  --direct-line: var(--el-border-color-lighter);
  --direct-surface: var(--el-bg-color);
  --direct-accent: var(--el-color-primary);
  --direct-accent-soft: var(--el-color-primary-light-9);

  display: grid;

  /* 缺省行是 auto，会按内容撑高、顶破固定高度容器把输入区挤到可视区外 */
  grid-template-rows: minmax(0, 1fr);
  grid-template-columns: 370px 1fr;
  height: calc(100vh - 112px);
  min-height: 620px;
  overflow: hidden;
  background: var(--el-bg-color-page);
  border: 1px solid var(--direct-line);
  border-radius: 18px;
  box-shadow: var(--el-box-shadow-light);
}

.direct-sidebar {
  padding: 24px;
  overflow: auto;
  background: linear-gradient(
    180deg,
    var(--el-fill-color-lighter),
    var(--direct-surface)
  );
  border-right: 1px solid var(--direct-line);
}

.sidebar-heading {
  margin-bottom: 20px;
}

.sidebar-heading p {
  font-size: 10px;
  font-weight: 800;
  color: var(--direct-accent);
  letter-spacing: 0.18em;
}

.sidebar-heading h1 {
  margin: 5px 0;
  font-family: 'Noto Serif SC', serif;
  font-size: 25px;
  color: var(--direct-ink);
}

.sidebar-heading span {
  font-size: 12px;
  color: var(--direct-muted);
}

.staff-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin: 14px 0 24px;
}

.staff-chip {
  display: flex;
  gap: 9px;
  align-items: center;
  min-width: 0;
  padding: 10px;
  text-align: left;
  background: var(--direct-surface);
  border: 1px solid var(--direct-line);
  border-radius: 11px;
}

.staff-chip > span,
.direct-avatar {
  display: grid;
  flex: none;
  place-items: center;
  width: 32px;
  height: 32px;
  color: var(--direct-accent);
  background: var(--direct-accent-soft);
  border-radius: 9px;
}

.staff-chip div {
  min-width: 0;
}

.staff-chip strong,
.staff-chip small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.staff-chip strong {
  font-size: 12px;
  color: var(--direct-ink);
}

.staff-chip small {
  font-size: 9px;
  color: var(--el-text-color-placeholder);
}

.conversation-heading {
  margin: 8px 0;
  font-size: 11px;
  font-weight: 700;
  color: var(--direct-muted);
  letter-spacing: 0.12em;
}

.rail-empty {
  padding: 4px 0 14px;
}

.direct-item {
  display: flex;
  gap: 11px;
  align-items: center;
  width: 100%;
  padding: 11px;
  margin-bottom: 5px;
  text-align: left;
  border-radius: 11px;
}

.direct-item:hover,
.direct-item.active {
  background: var(--direct-accent-soft);
}

.direct-copy {
  flex: 1;
  min-width: 0;
}

.direct-copy strong,
.direct-copy small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.direct-copy strong {
  font-size: 13px;
  color: var(--direct-ink);
}

.direct-copy small {
  margin-top: 4px;
  font-size: 11px;
  color: var(--direct-muted);
}

.direct-chat {
  display: grid;
  grid-template-rows: auto 1fr auto;
  min-width: 0;
  background:
    radial-gradient(
      circle at 92% 0,
      var(--el-color-primary-light-9),
      transparent 30%
    ),
    var(--direct-surface);
}

.direct-chat header {
  padding: 23px 28px;
  border-bottom: 1px solid var(--direct-line);
}

.direct-chat header p {
  font-size: 9px;
  color: var(--direct-accent);
  letter-spacing: 0.18em;
}

.direct-chat h2 {
  margin-top: 4px;
  font-size: 18px;
  color: var(--direct-ink);
}

.direct-timeline {
  padding: 28px 7%;
  overflow: auto;
}

.timeline-empty {
  padding: 24px 0;
}

.direct-timeline article {
  display: flex;
  margin-bottom: 16px;
}

.direct-timeline article.mine {
  justify-content: flex-end;
}

.direct-bubble {
  max-width: min(70%, 680px);
  padding: 12px 15px;
  color: var(--el-text-color-regular);
  background: var(--direct-surface);
  border: 1px solid var(--direct-line);
  border-radius: 5px 15px 15px;
  box-shadow: var(--el-box-shadow-lighter);
}

.mine .direct-bubble {
  color: var(--el-color-white);
  background: var(--direct-accent);
  border-color: var(--direct-accent);
  border-radius: 15px 5px 15px 15px;
}

.direct-bubble small {
  font-size: 10px;
  opacity: 0.65;
}

.direct-bubble p {
  margin: 7px 0;
  white-space: pre-wrap;
}

.direct-bubble time {
  font-size: 9px;
  opacity: 0.55;
}

.direct-chat footer {
  padding: 17px 24px;
  background: var(--direct-surface);
  border-top: 1px solid var(--direct-line);
}

.direct-chat footer div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 10px;
  color: var(--direct-muted);
}

.direct-empty {
  display: grid;
  place-items: center;
}
</style>
