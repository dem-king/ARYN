<script setup lang="ts">
import type { ViewChatMessage } from '../message-state';

import type { AgentInfo } from '#/api/message/agent';
import type { Conversation } from '#/api/message/types';

import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';

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
  getSelfAgent,
  heartbeatAgent,
  updateAgentPresence,
} from '#/api/message/agent';
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

import { markMessageFailed, mergeServerMessage } from '../message-state';
import AgentConfigDialog from './agent-config-dialog.vue';

const activeTab = ref<'mine' | 'waiting'>('mine');
const userStore = useUserStore();
const conversations = ref<Conversation[]>([]);
const active = ref<Conversation>();
const messages = ref<ViewChatMessage[]>([]);
const draft = ref('');
const presence = ref<'BUSY' | 'OFFLINE' | 'ONLINE' | 'PAUSED'>('OFFLINE');
const timeline = ref<HTMLElement>();
const agentConfigDialog = ref<InstanceType<typeof AgentConfigDialog>>();
let heartbeatTimer: ReturnType<typeof setInterval> | undefined;

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
  if (conversation.lastSeq > conversation.lastReadSeq) {
    await markConversationRead(conversation.id, conversation.lastSeq);
  }
  await nextTick();
  timeline.value?.scrollTo({
    behavior: 'smooth',
    top: timeline.value.scrollHeight,
  });
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
    timeline.value?.scrollTo({
      behavior: 'smooth',
      top: timeline.value.scrollHeight,
    });
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

async function changePresence(value: typeof presence.value) {
  const previous = presence.value;
  try {
    const agent = await updateAgentPresence(value);
    presence.value = agent.presenceStatus;
  } catch (error) {
    presence.value = previous;
    throw error;
  }
}

function handleAgentSaved(agent: AgentInfo) {
  if (agent.staffId !== userStore.userInfo?.userId) return;
  presence.value = agent.enabled === '1' ? agent.presenceStatus : 'OFFLINE';
}

function displayMessage(message: ViewChatMessage) {
  if (message.messageType === 'TEXT') return message.content;
  const labels: Record<string, string> = {
    IMAGE: '[图片]',
    NOTICE_CARD: '[通知卡片]',
    ORDER_CARD: '[订单卡片]',
    PRODUCT_CARD: '[商品卡片]',
    REFUND_CARD: '[退款卡片]',
    SYSTEM: message.content || '[系统消息]',
  };
  return labels[message.messageType] || '[消息]';
}

onMounted(async () => {
  try {
    const agent = await getSelfAgent();
    presence.value = agent.presenceStatus;
  } catch {
    presence.value = 'OFFLINE';
  }
  await loadConversations();
  heartbeatTimer = setInterval(() => {
    if (presence.value !== 'OFFLINE') void heartbeatAgent();
  }, 30_000);
});

onBeforeUnmount(() => heartbeatTimer && clearInterval(heartbeatTimer));
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
        ><ElSelect v-model="presence" size="small" @change="changePresence">
          <ElOption label="在线" value="ONLINE" /><ElOption
            label="忙碌"
            value="BUSY"
          /><ElOption label="暂停" value="PAUSED" /><ElOption
            label="离线"
            value="OFFLINE"
          />
        </ElSelect>
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
            <p>{{ displayMessage(message) }}</p>
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
  display: grid;
  grid-template-columns: 330px 1fr;
  height: calc(100vh - 112px);
  min-height: 620px;
  overflow: hidden;
  color: #dbeafe;
  background: #081723;
  border: 1px solid #16324f;
  border-radius: 16px;
  box-shadow: 0 24px 70px rgb(4 19 30 / 28%);
}

.conversation-rail {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: linear-gradient(180deg, #0c2233, #091923);
  border-right: 1px solid rgb(125 211 252 / 12%);
}

.desk-brand {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 24px;
  border-bottom: 1px solid rgb(125 211 252 / 10%);
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
  color: #5f8198;
  letter-spacing: 0.18em;
}

.config-trigger {
  flex: none;
  color: #99f6e4;
  background: rgb(20 184 166 / 9%);
  border-color: rgb(45 212 191 / 24%);
}

.config-trigger:hover {
  color: #ecfeff;
  background: rgb(20 184 166 / 18%);
  border-color: rgb(94 234 212 / 48%);
}

.pulse {
  width: 12px;
  height: 12px;
  background: #2dd4bf;
  border-radius: 50%;
  box-shadow:
    0 0 0 6px rgb(45 212 191 / 12%),
    0 0 22px #2dd4bf;
}

.presence-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  font-size: 12px;
  color: #8eb2c8;
}

.segment {
  display: grid;
  grid-template-columns: 1fr 1fr;
  padding: 4px;
  margin: 4px 16px 12px;
  background: #06121c;
  border-radius: 10px;
}

.segment button {
  padding: 9px;
  color: #6f91a6;
  border-radius: 8px;
}

.segment button.active {
  color: #dffcf8;
  background: #123b49;
}

.conversation-list {
  padding: 0 10px 20px;
  overflow: auto;
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
  background: rgb(56 189 248 / 9%);
}

.avatar {
  display: grid;
  flex: none;
  place-items: center;
  width: 38px;
  height: 38px;
  color: #5eead4;
  background: #0c2c38;
  border: 1px solid #1d5364;
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
  color: #dbeafe;
}

.copy small {
  margin-top: 5px;
  font-size: 11px;
  color: #607f92;
}

.chat-stage {
  display: grid;
  grid-template-rows: auto 1fr auto;
  min-width: 0;
  background:
    radial-gradient(circle at 100% 0, rgb(13 148 136 / 12%), transparent 30%),
    #0a1822;
}

.chat-stage header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid rgb(125 211 252 / 10%);
}

.chat-stage header p {
  font-size: 10px;
  color: #2dd4bf;
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
  color: #c7dce8;
  background: #102735;
  border: 1px solid #1a3a4b;
  border-radius: 5px 15px 15px;
}

.mine .bubble {
  color: #e6fffb;
  background: #0f4948;
  border-color: #176b66;
  border-radius: 15px 5px 15px 15px;
}

.system .bubble {
  padding: 7px 13px;
  color: #7395a8;
  background: #0b202c;
  border: 0;
  border-radius: 20px;
}

.bubble small {
  font-size: 10px;
  color: #6d93a8;
}

.bubble p {
  margin: 7px 0;
  white-space: pre-wrap;
}

.bubble time {
  font-size: 9px;
  color: #547387;
}

.chat-stage footer {
  padding: 16px 22px;
  background: #0b1d28;
  border-top: 1px solid rgb(125 211 252 / 10%);
}

.send-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 10px;
  color: #547387;
}

.empty-stage {
  display: grid;
  place-items: center;
  background: #0a1822;
}
</style>
