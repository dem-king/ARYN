<script setup lang="ts">
import type { ViewChatMessage } from '../message-state';

import type { AgentInfo } from '#/api/message/agent';
import type { Conversation } from '#/api/message/types';
import type { PresenceStatus } from '#/store/agent-presence';
import type { MessagePushSignal } from '#/store/message';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { useUserStore } from '@vben/stores';

import {
  ElButton,
  ElEmpty,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus';
import { nanoid } from 'nanoid';

import {
  claimConversation,
  closeConversation,
  getConversationMessages,
  getStaffConversations,
  getWaitingConversations,
  markConversationRead,
  sendConversationMessage,
  transferConversation,
} from '#/api/message/conversation';
import { useAgentPresenceStore } from '#/store/agent-presence';
import { useMessageStore } from '#/store/message';

import ChatMessageCard from '../chat-message-card.vue';
import {
  isStructuredMessage,
  latestServerSequence,
  markMessageFailed,
  mergeCursorMessages,
  mergeServerMessage,
} from '../message-state';
import { useConversationPush } from '../use-conversation-push';
import AgentConfigDialog from './agent-config-dialog.vue';

const activeTab = ref<'mine' | 'waiting'>('mine');
const userStore = useUserStore();
const messageStore = useMessageStore();
const presenceStore = useAgentPresenceStore();
const conversations = ref<Conversation[]>([]);
const active = ref<Conversation>();
const messages = ref<ViewChatMessage[]>([]);
const draft = ref('');
const timeline = ref<HTMLElement>();
const agentConfigDialog = ref<InstanceType<typeof AgentConfigDialog>>();

const title = computed(() =>
  active.value?.conversationType === 'STAFF_DIRECT'
    ? '工作人员私信'
    : `会员会话 · ${active.value?.customerId || ''}`,
);

async function loadConversations() {
  if (activeTab.value === 'waiting') {
    conversations.value = await getWaitingConversations({
      limit: 50,
      queueCode: 'DEFAULT',
    });
  } else {
    const page = await getStaffConversations({ limit: 50 });
    conversations.value = page.records;
  }
  if (active.value)
    active.value = conversations.value.find(
      (item) => item.id === active.value?.id,
    );
}

async function openConversation(conversation: Conversation) {
  active.value = conversation;
  const page = await getConversationMessages(conversation.id, { limit: 100 });
  messages.value = [...page.records].reverse();
  if (
    isReadable(conversation) &&
    conversation.lastSeq > conversation.lastReadSeq
  ) {
    await markConversationRead(conversation.id, conversation.lastSeq);
    await loadConversations();
    syncBellAfterRead();
  }
  await nextTick();
  scrollToBottom();
}

/** 待领取会话尚未分配给当前坐席，此时推进已读游标会被服务端拒绝。 */
function isReadable(conversation: Conversation) {
  return conversation.status !== 'WAITING';
}

/**
 * 工作台推进已读游标后，铃铛里的会话未读需要同步回落。
 *
 * 否则读过的会话会一直挂在角标上，坐席分不清哪些还没看。
 */
function syncBellAfterRead() {
  void messageStore.refreshNotifications();
}

function scrollToBottom(behavior: ScrollBehavior = 'smooth') {
  timeline.value?.scrollTo({
    behavior,
    top: timeline.value.scrollHeight,
  });
}

/** 推送到达后按 afterSeq 增量补拉当前会话，并按需推进已读游标。 */
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
    scrollToBottom('auto');
  }
  const latest = latestServerSequence(messages.value);
  if (isReadable(conversation) && latest > (conversation.lastReadSeq || 0)) {
    await markConversationRead(conversation.id, latest);
    // 推送触发的 store 刷新可能先于本次已读上报完成，这里补一次保证角标收敛。
    syncBellAfterRead();
  }
}

