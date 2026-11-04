import type { ViewChatMessage } from '../message-state';

import type { Conversation, ConversationStatus } from '#/api/message/types';
import type { PresenceStatus } from '#/store/agent-presence';

/**
 * 客服工作台的纯展示/交互状态推导。
 *
 * 抽成纯函数的原因：页签可见性、状态机按钮、输入框门禁、已读回执这些规则
 * 既要在模板里用，又必须有单测兜底；放进 .vue 就无法用 vitest 覆盖。
 */

/** 工作台页签：我的会话 / 待领取 / 历史会话 / 主管全部会话。 */
export type ServiceTab = 'history' | 'mine' | 'supervisor' | 'waiting';

export interface ServiceTabOption {
  key: ServiceTab;
  label: string;
  /** 列表接口的状态过滤；undefined 表示不过滤。 */
  status?: 'CLOSED' | 'OPEN';
}

const TAB_OPTIONS: Record<ServiceTab, Omit<ServiceTabOption, 'key'>> = {
  mine: { label: '我的会话', status: 'OPEN' },
  waiting: { label: '待领取' },
  history: { label: '历史会话', status: 'CLOSED' },
  supervisor: { label: '全部会话' },
};

/**
 * 工作台可见页签。
 *
 * 前三个是坐席基础页签；主管额外出现「全部会话」，
 * 对应后端 `/message/admin/conversation/page`（含关闭会话）。
 */
export function visibleServiceTabs(hasSupervisor: boolean): ServiceTabOption[] {
  const keys: ServiceTab[] = ['mine', 'waiting', 'history'];
  if (hasSupervisor) keys.push('supervisor');
  return keys.map((key) => ({ key, ...TAB_OPTIONS[key] }));
}

/** 页签对应的列表查询参数：我的=OPEN（WAITING/ASSIGNED/ACTIVE），历史=CLOSED。 */
export function conversationQuery(tab: ServiceTab): {
  status?: 'CLOSED' | 'OPEN';
} {
  const status = TAB_OPTIONS[tab].status;
  return status ? { status } : {};
}

/** 坐席状态门禁：来自 presence store 的四项事实。 */
export interface AgentGate {
  configured: boolean;
  enabled: boolean;
  loaded: boolean;
  presence: PresenceStatus;
}

/** 状态标签文案。 */
export function conversationStatusLabel(status?: ConversationStatus): string {
  const labels: Record<ConversationStatus, string> = {
    ACTIVE: '进行中',
    ASSIGNED: '已分配',
    CLOSED: '已关闭',
    WAITING: '待领取',
  };
  return status ? labels[status] : '';
}

/** 状态标签配色（ElTag type）。 */
export function conversationStatusTone(
  status?: ConversationStatus,
): 'danger' | 'info' | 'primary' | 'success' | 'warning' {
  const tones: Record<
    ConversationStatus,
    'danger' | 'info' | 'primary' | 'success' | 'warning'
  > = {
    ACTIVE: 'success',
    ASSIGNED: 'primary',
    CLOSED: 'info',
    WAITING: 'warning',
  };
  return status ? tones[status] : 'info';
}

/**
 * 会话展示名：优先会员昵称，缺失回退会员 ID；工作人员私信单独展示。
 */
export function conversationDisplayName(conversation?: Conversation): string {
  if (!conversation) return '';
  if (conversation.conversationType === 'STAFF_DIRECT') return '工作人员私信';
  const name = conversation.customerName?.trim();
  if (name) return name;
  const customerId = conversation.customerId?.trim();
  return customerId ? `会员 ${customerId}` : '会员会话';
}

/** 负责客服展示名：昵称优先，回退 ID，未分配时给明确文案。 */
export function conversationOwnerName(conversation?: Conversation): string {
  const name = conversation?.assignedStaffName?.trim();
  if (name) return name;
  return conversation?.assignedStaffId?.trim() || '未分配';
}

/**
 * 领取按钮状态。
 *
 * 产品口径：领取属于「承接新工作」，要求坐席已配置、已启用且 ONLINE；
 * 离线时按钮禁用并提示先上线，而不是让用户点了再吃一个后端错误。
 */
export interface ClaimState {
  disabled: boolean;
  hint: string;
  visible: boolean;
}

export function claimState(
  conversation: Conversation | undefined,
  gate: AgentGate,
): ClaimState {
  if (!conversation || conversation.status !== 'WAITING') {
    return { disabled: true, hint: '', visible: false };
  }
  if (!gate.loaded) {
    return { disabled: true, hint: '正在同步坐席状态…', visible: true };
  }
  if (!gate.configured) {
    return {
      disabled: true,
      hint: '尚未配置客服坐席，请先完成坐席设置再领取会话',
      visible: true,
    };
  }
  if (!gate.enabled) {
    return { disabled: true, hint: '坐席已禁用，无法领取会话', visible: true };
  }
  if (gate.presence !== 'ONLINE') {
    return {
      disabled: true,
      hint: '当前离线（或忙碌/暂停），请先将接待状态切换为在线后再领取',
      visible: true,
    };
  }
  return { disabled: false, hint: '', visible: true };
}

/** 回复输入区状态：WAITING/CLOSED 只读，ASSIGNED/ACTIVE 可回复（与离线无关）。 */
export interface ComposerState {
  disabled: boolean;
  hint: string;
  placeholder: string;
}

