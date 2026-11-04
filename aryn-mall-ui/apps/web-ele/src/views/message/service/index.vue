<script setup lang="ts">
import type { ViewChatMessage } from '../message-state';
import type { ServiceTab } from './service-state';

import type { AgentInfo } from '#/api/message/agent';
import type { Conversation } from '#/api/message/types';
import type { PresenceStatus } from '#/store/agent-presence';
import type { MessagePushSignal } from '#/store/message';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { useAccess } from '@vben/access';
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
  assignConversation,
  claimConversation,
  closeConversation,
  getAdminConversations,
  getConversationMessages,
  getStaffConversations,
  getWaitingConversations,
  initiateCustomerConversation,
  markConversationRead,
  sendConversationMessage,
  transferConversation,
} from '#/api/message/conversation';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';
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
import AssignmentLogDrawer from './assignment-log-drawer.vue';
import ConversationInfoPanel from './conversation-info-panel.vue';
import {
  claimState,
  composerState,
  conversationActions,
  conversationDisplayName,
  conversationOwnerName,
  conversationQuery,
  conversationStatusLabel,
  conversationStatusTone,
  formatDateTime,
  isLatestOutgoingMessage,
  isSendEnter,
  latestOutgoingMessage,
  readReceipt,
  shouldMarkRead,
  visibleServiceTabs,
} from './service-state';

/** 列表单页条数：历史/主管页签用 cursor 加载更多。 */
const PAGE_SIZE = 20;

const PRESENCE_OPTIONS: Array<{ label: string; value: PresenceStatus }> = [
  { label: '在线', value: 'ONLINE' },
  { label: '忙碌', value: 'BUSY' },
  { label: '暂停', value: 'PAUSED' },
  { label: '离线', value: 'OFFLINE' },
];

const { hasAccessByCodes } = useAccess();
const userStore = useUserStore();
const messageStore = useMessageStore();
const presenceStore = useAgentPresenceStore();

const activeTab = ref<ServiceTab>('mine');
const conversations = ref<Conversation[]>([]);
const active = ref<Conversation>();
const messages = ref<ViewChatMessage[]>([]);
const draft = ref('');
const timeline = ref<HTMLElement>();
const agentConfigDialog = ref<InstanceType<typeof AgentConfigDialog>>();
const assignmentLogDrawer = ref<InstanceType<typeof AssignmentLogDrawer>>();

const listLoading = ref(false);
const messageLoading = ref(false);
const messageLoadFailed = ref(false);
const claiming = ref(false);
const nextCursor = ref<string>();
const hasMore = ref(false);

/** 列表/打开会话的请求代际：慢网连点时只认最后一次请求的结果。 */
let listGeneration = 0;
let openGeneration = 0;
/** 铃铛定位串行链：连续点击多个提醒时按顺序定位，避免列表请求互相覆盖。 */
let locateChain: Promise<void> = Promise.resolve();

const hasSupervisor = computed(() =>
  hasAccessByCodes(['message:service:supervisor']),
);
const tabs = computed(() => visibleServiceTabs(hasSupervisor.value));

const gate = computed(() => ({
  configured: presenceStore.configured,
  enabled: presenceStore.enabled,
  loaded: presenceStore.loaded,
  presence: presenceStore.presence,
}));

const actions = computed(() =>
  conversationActions({
    conversation: active.value,
    gate: gate.value,
    supervisor: hasSupervisor.value,
    supervisorView: activeTab.value === 'supervisor',
  }),
);

const composer = computed(() => composerState(active.value));

const title = computed(() => conversationDisplayName(active.value));
const ownerName = computed(() => conversationOwnerName(active.value));
const statusLabel = computed(() =>
  conversationStatusLabel(active.value?.status),
);
const statusTone = computed(() => conversationStatusTone(active.value?.status));

/** 主管视图打开他人负责的会话：提示先强制分配给自己再回复，避免发送被拒。 */
const isOtherOwner = computed(() => {
  if (activeTab.value !== 'supervisor' || !active.value) return false;
  const assignedStaffId = active.value.assignedStaffId;
  if (!assignedStaffId) return false;
  return String(assignedStaffId) !== String(userStore.userInfo?.userId ?? '');
});