/**
 * 推送到达后刷新会话列表；只有当前会话有新消息时才补拉消息，
 * 避免为其他会话的变化白白拉一次当前会话。
 */
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
  draft.value = '';
  messages.value.push({
    clientMessageId,
    content,
    conversationId: active.value.id,
    createTime: new Date().toISOString(),
    id: `local:${clientMessageId}`,
    messageType: 'TEXT',
    seqNo: Number.MAX_SAFE_INTEGER,
    senderId: 'current',
    senderName: '我',
    senderType: 'SYS_USER',
    sendState: 'sending',
  });
  try {
    const message = await sendConversationMessage(active.value.id, {
      clientMessageId,
      content,
      messageType: 'TEXT',
    });
    messages.value = mergeServerMessage(messages.value, message);
    await nextTick();
    scrollToBottom();
  } catch (error) {
    messages.value = markMessageFailed(messages.value, clientMessageId);
    throw error;
  }
}

async function claim() {
  if (!active.value) return;
  await claimConversation(active.value.id);
  ElMessage.success('会话已领取');
  activeTab.value = 'mine';
  await loadConversations();
  // 领取后该会话不再属于待领取池，铃铛角标要同步减少。
  syncBellAfterRead();
}

async function transfer() {
  if (!active.value) return;
  const { value } = await ElMessageBox.prompt(
    '请输入目标客服工作人员 ID',
    '转交会话',
  );
  await transferConversation(active.value.id, value);
  active.value = undefined;
  await loadConversations();
}

async function close() {
  if (!active.value) return;
  await ElMessageBox.confirm(
    '关闭后会员可在 24 小时内重开该会话。',
    '关闭会话',
  );
  await closeConversation(active.value.id, '客服已完成处理');
  active.value = undefined;
  await loadConversations();
}

const showOfflineTip = computed(
  () =>
    presenceStore.loaded &&
    presenceStore.enabled &&
    presenceStore.presence === 'OFFLINE',
);

/** 下拉切换接待状态；失败时 store 已回滚状态，错误提示由请求层统一弹出。 */
async function changePresence(value: PresenceStatus) {
  try {
    await presenceStore.changePresence(value);
  } catch {
    // 已由请求层提示，这里无需重复处理。
  }
}

/** 离线提示条的一键上线。 */
function goOnline() {
  void changePresence('ONLINE');
}

function handleAgentSaved(agent: AgentInfo) {
  if (agent.staffId !== userStore.userInfo?.userId) return;
  // 首次配置坐席时本地还没有心跳循环，先补激活再同步状态。
  presenceStore.activate();
  presenceStore.applyAgent(agent);
}

/** 结构化消息（图片/业务卡片）交给卡片组件，这里只兜底文本与系统消息。 */
function displayMessage(message: ViewChatMessage) {
  if (message.messageType === 'TEXT') return message.content;
  return message.content || '[系统消息]';
}

/**
 * 定位铃铛指定的会话。
 *
 * 待领取会话不在“我的会话”里，需要切到待领取页签再打开。
 */
async function openPendingConversation(conversationId: string) {
  const mine = conversations.value.find((item) => item.id === conversationId);
  if (mine) {
    await openConversation(mine);
    return;
  }
  activeTab.value = 'waiting';
  await loadConversations();
  const waiting = conversations.value.find(
    (item) => item.id === conversationId,
  );
  if (waiting) await openConversation(waiting);
}

/** 会话已在工作台打开时再次点击铃铛，走 watch 分支而不是重新挂载。 */
watch(
  () => messageStore.pendingConversationId,
  async (conversationId) => {
    if (!conversationId) return;
    messageStore.clearPendingConversation();
    await openPendingConversation(conversationId);
  },
);

useConversationPush(refreshOnPush);

onMounted(async () => {
  // 接待状态与心跳由应用级 store 托管，页面卸载（去查订单等）不再掉线。
  presenceStore.activate();
  await loadConversations();
  const pending = messageStore.pendingConversationId;
  if (pending) {
    messageStore.clearPendingConversation();
    await openPendingConversation(pending);
  }
});
</script>

