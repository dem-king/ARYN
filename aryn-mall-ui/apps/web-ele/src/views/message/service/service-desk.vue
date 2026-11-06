<script setup lang="ts">
import type { ViewChatMessage } from '../message-state';
import type { ConversationFilterState, ServiceTab } from './service-state';

import type { Conversation } from '#/api/message/types';
import type { MessagePushSignal } from '#/store/message';

import {
  computed,
  onActivated,
  onBeforeUnmount,
  onDeactivated,
  onMounted,
  ref,
  watch,
} from 'vue';
import { useRoute } from 'vue-router';

import { useAccess } from '@vben/access';
import { useUserStore } from '@vben/stores';

import { useElementSize } from '@vueuse/core';
import {
  ElButton,
  ElDrawer,
  ElEmpty,
  ElMessage,
  ElMessageBox,
} from 'element-plus';

import { getAgentRoster } from '#/api/message/agent';
import {
  assignConversation,
  claimConversation,
  closeConversation,
  initiateCustomerConversation,
  transferConversation,
} from '#/api/message/conversation';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';
import { useMessageStore } from '#/store/message';
import {
  broadcastServiceDeskChanged,
  buildServiceDeskUrl,
  focusServiceDeskWindow,
  openServiceDeskWindow,
  reclaimServiceDeskWindow,
  subscribeServiceDesk,
  useServiceDeskState,
} from '#/store/service-window';

import { useConversationPush } from '../use-conversation-push';
import AgentConfigDialog from './agent-config-dialog.vue';
import AssignmentLogDrawer from './assignment-log-drawer.vue';
import ChatHeader from './chat-header.vue';
import Composer from './composer.vue';
import ConversationInfoPanel from './conversation-info-panel.vue';
import ConversationRail from './conversation-rail.vue';
import DeskNotice from './desk-notice.vue';
import MessageTimeline from './message-timeline.vue';
import QuickReplyDialog from './quick-reply-dialog.vue';
import {
  claimState,
  conversationActions,
  conversationOwnerName,
} from './service-state';
import ShortcutSheet from './shortcut-sheet.vue';
import TransferDialog from './transfer-dialog.vue';
import { useDeskConversations } from './use-desk-conversations';
import { useDeskMessages } from './use-desk-messages';
import { useDeskPresence } from './use-desk-presence';
import { useDeskQueueStats } from './use-desk-queue-stats';
import { useDeskShortcuts } from './use-desk-shortcuts';

import './desk-tokens.css';

/**
 * 工作台形态。
 *
 * <p>`embedded` = 主窗口内容区（后端菜单页）；`window` = 独立浏览器窗口。
 * 两种形态共用同一份实现，只在外壳/高度/独立窗口入口上有差异。</p>
 */
const props = withDefaults(defineProps<{ mode?: 'embedded' | 'window' }>(), {
  mode: 'embedded',
});

const isWindowMode = computed(() => props.mode === 'window');
const route = useRoute();
const { deskAlive, deskConversationId } = useServiceDeskState();
const { hasAccessByCodes } = useAccess();
const userStore = useUserStore();
const messageStore = useMessageStore();

const claiming = ref(false);
const agentConfigDialog = ref<InstanceType<typeof AgentConfigDialog>>();
const assignmentLogDrawer = ref<InstanceType<typeof AssignmentLogDrawer>>();
const transferDialog = ref<InstanceType<typeof TransferDialog>>();
/** 转交弹窗确认时使用的目标会话（防止弹窗期间切换会话串目标）。 */
const transferTarget = ref<
  undefined | { id: string; mode: 'assign' | 'transfer' }
>();
const quickReplyOpen = ref(false);
const helpOpen = ref(false);
const railRef = ref<InstanceType<typeof ConversationRail>>();
const deskRoot = ref<HTMLElement>();
const { width: deskWidth } = useElementSize(deskRoot);
/** 容器宽度驱动布局：宽屏三栏、中等两栏、窄屏单栏（不依赖视口，主窗口/独立窗口都适用）。 */
const layoutMode = computed<'compact' | 'narrow' | 'wide'>(() => {
  const width = deskWidth.value || 1440;
  if (width >= 1440) return 'wide';
  if (width >= 1100) return 'compact';
  return 'narrow';
});
const showContextPanel = computed(() => layoutMode.value === 'wide');
const contextDrawerOpen = ref(false);

