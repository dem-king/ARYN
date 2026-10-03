interface NotificationItem {
  avatar: string;
  /** 无头像图时显示的文字头像，用于客服会话这类没有图片的提醒。 */
  avatarText?: string;
  date: string;
  isRead?: boolean;
  /**
   * 提醒类型。客服会话类提醒点击后要跳到工作台，
   * 而不是走“标记已读”，因此需要与普通站内信区分。
   */
  kind?: 'conversation' | 'notice';
  message: string;
  title: string;
  /** 站内信 ID，用于标记已读。 */
  messageId?: string;
  /** 会话类提醒携带的会话 ID，用于点击后直达对应会话。 */
  conversationId?: string;
  /** 会话类提醒的状态角标，如“待领取”。 */
  tag?: string;
  /** 会话类提醒的未读条数。 */
  unreadCount?: number;
}

export type { NotificationItem };
