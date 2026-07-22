export type ChatMessageType
  = | 'IMAGE'
    | 'NOTICE_CARD'
    | 'ORDER_CARD'
    | 'PRODUCT_CARD'
    | 'REFUND_CARD'
    | 'SYSTEM'
    | 'TEXT'

export interface CursorPage<T> {
  hasMore: boolean
  nextCursor?: string
  records: T[]
}

export interface NoticeInboxItem {
  cardPayload?: string
  category: string
  content: string
  expireTime?: string
  jumpPayload?: string
  jumpType?: string
  messageId: string
  priority: string
  publishTime: string
  readStatus: '0' | '1'
  readTime?: string
  receivedTime: string
  recipientRecordId: string
  senderName?: string
  sourceType?: string
  summary?: string
  title: string
}

export interface Conversation {
  assignedStaffId?: string
  closedTime?: string
  contextPayload?: string
  conversationType: 'CUSTOMER_SERVICE'
  customerId?: string
  id: string
  lastMessageSummary?: string
  lastMessageTime?: string
  lastReadSeq: number
  lastSeq: number
  queueCode: string
  reopenDeadline?: string
  status: 'ACTIVE' | 'ASSIGNED' | 'CLOSED' | 'WAITING'
  unreadCount: number
}

export interface ChatMessage {
  clientMessageId: string
  content?: string
  conversationId: string
  createTime: string
  id: string
  messageType: ChatMessageType
  payload?: string
  seqNo: number
  senderAvatar?: string
  senderId: string
  senderName?: string
  senderType: 'MALL_USER' | 'SYS_USER' | 'SYSTEM'
}

export interface ChatMessagePage {
  hasMore: boolean
  nextAfterSeq?: number
  nextBeforeSeq?: number
  records: ChatMessage[]
}