/** 铃铛定位串行链：连续点击多个提醒时按顺序定位，避免列表请求互相覆盖。 */
let locateChain: Promise<void> = Promise.resolve();

const hasSupervisor = computed(() =>
  hasAccessByCodes(['message:service:supervisor']),
);

/** 当前打开的会话（消息域与操作区共用同一引用）。 */
const active = ref<Conversation>();

/** 队列统计：有 agent/supervisor 权限才拉取（契约 §5），失败静默不阻塞列表。 */
const canReadQueueStats = computed(() =>
  hasAccessByCodes(['message:service:agent', 'message:service:supervisor']),
);
const { queueStats, queueStatsDegraded, refreshQueueStats } = useDeskQueueStats(
  {
    enabled: canReadQueueStats,
  },
);

/**
 * 会话列表（页签 / 游标分页 / 服务端搜索 / 精细筛选）抽到 composable，
 * 页面只做编排；每次列表加载成功后顺带节流刷新队列统计。
 */
const {
  activeTab,
  clearFilters,
  conversationKeyword,
  conversations,
  filteredConversations,
  filterTags,
  filters,
  handleTabChange,
  hasActiveFilters,
  hasActiveQuery,
  hasMore,
  listFailed,
  listLoading,
  loadConversations,
  loadMore,
  resetQuery,
  tabs,
  updateFilters,
} = useDeskConversations({
  active,
  hasSupervisor,
  onLoaded: () => void refreshQueueStats(),
});

/** 精细筛选「负责人」候选：仅主管展示，首次打开筛选弹层时懒加载 roster。 */
const assigneeOptions = ref<Array<{ label: string; value: string }>>([]);
const assigneeLoading = ref(false);
let assigneeLoaded = false;

async function ensureAssigneeOptions() {
  if (!hasSupervisor.value || assigneeLoaded || assigneeLoading.value) return;
  assigneeLoading.value = true;
  try {
    const roster = (await getAgentRoster()) ?? [];
    assigneeOptions.value = roster.map((item) => ({
      label: item.staffName || item.staffId,
      value: item.staffId,
    }));
    assigneeLoaded = true;
  } catch {
    // 筛选是增强能力：失败静默，保留空候选（仍可用其它筛选条件）
  } finally {
    assigneeLoading.value = false;
  }
}

function onUpdateFilter(patch: Partial<ConversationFilterState>) {
  updateFilters(patch);
}

/** 页签计数：我的=未读会话数，待领取=共享池等待数（attention 口径；历史/全部无后端计数）。 */
const tabCounts = computed<Record<ServiceTab, number>>(() => ({
  history: 0,
  mine: messageStore.attentionUnreadConversations,
  supervisor: 0,
  waiting: messageStore.attentionWaitingTotal,
}));

/** 接待状态域（门禁/提示/容量/切换）抽到 composable。 */
const {
  PRESENCE_OPTIONS,
  composerNote,
  gate,
  goOnline,
  handleAgentSaved,
  handlePresenceChange,
  presenceCapacity,
  presenceNotice,
  presenceStore,
  retryPresence,
} = useDeskPresence({
  active,
  currentUserId: () => String(userStore.userInfo?.userId ?? ''),
  reloadConversations: () => loadConversations(true),
});

const actions = computed(() =>
  conversationActions({
    conversation: active.value,
    gate: gate.value,
    supervisor: hasSupervisor.value,
    supervisorView: activeTab.value === 'supervisor',
  }),
);

/** 消息域（打开会话/时间线/上翻/滚动跟随/发送/引用/撤回/图片/已读）抽到 composable。 */
const {
  clearActive,
  clearQuote,
  composer,
  draft,
  hasOlder,
  loadOlder,
  messages,
  messageLoadFailed,
  messageLoading,
  onComposerKeydown,
  onTimelineScroll,
  openConversation,
  olderLoading,
  quote,
  receipt,
  receiptLabel,
  recallClock,
  recallMessage,
  refreshActiveConversation,
  resend,
  scrollToBottom,
  send,
  setQuote,
  stickToBottom,
  timeline,
  timelineItems,
  unreadWhileAway,
  uploadingImage,
  uploadAndSendImage,
} = useDeskMessages({
  active,
  canReply: () => actions.value.canReply,
  isWindowMode,
  messageStore,
  userId: computed(() => String(userStore.userInfo?.userId ?? '')),
});