export function composerState(
  conversation: Conversation | undefined,
): ComposerState {
  if (!conversation) {
    return {
      disabled: true,
      hint: '',
      placeholder: '请选择左侧会话',
    };
  }
  if (conversation.status === 'WAITING') {
    return {
      disabled: true,
      hint: '待领取会话支持只读预览，领取后可回复',
      placeholder: '领取后可回复',
    };
  }
  if (conversation.status === 'CLOSED') {
    const deadline = formatDateTime(conversation.reopenDeadline);
    return {
      disabled: true,
      hint: deadline
        ? `会话已关闭，会员可在 ${deadline} 前重新咨询（当前仅可查看历史消息）`
        : '会话已关闭，仅可查看历史消息',
      placeholder: '会话已关闭，无法继续回复',
    };
  }
  return {
    disabled: false,
    hint: '',
    placeholder: '输入回复，Enter 发送，Shift + Enter 换行',
  };
}

/** 会话维度的按钮可见性/可用性。 */
export interface ConversationActions {
  canClaim: boolean;
  canClose: boolean;
  canForceAssign: boolean;
  canOpenAssignmentLogs: boolean;
  canReply: boolean;
  canTransfer: boolean;
  claimDisabled: boolean;
  claimHint: string;
  /** 关闭态的重开截止提示；非关闭态为空。 */
  reopenHint: string;
}

export function conversationActions(input: {
  conversation?: Conversation;
  gate: AgentGate;
  /** 是否主管（决定强制分配/分配日志入口）。 */
  supervisor: boolean;
  /** 当前是否在「全部会话」页签（主管动作只在该视图出现）。 */
  supervisorView: boolean;
}): ConversationActions {
  const { conversation, gate, supervisor, supervisorView } = input;
  if (!conversation) {
    return {
      canClaim: false,
      canClose: false,
      canForceAssign: false,
      canOpenAssignmentLogs: false,
      canReply: false,
      canTransfer: false,
      claimDisabled: true,
      claimHint: '',
      reopenHint: '',
    };
  }
  const claim = claimState(conversation, gate);
  const replyable =
    conversation.status === 'ASSIGNED' || conversation.status === 'ACTIVE';
  const closed = conversation.status === 'CLOSED';
  const deadline = formatDateTime(conversation.reopenDeadline);
  let reopenHint = '';
  if (closed) {
    reopenHint = deadline
      ? `会员可在 ${deadline} 前重新咨询该会话`
      : '会话已关闭，会员重开窗口已结束';
  }
  return {
    canClaim: claim.visible,
    canClose: replyable,
    canForceAssign: supervisor && supervisorView && !closed,
    canOpenAssignmentLogs: supervisor && supervisorView,
    canReply: replyable,
    // 主管视图用「强制分配」代替普通转交，避免两套入口混淆
    canTransfer: replyable && !supervisorView,
    claimDisabled: claim.disabled,
    claimHint: claim.hint,
    reopenHint,
  };
}

/** 工作台推进已读游标的前置条件：只有已分配给本人的进行中会话才推进。 */
export function shouldMarkRead(conversation?: Conversation): boolean {
  if (!conversation) return false;
  const replyable =
    conversation.status === 'ASSIGNED' || conversation.status === 'ACTIVE';
  return replyable && conversation.lastSeq > (conversation.lastReadSeq || 0);
}

/** 已读回执：最后一条我方消息的状态。 */
export type ReadReceipt = 'none' | 'read' | 'unread';

/**
 * 已读/未读判定：`peerLastReadSeq >= seqNo` 表示对方已读。
 *
 * 本地乐观消息、失败/发送中消息不展示回执（还没有服务端序号）。
 */
export function readReceipt(
  peerLastReadSeq: number | undefined,
  message: undefined | ViewChatMessage,
): ReadReceipt {
  if (!message || message.senderType !== 'SYS_USER') return 'none';
  if (message.sendState === 'failed' || message.sendState === 'sending') {
    return 'none';
  }
  if (message.seqNo >= Number.MAX_SAFE_INTEGER || message.seqNo <= 0) {
    return 'none';
  }
  const peerSeq = peerLastReadSeq ?? 0;
  return peerSeq >= message.seqNo ? 'read' : 'unread';
}

/** 时间线里最后一条已落库的我方消息（用于展示已读回执）。 */
export function latestOutgoingMessage(
  messages: ViewChatMessage[],
): undefined | ViewChatMessage {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index];
    if (!message) continue;
    if (message.senderType !== 'SYS_USER') continue;
    if (message.sendState === 'failed' || message.sendState === 'sending') {
      continue;
    }
    if (message.seqNo >= Number.MAX_SAFE_INTEGER) continue;
    return message;
  }
  return undefined;
}

/** 判断某条消息是否是时间线里最后一条我方消息（回执只挂在它下面）。 */
export function isLatestOutgoingMessage(
  messages: ViewChatMessage[],
  message: ViewChatMessage,
): boolean {
  return (
    latestOutgoingMessage(messages)?.clientMessageId === message.clientMessageId
  );
}

/** 中文输入法组合期间的 Enter 是“确认候选词”，不能当发送。 */
export function isSendEnter(event: {
  isComposing?: boolean;
  key?: string;
  shiftKey?: boolean;
}): boolean {
  return event.key === 'Enter' && !event.shiftKey && !event.isComposing;
}

/** 兼容后端 LocalDateTime 的两种序列化形态，展示为 yyyy-MM-dd HH:mm。 */
export function formatDateTime(value?: string): string {
  if (!value) return '';
  const normalized = value.includes('T') ? value : value.replace(' ', 'T');
  const date = new Date(normalized);
  if (Number.isNaN(date.getTime())) return value;
  const pad = (input: number) => String(input).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(
    date.getDate(),
  )} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** 结构化 contextPayload 的展示：能解析就格式化，不能解析就原样展示。 */
export function formatContextPayload(payload?: string): string {
  if (!payload) return '';
  try {
    const parsed: unknown = JSON.parse(payload);
    if (parsed && typeof parsed === 'object') {
      return JSON.stringify(parsed, null, 2);
    }
    return payload;
  } catch {
    return payload;
  }
}