/** 会话头下方的状态横幅：领取门禁提示 / 关闭态重开截止 / 主管他人在线会话提示。 */
const stateBanner = computed<
  undefined | { text: string; tone: 'info' | 'warning' }
>(() => {
  if (!active.value) return undefined;
  if (actions.value.canClaim && actions.value.claimHint) {
    return { text: actions.value.claimHint, tone: 'warning' };
  }
  if (active.value.status === 'CLOSED' && actions.value.reopenHint) {
    return { text: actions.value.reopenHint, tone: 'info' };
  }
  if (isOtherOwner.value) {
    return {
      text: `该会话由 ${ownerName.value} 负责；主管如需直接回复，请先「强制分配」给自己`,
      tone: 'info',
    };
  }
  return undefined;
});

/** 接待状态提示条：区分同步失败 / 未配置 / 已禁用 / 离线四种语义。 */
const presenceNotice = computed<
  | undefined
  | {
      action?: 'config' | 'online' | 'retry';
      text: string;
      tone: 'muted' | 'warning';
    }
>(() => {
  if (!presenceStore.loaded) return undefined;
  if (presenceStore.syncError) {
    return {
      action: 'retry',
      text: '接待状态同步失败，请检查网络后重试',
      tone: 'warning',
    };
  }
  if (!presenceStore.configured) {
    return {
      action: 'config',
      text: '尚未配置客服坐席，完成配置后即可上线接待',
      tone: 'warning',
    };
  }
  if (!presenceStore.enabled) {
    return {
      text: '客服坐席已禁用，请联系主管启用后再上线',
      tone: 'warning',
    };
  }
  if (presenceStore.presence === 'OFFLINE') {
    return {
      action: 'online',
      text: '离线仅暂停自动分配；已分配的会话仍可回复',
      tone: 'warning',
    };
  }
  if (presenceStore.presence === 'BUSY') {
    return {
      text: '忙碌：暂停新会话自动分配；已分配的会话仍可回复',
      tone: 'muted',
    };
  }
  if (presenceStore.presence === 'PAUSED') {
    return {
      text: '暂停：暂停新会话自动分配；已分配的会话仍可回复',
      tone: 'muted',
    };
  }
  return undefined;
});

/** 输入区左下角说明：离线不阻塞已分配会话回复。 */
const composerNote = computed(() => {
  if (!active.value) return '选择会话后可回复';
  if (active.value.status === 'WAITING')
    return '待领取会话只读预览，领取后可回复';
  if (active.value.status === 'CLOSED') return '会话已关闭，仅可查看历史消息';
  if (presenceStore.presence === 'OFFLINE')
    return '当前离线：已分配的会话仍可正常回复';
  return 'HTTP 可靠发送 · WebSocket 实时索引';
});

const latestOutgoing = computed(() => latestOutgoingMessage(messages.value));
const receipt = computed(() =>
  readReceipt(active.value?.peerLastReadSeq, latestOutgoing.value),
);
const receiptLabel = computed(() => {
  if (receipt.value === 'read') return '已读';
  if (receipt.value === 'unread') return '未读';
  return '';
});

/** 当前打开的会话是否仍然有效（防慢请求回来串会话）。 */
function isCurrentOpen(generation: number, conversationId: string) {
  return generation === openGeneration && active.value?.id === conversationId;
}

async function loadConversations(reset = true) {
  const generation = ++listGeneration;
  if (reset) {
    nextCursor.value = undefined;
    hasMore.value = false;
  }
  listLoading.value = true;
  try {
    if (activeTab.value === 'waiting') {
      const records = await getWaitingConversations({
        limit: 50,
        queueCode: 'DEFAULT',
      });
      if (generation !== listGeneration) return;
      conversations.value = reset
        ? records
        : [...conversations.value, ...records];
      hasMore.value = false;
    } else {
      const query = conversationQuery(activeTab.value);
      const cursor = reset ? undefined : nextCursor.value;
      const page =
        activeTab.value === 'supervisor'
          ? await getAdminConversations({ cursor, limit: PAGE_SIZE })
          : await getStaffConversations({
              cursor,
              limit: PAGE_SIZE,
              ...(query.status ? { status: query.status } : {}),
            });
      if (generation !== listGeneration) return;
      conversations.value = reset
        ? page.records
        : [...conversations.value, ...page.records];
      nextCursor.value = page.nextCursor;
      hasMore.value = page.hasMore === true && !!page.nextCursor;
    }
    // 列表刷新后同步当前会话的未读/状态快照（消息体仍以时间线为准）。
    if (active.value) {
      const fresh = conversations.value.find(
        (item) => item.id === active.value?.id,
      );
      if (fresh) active.value = fresh;
    }
  } catch {
    if (generation !== listGeneration) return;
    if (reset) conversations.value = [];
  } finally {
    if (generation === listGeneration) listLoading.value = false;
  }
}

