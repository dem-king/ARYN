import type { NotificationItem } from '@vben/layouts';

import type {
  ConversationAttention,
  NoticeInboxItem,
} from '#/api/message/types';

import { computed, onScopeDispose, ref } from 'vue';

import { useAppConfig } from '@vben/hooks';
import { useAccessStore } from '@vben/stores';
import { parseNoticeTime } from '@vben/utils';

import { defineStore } from 'pinia';

import { parseOpenBoot, rewriteBootUrl } from '#/api/boot-url';
import { getConversationAttention } from '#/api/message/conversation';
import {
  getStaffNoticeInbox,
  getStaffNoticeUnreadCount,
  markAllStaffNoticesRead,
  markStaffNoticeRead,
} from '#/api/message/notice';
import {
  ALERT_DESKTOP_KEY,
  ALERT_SOUND_KEY,
  ALERT_VOICE_KEY,
  armAudioUnlock,
  buildAlert,
  buildSpeech,
  detachAudioUnlock,
  isAudioPlayable,
  parseCardAmount,
  playChime,
  queueAudioReplay,
  readToggle,
  requestDesktopPermission,
  shouldSpeak,
  showDesktopNotification,
  speak,
  unreadIncrease,
  writeToggle,
} from '#/composables/use-message-alert';
import { resolveNoticeLink } from '#/utils/notice-link';

export interface MessagePushSignal {
  conversationId?: string;
  eventType: string;
  messageId?: string;
  seqNo?: number;
}

/** 会话相关的推送事件，收到后需要按会话补拉。 */
const CONVERSATION_EVENT_TYPES = new Set([
  'CONVERSATION_MESSAGE',
  'CONVERSATION_QUEUED',
]);

/**
 * 解析服务端下发的轻量推送事件。
 *
 * 事件只表示“数据已变化”，正文仍由 HTTP 游标补拉；非 JSON 或缺少事件类型时忽略。
 */
export function parsePushSignal(raw: unknown): MessagePushSignal | undefined {
  if (typeof raw !== 'string' || !raw.startsWith('{')) return undefined;
  let payload: unknown;
  try {
    payload = JSON.parse(raw);
  } catch {
    return undefined;
  }
  if (!payload || typeof payload !== 'object') return undefined;
  const event = payload as Record<string, unknown>;
  if (typeof event.eventType !== 'string') return undefined;
  return {
    conversationId:
      typeof event.conversationId === 'string'
        ? event.conversationId
        : undefined,
    eventType: event.eventType,
    messageId:
      typeof event.messageId === 'string' ? event.messageId : undefined,
    seqNo: typeof event.seqNo === 'number' ? event.seqNo : undefined,
  };
}

export function isConversationPush(signal?: MessagePushSignal) {
  return (
    !!signal?.conversationId && CONVERSATION_EVENT_TYPES.has(signal.eventType)
  );
}

/**
 * 拼装工作人员实时通道地址。
 *
 * 接口前缀（开发与生产的 `/api`）必须保留：去掉后既不被开发代理转发，
 * 也匹配不到生产 nginx 与网关的转发规则。
 */
export function buildStaffWebSocketUrl(options: {
  apiUrl?: string;
  openBoot?: boolean | string;
  origin: string;
  token?: null | string;
}) {
  const base = new URL(options.apiUrl || '/', options.origin);
  base.protocol = base.protocol === 'https:' ? 'wss:' : 'ws:';
  const path =
    rewriteBootUrl('/message/ws/staff', parseOpenBoot(options.openBoot)) ??
    '/message/ws/staff';
  base.pathname = `${base.pathname.replace(/\/+$/, '')}${path}`;
  if (options.token) base.searchParams.set('satoken', options.token);
  return base.toString();
}

