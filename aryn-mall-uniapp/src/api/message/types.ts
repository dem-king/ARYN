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
  /** 负责客服头像（参与者快照，WAITING 时为空） */
  assignedStaffAvatar?: string
  assignedStaffId?: string
  /** 负责客服昵称：ASSIGNED/ACTIVE 时 C 端状态条展示「客服：{昵称}」 */
  assignedStaffName?: string
  closedTime?: string
  contextPayload?: string
  conversationType: 'CUSTOMER_SERVICE'
  /** 会员头像（参与者快照） */
  customerAvatar?: string
  customerId?: string
  /** 会员昵称（参与者快照） */
  customerName?: string
  id: string
  lastMessageSummary?: string
  lastMessageTime?: string
  lastReadSeq: number
  lastSeq: number
  /** 对方最后已读 seq：用于已读回执展示（C 端聊天页暂不消费，字段先按契约对齐） */
  peerLastReadSeq?: number
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