async function loadMore() {
  if (listLoading.value || !hasMore.value) return;
  await loadConversations(false);
}

/**
 * 打开会话。
 *
 * 先清空旧消息再发请求；响应回来后同时校验请求代际与 active.id，
 * 慢网连点不会把 A 会话的消息渲染到 B 会话里。
 */
async function openConversation(conversation: Conversation) {
  const generation = ++openGeneration;
  active.value = conversation;
  messages.value = [];
  messageLoadFailed.value = false;
  messageLoading.value = true;
  try {
    const page = await getConversationMessages(conversation.id, {
      limit: 100,
    });
    if (!isCurrentOpen(generation, conversation.id)) return;
    messages.value = [...page.records].reverse();
    await markActiveRead(generation);
  } catch {
    if (!isCurrentOpen(generation, conversation.id)) return;
    messages.value = [];
    messageLoadFailed.value = true;
  } finally {
    if (isCurrentOpen(generation, conversation.id))
      messageLoading.value = false;
  }
  if (!isCurrentOpen(generation, conversation.id)) return;
  await nextTick();
  scrollToBottom('auto');
}

/** 推进当前会话已读游标：只有已分配的进行中会话才上报，避免越权。 */
async function markActiveRead(generation: number) {
  const conversation = active.value;
  if (!conversation || !shouldMarkRead(conversation)) return;
  const latest = Math.max(
    latestServerSequence(messages.value),
    conversation.lastSeq || 0,
  );
  if (latest <= (conversation.lastReadSeq || 0)) return;
  try {
    await markConversationRead(conversation.id, latest);
  } catch {
    // 已读上报失败不阻塞查看消息；铃铛会在下次推送/刷新时收敛。
    return;
  }
  if (!isCurrentOpen(generation, conversation.id)) return;
  const next = { ...conversation, lastReadSeq: latest, unreadCount: 0 };
  active.value = next;
  const index = conversations.value.findIndex((item) => item.id === next.id);
  if (index !== -1) {
    conversations.value[index] = {
      ...conversations.value[index]!,
      lastReadSeq: latest,
      unreadCount: 0,
    };
  }
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
  const generation = openGeneration;
  try {
    const afterSeq = latestServerSequence(messages.value);
    const page = await getConversationMessages(conversation.id, {
      afterSeq,
      limit: 100,
    });
    if (!isCurrentOpen(generation, conversation.id)) return;
    if (page.records.length > 0) {
      messages.value = mergeCursorMessages(messages.value, page.records);
      await nextTick();
      scrollToBottom('auto');
    }
    await markActiveRead(generation);
  } catch {
    // 实时刷新失败不影响页面可用性，等下一次推送或手动刷新。
  }
}

/**
 * 推送到达后刷新会话列表；只有当前会话有新消息时才补拉消息，
 * 避免为其他会话的变化白白拉一次当前会话。
 */
async function refreshOnPush(signal: MessagePushSignal) {
  await loadConversations(true);
  if (!signal.conversationId || signal.conversationId === active.value?.id) {
    await refreshActiveConversation();
  }
}

/** 输入法组合中的 Enter 是确认候选词，不能当发送；Shift+Enter 换行。 */
function onComposerKeydown(event: Event) {
  if (!(event instanceof KeyboardEvent)) return;
  if (!isSendEnter(event)) return;
  event.preventDefault();
  void send();
}

