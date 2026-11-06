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
  /** 会话创建时间；历史数据可能为 null */
  createTime?: string;
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
  /** 进入等待的时间；仅 status=WAITING 时有值，非 WAITING 为 null（勿用 lastMessageTime 推算） */
  waitingSince?: string;
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

/** 坐席在线状态：ONLINE/BUSY/PAUSED 表示 presence 有效，OFFLINE 为显式离线或心跳过期。 */
export type AgentPresenceStatus = 'BUSY' | 'OFFLINE' | 'ONLINE' | 'PAUSED';

/**
 * 在线坐席名单元素（转交/强制分配/发起会话选人）。
 *
 * 数据源为已配置坐席 + Redis presence + 负载快照，见契约 §4.3；
 * `enabled`/`autoAccept` 沿用消息域 char(1) 字符串约定。
 */
export interface AgentRosterItem {
  /** 是否可接新会话：启用 + ONLINE + 未满员 */
  available: boolean;
  /** 自动接待："1" 是 / "0" 否 */
  autoAccept: '0' | '1';
  /** 当前在办会话数快照 */
  currentActiveCount: number;
  /** 启用状态："1" 是 / "0" 否 */
  enabled: '0' | '1';
  /** 接待上限 */
  maxActiveCount: number;
  presenceStatus: AgentPresenceStatus;
  /** 坐席头像（upms 人员快照，缺失/降级时为 null） */
  staffAvatar?: string;
  /** 工作人员 ID */
  staffId: string;
  /** 坐席昵称（upms 人员快照，缺失/降级时为 null，前端回退展示 staffId） */
  staffName?: string;
}

/** 队列统计的单个队列分组（只含有等待会话的队列）。 */
export interface QueueStatsGroup {
  /** 该队列最久等待开始时间（yyyy-MM-dd HH:mm:ss） */
  oldestWaitingSince: string;
  queueCode: string;
  /** 该队列等待中会话数（≥1） */
  waitingCount: number;
}

/** 队列统计的全租户汇总（无等待时也返回）。 */
export interface QueueStatsTotal {
  /** 可用接待容量：仅启用且 ONLINE 坐席的剩余容量之和 */
  availableCapacity: number;
  /** 全部等待会话中最早的等待开始时间；无等待时为 null */
  oldestWaitingSince?: string;
  /** 在线坐席数：启用且 presence 有效（ONLINE/BUSY/PAUSED） */
  onlineAgentCount: number;
  /** 全部队列等待中会话数合计 */
  waitingCount: number;
}

/** 队列统计：等待数/最久等待/在线坐席/可用容量。 */
export interface QueueStats {
  groups: QueueStatsGroup[];
  total: QueueStatsTotal;
}

export interface ChatMessage {
  clientMessageId: string;
  content?: string;
  conversationId: string;
  createTime: string;
  id: string;
  messageType: ChatMessageType;
  payload?: string;
  /** 引用目标消息 ID（无引用为 null/undefined） */
  quotedMessageId?: string;
  /** 引用目标消息类型；目标不可用/已撤回时为 null */
  quotedMessageType?: ChatMessageType;
  /** 引用摘要（后端截断 ≤100 字符）；目标不可用/已撤回时为 null */
  quotedMessageSummary?: string;
  /** 引用目标发送者昵称快照 */
  quotedSenderName?: string;
  /** 仅在有引用时出现：true=被引用消息已撤回/不可用，前端显示占位文案 */
  quotedRecalled?: boolean;
  /**
   * 撤回状态："0" 正常 / "1" 已撤回。
   *
   * 后端 VO 恒有值；本地乐观消息与测试构造可能缺失，故可选。
   */
  recallStatus?: '0' | '1';
  /** 撤回时间（yyyy-MM-dd HH:mm:ss），未撤回为 null */
  recallTime?: string;
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

/** 消息专用图片上传结果（契约 §17.4；fileId 与 url 同值）。 */
export interface MessageUploadResult {
  /** 规范化后的 MIME：image/jpeg / image/png / image/webp */
  contentType: string;
  /** = url（沿用 C 端把上传 URL 当 fileId 的既有约定） */
  fileId: string;
  /** 清洗后的原始文件名（≤100） */
  name: string;
  /** 字节数 */
  size: number;
  /** 可访问地址：cloud `/upms/file/local/{tenantId}/...`、boot `/boot/file/local/{tenantId}/...` */
  url: string;
}
