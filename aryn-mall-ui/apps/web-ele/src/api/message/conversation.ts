import type {
  ChatMessage,
  ChatMessagePage,
  ChatMessageType,
  Conversation,
  ConversationAssignmentLog,
  ConversationAttention,
  ConversationInboxStatus,
  CursorPage,
} from './types';

import { requestClient } from '#/api/request';

/**
 * 本人会话列表（我的会话 / 历史会话）。
 *
 * `status=OPEN` 覆盖 WAITING/ASSIGNED/ACTIVE，`status=CLOSED` 为历史会话；
 * 缺省不过滤，供工作人员私信页复用。
 */
export function getStaffConversations(params: {
  cursor?: string;
  limit?: number;
  status?: ConversationInboxStatus;
}) {
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
 * 与本人列表同构，复用 `ConversationInboxPageVO` 的 cursor 分页协议，
 * 权限由后端 `message:service:supervisor` 校验。
 */
export function getAdminConversations(params: {
  cursor?: string;
  limit?: number;
  status?: ConversationInboxStatus;
}) {
  return requestClient.get<CursorPage<Conversation>>(
    '/message/admin/conversation/page',
    { params },
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
  },
) {
  return requestClient.post<ChatMessage>(
    `/message/staff/conversation/${conversationId}/messages`,
    data,
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