async function send() {
  const conversation = active.value;
  const content = draft.value.trim();
  if (!conversation || !content || messageLoading.value) return;
  if (!actions.value.canReply) return;
  const conversationId = conversation.id;
  const clientMessageId = nanoid();
  draft.value = '';
  messages.value.push({
    clientMessageId,
    content,
    conversationId,
    createTime: new Date().toISOString(),
    id: `local:${clientMessageId}`,
    messageType: 'TEXT',
    seqNo: Number.MAX_SAFE_INTEGER,
    senderId: 'current',
    senderName: '我',
    senderType: 'SYS_USER',
    sendState: 'sending',
  });
  await nextTick();
  scrollToBottom();
  try {
    const message = await sendConversationMessage(conversationId, {
      clientMessageId,
      content,
      messageType: 'TEXT',
    });
    // 响应回来时会话可能已切换：只有仍停留在原会话才合并到时间线，
    // 否则交给重新打开时的历史加载去合并。
    if (active.value?.id === conversationId) {
      messages.value = mergeServerMessage(messages.value, message);
      await nextTick();
      scrollToBottom();
    }
  } catch {
    // 请求层已统一弹出失败原因；气泡标记为「发送失败，点击重发」。
    if (active.value?.id === conversationId) {
      messages.value = markMessageFailed(messages.value, clientMessageId);
    }
  }
}

/** 点击失败气泡重发：复用原 clientMessageId，服务端幂等去重。 */
async function resend(message: ViewChatMessage) {
  const conversation = active.value;
  if (!conversation || message.sendState !== 'failed') return;
  if (!actions.value.canReply) return;
  const conversationId = conversation.id;
  messages.value = messages.value.map((item) =>
    item.clientMessageId === message.clientMessageId
      ? { ...item, sendState: 'sending' }
      : item,
  );
  try {
    const serverMessage = await sendConversationMessage(conversationId, {
      clientMessageId: message.clientMessageId,
      content: message.content,
      messageType: message.messageType,
      payload: message.payload,
    });
    if (active.value?.id === conversationId) {
      messages.value = mergeServerMessage(messages.value, serverMessage);
      await nextTick();
      scrollToBottom();
    }
  } catch {
    // 请求层已统一提示；失败气泡继续保留，可再次点击重发。
    if (active.value?.id === conversationId) {
      messages.value = markMessageFailed(
        messages.value,
        message.clientMessageId,
      );
    }
  }
}

async function claim() {
  const conversation = active.value;
  if (!conversation || claiming.value) return;
  const state = claimState(conversation, gate.value);
  if (state.disabled) {
    ElMessage.warning(state.hint || '当前无法领取该会话');
    return;
  }
  claiming.value = true;
  try {
    await claimConversation(conversation.id);
    ElMessage.success('会话已领取');
    activeTab.value = 'mine';
    await loadConversations(true);
    // 列表可能因后端最终一致暂未包含刚领取的会话：本地先按「已分配」渲染，
    // 避免领取成功后仍停留在待领取只读态。
    const fresh = conversations.value.find(
      (item) => item.id === conversation.id,
    ) ?? {
      ...conversation,
      assignedStaffId:
        String(userStore.userInfo?.userId ?? '') ||
        conversation.assignedStaffId,
      status: 'ASSIGNED' as const,
    };
    await openConversation(fresh);
    void messageStore.refreshNotifications();
  } catch {
    // 请求层已统一提示（离线/未启用/已被他人领取等业务错误）。
  } finally {
    claiming.value = false;
  }
}

/** 转交会话：仅已分配会话可用，目标坐席需在线（后端校验）。 */
async function transfer() {
  const conversation = active.value;
  if (!conversation || !actions.value.canTransfer) return;
  let targetStaffId: string;
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入目标客服工作人员 ID（目标坐席需在线）',
      '转交会话',
      {
        confirmButtonText: '转交',
        inputErrorMessage: '目标客服 ID 不能为空',
        inputPattern: /^\S+$/,
      },
    );
    targetStaffId = String(value).trim();
  } catch {
    return;
  }
  try {
    await transferConversation(
      conversation.id,
      targetStaffId,
      '客服工作台转交',
    );
    ElMessage.success('会话已转交');
    active.value = undefined;
    messages.value = [];
    await loadConversations(true);
    void messageStore.refreshNotifications();
  } catch {
    // 请求层已提示失败原因（目标离线/权限不足/会话状态变化等）。
  }
}