/** 时间线滚动容器由子组件持有：把子组件暴露的 scroller 接到消息域的 timeline ref。 */
const timelineRef = ref<InstanceType<typeof MessageTimeline>>();
watch(
  () => timelineRef.value?.scroller,
  (scroller) => {
    timeline.value = scroller;
  },
  { flush: 'post' },
);

const ownerName = computed(() => conversationOwnerName(active.value));
/** 窄屏单栏且已打开会话：侧栏让位给消息区（布局由页面推导，侧栏只负责隐藏）。 */
const railHidden = computed(
  () => layoutMode.value === 'narrow' && Boolean(active.value),
);

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

/**
 * 主区单条上下文提示。
 *
 * 旧实现里「会话状态横幅」「实时连接提示」「presence 提示」可能同时出现，
 * 主区被多条横幅挤压；现在按优先级合并成一条：会话状态 > 连接状态。
 */
const deskNotice = computed<
  undefined | { text: string; tone: 'info' | 'warning' }
>(() => {
  if (stateBanner.value) return stateBanner.value;
  if (active.value && messageStore.socketState !== 'open') {
    return {
      text: '实时连接未就绪，消息仍可通过 HTTP 发送，重连后自动恢复',
      tone: 'info',
    };
  }
  return undefined;
});

/** 接待状态提示条：区分同步失败 / 未配置 / 已禁用 / 离线四种语义。 */
/**
 * 推送到达后刷新会话列表；只有当前会话有新消息时才补拉消息，
 * 避免为其他会话的变化白白拉一次当前会话。
 */
async function refreshOnPush(signal: MessagePushSignal) {
  await loadConversations(true);
  if (!signal.conversationId || signal.conversationId === active.value?.id) {
    // 带上 messageId：撤回复用 CONVERSATION_MESSAGE 推送（契约 §16.4），
    // 消息域据此识别「已知消息被变更」并拉最新快照覆盖本地撤回态。
    await refreshActiveConversation(signal.messageId);
  }
}

/**
 * 撤回消息（契约 §16）：二次确认与接口调用在消息域，成功后刷新列表摘要
 * （后端会把最后一条被撤回消息的摘要改为「[消息已撤回]」）。
 */