<template>
  <div class="service-desk">
    <aside class="conversation-rail">
      <div class="desk-brand">
        <div class="desk-brand-copy">
          <span class="pulse"></span>
          <div>
            <strong>客服调度台</strong><small>SHARED SERVICE POOL</small>
          </div>
        </div>
        <ElButton
          v-access:code="'message:service:agent'"
          class="config-trigger"
          size="small"
          @click="agentConfigDialog?.open()"
        >
          坐席设置
        </ElButton>
      </div>
      <div class="presence-row">
        <span>接待状态</span
        ><ElSelect
          v-model="presenceStore.presence"
          size="small"
          @change="changePresence"
        >
          <ElOption label="在线" value="ONLINE" /><ElOption
            label="忙碌"
            value="BUSY"
          /><ElOption label="暂停" value="PAUSED" /><ElOption
            label="离线"
            value="OFFLINE"
          />
        </ElSelect>
      </div>
      <div v-if="showOfflineTip" class="offline-tip">
        <span>当前离线，不参与自动接待与待领取分配</span>
        <ElButton size="small" type="primary" @click="goOnline">
          一键上线
        </ElButton>
      </div>
      <div class="segment">
        <button
          :class="{ active: activeTab === 'mine' }"
          @click="
            activeTab = 'mine';
            loadConversations();
          "
        >
          我的会话
        </button>
        <button
          :class="{ active: activeTab === 'waiting' }"
          @click="
            activeTab = 'waiting';
            loadConversations();
          "
        >
          待领取
        </button>
      </div>
      <div class="conversation-list">
        <ElEmpty
          v-if="conversations.length === 0"
          class="rail-empty"
          description="暂无会话"
          :image-size="60"
        />
        <button
          v-for="item in conversations"
          :key="item.id"
          class="conversation-item"
          :class="{ active: active?.id === item.id }"
          @click="openConversation(item)"
        >
          <span class="avatar">{{
            item.conversationType === 'STAFF_DIRECT' ? '内' : '客'
          }}</span>
          <span class="copy"
            ><strong>{{
              item.conversationType === 'STAFF_DIRECT'
                ? '工作人员私信'
                : `会员 ${item.customerId}`
            }}</strong
            ><small>{{ item.lastMessageSummary || '等待新消息' }}</small></span
          >
          <ElTag v-if="item.unreadCount" round size="small" type="danger">
            {{ item.unreadCount }}
          </ElTag>
        </button>
      </div>
    </aside>

    <main v-if="active" class="chat-stage">
      <header>
        <div>
          <p>{{ active.queueCode }}</p>
          <h1>{{ title }}</h1>
        </div>
        <div class="actions">
          <ElButton
            v-if="activeTab === 'waiting'"
            type="primary"
            @click="claim"
          >
            领取
          </ElButton>
          <ElButton v-if="activeTab === 'mine'" @click="transfer">
            转交
          </ElButton>
          <ElButton
            v-if="activeTab === 'mine'"
            type="danger"
            plain
            @click="close"
          >
            关闭
          </ElButton>
        </div>
      </header>
      <div ref="timeline" class="timeline">
        <ElEmpty
          v-if="messages.length === 0"
          class="timeline-empty"
          description="还没有消息"
          :image-size="70"
        />
        <div
          v-for="message in messages"
          :key="message.id"
          class="message-row"
          :class="{
            mine: message.senderType === 'SYS_USER',
            system: message.senderType === 'SYSTEM',
          }"
        >
          <div class="bubble">
            <small>{{ message.senderName || message.senderType }}</small>
            <ChatMessageCard
              v-if="isStructuredMessage(message)"
              :message="message"
            />
            <p v-else>{{ displayMessage(message) }}</p>
            <time>{{
              message.sendState === 'sending'
                ? '发送中…'
                : message.sendState === 'failed'
                  ? '发送失败，可重新发送'
                  : `#${message.seqNo} · ${message.createTime}`
            }}</time>
          </div>
        </div>
      </div>
      <footer>
        <ElInput
          v-model="draft"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="输入回复，Enter 发送"
          @keydown.enter.exact.prevent="send"
        />
        <div class="send-row">
          <span>HTTP 可靠发送 · WebSocket 实时索引</span
          ><ElButton type="primary" @click="send">发送回复</ElButton>
        </div>
      </footer>
    </main>
    <section v-else class="empty-stage">
      <ElEmpty description="选择一个会话开始处理" />
    </section>
    <AgentConfigDialog ref="agentConfigDialog" @saved="handleAgentSaved" />
  </div>
