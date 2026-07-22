import type {
  ChatMessage,
  ChatMessagePage,
  ChatMessageType,
  Conversation,
  CursorPage,
} from './types';

import { requestClient } from '#/api/request';

export function getStaffConversations(params: {
  cursor?: string;
  limit?: number;
}) {
  return requestClient.get<CursorPage<Conversation>>(
    '/message/staff/conversation',
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
