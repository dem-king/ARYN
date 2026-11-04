<script setup lang="ts">
import type { ViewChatMessage } from '../message-state';

import type { Conversation } from '#/api/message/types';
import type { MessagePushSignal } from '#/store/message';

import { nextTick, onActivated, onMounted, ref, watch } from 'vue';

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

interface StaffOption {
  avatar?: string;
  id: string;
  nickname?: string;
  username?: string;
}

const messageStore = useMessageStore();
const conversations = ref<Conversation[]>([]);
const active = ref<Conversation>();
const messages = ref<ViewChatMessage[]>([]);
const staffRecords = ref<StaffOption[]>([]);
const keyword = ref('');
const draft = ref('');
const timeline = ref<HTMLElement>();

/**
 * 会话打开代际：慢网连点时只认最后一次点击。
 *
 * 请求返回时代际已变化，说明用户又打开了别的会话，旧响应必须丢弃，
 * 否则后到的响应会把消息写进新会话的列表里（串会话）。
 */
let openToken = 0;

function scrollToBottom(behavior: ScrollBehavior = 'smooth') {
  timeline.value?.scrollTo({
    behavior,
    top: timeline.value.scrollHeight,
  });
}

/** 加载私信会话列表；按会话 ID 去重，避免推送刷新与手动加载交叠时出现重复项。 */
async function loadConversations() {
  const page = await getStaffConversations({ limit: 100 });
  const seen = new Set<string>();
  conversations.value = page.records.filter((item) => {
    if (item.conversationType !== 'STAFF_DIRECT' || seen.has(item.id)) {
      return false;
    }
    seen.add(item.id);
    return true;
  });
  // 列表刷新后用最新未读/摘要覆盖当前打开项，模板角标才不会停在旧值
  if (active.value) {
    const refreshed = conversations.value.find(
      (item) => item.id === active.value?.id,
    );
    if (refreshed) active.value = refreshed;
  }
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
  try {
    const conversation = await createStaffDirect(staff.id);
    await loadConversations();
    await openConversation(conversation);
    ElMessage.success(
      `已打开与 ${staff.nickname || staff.username || staff.id} 的私信`,
    );
  } catch {
    // 请求层已统一提示；保留列表页可用性
  }
}

/** 列表点击入口：吞掉请求层已提示的异常，避免 unhandled rejection。 */
function handleOpenConversation(conversation: Conversation) {
  void openConversation(conversation).catch(() => {
    // 请求层已统一提示
  });
}

/** 打开会话并标记已读（带竞态保护）。 */
async function openConversation(conversation: Conversation) {
  const token = ++openToken;
  active.value = conversation;
  const page = await getConversationMessages(conversation.id, { limit: 100 });
  if (token !== openToken) return;
  messages.value = [...page.records].reverse();
  if (conversation.lastSeq > conversation.lastReadSeq) {
    await markConversationRead(conversation.id, conversation.lastSeq);
    if (token !== openToken) return;
    conversation.lastReadSeq = conversation.lastSeq;
    const current = active.value;
    if (current && current.id === conversation.id) {
      active.value = { ...current, lastReadSeq: conversation.lastSeq };
    }
    // 读掉会话未读后回刷铃铛，否则角标要等到下一次推送才回落
    void messageStore.refreshNotifications();
    await loadConversations();
    if (token !== openToken) return;
  }
  await nextTick();
  if (token !== openToken) return;
  scrollToBottom();
}

/** 推送/重连后按 afterSeq 增量补拉当前会话，并推进已读游标。 */
async function refreshActiveConversation() {
  const conversation = active.value;
  if (!conversation) return;
  const token = openToken;
  const afterSeq = latestServerSequence(messages.value);
  const page = await getConversationMessages(conversation.id, {
    afterSeq,
    limit: 100,
  });
  if (token !== openToken) return;
  if (page.records.length > 0) {
    messages.value = mergeCursorMessages(messages.value, page.records);
    await nextTick();
    if (token !== openToken) return;
    scrollToBottom();
  }
  const latest = latestServerSequence(messages.value);
  const current = active.value;
  if (
    current?.id === conversation.id &&
    latest > (conversation.lastReadSeq || 0)
  ) {
    await markConversationRead(conversation.id, latest);
    if (token !== openToken) return;
    current.lastReadSeq = latest;
    // 读后回刷铃铛：会话未读与角标必须同步收敛
    void messageStore.refreshNotifications();
  }
}

/**
 * 推送到达后刷新私信列表；当前会话有新消息时再按游标补拉。
 *
 * 重连信号（RECONNECTED）没有 conversationId，等同于“当前会话可能缺消息”，
 * 因此同样走一次当前会话补拉。
 */
async function refreshOnPush(signal: MessagePushSignal) {
  await loadConversations();
  if (!signal.conversationId || signal.conversationId === active.value?.id) {
    await refreshActiveConversation();
  }
}