</template>

<style scoped>
.service-desk {
  /* 品牌视觉：客服调度台是深色工作台，整套深色配色集中在这里，
     浅色/深色模式下都保持同一块深色大屏，避免色值散落在各条规则里 */
  --desk-bg: #081723;
  --desk-canvas: #0a1822;
  --desk-surface: #0b1d28;
  --desk-segment-bg: #06121c;
  --desk-rail-bg: linear-gradient(180deg, #0c2233, #091923);
  --desk-line: #16324f;
  --desk-line-soft: rgb(125 211 252 / 12%);
  --desk-line-faint: rgb(125 211 252 / 10%);
  --desk-text: #dbeafe;
  --desk-muted: #8eb2c8;
  --desk-dim: #607f92;
  --desk-faint: #547387;
  --desk-shadow: 0 24px 70px rgb(4 19 30 / 28%);
  --desk-accent: #2dd4bf;
  --desk-accent-strong: #5eead4;
  --desk-accent-contrast: #ecfeff;
  --desk-accent-soft: rgb(20 184 166 / 9%);
  --desk-accent-soft-hover: rgb(20 184 166 / 18%);
  --desk-accent-line: rgb(45 212 191 / 24%);
  --desk-accent-line-hover: rgb(94 234 212 / 48%);
  --desk-glow: rgb(13 148 136 / 12%);
  --desk-hover: rgb(56 189 248 / 9%);
  --desk-avatar-bg: #0c2c38;
  --desk-avatar-line: #1d5364;
  --desk-segment-text: #6f91a6;
  --desk-segment-active-text: #dffcf8;
  --desk-segment-active-bg: #123b49;
  --desk-bubble-bg: #102735;
  --desk-bubble-line: #1a3a4b;
  --desk-bubble-text: #c7dce8;
  --desk-bubble-dim: #6d93a8;
  --desk-mine-bg: #0f4948;
  --desk-mine-line: #176b66;
  --desk-mine-text: #e6fffb;
  --desk-system-bg: #0b202c;
  --desk-system-text: #7395a8;
  --desk-warning: #fbbf24;
  --desk-warning-soft: rgb(251 191 36 / 10%);
  --desk-warning-line: rgb(251 191 36 / 32%);

  display: grid;

  /* 缺省行是 auto，会按内容撑高、顶破固定高度容器把输入区挤到可视区外 */
  grid-template-rows: minmax(0, 1fr);
  grid-template-columns: 330px 1fr;
  height: calc(100vh - 112px);
  min-height: 620px;
  overflow: hidden;
  color: var(--desk-text);
  background: var(--desk-bg);
  border: 1px solid var(--desk-line);
  border-radius: 16px;
  box-shadow: var(--desk-shadow);
}

.conversation-rail {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--desk-rail-bg);
  border-right: 1px solid var(--desk-line-soft);
}

.desk-brand {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 24px;
  border-bottom: 1px solid var(--desk-line-faint);
}

.desk-brand-copy {
  display: flex;
  gap: 13px;
  align-items: center;
  min-width: 0;
}

.desk-brand strong {
  display: block;
  font-size: 17px;
}

.desk-brand small {
  font-size: 9px;
  color: var(--desk-dim);
  letter-spacing: 0.18em;
}

.config-trigger {
  flex: none;
  color: var(--desk-accent-strong);
  background: var(--desk-accent-soft);
  border-color: var(--desk-accent-line);
}

.config-trigger:hover {
  color: var(--desk-accent-contrast);
  background: var(--desk-accent-soft-hover);
  border-color: var(--desk-accent-line-hover);
}

.pulse {
  width: 12px;
  height: 12px;
  background: var(--desk-accent);
  border-radius: 50%;
  box-shadow:
    0 0 0 6px var(--desk-accent-soft),
    0 0 22px var(--desk-accent);
}

