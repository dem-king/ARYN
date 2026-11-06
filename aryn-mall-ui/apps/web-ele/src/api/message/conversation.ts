import type {
  ChatMessage,
  ChatMessagePage,
  ChatMessageType,
  Conversation,
  ConversationAssignmentLog,
  ConversationAttention,
  ConversationInboxStatus,
  CursorPage,
  QueueStats,
} from './types';

import { requestClient } from '#/api/request';

/**
 * 会话列表筛选参数（契约 §2.2，staff/admin 共用）。
 *
 * 筛选条件之间为 AND；`statuses` 归一化后非空时覆盖 `status`。
 * 翻页时必须原样回传同一组筛选参数（`cursor` 不与筛选条件绑定）。
 */
export interface ConversationInboxQueryParams {
  /** 精确匹配负责客服 ID，trim 后为空则忽略 */
  assignedStaffId?: string;
  /** 服务端生成的不透明游标，首页不传且不允许前端构造 */
  cursor?: string;
  /** 对会员 ID/昵称快照/最后消息摘要做包含匹配，trim 后为空则忽略，长度 ≤64 */
  keyword?: string;
  /** 每页条数 1–100，缺省 20 */
  limit?: number;
  /** 精确匹配队列编码，缺省不按队列过滤 */
  queueCode?: string;
  /** 页签遗留语义：OPEN=WAITING/ASSIGNED/ACTIVE，CLOSED=已关闭；被非空 statuses 覆盖 */
  status?: ConversationInboxStatus;
  /**
   * 精确状态集合，逗号拼接为字符串（如 `'WAITING,ACTIVE'`）。
   *
   * 用 string 而不是数组，避免 axios 对数组参数序列化的方言差异；
   * 契约 §2.2 允许逗号分隔或重复参数，这里固定用逗号分隔。
   */
  statuses?: string;
  /** true 时只保留有未读的会话（staff=本人未读，admin=负责客服未读） */
  unreadOnly?: boolean;
}

/**
 * 本人会话列表（我的会话 / 历史会话）。
 *
 * `status=OPEN` 覆盖 WAITING/ASSIGNED/ACTIVE，`status=CLOSED` 为历史会话；
 * 缺省不过滤，供工作人员私信页复用。P2-A 起支持 keyword/statuses 等精细筛选。
 */
export function getStaffConversations(params: ConversationInboxQueryParams) {
  return requestClient.get<CursorPage<Conversation>>(
    '/message/staff/conversation',
    {
      params,
    },
  );
}

/**
 * 主管视角：本租户全部会话（含关闭）。
 *
 * 与本人列表同构，复用 `ConversationInboxPageVO` 的 cursor 分页协议与筛选参数，
 * 权限由后端 `message:service:supervisor` 校验。
 */
export function getAdminConversations(params: ConversationInboxQueryParams) {
  return requestClient.get<CursorPage<Conversation>>(
    '/message/admin/conversation/page',
    { params },
  );
}

/**
 * 队列统计：等待数/最久等待/在线坐席/可用容量（契约 §5）。
 *
 * 无请求参数、无缓存；建议 ≥30s 轮询或随会话列表刷新触发。
 * 权限 `message:service:agent` OR `message:service:supervisor`。
 */
export function getQueueStats() {
  return requestClient.get<QueueStats>(
    '/message/staff/conversation/queue-stats',
  );
}

/** 主管视角：指定会话的分配日志（强制分配/转交/领取等审计记录）。 */
export function getAssignmentLogs(conversationId: string) {
  return requestClient.get<ConversationAssignmentLog[]>(
    `/message/admin/conversation/${conversationId}/assignment-logs`,
  );
}

/** 主管强制分配会话：不受目标坐席在线限制，后端会写 FORCE_ASSIGN 审计。 */
export function assignConversation(
  id: string,
  targetStaffId: string,
  reason?: string,
) {
  return requestClient.post(`/message/admin/conversation/${id}/assign`, {
    reason,
    targetStaffId,
  });
}

/** 坐席待办提醒：本人未读会话 + 共享池待领取，供管理端铃铛使用。 */
export function getConversationAttention(params: {
  limit?: number;
  queueCode?: string;
}) {
  return requestClient.get<ConversationAttention>(
    '/message/staff/conversation/attention',
    {
      params,
    },
  );
}

export function getWaitingConversations(params: {
  limit?: number;
  queueCode?: string;
}) {
  return requestClient.get<Conversation[]>(
    '/message/staff/conversation/waiting',
    {
      params,
    },
  );
}

export function getConversationMessages(
  conversationId: string,
  params: { afterSeq?: number; beforeSeq?: number; limit?: number },
) {
  return requestClient.get<ChatMessagePage>(
    `/message/staff/conversation/${conversationId}/messages`,
    { params },
  );
}

export function sendConversationMessage(
  conversationId: string,
  data: {
    clientMessageId: string;
    content?: string;
    messageType: ChatMessageType;
    payload?: string;
    /** 引用目标消息 ID（同会话、未撤回；空白视为未引用） */
    quotedMessageId?: string;
  },
) {
  return requestClient.post<ChatMessage>(
    `/message/staff/conversation/${conversationId}/messages`,
    data,
  );
}

/**
 * 撤回消息（契约 §16）：仅本人发送的 TEXT/IMAGE、2 分钟内、会话未关闭；
 * 重复撤回幂等成功。返回脱敏后的消息（recallStatus='1'、content/payload=null）。
 */
export function recallConversationMessage(
  conversationId: string,
  messageId: string,
) {
  return requestClient.post<ChatMessage>(
    `/message/staff/conversation/${conversationId}/messages/${messageId}/recall`,
  );
}

export function claimConversation(id: string) {
  return requestClient.post(`/message/staff/conversation/${id}/claim`);
}

export function transferConversation(
  id: string,
  targetStaffId: string,
  reason?: string,
) {
  return requestClient.post(`/message/staff/conversation/${id}/transfer`, {
    reason,
    targetStaffId,
  });
}

export function closeConversation(id: string, reason?: string) {
  return requestClient.post(`/message/staff/conversation/${id}/close`, {
    reason,
  });
}

export function markConversationRead(id: string, lastReadSeq: number) {
  return requestClient.post(`/message/staff/conversation/${id}/read`, {
    lastReadSeq,
  });
}

export function createStaffDirect(targetStaffId: string) {
  return requestClient.post<Conversation>('/message/staff/direct', {
    targetStaffId,
  });
}

export function initiateCustomerConversation(customerId: string) {
  return requestClient.post<Conversation>(
    '/message/staff/conversation/initiate',
    {
      customerId,
      queueCode: 'DEFAULT',
    },
  );
}
