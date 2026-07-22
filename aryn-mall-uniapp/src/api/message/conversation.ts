import type {
  ChatMessage,
  ChatMessagePage,
  ChatMessageType,
  Conversation,
  CursorPage,
} from './types'
import { alovaInstance } from '@/api/core/instance'

export function getOrCreateCustomerService(contextPayload?: string) {
  return alovaInstance.Post<Conversation>(
    '/message/app/conversation/customer-service',
    {
      contextPayload,
      queueCode: 'DEFAULT',
    },
  )
}

export function getConversationInbox(params: {
  cursor?: string
  limit?: number
}) {
  return alovaInstance.Get<CursorPage<Conversation>>(
    '/message/app/conversation',
    { params },
  )
}

export function getConversationMessages(
  conversationId: string,
  params: { afterSeq?: number, beforeSeq?: number, limit?: number },
) {
  return alovaInstance.Get<ChatMessagePage>(
    `/message/app/conversation/${conversationId}/messages`,
    { params },
  )
}

export function sendConversationMessage(
  conversationId: string,
  data: {
    clientMessageId: string
    content?: string
    messageType: ChatMessageType
    payload?: string
  },
) {
  return alovaInstance.Post<ChatMessage>(
    `/message/app/conversation/${conversationId}/messages`,
    data,
  )
}

export function markConversationRead(
  conversationId: string,
  lastReadSeq: number,
) {
  return alovaInstance.Post(
    `/message/app/conversation/${conversationId}/read`,
    { lastReadSeq },
  )
}

export function closeConversation(conversationId: string, reason?: string) {
  return alovaInstance.Post(
    `/message/app/conversation/${conversationId}/close`,
    { reason },
  )
}
