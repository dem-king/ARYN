export type IdentityType = 'MALL_USER' | 'SYS_USER';
export type ConversationStatus = 'ACTIVE' | 'ASSIGNED' | 'CLOSED' | 'WAITING';
export type ChatMessageType =
  | 'IMAGE'
  | 'NOTICE_CARD'
  | 'ORDER_CARD'
  | 'PRODUCT_CARD'
  | 'REFUND_CARD'
  | 'SYSTEM'
  | 'TEXT';

export interface NoticeInboxItem {
  cardPayload?: string;
  category: string;
  content: string;
  expireTime?: string;
  jumpPayload?: string;
  jumpType?: string;
  messageId: string;
  priority: string;
  publishTime: string;
  readStatus: '0' | '1';
  readTime?: string;
  receivedTime: string;
  recipientRecordId: string;
  senderName?: string;
  sourceType?: string;
  summary?: string;
  title: string;
}

export interface CursorPage<T> {
  hasMore: boolean;
  nextCursor?: string;
  records: T[];
}

export interface NoticeRecord {
  cardPayload?: string;
  audienceSnapshot?: string;
  category: string;
  content: string;
  createTime: string;
  expireTime?: string;
  id: string;
  jumpPayload?: string;
  jumpType?: string;
  priority: string;
  publishTime?: string;
  senderName?: string;
  sourceType?: string;
  status: 'DRAFT' | 'PUBLISHED' | 'REVOKED';
  summary?: string;
  targetTypes: string;
  title: string;
}

export interface Conversation {
  assignedStaffId?: string;
  closedTime?: string;
  contextPayload?: string;
  conversationType: 'CUSTOMER_SERVICE' | 'STAFF_DIRECT';
  customerId?: string;
  id: string;
  lastMessageSummary?: string;
  lastMessageTime?: string;
  lastReadSeq: number;
  lastSeq: number;
  queueCode: string;
  reopenDeadline?: string;
  status: ConversationStatus;
  unreadCount: number;
}

/** 坐席待办提醒：本人未读会话与共享池待领取。 */
export interface ConversationAttention {
  /** 逐个会话的提醒明细，未读在前、待领取在后。 */
  conversations: Conversation[];
  /** 存在未读消息的本人会话数。 */
  unreadConversations: number;
  /** 共享池中等待领取的会话数。 */
  waitingTotal: number;
}

export interface ChatMessage {
  clientMessageId: string;
  content?: string;
  conversationId: string;
  createTime: string;
  id: string;
  messageType: ChatMessageType;
  payload?: string;
  seqNo: number;
  senderAvatar?: string;
  senderId: string;
  senderName?: string;
  senderType: 'SYSTEM' | IdentityType;
}

export interface ChatMessagePage {
  hasMore: boolean;
  nextAfterSeq?: number;
  nextBeforeSeq?: number;
  records: ChatMessage[];
}