/** 发送文本消息并合并服务端回执；失败抛给调用方决定本地失败态。 */
async function deliverMessage(
  conversationId: string,
  clientMessageId: string,
  content: string,
) {
  const serverMessage = await sendConversationMessage(conversationId, {
    clientMessageId,
    content,
    messageType: 'TEXT',
  });
  // 慢网期间用户可能已切换会话：旧会话的回执不得写进当前消息列表
  if (active.value?.id !== conversationId) return;
  messages.value = mergeServerMessage(messages.value, serverMessage);
  await nextTick();
  scrollToBottom();
}

async function send() {
  const content = draft.value.trim();
  if (!active.value || !content) return;
  const conversationId = active.value.id;
  const clientMessageId = nanoid();
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
    await deliverMessage(conversationId, clientMessageId, content);
  } catch {
    // 失败由请求层提示；这里只在仍是同一会话时把气泡标成可重发
    if (active.value?.id === conversationId) {
      messages.value = markMessageFailed(messages.value, clientMessageId);
    }
  }
}

/**
 * 失败气泡点击重发。
 *
 * 复用原 clientMessageId：服务端按幂等键去重，重发不会产生两条消息，
 * 也能接住“请求超时但实际已落库”的情况。
 */
async function resend(message: ViewChatMessage) {
  const conversationId = message.conversationId || active.value?.id;
  if (!conversationId || message.sendState !== 'failed') return;
  if (active.value?.id !== conversationId) return;
  if (message.messageType !== 'TEXT') return;
  const { clientMessageId } = message;
  // 立刻切到发送中，避免连点重复提交
  messages.value = messages.value.map((item): ViewChatMessage =>
    item.clientMessageId === clientMessageId
      ? { ...item, sendState: 'sending' }
      : item,
  );
  try {
    await deliverMessage(
      conversationId,
      clientMessageId,
      message.content || '',
    );
  } catch {
    if (active.value?.id === conversationId) {
      messages.value = markMessageFailed(messages.value, clientMessageId);
    }
  }
}

/**
 * 消费登记给私信页的待定位会话（铃铛私信提醒、通知回复跳转）。
 *
 * 先读后清：同一事件可能同时触发 watch 与激活钩子，第二次进来拿不到 id 即天然幂等。
 */
async function consumePendingConversation() {
  const conversationId = messageStore.pendingDirectConversationId;
  if (!conversationId) return;
  messageStore.clearPendingConversation();
  let target = conversations.value.find((item) => item.id === conversationId);
  if (!target) {
    // 可能是刚由通知回复创建的会话，本地列表还没拉到
    await loadConversations();
    target = conversations.value.find((item) => item.id === conversationId);
  }
  if (!target) {
    ElMessage.warning('未找到对应的私信会话，请稍后刷新重试');
    return;
  }
  await openConversation(target);
}

useConversationPush(refreshOnPush);

/** 切回标签页时补拉列表与当前会话，并消费可能新登记的待定位会话。 */
async function refreshOnReactivate() {
  try {
    await loadConversations();
    await consumePendingConversation();
    await refreshActiveConversation();
  } catch {
    // 请求层已统一提示，保留页面现有数据
  }
}

/**
 * keepAlive 缓存页回到前台时的静默刷新。
 *
 * onActivated 在首次挂载后也会触发一次（紧跟 onMounted），那次必须跳过，
 * 否则首进就会查两遍；页面未启用 keepAlive 时该钩子不会触发，天然无副作用。
 */
let activated = false;
onActivated(() => {
  if (!activated) {
    activated = true;
    return;
  }
  void refreshOnReactivate();
});

/**
 * 通知回复/铃铛私信提醒会登记待定位会话。
 *
 * 页面已挂载时由 watch 直接打开；页面尚未挂载时登记会留在 store 里，
 * 由 onMounted 消费，因此两个入口都能定位到会话。
 */
watch(
  () => messageStore.pendingDirectConversationId,
  (conversationId) => {
    if (!conversationId) return;
    void consumePendingConversation().catch(() => {
      // 请求层已提示；保留页面可用性，等下一次手动刷新
    });
  },
);

onMounted(async () => {
  try {
    await Promise.all([loadConversations(), searchStaff()]);
  } catch {
    // 请求层已统一提示；页面保持可用，后续操作会重试
  }
  // 待定位会话不依赖工作人员列表：上一步失败也要尝试打开（通知回复跳转依赖它）
  try {
    await consumePendingConversation();
  } catch {
    // 请求层已提示；保留页面可用性
  }
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
        @click="handleOpenConversation(item)"
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
            <time
              v-if="message.sendState === 'failed'"
              class="retry"
              role="button"
              tabindex="0"
              title="重新发送这条消息"
              @click="resend(message)"
              @keydown.enter.prevent="resend(message)"
            >
              发送失败，点击重发
            </time>
            <time v-else>{{
              message.sendState === 'sending'
                ? '发送中…'
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

/* 失败气泡的重发入口：可点击文字按钮，键盘/读屏都能操作 */
.direct-bubble time.retry {
  text-decoration: underline;
  cursor: pointer;
}

.direct-bubble time.retry:focus-visible {
  outline: 2px solid var(--direct-accent);
  outline-offset: 2px;
  border-radius: 3px;
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