async function handleRecall(message: ViewChatMessage) {
  const recalled = await recallMessage(message);
  if (!recalled) return;
  await loadConversations(true);
  void messageStore.refreshNotifications();
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
    broadcastServiceDeskChanged(conversation.id, 'claimed');
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

/** 转交会话：可搜索在线客服选择器；无坐席配置权限时降级为手输 ID。 */
async function transfer() {
  const conversation = active.value;
  if (!conversation || !actions.value.canTransfer) return;
  openTransferPicker('transfer', conversation.id);
}

/** 主管强制分配：不受目标坐席在线限制，后端写 FORCE_ASSIGN 审计。 */
async function forceAssign() {
  const conversation = active.value;
  if (!conversation || !actions.value.canForceAssign) return;
  openTransferPicker('assign', conversation.id);
}

/**
 * 打开转交/强制分配选择器。
 *
 * <p>roster（`/message/staff/agent/roster`）权限是
 * `message:service:agent` OR `message:service:supervisor`（契约 §6），
 * 满足其一即可用带在线状态的选择器；都没有权限时退回手输 ID 并说明原因，
 * 不能出现「点了转交却不知道该填谁」的空白交互。</p>
 */
function openTransferPicker(
  mode: 'assign' | 'transfer',
  conversationId: string,
) {
  if (
    hasAccessByCodes(['message:service:agent', 'message:service:supervisor'])
  ) {
    transferTarget.value = { id: conversationId, mode };
    transferDialog.value?.open({
      mode,
      title: mode === 'transfer' ? '转交会话' : '强制分配会话',
      confirmText: mode === 'transfer' ? '确认转交' : '强制分配',
      hint:
        mode === 'transfer'
          ? '普通转交要求目标坐席在线且可接；离线坐席请由主管使用「强制分配」。'
          : '强制分配不受目标坐席在线限制，操作会记入分配日志。',
    });
    return;
  }
  void promptTargetStaffId(mode, conversationId);
}

async function promptTargetStaffId(
  mode: 'assign' | 'transfer',
  conversationId: string,
) {
  let targetStaffId: string;
  try {
    const { value } = await ElMessageBox.prompt(
      mode === 'transfer'
        ? '当前账号没有「客服坐席配置」权限，无法读取坐席列表。请输入目标客服工作人员 ID（目标坐席需在线）'
        : '当前账号没有「客服坐席配置」权限，无法读取坐席列表。请输入目标客服工作人员 ID（强制分配不限在线）',
      mode === 'transfer' ? '转交会话' : '强制分配会话',
      {
        confirmButtonText: mode === 'transfer' ? '转交' : '强制分配',
        inputErrorMessage: '目标客服 ID 不能为空',
        inputPattern: /^\S+$/,
      },
    );
    targetStaffId = String(value).trim();
  } catch {
    return;
  }
  await (mode === 'transfer'
    ? submitTransfer(conversationId, targetStaffId)
    : submitForceAssign(conversationId, targetStaffId));
}

function onTransferConfirm(targetStaffId: string) {
  const target = transferTarget.value;
  transferTarget.value = undefined;
  if (!target) return;
  if (target.mode === 'transfer') {
    void submitTransfer(target.id, targetStaffId);
  } else {
    void submitForceAssign(target.id, targetStaffId);
  }
}

async function submitTransfer(conversationId: string, targetStaffId: string) {
  try {
    await transferConversation(conversationId, targetStaffId, '客服工作台转交');
    ElMessage.success('会话已转交');
    broadcastServiceDeskChanged(conversationId, 'transferred');
    if (active.value?.id === conversationId) {
      clearActive();
    }
    await loadConversations(true);
    void messageStore.refreshNotifications();
  } catch {
    // 请求层已提示失败原因（目标离线/权限不足/会话状态变化等）。
  }
}

/** 主管强制分配：不受目标坐席在线限制，后端写 FORCE_ASSIGN 审计。 */
async function submitForceAssign(
  conversationId: string,
  targetStaffId: string,
) {
  try {
    await assignConversation(conversationId, targetStaffId, '主管强制分配');
    ElMessage.success('会话已强制分配');
    broadcastServiceDeskChanged(conversationId, 'assigned');
    await loadConversations(true);
    const fresh = conversations.value.find(
      (item) => item.id === conversationId,
    );
    if (fresh) active.value = fresh;
    void messageStore.refreshNotifications();
  } catch {
    // 请求层已提示失败原因。
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
    broadcastServiceDeskChanged(conversation.id, 'closed');
    clearActive();
    await loadConversations(true);
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
    broadcastServiceDeskChanged(conversation.id, 'initiated');
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

/** 打开独立窗口；被浏览器拦截时给可点链接降级（不能静默失败）。 */
function openInWindow() {
  const result = openServiceDeskWindow(active.value?.id);
  if (result !== 'blocked') return;
  const url = buildServiceDeskUrl({ conversationId: active.value?.id });
  ElMessageBox.alert(
    `<p>浏览器拦截了弹窗。请允许本站弹出窗口后重试，或</p>` +
      `<p><a href="${url}" target="_blank" rel="noopener noreferrer">点击这里在新窗口打开客服工作台</a></p>`,
    '无法打开独立窗口',
    {
      confirmButtonText: '知道了',
      dangerouslyUseHTMLString: true,
    },
  ).catch(() => undefined);
}

function focusDesk() {
  if (!focusServiceDeskWindow()) {
    ElMessage.warning('独立窗口已关闭，可重新点击「独立窗口」打开');
  }
}

function reclaimDesk() {
  reclaimServiceDeskWindow();
  ElMessage.success('已收回工作台');
}

/**
 * 跨窗协同：对端已读/会话变更后收敛本页；独立窗口中收到主窗口转发的
 * 铃铛定位消息时打开对应会话（复用既有串行定位链，避免并发请求互相覆盖）。
 */
const stopServiceDeskSync = subscribeServiceDesk((message) => {
  if (message.type === 'read') {
    void messageStore.refreshNotifications();
    return;
  }
  if (message.type === 'changed') {
    void loadConversations(true);
    if (message.conversationId && active.value?.id === message.conversationId) {
      void refreshActiveConversation();
    }
    return;
  }
  if (
    message.type === 'locate' &&
    isWindowMode.value &&
    message.conversationId
  ) {
    void requestLocate(message.conversationId);
  }
});
onBeforeUnmount(stopServiceDeskSync);
onBeforeUnmount(() => {
  window.removeEventListener(
    'aryn:open-conversation',
    handleOpenConversationEvent,
  );
});

/** 快捷回复插入：追加到草稿末尾，保留坐席已输入的内容。 */
function applyQuickReply(content: string) {
  const current = draft.value.trimEnd();
  draft.value = current ? `${current}\n${content}` : content;
}

/** 快捷键上下切换会话：以本地过滤后的列表为序，找不到时保持当前会话。 */
function moveConversation(step: number) {
  const list = filteredConversations.value;
  if (list.length === 0) return;
  const currentIndex = list.findIndex((item) => item.id === active.value?.id);
  let nextIndex: number;
  if (currentIndex === -1) {
    nextIndex = step > 0 ? 0 : list.length - 1;
  } else {
    nextIndex = Math.min(Math.max(currentIndex + step, 0), list.length - 1);
  }
  const target = list[nextIndex];
  if (target && target.id !== active.value?.id) {
    void openConversation(target);
  }
}

/** 键盘快捷键：全部动作都只操作工作台内部状态，不依赖路由。 */
useDeskShortcuts({
  closeTopLayer: () => {
    if (helpOpen.value) {
      helpOpen.value = false;
      return true;
    }
    if (quickReplyOpen.value) {
      quickReplyOpen.value = false;
      return true;
    }
    if (conversationKeyword.value) {
      conversationKeyword.value = '';
      return true;
    }
    return false;
  },
  focusSearch: () => railRef.value?.focusSearch(),
  nextConversation: () => moveConversation(1),
  nextUnread: () => {
    const unread = filteredConversations.value.find(
      (item) => item.unreadCount > 0 && item.id !== active.value?.id,
    );
    if (unread) void openConversation(unread);
  },
  openHelp: () => {
    helpOpen.value = true;
  },
  openWindow: () => {
    if (!isWindowMode.value) openInWindow();
  },
  previousConversation: () => moveConversation(-1),
});

useConversationPush(refreshOnPush);

useKeepAliveRefresh(() => {
  // 切回工作台：静默对齐坐席状态、列表与当前会话，避免停留期间状态过期。
  void presenceStore.refresh();
  void (async () => {
    await loadConversations(true);
    if (active.value) await refreshActiveConversation();
  })();
});

/** 深色令牌作用域：工作台可见时给 html 挂类，让 teleport 到 body 的弹层也变深。 */
function attachDeskTheme() {
  if (typeof document === 'undefined') return;
  document.documentElement.classList.add('desk-theme');
}

function detachDeskTheme() {
  if (typeof document === 'undefined') return;
  document.documentElement.classList.remove('desk-theme');
}

onActivated(attachDeskTheme);
onDeactivated(detachDeskTheme);

/** 桌面通知点击：主窗口收到事件后跳工作台；独立窗口直接定位会话。 */
function handleOpenConversationEvent(event: Event) {
  const detail = (event as CustomEvent<{ conversationId?: string }>).detail;
  if (!detail?.conversationId) return;
  if (isWindowMode.value) {
    void requestLocate(detail.conversationId);
    return;
  }
  messageStore.requestConversation(detail.conversationId, 'service');
}

onMounted(async () => {
  window.addEventListener(
    'aryn:open-conversation',
    handleOpenConversationEvent,
  );
  attachDeskTheme();
  // 接待状态与心跳由应用级 store 托管，页面卸载（去查订单等）不再掉线。
  presenceStore.activate();
  // 独立窗口没有 BasicLayout 启动实时通道：这里显式启动（幂等）。
  // 主窗口仍由 basic.vue 启动；提醒宿主由跨窗协同决定（独立窗口优先）。
  if (isWindowMode.value) {
    messageStore.start();
  }
  await loadConversations(true);
  // 独立窗口刷新/深链可带 conversationId 恢复选中会话。
  const initialConversationId = isWindowMode.value
    ? String(route.query.conversationId ?? '')
    : '';
  if (initialConversationId) {
    void requestLocate(initialConversationId);
  } else {
    consumePendingConversation();
  }
});
</script>

<template>
  <div
    ref="deskRoot"
    class="service-desk"
    :class="{ 'service-desk--window': isWindowMode }"
  >
    <div v-if="!isWindowMode && deskAlive" class="desk-live-banner">
      <span>
        客服工作台已在独立窗口运行<template v-if="deskConversationId">
          （正在处理其它会话）</template
        >
      </span>
      <span class="desk-live-actions">
        <ElButton size="small" @click="focusDesk">聚焦独立窗口</ElButton>
        <ElButton size="small" @click="reclaimDesk">收回工作台</ElButton>
      </span>
    </div>
    <div class="desk-grid" :class="`desk-grid--${layoutMode}`">
      <ConversationRail
        ref="railRef"
        v-model:conversation-keyword="conversationKeyword"
        :active-id="active?.id"
        :active-tab="activeTab"
        :assignee-loading="assigneeLoading"
        :assignee-options="assigneeOptions"
        :auto-accept="presenceStore.autoAccept"
        :can-filter-assignee="hasSupervisor"
        :configured="presenceStore.configured"
        :conversations="conversations"
        :enabled="presenceStore.enabled"
        :filtered-conversations="filteredConversations"
        :filter-tags="filterTags"
        :filters="filters"
        :has-active-filters="hasActiveFilters"
        :has-active-query="hasActiveQuery"
        :has-more="hasMore"
        :is-window-mode="isWindowMode"
        :list-failed="listFailed"
        :list-loading="listLoading"
        :presence="presenceStore.presence"
        :presence-capacity="presenceCapacity"
        :presence-notice="presenceNotice"
        :presence-options="PRESENCE_OPTIONS"
        :queue-stats="queueStats"
        :queue-stats-degraded="queueStatsDegraded"
        :rail-hidden="railHidden"
        :syncing="presenceStore.syncing"
        :tab-counts="tabCounts"
        :tabs="tabs"
        @change-tab="handleTabChange"
        @clear-filters="clearFilters"
        @clear-query="resetQuery"
        @config-agent="agentConfigDialog?.open()"
        @go-online="goOnline"
        @initiate-conversation="initiateConversation"
        @load-more="loadMore"
        @open-filters="ensureAssigneeOptions"
        @open-window="openInWindow"
        @presence-change="handlePresenceChange"
        @reload="loadConversations(true)"
        @retry-presence="retryPresence"
        @select-conversation="openConversation"
        @update:filter="onUpdateFilter"
      />

      <main v-if="active" class="chat-stage">
        <ChatHeader
          :actions="actions"
          :claiming="claiming"
          :conversation="active"
          :layout-mode="layoutMode"
          :owner-name="ownerName"
          :show-context-panel="showContextPanel"
          @back="clearActive()"
          @claim="claim"
          @close="close"
          @force-assign="forceAssign"
          @open-assignment-logs="openAssignmentLogs"
          @open-context="contextDrawerOpen = true"
          @transfer="transfer"
        />

        <DeskNotice
          v-if="deskNotice"
          :text="deskNotice.text"
          :tone="deskNotice.tone"
        />

        <div class="chat-body">
          <section class="chat-main">
            <MessageTimeline
              ref="timelineRef"
              :conversation="active"
              :current-user-id="String(userStore.userInfo?.userId ?? '')"
              :has-older="hasOlder"
              :message-load-failed="messageLoadFailed"
              :message-loading="messageLoading"
              :messages="messages"
              :now="recallClock"
              :older-loading="olderLoading"
              :receipt="receipt"
              :receipt-label="receiptLabel"
              :stick-to-bottom="stickToBottom"
              :timeline-items="timelineItems"
              :unread-while-away="unreadWhileAway"
              @jump-bottom="scrollToBottom()"
              @load-older="loadOlder"
              @quote="setQuote"
              @recall="handleRecall"
              @retry="resend"
              @scroll="onTimelineScroll"
            />

            <Composer
              v-model:draft="draft"
              :composer="composer"
              :composer-note="composerNote"
              :message-loading="messageLoading"
              :quote="quote"
              :uploading="uploadingImage"
              @cancel-quote="clearQuote"
              @keydown="onComposerKeydown"
              @open-help="helpOpen = true"
              @open-quick-reply="quickReplyOpen = true"
              @send="send"
              @upload-image="uploadAndSendImage"
            />
          </section>

          <ConversationInfoPanel
            v-if="showContextPanel"
            :conversation="active"
            :messages="messages"
          />
        </div>
      </main>

      <section v-else-if="layoutMode !== 'narrow'" class="empty-stage">
        <ElEmpty description="选择一个会话开始处理" />
      </section>

      <ElDrawer
        v-model="contextDrawerOpen"
        :with-header="false"
        append-to-body
        class="desk-context-drawer"
        size="320px"
      >
        <ConversationInfoPanel
          :conversation="active"
          :messages="messages"
          dismissible
          @close="contextDrawerOpen = false"
        />
      </ElDrawer>
      <AgentConfigDialog ref="agentConfigDialog" @saved="handleAgentSaved" />
      <QuickReplyDialog
        :open="quickReplyOpen"
        @close="quickReplyOpen = false"
        @pick="applyQuickReply"
      />
      <ShortcutSheet :open="helpOpen" @close="helpOpen = false" />
      <AssignmentLogDrawer ref="assignmentLogDrawer" />
      <TransferDialog ref="transferDialog" @confirm="onTransferConfirm" />
    </div>
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
  .desk-grid {
    grid-template-columns: 280px minmax(0, 1fr);
  }
}

.service-desk {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 112px);
  min-height: 620px;
  overflow: hidden;
  color: var(--desk-text);
  background: var(--desk-bg);
  border: 1px solid var(--desk-line);
  border-radius: 16px;
  box-shadow: var(--desk-shadow);
}

/* 嵌入模式的历史横幅：打开独立窗口后主窗口工作台只做跳转/收回入口 */
.desk-live-banner {
  display: flex;
  flex: none;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--desk-muted);
  background: var(--desk-hover);
  border-bottom: 1px solid var(--desk-line-soft);
}

