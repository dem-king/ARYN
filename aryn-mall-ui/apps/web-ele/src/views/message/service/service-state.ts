import type { ViewChatMessage } from '../message-state';

import type { ConversationInboxQueryParams } from '#/api/message/conversation';
import type {
  ChatMessageType,
  Conversation,
  ConversationStatus,
  MessageUploadResult,
} from '#/api/message/types';
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

/**
 * 会话列表精细筛选状态（P2-A 契约 §2.2）。
 *
 * `statuses` 非空时按契约 §2.3-1 覆盖页签 `status`；其余条件与状态条件为 AND。
 */
export interface ConversationFilterState {
  /** 精确匹配负责客服 ID（仅主管筛选展示） */
  assignedStaffId?: string;
  /** 负责客服展示名：仅用于筛选标签，不参与请求 */
  assignedStaffName?: string;
  /** 精确匹配队列编码；空=不过滤（注意与待领取池固定 DEFAULT 不同） */
  queueCode?: string;
  /** 精确状态集合；非空时覆盖页签 status */
  statuses: ConversationStatus[];
  /** 仅保留有未读的会话（staff=本人未读，admin=负责客服未读） */
  unreadOnly: boolean;
}

export function createConversationFilterState(): ConversationFilterState {
  return { statuses: [], unreadOnly: false };
}

/**
 * 组装会话列表请求参数（staff/admin 共用）。
 *
 * 优先级遵循契约 §2.3：`statuses` 非空时不下发页签 `status`，避免两者同时出现；
 * 空串/空白条件直接省略，翻页时由调用方原样复用同一组参数。
 */
export function buildConversationInboxParams(input: {
  cursor?: string;
  filters: ConversationFilterState;
  /** 服务端 keyword；调用方负责 trim（空串不传） */
  keyword?: string;
  limit: number;
  /** 页签状态过滤；statuses 非空时被覆盖 */
  status?: 'CLOSED' | 'OPEN';
}): ConversationInboxQueryParams {
  const params: ConversationInboxQueryParams = { limit: input.limit };
  const cursor = input.cursor?.trim();
  if (cursor) params.cursor = cursor;
  const keyword = input.keyword?.trim();
  if (keyword) params.keyword = keyword;
  const queueCode = input.filters.queueCode?.trim();
  if (queueCode) params.queueCode = queueCode;
  const assignedStaffId = input.filters.assignedStaffId?.trim();
  if (assignedStaffId) params.assignedStaffId = assignedStaffId;
  if (input.filters.statuses.length > 0) {
    // 契约要求逗号拼接的 string（不用数组，规避 axios 序列化差异）
    params.statuses = input.filters.statuses.join(',');
  } else if (input.status) {
    params.status = input.status;
  }
  if (input.filters.unreadOnly) params.unreadOnly = true;
  return params;
}

/**
 * 当前生效的筛选标签（rail 展示 + 清除入口）。
 *
 * 标签只覆盖精细筛选；搜索关键字由搜索框自身承载，不重复展示。
 */