async function close() {
  const conversation = active.value;
  if (!conversation || !actions.value.canClose) return;
  const confirmed = await ElMessageBox.confirm(
    '关闭后会员可在 24 小时内重开该会话，确认关闭？',
    '关闭会话',
    {
      confirmButtonText: '关闭会话',
      cancelButtonText: '取消',
      type: 'warning',
    },
  )
    .then(() => true)
    .catch(() => false);
  if (!confirmed) return;
  try {
    await closeConversation(conversation.id, '客服已完成处理');
    ElMessage.success('会话已关闭');
    active.value = undefined;
    messages.value = [];
    await loadConversations(true);
    void messageStore.refreshNotifications();
  } catch {
    // 请求层已提示失败原因。
  }
}

/** 主管强制分配：不受目标坐席在线限制，后端写 FORCE_ASSIGN 审计。 */
async function forceAssign() {
  const conversation = active.value;
  if (!conversation || !actions.value.canForceAssign) return;
  let targetStaffId: string;
  try {
    const { value } = await ElMessageBox.prompt(
      '主管强制分配不受目标坐席在线限制，操作会记入分配日志。请输入目标客服工作人员 ID',
      '强制分配会话',
      {
        confirmButtonText: '强制分配',
        inputErrorMessage: '目标客服 ID 不能为空',
        inputPattern: /^\S+$/,
      },
    );
    targetStaffId = String(value).trim();
  } catch {
    return;
  }
  try {
    await assignConversation(conversation.id, targetStaffId, '主管强制分配');
    ElMessage.success('会话已强制分配');
    await loadConversations(true);
    const fresh = conversations.value.find(
      (item) => item.id === conversation.id,
    );
    if (fresh) active.value = fresh;
    void messageStore.refreshNotifications();
  } catch {
    // 请求层已提示失败原因。
  }
}

function openAssignmentLogs() {
  if (!active.value) return;
  assignmentLogDrawer.value?.open(active.value.id);
}

/** 主动联系会员：输入会员 ID → 发起会话 → 直接打开。 */
async function initiateConversation() {
  let customerId: string;
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入要联系的会员 ID',
      '主动联系会员',
      {
        confirmButtonText: '发起会话',
        inputErrorMessage: '会员 ID 不能为空',
        inputPattern: /^\S+$/,
      },
    );
    customerId = String(value).trim();
  } catch {
    return;
  }
  try {
    const conversation = await initiateCustomerConversation(customerId);
    ElMessage.success('已发起会话');
    activeTab.value = 'mine';
    await loadConversations(true);
    const fresh =
      conversations.value.find((item) => item.id === conversation.id) ??
      conversation;
    await openConversation(fresh);
    void messageStore.refreshNotifications();
  } catch {
    // 请求层已提示（会员不存在/无权限等）。
  }
}

async function handleTabChange(tab: ServiceTab) {
  activeTab.value = tab;
  await loadConversations(true);
}

/**
 * 铃铛定位：依次在我的会话/待领取/历史会话（主管再加全部会话）中查找。
 * 找到后切换到对应页签并打开；找不到给出明确提示，不能静默失败。
 */
async function locateConversation(conversationId: string) {
  const order: ServiceTab[] = ['mine', 'waiting', 'history'];
  if (hasSupervisor.value) order.push('supervisor');
  try {
    for (const tab of order) {
      activeTab.value = tab;
      await loadConversations(true);
      const found = conversations.value.find(
        (item) => item.id === conversationId,
      );
      if (found) {
        await openConversation(found);
        return;
      }
    }
    ElMessage.warning('未找到该会话，可能已被其他客服领取或已经关闭');
  } catch {
    // 请求层已提示错误。
  }
}

function requestLocate(conversationId: string) {
  locateChain = locateChain.then(
    () => locateConversation(conversationId),
    () => locateConversation(conversationId),
  );
  return locateChain;
}