.presence-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  font-size: 12px;
  color: var(--desk-muted);
}

.offline-tip {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding: 9px 12px;
  margin: 0 16px 12px;
  font-size: 12px;
  color: var(--desk-warning);
  background: var(--desk-warning-soft);
  border: 1px solid var(--desk-warning-line);
  border-radius: 10px;
}

.segment {
  display: grid;
  grid-template-columns: 1fr 1fr;
  padding: 4px;
  margin: 4px 16px 12px;
  background: var(--desk-segment-bg);
  border-radius: 10px;
}

.segment button {
  padding: 9px;
  color: var(--desk-segment-text);
  border-radius: 8px;
}

.segment button.active {
  color: var(--desk-segment-active-text);
  background: var(--desk-segment-active-bg);
}

.conversation-list {
  padding: 0 10px 20px;
  overflow: auto;
}

.rail-empty {
  padding: 8px 0 14px;
}

.conversation-item {
  display: flex;
  gap: 12px;
  align-items: center;
  width: 100%;
  padding: 13px;
  text-align: left;
  border-radius: 12px;
}

.conversation-item:hover,
.conversation-item.active {
  background: var(--desk-hover);
}

.avatar {
  display: grid;
  flex: none;
  place-items: center;
  width: 38px;
  height: 38px;
  color: var(--desk-accent-strong);
  background: var(--desk-avatar-bg);
  border: 1px solid var(--desk-avatar-line);
  border-radius: 11px;
}

.copy {
  flex: 1;
  min-width: 0;
}

.copy strong,
.copy small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.copy strong {
  font-size: 13px;
  color: var(--desk-text);
}

.copy small {
  margin-top: 5px;
  font-size: 11px;
  color: var(--desk-dim);
}

.chat-stage {
  display: grid;
  grid-template-rows: auto 1fr auto;
  min-width: 0;
  background:
    radial-gradient(circle at 100% 0, var(--desk-glow), transparent 30%),
    var(--desk-canvas);
}

.chat-stage header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid var(--desk-line-faint);
}

.chat-stage header p {
  font-size: 10px;
  color: var(--desk-accent);
  letter-spacing: 0.16em;
}

.chat-stage h1 {
  margin-top: 4px;
  font-size: 18px;
}

.timeline {
  padding: 28px 6%;
  overflow: auto;
}

.timeline-empty {
  padding: 24px 0;
}

.message-row {
  display: flex;
  margin-bottom: 17px;
}

.message-row.mine {
  justify-content: flex-end;
}

.message-row.system {
  justify-content: center;
}

.bubble {
  max-width: min(68%, 680px);
  padding: 12px 15px;
  color: var(--desk-bubble-text);
  background: var(--desk-bubble-bg);
  border: 1px solid var(--desk-bubble-line);
  border-radius: 5px 15px 15px;
}

.mine .bubble {
  color: var(--desk-mine-text);
  background: var(--desk-mine-bg);
  border-color: var(--desk-mine-line);
  border-radius: 15px 5px 15px 15px;
}

.system .bubble {
  padding: 7px 13px;
  color: var(--desk-system-text);
  background: var(--desk-system-bg);
  border: 0;
  border-radius: 20px;
}

.bubble small {
  font-size: 10px;
  color: var(--desk-bubble-dim);
}

.bubble p {
  margin: 7px 0;
  white-space: pre-wrap;
}

.bubble time {
  font-size: 9px;
  color: var(--desk-faint);
}

.chat-stage footer {
  padding: 16px 22px;
  background: var(--desk-surface);
  border-top: 1px solid var(--desk-line-faint);
}

.send-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 10px;
  color: var(--desk-faint);
}

.empty-stage {
  display: grid;
  place-items: center;
  background: var(--desk-canvas);
}

/* 深色工作台里的空态文字跟随工作台配色，避免浅色模式默认灰字对比不足 */
.rail-empty :deep(.el-empty__description p),
.timeline-empty :deep(.el-empty__description p),
.empty-stage :deep(.el-empty__description p) {
  color: var(--desk-muted);
}
</style>
