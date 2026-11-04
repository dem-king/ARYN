export type IdentityType = 'MALL_USER' | 'SYS_USER';
export type NoticeStatus = 'DRAFT' | 'PUBLISHED' | 'REVOKED';
export type NoticeDispatchStatus =
  'FAILED' | 'NONE' | 'PARTIAL_FAILED' | 'RUNNING' | 'SUCCEEDED';
export type ConversationStatus = 'ACTIVE' | 'ASSIGNED' | 'CLOSED' | 'WAITING';
/** 工作人员会话列表的状态过滤：OPEN=WAITING/ASSIGNED/ACTIVE，CLOSED=已关闭。 */
export type ConversationInboxStatus = 'CLOSED' | 'OPEN';
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

export interface NoticeDispatchTask {
  audienceType: string;
  errorSummary?: string;
  estimatedCount?: number;
  failureCount?: number;
  id: string;
  lastHeartbeatTime?: string;
  retryCount?: number;
  status: string;
  successCount?: number;
  targetType: string;
}

export interface NoticeRecipientItem {
  inboxStatus: 'HIDDEN' | 'VISIBLE';
  readStatus: '0' | '1';
  readTime?: string;
  receivedTime: string;
  recipientId: string;
  recipientName?: string;
  recipientRecordId: string;
  recipientType: IdentityType;
}

export interface NoticeRecord {
  cardPayload?: string;
  audienceSnapshot?: string;
  category: string;
  content: string;
  createTime: string;
  dispatchStatus?: NoticeDispatchStatus;
  dispatchTasks?: NoticeDispatchTask[];
  expireTime?: string;
  hiddenCount?: number;
  id: string;
  jumpPayload?: string;
  jumpType?: string;
  priority: string;
  publishTime?: string;
  readCount?: number;
  recipientCount?: number;
  senderName?: string;
  senderType?: string;
  sourceType?: string;
  status: NoticeStatus;
  summary?: string;
  targetTypes: string;
  title: string;
}

export interface Conversation {
  /** 负责客服昵称快照 */
  assignedStaffAvatar?: string;
  assignedStaffId?: string;
  assignedStaffName?: string;
  closedTime?: string;
  contextPayload?: string;
  conversationType: 'CUSTOMER_SERVICE' | 'STAFF_DIRECT';
  customerAvatar?: string;
  customerId?: string;
  /** 会员昵称快照：缺失时前端回退展示 customerId */
  customerName?: string;
  id: string;
  lastMessageSummary?: string;
  lastMessageTime?: string;
  lastReadSeq: number;
  lastSeq: number;
  /** 对方最后已读的消息序号，用于「已读/未读」回执 */
  peerLastReadSeq?: number;
  queueCode: string;
  reopenDeadline?: string;
  status: ConversationStatus;
  unreadCount: number;
}

/** 主管视角的会话分配日志（强制分配/转交/领取等审计动作）。 */
export interface ConversationAssignmentLog {
  actionType: string;
  createTime?: string;
  fromStaffId?: string;
  operatorId?: string;
  operatorType?: string;
  reason?: string;
  toStaffId?: string;
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