/** 消费铃铛登记的待定位会话；watch 与 onMounted 双通道都走这里。 */
function consumePendingConversation(preferredId?: string) {
  const conversationId = preferredId ?? messageStore.pendingConversationId;
  if (!conversationId) return;
  messageStore.clearPendingConversation();
  void requestLocate(conversationId);
}

/** 下拉切换接待状态；失败时 store 已回滚到服务端确认值，错误由请求层提示。 */
async function changePresence(value: PresenceStatus) {
  try {
    await presenceStore.changePresence(value);
  } catch {
    // 已由请求层提示，这里无需重复处理。
  }
}

function handlePresenceChange(value: PresenceStatus) {
  void changePresence(value);
}

/** 离线提示条的一键上线。 */
function goOnline() {
  void changePresence('ONLINE');
}

function retryPresence() {
  void presenceStore.refresh();
}

function handleAgentSaved(agent: AgentInfo) {
  if (String(agent.staffId) !== String(userStore.userInfo?.userId ?? '')) {
    // 主管可能给其他工作人员保存配置：只刷新列表/状态，不覆盖当前坐席本地状态。
    void loadConversations(true);
    return;
  }
  // 首次配置坐席时本地还没有心跳循环，先补激活再同步状态。
  presenceStore.activate();
  presenceStore.applyAgent(agent);
}

/** 结构化消息（图片/业务卡片）交给卡片组件，这里只兜底文本与系统消息。 */
function displayMessage(message: ViewChatMessage) {
  if (message.messageType === 'TEXT') return message.content;
  return message.content || '[系统消息]';
}

function metaText(message: ViewChatMessage) {
  if (message.sendState === 'sending') return '发送中…';
  if (message.sendState === 'failed') return '发送失败';
  const time = formatDateTime(message.createTime) || message.createTime;
  return `#${message.seqNo} · ${time}`;
}

watch(
  () => messageStore.pendingConversationId,
  (conversationId) => {
    if (!conversationId) return;
    // 已经打开同一个会话时不必重新定位（内容会由推送/激活刷新补拉）。
    if (active.value?.id === conversationId) {
      messageStore.clearPendingConversation();
      void refreshActiveConversation();
      return;
    }
    consumePendingConversation(conversationId);
  },
);

useConversationPush(refreshOnPush);

useKeepAliveRefresh(() => {
  // 切回工作台：静默对齐坐席状态、列表与当前会话，避免停留期间状态过期。
  void presenceStore.refresh();
  void (async () => {
    await loadConversations(true);
    if (active.value) await refreshActiveConversation();
  })();
});