export function conversationFilterTags(
  filters: ConversationFilterState,
): Array<{ key: string; label: string }> {
  const tags: Array<{ key: string; label: string }> = [];
  if (filters.unreadOnly) tags.push({ key: 'unreadOnly', label: '仅未读' });
  const queueCode = filters.queueCode?.trim();
  if (queueCode) tags.push({ key: 'queueCode', label: `队列 ${queueCode}` });
  const assignedStaffId = filters.assignedStaffId?.trim();
  if (assignedStaffId) {
    const owner = filters.assignedStaffName?.trim() || assignedStaffId;
    tags.push({ key: 'assignedStaffId', label: `负责人 ${owner}` });
  }
  if (filters.statuses.length > 0) {
    const labels = filters.statuses.map((status) =>
      conversationStatusLabel(status),
    );
    tags.push({ key: 'statuses', label: `状态 ${labels.join('/')}` });
  }
  return tags;
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
 * 聊天气泡发送者展示名。
 *
 * 会员历史消息的 senderName 可能为空（早期 TOC 登录快照没写昵称），模板直接回退
 * senderType 会把 `MALL_USER` 这种枚举码暴露给客服；此时用会话里的会员昵称兜底。
 */
export function chatMessageSenderLabel(
  message?: ViewChatMessage,
  conversation?: Conversation,
): string {
  if (!message) return '';
  if (message.senderType === 'MALL_USER') {
    return conversation?.customerName?.trim() || '会员';
  }
  return message.senderName?.trim() || message.senderType || '';
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

/**
 * 等待时长（契约 §3.1）：只用于 `status=WAITING` 且 `waitingSince` 有值的会话，
 * 不得用 `lastMessageTime` 推算（无消息的新会话没有该字段）。
 *
 * 返回等宽数字友好的纯时长文案：`mm:ss`（<1h）→ `HH:mm`（<1d）→ `Nd HH:mm`；
 * 缺失/非法时间返回空串，未来时间钳制为 `00:00`。
 */
export function formatWaitingDuration(
  waitingSince?: string,
  now: Date | number = new Date(),
): string {
  const start = parseDateTimeValue(waitingSince);
  if (!start) return '';
  const nowDate = typeof now === 'number' ? new Date(now) : now;
  const diffMs = nowDate.getTime() - start.getTime();
  if (Number.isNaN(diffMs)) return '';
  if (diffMs <= 0) return '00:00';
  const totalMinutes = Math.floor(diffMs / 60_000);
  const hours = Math.floor(totalMinutes / 60);
  const days = Math.floor(hours / 24);
  if (days > 0) {
    return `${days}天 ${pad2(hours % 24)}:${pad2(totalMinutes % 60)}`;
  }
  if (hours > 0) return `${pad2(hours)}:${pad2(totalMinutes % 60)}`;
  const totalSeconds = Math.floor(diffMs / 1000);
  return `${pad2(totalMinutes)}:${pad2(totalSeconds % 60)}`;
}

/** 会话行等待徽标文案：`等待 03:12`；非等待态/缺失时间返回空串。 */
export function waitingDurationLabel(
  waitingSince?: string,
  now: Date | number = new Date(),
): string {
  const duration = formatWaitingDuration(waitingSince, now);
  return duration ? `等待 ${duration}` : '';
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

/** 解析后端 LocalDateTime 的两种序列化形态；非法值返回 undefined。 */
function parseDateTimeValue(value?: string): Date | undefined {
  if (!value) return undefined;
  const normalized = value.includes('T') ? value : value.replace(' ', 'T');
  const date = new Date(normalized);
  return Number.isNaN(date.getTime()) ? undefined : date;
}

function pad2(value: number): string {
  return String(value).padStart(2, '0');
}

function isSameDay(first: Date, second: Date): boolean {
  return (
    first.getFullYear() === second.getFullYear() &&
    first.getMonth() === second.getMonth() &&
    first.getDate() === second.getDate()
  );
}

/**
 * 会话列表右侧时间：今天显示时分、昨天显示「昨天」、同年显示月日、跨年显示完整日期。
 *
 * 客服扫列表时最关心「多久没动了」，绝对时间戳反而难扫读，因此按距离做相对化。
 */
export function conversationTimeLabel(
  value?: string,
  now: Date = new Date(),
): string {
  const date = parseDateTimeValue(value);
  if (!date) return '';
  if (isSameDay(date, now)) {
    return `${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
  }
  const yesterday = new Date(
    now.getFullYear(),
    now.getMonth(),
    now.getDate() - 1,
  );
  if (isSameDay(date, yesterday)) return '昨天';
  if (date.getFullYear() === now.getFullYear()) {
    return `${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
  }
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(
    date.getDate(),
  )}`;
}

/** 时间线日期分隔文案：今天/昨天/完整日期。 */
export function formatDateDivider(date: Date, now: Date = new Date()): string {
  if (isSameDay(date, now)) return '今天';
  const yesterday = new Date(
    now.getFullYear(),
    now.getMonth(),
    now.getDate() - 1,
  );
  if (isSameDay(date, yesterday)) return '昨天';
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(
    date.getDate(),
  )}`;
}

/** 同一发送人连续消息的聚合窗口：窗口内不重复发送者/头像。 */
const GROUP_WINDOW_MS = 5 * 60 * 1000;

/** 时间线渲染项：日期分隔 / 未读边界 / 消息。 */
export type TimelineRenderItem =
  | {
      /** 是否同一发送人连续消息的组首（组首显示发送者、拉大间距）。 */
      groupStart: boolean;
      key: string;
      message: ViewChatMessage;
      type: 'message';
    }
  | { key: string; label: string; type: 'date' }
  | { key: string; type: 'unread' };

/**
 * 时间线消息项（组首/未读边界等展示信息）。
 *
 * 抽成独立接口，渲染层只读字段，不再自己判断相邻消息关系。
 */
export interface TimelineBuildOptions {
  now?: Date;
  /** 打开会话前的已读游标：大于该 seq 的第一条消息前插入「以下为新消息」。 */
  unreadAfterSeq?: number;
}

/**
 * 构造时间线渲染列表（日期分隔 + 未读边界 + 消息 + 同人聚合）。
 *
 * 纯函数，便于单测覆盖跨天/空时间/单日/未读边界/连续消息聚合等边界；
 * 渲染层只负责 v-for。
 */
export function buildTimelineItems(
  messages: ViewChatMessage[],
  options: TimelineBuildOptions = {},
): TimelineRenderItem[] {
  const now = options.now ?? new Date();
  const unreadAfterSeq = options.unreadAfterSeq ?? 0;
  const items: TimelineRenderItem[] = [];
  let lastDateKey = '';
  let unreadInserted = unreadAfterSeq <= 0;
  let previous: undefined | ViewChatMessage;

  for (const message of messages) {
    const date = parseDateTimeValue(message.createTime);
    const dateKey = date
      ? `${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`
      : '';
    if (date && dateKey !== lastDateKey) {
      lastDateKey = dateKey;
      items.push({
        key: `date:${dateKey}`,
        label: formatDateDivider(date, now),
        type: 'date',
      });
    }
    if (
      !unreadInserted &&
      message.seqNo > unreadAfterSeq &&
      message.seqNo < Number.MAX_SAFE_INTEGER
    ) {
      unreadInserted = true;
      items.push({ key: 'unread-boundary', type: 'unread' });
    }
    const groupStart =
      !previous ||
      previous.senderType !== message.senderType ||
      previous.senderId !== message.senderId ||
      message.senderType === 'SYSTEM' ||
      !isWithinGroupWindow(previous.createTime, message.createTime);
    items.push({
      groupStart,
      key: message.id,
      message,
      type: 'message',
    });
    previous = message;
  }
  return items;
}

/** 两条消息是否在聚合窗口内（时间缺失时按不聚合处理，宁多显示组首）。 */
function isWithinGroupWindow(first?: string, second?: string): boolean {
  const firstDate = parseDateTimeValue(first);
  const secondDate = parseDateTimeValue(second);
  if (!firstDate || !secondDate) return false;
  return (
    Math.abs(secondDate.getTime() - firstDate.getTime()) <= GROUP_WINDOW_MS
  );
}

/* ============================================================================
 * 批次二：消息引用 / 撤回 / 图片发送（P2-A 契约 v2 §14-§17）
 * ==========================================================================*/

/** 可撤回时间窗：与后端 RECALL_WINDOW_MINUTES=2 保持一致（边界含 2 分钟）。 */
export const RECALL_WINDOW_MS = 2 * 60 * 1000;
/** 图片上传大小上限：5MB（契约 §17.3）。 */
export const IMAGE_UPLOAD_MAX_BYTES = 5 * 1024 * 1024;
/** 图片选择 accept：GIF 本期不支持（Lead 2026-11-06 决策）。 */
export const IMAGE_UPLOAD_ACCEPT = 'image/jpeg,image/png,image/webp';
/** 图片 MIME 白名单（与后端上传/发送白名单一致）。 */
export const IMAGE_UPLOAD_MIME_TYPES: readonly string[] = [
  'image/jpeg',
  'image/png',
  'image/webp',
];

/** 消息类型展示名（引用块/摘要用）。 */
export function messageTypeLabel(type?: ChatMessageType): string {
  const labels: Record<ChatMessageType, string> = {
    IMAGE: '图片',
    NOTICE_CARD: '通知',
    ORDER_CARD: '订单',
    PRODUCT_CARD: '商品',
    REFUND_CARD: '退款',
    SYSTEM: '系统消息',
    TEXT: '文本',
  };
  return type ? labels[type] : '';
}

/**
 * 是否已落库的服务端消息。
 *
 * 本地乐观消息（`seqNo=MAX_SAFE_INTEGER`）与发送中/失败消息没有服务端 ID，
 * 既不能引用（后端校验存在性）也不能撤回（后端找不到）。
 */
export function isServerMessage(message?: ViewChatMessage): boolean {
  if (!message) return false;
  if (message.sendState === 'failed' || message.sendState === 'sending') {
    return false;
  }
  return message.seqNo > 0 && message.seqNo < Number.MAX_SAFE_INTEGER;
}

/** 可引用：已落库、未撤回、TEXT/IMAGE（契约 §15；系统消息与业务卡片不参与引用交互）。 */
export function canQuoteMessage(message?: ViewChatMessage): boolean {
  if (!message || !isServerMessage(message)) return false;
  if (message.recallStatus === '1') return false;
  return message.messageType === 'TEXT' || message.messageType === 'IMAGE';
}

/**
 * 可撤回（契约 §16.2）：本人发送、TEXT/IMAGE、已落库未撤回、会话未关闭、
 * 发送时间在 2 分钟窗口内。时间窗由页面定时刷新 `now` 驱动，超时自动隐藏按钮。
 */
export function canRecallMessage(
  message: undefined | ViewChatMessage,
  input: {
    /** 会话状态：已关闭会话后端拒绝撤回 */
    conversationStatus?: ConversationStatus;
    now?: Date | number;
    /** 当前登录坐席 staffId（统一转字符串比较） */
    userId: string;
  },
): boolean {
  if (!message || !input.userId || !isServerMessage(message)) return false;
  if (message.recallStatus === '1') return false;
  if (message.senderType !== 'SYS_USER') return false;
  if (String(message.senderId) !== String(input.userId)) return false;
  if (message.messageType !== 'TEXT' && message.messageType !== 'IMAGE') {
    return false;
  }
  if (input.conversationStatus === 'CLOSED') return false;
  const createdAt = parseDateTimeValue(message.createTime);
  if (!createdAt) return false;
  const now =
    typeof input.now === 'number'
      ? new Date(input.now)
      : (input.now ?? new Date());
  const elapsed = now.getTime() - createdAt.getTime();
  return elapsed >= 0 && elapsed <= RECALL_WINDOW_MS;
}

/** 引用目标：composer 引用条与发送参数共用。 */
export interface QuoteTarget {
  id: string;
  messageType: ChatMessageType;
  senderName: string;
  summary: string;
}

/** 引用摘要：与后端 §15.2 同口径（TEXT 原文截断 100，图片/卡片给 `[标签]`）。 */
export function buildQuoteSummary(message: ViewChatMessage): string {
  switch (message.messageType) {
    case 'IMAGE': {
      return '[图片]';
    }
    case 'NOTICE_CARD': {
      return '[通知]';
    }
    case 'ORDER_CARD': {
      return '[订单]';
    }
    case 'PRODUCT_CARD': {
      return '[商品]';
    }
    case 'REFUND_CARD': {
      return '[退款]';
    }
    case 'SYSTEM': {
      return '';
    }
    default: {
      const text = (message.content ?? '').trim();
      return text.length > 100 ? `${text.slice(0, 100)}…` : text;
    }
  }
}

/** 构造引用目标；不可引用的消息返回 undefined。 */
export function buildQuoteTarget(
  message?: ViewChatMessage,
  conversation?: Conversation,
): QuoteTarget | undefined {
  if (!message || !canQuoteMessage(message)) return undefined;
  return {
    id: message.id,
    messageType: message.messageType,
    senderName: chatMessageSenderLabel(message, conversation),
    summary: buildQuoteSummary(message),
  };
}

/** 引用块展示数据。 */
export interface QuotedBlockView {
  /** 被引用消息已撤回/不可用 */
  recalled: boolean;
  /** 引用块正文（普通引用=摘要；已撤回=占位文案） */
  text: string;
  /** 引用目标发送者昵称（不可用时不展示） */
  senderName?: string;
  /** 引用目标类型文案 */
  typeLabel?: string;
}

/**
 * 引用块展示（契约 §15.4）：`quotedMessageId` 存在即渲染引用块；
 * `quotedRecalled=true` 时显示「引用的消息已撤回」，不展示原内容。
 */
export function quotedBlockView(
  message?: ViewChatMessage,
): QuotedBlockView | undefined {
  if (!message?.quotedMessageId) return undefined;
  if (message.quotedRecalled) {
    return { recalled: true, text: '引用的消息已撤回' };
  }
  const summary = message.quotedMessageSummary?.trim();
  const typeLabel = messageTypeLabel(message.quotedMessageType);
  const fallback = message.quotedMessageType ? `[${typeLabel}]` : '[原消息]';
  return {
    recalled: false,
    senderName: message.quotedSenderName?.trim() || undefined,
    text: summary || fallback,
    typeLabel,
  };
}

/** 图片预校验结果。 */
export interface ImageUploadValidation {
  error?: string;
  ok: boolean;
}

/**
 * 前端图片预校验（与后端 §17.3 同口径）：非空、≤5MB、MIME 仅 jpeg/png/webp。
 *
 * 扩展名/魔数由后端最终校验；这里只拦「选了明显不对的文件」的无效上传请求。
 */
export function validateImageUpload(file: {
  size: number;
  type: string;
}): ImageUploadValidation {
  if (file.size <= 0) return { error: '上传文件不能为空', ok: false };
  if (file.size > IMAGE_UPLOAD_MAX_BYTES) {
    return { error: '图片大小不能超过 5MB', ok: false };
  }
  if (!IMAGE_UPLOAD_MIME_TYPES.includes((file.type || '').toLowerCase())) {
    return { error: '仅支持 JPG/JPEG/PNG/WEBP 图片', ok: false };
  }
  return { ok: true };
}

/** 组装 IMAGE 消息 payload（契约 §17.5；fileId 与 url 同值）。 */
export function buildImageMessagePayload(upload: MessageUploadResult): string {
  return JSON.stringify({
    fileId: upload.fileId || upload.url,
    url: upload.url,
    mimeType: upload.contentType,
    name: upload.name,
    size: upload.size,
  });
}
