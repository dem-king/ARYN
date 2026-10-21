import type { NoticeCategory } from '@vben/utils';

interface NotificationItem {
  /** 站内信金额（已格式化为展示文案，如 `238.90`）。 */
  amount?: string;
  avatar: string;
  /** 无头像图时显示的文字头像，用于客服会话这类没有图片的提醒。 */
  avatarText?: string;
  /** 通知分类：订单/退款/船舶/系统/客服，铃铛据此选图标与筛选页签。 */
  category?: NoticeCategory | string;
  date: string;
  isRead?: boolean;
  /**
   * 提醒类型。客服会话类提醒点击后要跳到工作台，
   * 而不是走“标记已读”，因此需要与普通站内信区分。
   */
  kind?: 'conversation' | 'notice';
  /**
   * 点击后跳转的管理端站内路由，由应用层解析后传入；
   * 共享组件不感知业务路由，只负责把“可跳转”表达清楚。
   */
  link?: string;
  message: string;
  title: string;
  /** 站内信 ID，用于标记已读。 */
  messageId?: string;
  /** 业务优先级：HIGH 时列表展示「重要」标记。 */
  priority?: string;
  /** 后端业务来源（ORDER_CREATE / ORDER_REFUND_APPLY …），用于分类与文案。 */
  sourceType?: string;
  /** 会话类提醒携带的会话 ID，用于点击后直达对应会话。 */
  conversationId?: string;
  /** 会话类提醒的状态角标，如“待领取”。 */
  tag?: string;
  /** 已解析的消息时间戳（毫秒），用于紧凑时间展示；缺失时回退 date 字段。 */
  timestamp?: number;
  /** 会话类提醒的未读条数。 */
  unreadCount?: number;
}

export type { NotificationItem };