onMounted(async () => {
  // 接待状态与心跳由应用级 store 托管，页面卸载（去查订单等）不再掉线。
  presenceStore.activate();
  await loadConversations(true);
  consumePendingConversation();
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
        <div class="brand-actions">
          <ElButton
            v-access:code="'message:conversation:initiate'"
            class="ghost-trigger"
            size="small"
            @click="initiateConversation"
          >
            主动联系
          </ElButton>
          <ElButton
            v-access:code="'message:service:agent'"
            class="config-trigger"
            size="small"
            @click="agentConfigDialog?.open()"
          >
            坐席设置
          </ElButton>
        </div>
      </div>

      <div class="presence-row">
        <span>接待状态</span>
        <ElSelect
          class="presence-select"
          size="small"
          :disabled="!presenceStore.configured || !presenceStore.enabled"
          :loading="presenceStore.syncing"
          :model-value="presenceStore.presence"
          @update:model-value="handlePresenceChange"
        >
          <ElOption
            v-for="option in PRESENCE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </ElSelect>
      </div>

      <div
        v-if="presenceNotice"
        class="presence-tip"
        :class="presenceNotice.tone"
      >
        <span>{{ presenceNotice.text }}</span>
        <ElButton
          v-if="presenceNotice.action === 'online'"
          size="small"
          type="primary"
          @click="goOnline"
        >
          一键上线
        </ElButton>
        <ElButton
          v-else-if="presenceNotice.action === 'retry'"
          size="small"
          @click="retryPresence"
        >
          重试
        </ElButton>
        <ElButton
          v-else-if="presenceNotice.action === 'config'"
          v-access:code="'message:service:agent'"
          size="small"
          @click="agentConfigDialog?.open()"
        >
          去配置
        </ElButton>
      </div>

      <div
        class="segment"
        :style="{ gridTemplateColumns: `repeat(${tabs.length}, 1fr)` }"
      >
        <button
          v-for="tab in tabs"
          :key="tab.key"
          :class="{ active: activeTab === tab.key }"
          @click="handleTabChange(tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>

      <div v-loading="listLoading" class="conversation-list">
        <ElEmpty
          v-if="!listLoading && conversations.length === 0"
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
          <span class="copy">
            <strong>{{ conversationDisplayName(item) }}</strong>
            <small>{{ item.lastMessageSummary || '等待新消息' }}</small>
          </span>
          <span class="conversation-badges">
            <ElTag v-if="item.unreadCount" round size="small" type="danger">
              {{ item.unreadCount }}
            </ElTag>
            <ElTag
              v-else-if="item.status === 'WAITING'"
              effect="plain"
              size="small"
              type="warning"
            >
              待领取
            </ElTag>
            <ElTag
              v-else-if="item.status === 'CLOSED'"
              effect="plain"
              size="small"
              type="info"
            >
              已关闭
            </ElTag>
          </span>
        </button>
        <ElButton
          v-if="hasMore && !listLoading"
          class="load-more"
          size="small"
          @click="loadMore"
        >
          加载更多
        </ElButton>
      </div>
    </aside>

    <main v-if="active" class="chat-stage">
      <header>
        <div class="conversation-heading">
          <p>
            {{ statusLabel }} · {{ active.queueCode || 'DEFAULT' }}
            <template
              v-if="
                active.conversationType === 'CUSTOMER_SERVICE' &&
                active.customerId
              "
            >
              · 会员 ID {{ active.customerId }}
            </template>
          </p>
          <h1>{{ title }}</h1>
          <small class="owner-line">负责客服：{{ ownerName }}</small>
        </div>
        <div class="actions">
          <ElTag :type="statusTone" effect="plain" round>
            {{ statusLabel }}
          </ElTag>
          <ElButton
            v-if="actions.canClaim"
            type="primary"
            :disabled="actions.claimDisabled"
            :loading="claiming"
            @click="claim"
          >
            领取
          </ElButton>
          <ElButton v-if="actions.canForceAssign" @click="forceAssign">
            强制分配
          </ElButton>
          <ElButton v-if="actions.canTransfer" @click="transfer">转交</ElButton>
          <ElButton v-if="actions.canClose" type="danger" plain @click="close">
            关闭
          </ElButton>
          <ElButton
            v-if="actions.canOpenAssignmentLogs"
            @click="openAssignmentLogs"
          >
            分配日志
          </ElButton>
        </div>
      </header>

      <div v-if="stateBanner" class="state-banner" :class="stateBanner.tone">
        {{ stateBanner.text }}
      </div>

      <div class="chat-body">
        <section class="chat-main">
          <div ref="timeline" v-loading="messageLoading" class="timeline">
            <ElEmpty
              v-if="!messageLoading && messages.length === 0"
              class="timeline-empty"
              :description="
                messageLoadFailed
                  ? '消息加载失败，请重新打开会话'
                  : '还没有消息'
              "
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
                <div class="bubble-meta">
                  <time>{{ metaText(message) }}</time>
                  <button
                    v-if="message.sendState === 'failed'"
                    class="retry-link"
                    type="button"
                    @click="resend(message)"
                  >
                    发送失败，点击重发
                  </button>
                  <em
                    v-else-if="
                      isLatestOutgoingMessage(messages, message) && receiptLabel
                    "
                    class="receipt"
                    :class="receipt"
                  >
                    {{ receiptLabel }}
                  </em>
                </div>
              </div>
            </div>
          </div>

          <footer>
            <div v-if="composer.hint" class="composer-hint">
              {{ composer.hint }}
            </div>
            <ElInput
              v-model="draft"
              type="textarea"
              :rows="3"
              resize="none"
              :disabled="composer.disabled"
              :placeholder="composer.placeholder"
              @keydown="onComposerKeydown"
            />
            <div class="send-row">
              <span>{{ composerNote }}</span>
              <ElButton
                type="primary"
                :disabled="composer.disabled || !draft.trim() || messageLoading"
                @click="send"
              >
                发送回复
              </ElButton>
            </div>
          </footer>
        </section>

        <ConversationInfoPanel :conversation="active" :messages="messages" />
      </div>
    </main>

    <section v-else class="empty-stage">
      <ElEmpty description="选择一个会话开始处理" />
    </section>

    <AgentConfigDialog ref="agentConfigDialog" @saved="handleAgentSaved" />
    <AssignmentLogDrawer ref="assignmentLogDrawer" />
  </div>
</template>

<style scoped>
/* 窄屏优先保证会话区可用：信息面板让位给消息时间线 */
@media (max-width: 1280px) {
  .chat-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .chat-body :deep(.info-panel) {
    display: none;
  }
}

@media (max-width: 900px) {
  .service-desk {
    grid-template-columns: 280px minmax(0, 1fr);
  }

  .chat-stage .actions {
    gap: 4px;
  }
}

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
  grid-template-columns: 330px minmax(0, 1fr);
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
  padding: 20px 20px 16px;
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

.brand-actions {
  display: flex;
  flex: none;
  gap: 6px;
  align-items: center;
}

.config-trigger,
.ghost-trigger {
  flex: none;
  color: var(--desk-accent-strong);
  background: var(--desk-accent-soft);
  border-color: var(--desk-accent-line);
}

.ghost-trigger {
  color: var(--desk-muted);
  background: transparent;
  border-color: var(--desk-line);
}

.config-trigger:hover,
.ghost-trigger:hover {
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

.presence-select {
  width: 118px;
}

.presence-tip {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding: 9px 12px;
  margin: 0 16px 12px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--desk-warning);
  background: var(--desk-warning-soft);
  border: 1px solid var(--desk-warning-line);
  border-radius: 10px;
}

.presence-tip.muted {
  color: var(--desk-muted);
  background: var(--desk-hover);
  border-color: var(--desk-line-soft);
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
  padding: 9px 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--desk-segment-text);
  white-space: nowrap;
  border-radius: 8px;
}

.segment button.active {
  color: var(--desk-segment-active-text);
  background: var(--desk-segment-active-bg);
}

.conversation-list {
  flex: 1;
  min-height: 0;
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

.conversation-badges {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: 4px;
  align-items: flex-end;
}

.load-more {
  width: 100%;
  margin-top: 8px;
  color: var(--desk-muted);
  background: transparent;
  border-color: var(--desk-line);
}

.chat-stage {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background:
    radial-gradient(circle at 100% 0, var(--desk-glow), transparent 30%),
    var(--desk-canvas);
}

.chat-stage header {
  display: flex;
  flex: none;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px;
  border-bottom: 1px solid var(--desk-line-faint);
}

.conversation-heading {
  min-width: 0;
}

.chat-stage header p {
  font-size: 10px;
  color: var(--desk-accent);
  letter-spacing: 0.16em;
}

.chat-stage h1 {
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 18px;
  white-space: nowrap;
}

.owner-line {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  color: var(--desk-dim);
}

.chat-stage .actions {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
}

.state-banner {
  flex: none;
  padding: 9px 24px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--desk-warning);
  background: var(--desk-warning-soft);
  border-bottom: 1px solid var(--desk-warning-line);
}

.state-banner.info {
  color: var(--desk-muted);
  background: var(--desk-hover);
  border-bottom-color: var(--desk-line-soft);
}

.chat-body {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(0, 1fr) 296px;
  min-height: 0;
}

.chat-main {
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  min-width: 0;
  min-height: 0;
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

.bubble-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  font-size: 9px;
  color: var(--desk-faint);
}

.bubble-meta time {
  font-size: 9px;
}

.retry-link {
  padding: 0;
  font-size: 10px;
  color: var(--desk-warning);
  text-decoration: underline;
}

.receipt {
  font-size: 10px;
  font-style: normal;
  color: var(--desk-warning);
}

.receipt.read {
  color: var(--desk-accent-strong);
}

.chat-stage footer {
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