export const useMessageStore = defineStore('message', () => {
  const unreadCount = ref(0);
  const notifications = ref<NotificationItem[]>([]);
  const conversationUnread = ref(0);
  const socketState = ref<'closed' | 'connecting' | 'open'>('closed');
  const lastConversationPush = ref<MessagePushSignal>();
  /** 新消息强提醒开关：声音/语音/桌面通知各自独立，缺省开启（存储里显式存 '0' 才关闭）。 */
  const soundEnabled = ref(readToggle(ALERT_SOUND_KEY));
  const voiceEnabled = ref(readToggle(ALERT_VOICE_KEY));
  const desktopEnabled = ref(readToggle(ALERT_DESKTOP_KEY));
  /** 上一次的未读快照：只在上升沿提醒，首次拉取只用来定基线。 */
  let lastAlertCounts:
    undefined | { conversationUnread: number; noticeUnread: number };
  /** 上次语音播报时间：连单高峰节流，避免逐条朗读刷屏。 */
  let lastSpokenAt = 0;
  /**
   * 铃铛点击后要定位的会话。
   *
   * 走 store 而不是路由 query：工作台的标签页 key 取自 fullPath，
   * 用 query 会让每个会话都开一个新标签页。
   */
  const pendingConversationId = ref<string>();
  let socket: null | WebSocket = null;
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
  let shouldReconnect = false;
  let sessionGeneration = 0;

  const showDot = computed(() => unreadCount.value > 0);

  /** 铃铛角标 = 站内信未读 + 客服会话未读（含共享池待领取）。 */
  const totalUnread = computed(
    () => unreadCount.value + conversationUnread.value,
  );

  /**
   * 会话提醒：未读的本人会话在前，共享池待领取在后。
   *
   * 待领取会话对坐席是不可读的（还没有参与者身份），因此单独标注状态，
   * 点击后进入工作台的“待领取”页签而不是直接打开会话。
   */
  function conversationNotifications(
    attention: ConversationAttention,
  ): NotificationItem[] {
    return attention.conversations.map((item) => {
      const waiting = item.status === 'WAITING';
      const name =
        item.conversationType === 'STAFF_DIRECT'
          ? '工作人员私信'
          : `会员 ${item.customerId || ''}`.trim();
      return {
        avatar: '',
        avatarText: item.conversationType === 'STAFF_DIRECT' ? '内' : '客',
        category: 'conversation',
        conversationId: item.id,
        date: item.lastMessageTime || '',
        isRead: !waiting && !item.unreadCount,
        kind: 'conversation',
        link: '/message/service',
        message: item.lastMessageSummary || '暂无新消息',
        tag: waiting ? '待领取' : undefined,
        timestamp: parseNoticeTime(item.lastMessageTime),
        title: name,
        unreadCount: waiting ? 0 : item.unreadCount,
      };
    });
  }

  /** 站内信条目：补上分类、金额与点击跳转路由，铃铛和收件箱共用同一套解析。 */
  function noticeNotification(item: NoticeInboxItem): NotificationItem {
    return {
      amount: parseCardAmount(item.cardPayload),
      avatar: '/favicon.ico',
      category: item.category,
      date: item.publishTime || item.receivedTime,
      isRead: item.readStatus === '1',
      kind: 'notice',
      link: resolveNoticeLink(item),
      message: item.summary || item.content,
      messageId: item.messageId,
      priority: item.priority,
      sourceType: item.sourceType,
      timestamp: parseNoticeTime(item.publishTime || item.receivedTime),
      title: item.title,
    };
  }

  async function refreshNotifications() {
    const requestGeneration = sessionGeneration;
    const [count, page, attention] = await Promise.all([
      getStaffNoticeUnreadCount(),
      getStaffNoticeInbox({ limit: 6 }),
      // 会话提醒失败不能拖垮站内信：非坐席账号调用该接口没有意义，
      // 这里降级为“没有会话提醒”，避免整个铃铛空白。
      getConversationAttention({ limit: 10 }).catch(() => undefined),
    ]);
    if (requestGeneration !== sessionGeneration) return;
    unreadCount.value = count;
    const notices = page.records.map((item) => noticeNotification(item));
    conversationUnread.value = attention
      ? attention.unreadConversations + attention.waitingTotal
      : 0;
    notifications.value = attention
      ? [...conversationNotifications(attention), ...notices]
      : notices;
    alertOnUnreadIncrease();
  }

  /**
   * 未读上升沿提醒：提示音 + 语音播报 + 桌面通知。
   *
   * 必须在 refreshNotifications 末尾调用：此时 notifications 已是最新列表，
   * 提醒文案才能带上「谁发来的 / 多少钱」。首次调用只定基线（登录后的存量未读不提醒）。
   */
  function alertOnUnreadIncrease() {
    const current = {
      conversationUnread: conversationUnread.value,
      noticeUnread: unreadCount.value,
    };
    const increase = unreadIncrease(lastAlertCounts, current);
    lastAlertCounts = current;
    if (!increase) return;
    const payload = buildAlert(increase, notifications.value);
    // 语音走 Web Speech：Chrome 同样要求页面获得过用户手势（无手势 speak 会
    // error=not-allowed 且不抛异常）。这里仍然立刻尝试，被拦时 speak() 内部会
    // 排队等首次手势补播，不会像以前那样永久静默。
    if (voiceEnabled.value) {
      const now = Date.now();
      if (shouldSpeak(now, lastSpokenAt)) {
        lastSpokenAt = now;
        speak(buildSpeech(payload));
      }
    }
    if (soundEnabled.value) {
      if (isAudioPlayable()) {
        playChime();
      } else {
        // 提示音靠 WebAudio，上下文仍被自动播放策略挂起时不会报错、只是没声。
        // 排队等首次手势补响，否则「打开后台还没点过任何东西」时这一段彻底静默。
        queueAudioReplay(playChime);
      }
    }
    if (desktopEnabled.value) showDesktopNotification(payload);
  }

  /**
   * 把未读基线重新对齐到当前显示值。
   *
   * 已读会让计数**下降**，而基线只在 refresh 里更新——若不在本地已读时同步，
   * 下一条新消息把计数顶回原值时，上升沿判定看到的是「没变」而静默。
   * 这正是「点掉一条通知后，下一单就没有声音」的成因，必须显式对齐。
   */
  function syncAlertBaseline() {
    lastAlertCounts = {
      conversationUnread: conversationUnread.value,
      noticeUnread: unreadCount.value,
    };
  }

  /**
   * 开关切换：打开桌面通知时才申请权限（浏览器要求用户手势），
   * 权限被拒就把开关落回关闭，避免出现「开着但永远不会响」。
   */
  async function setDesktopEnabled(enabled: boolean) {
    if (!enabled) {
      desktopEnabled.value = false;
      writeToggle(ALERT_DESKTOP_KEY, false);
      return;
    }
    const permission = await requestDesktopPermission();
    if (permission === 'granted') {
      desktopEnabled.value = true;
      writeToggle(ALERT_DESKTOP_KEY, true);
      return;
    }
    desktopEnabled.value = false;
    writeToggle(ALERT_DESKTOP_KEY, false);
  }

  function setSoundEnabled(enabled: boolean) {
    soundEnabled.value = enabled;
    writeToggle(ALERT_SOUND_KEY, enabled);
    // 开关本身就是一次用户手势：顺手解锁音频上下文，立即让用户听到效果
    if (enabled) playChime();
  }

  function setVoiceEnabled(enabled: boolean) {
    voiceEnabled.value = enabled;
    writeToggle(ALERT_VOICE_KEY, enabled);
    if (enabled) {
      // 试播一句：既解锁音频，也让用户确认本机有可用音色（无音色时会静默）
      speak('语音播报已开启');
    }
  }

  async function markRead(item: NotificationItem) {
    const { messageId } = item;
    if (!messageId || item.isRead) return;
    const requestGeneration = sessionGeneration;
    await markStaffNoticeRead(messageId);
    if (requestGeneration !== sessionGeneration) return;
    item.isRead = true;
    unreadCount.value = Math.max(0, unreadCount.value - 1);
    // 已读是同一次「读掉」的事实：基线必须跟着降，否则下一条新消息顶回原值时不响
    syncAlertBaseline();
  }

  /**
   * 全部标记已读只覆盖站内信。
   *
   * 会话未读由工作台里的已读游标驱动，在铃铛里“一键已读”等于假装读过，
   * 会让坐席漏掉消息，因此这里保留会话未读计数。
   */
  async function markAllRead() {
    const requestGeneration = sessionGeneration;
    await markAllStaffNoticesRead();
    if (requestGeneration !== sessionGeneration) return;
    notifications.value.forEach((item) => {
      if (item.kind !== 'conversation') item.isRead = true;
    });
    unreadCount.value = 0;
    // 同 markRead：全部已读后基线归零，否则下一条新消息只是把计数顶回原值
    syncAlertBaseline();
  }

  function websocketUrl() {
    // 与 HTTP 请求共用同一份运行时配置，避免两者指向不同地址。
    const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
    return buildStaffWebSocketUrl({
      apiUrl: apiURL,
      openBoot: import.meta.env.VITE_OPEN_BOOT,
      origin: window.location.origin,
      token: useAccessStore().accessToken,
    });
  }

  function connect() {
    if (
      socket?.readyState === WebSocket.OPEN ||
      socketState.value === 'connecting'
    )
      return;
    shouldReconnect = true;
    socketState.value = 'connecting';
    const currentSocket = new WebSocket(websocketUrl());
    socket = currentSocket;
    currentSocket.addEventListener('open', () => {
      if (socket !== currentSocket) return;
      socketState.value = 'open';
    });
    currentSocket.addEventListener('message', (event) => {
      if (socket !== currentSocket) return;
      void refreshNotifications();
      const signal = parsePushSignal((event as MessageEvent).data);
      if (isConversationPush(signal)) lastConversationPush.value = signal;
    });
    currentSocket.addEventListener('close', () => {
      if (socket !== currentSocket) return;
      socket = null;
      socketState.value = 'closed';
      if (shouldReconnect) {
        reconnectTimer = setTimeout(() => {
          reconnectTimer = undefined;
          connect();
        }, 3000);
      }
    });
    currentSocket.addEventListener('error', () => currentSocket.close());
  }

  function disconnect() {
    shouldReconnect = false;
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = undefined;
    }
    const currentSocket = socket;
    socket = null;
    currentSocket?.close();
    socketState.value = 'closed';
  }

  /** 请求工作台定位到指定会话；工作台消费后调用 clearPendingConversation。 */
  function requestConversation(conversationId: string) {
    pendingConversationId.value = conversationId;
  }

  function clearPendingConversation() {
    pendingConversationId.value = undefined;
  }

  function $reset() {
    sessionGeneration += 1;
    disconnect();
    unreadCount.value = 0;
    conversationUnread.value = 0;
    notifications.value = [];
    lastConversationPush.value = undefined;
    pendingConversationId.value = undefined;
    // 清掉未读基线：下次登录后的首个快照只用来定基线，不会把存量未读当新消息提醒
    lastAlertCounts = undefined;
    lastSpokenAt = 0;
    detachAudioUnlock();
  }

  /**
   * 建立实时提醒链路：连接推送 + 挂首次手势解锁音频。
   *
   * 手势解锁必须在这里挂上（而不是等用户点开关）：浏览器 autoplay 策略下，
   * 未解锁的音频不会发声——只靠开关解锁会让「打开后台后什么都没点」的管理员
   * 静默漏掉第一条新订单。
   */
  function start() {
    armAudioUnlock();
    refreshNotifications();
    connect();
  }

  onScopeDispose(disconnect);

  return {
    $reset,
    clearPendingConversation,
    connect,
    conversationUnread,
    desktopEnabled,
    disconnect,
    lastConversationPush,
    markAllRead,
    markRead,
    notifications,
    pendingConversationId,
    refreshNotifications,
    requestConversation,
    setDesktopEnabled,
    setSoundEnabled,
    setVoiceEnabled,
    showDot,
    socketState,
    soundEnabled,
    start,
    totalUnread,
    unreadCount,
    voiceEnabled,
  };
});