.desk-live-actions {
  display: flex;
  flex: none;
  gap: 8px;
}

.desk-grid {
  display: grid;
  flex: 1;

  /* 缺省行是 auto，会按内容撑高、顶破固定高度容器把输入区挤到可视区外 */
  grid-template-rows: minmax(0, 1fr);
  grid-template-columns: 330px minmax(0, 1fr);
  min-height: 0;
  overflow: hidden;
}

/* 独立窗口：整窗铺满，去掉圆角/边框/阴影 */
.service-desk--window {
  height: 100%;
  min-height: 0;
  border: 0;
  border-radius: 0;
  box-shadow: none;
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

.empty-stage {
  display: grid;
  place-items: center;
  background: var(--desk-canvas);
}

/* 深色工作台里的空态文字跟随工作台配色，避免浅色模式默认灰字对比不足 */
.empty-stage :deep(.el-empty__description p) {
  color: var(--desk-muted);
}

/* 容器宽度驱动布局：compact 收窄列表并隐藏常驻上下文；narrow 单栏 */
.desk-grid--compact {
  grid-template-columns: 300px minmax(0, 1fr);
}

.desk-grid--compact .chat-body {
  grid-template-columns: minmax(0, 1fr);
}

.desk-grid--compact .chat-body :deep(.info-panel) {
  display: none;
}

.desk-grid--narrow {
  grid-template-columns: minmax(0, 1fr);
}
</style>
