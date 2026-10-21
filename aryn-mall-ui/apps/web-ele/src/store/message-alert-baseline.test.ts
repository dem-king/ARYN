import type { NotificationItem } from '@vben/layouts';

import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { getConversationAttention } from '#/api/message/conversation';
import {
  getStaffNoticeInbox,
  getStaffNoticeUnreadCount,
  markAllStaffNoticesRead,
  markStaffNoticeRead,
} from '#/api/message/notice';
import { playChime, speak } from '#/composables/use-message-alert';

import { useMessageStore } from './message';

/**
 * 「读掉一条之后，下一单就不响了」的回归守卫。
 *
 * 真实缺陷：上升沿判定拿「上一次 refresh 的未读快照」当基线，但在铃铛里点掉
 * 一条通知时只改了 unreadCount、没有同步基线。于是下一条新消息把计数顶回原值，
 * 视图看到的是「没变」，提醒被静默——站内信照样到、铃铛照样有数，就是没声音。
 */
vi.mock('#/api/message/notice', () => ({
  getStaffNoticeInbox: vi.fn(),
  getStaffNoticeUnreadCount: vi.fn(),
  markAllStaffNoticesRead: vi.fn(),
  markStaffNoticeRead: vi.fn(),
}));

vi.mock('#/api/message/conversation', () => ({
  getConversationAttention: vi.fn(),
}));

vi.mock('#/composables/use-message-alert', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('#/composables/use-message-alert')>();
  return {
    ...actual,
    // 音频可用性在 jsdom 里没有真实实现，缺省桩成「可以直接发声」，
    // 避免补播队列吞掉本轮断言；验证被自动播放策略拦住时再改回 false
    isAudioPlayable: vi.fn(() => true),
    playChime: vi.fn(),
    queueAudioReplay: vi.fn(),
    showDesktopNotification: vi.fn(),
    speak: vi.fn(),
  };
});

function noticePage(title: string) {
  return {
    hasMore: false,
    records: [
      {
        cardPayload: JSON.stringify({
          amount: '88.00',
          orderId: '2111728881667751936',
          title,
        }),
        category: 'ORDER',
        content: `${title} 内容`,
        messageId: `notice-${title}`,
        priority: 'NORMAL',
        publishTime: '2026-10-18 15:59:35',
        readStatus: '0' as const,
        receivedTime: '2026-10-18 15:59:35',
        recipientRecordId: `recipient-${title}`,
        sourceType: 'ORDER_CREATE',
        summary: `${title} 摘要`,
        title,
      },
    ],
  };
}

describe('unread baseline after local mark-read', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getConversationAttention).mockResolvedValue({
      conversations: [],
      unreadConversations: 0,
      waitingTotal: 0,
    });
    setActivePinia(createPinia());
  });

  it('still alerts on the next new order after a notice was read', async () => {
    const store = useMessageStore();

    // 定基线：存量 1 条未读不提醒
    vi.mocked(getStaffNoticeUnreadCount).mockResolvedValueOnce(1);
    vi.mocked(getStaffNoticeInbox).mockResolvedValueOnce(
      noticePage('新订单待处理'),
    );
    await store.refreshNotifications();
    expect(playChime).not.toHaveBeenCalled();

    // 用户在铃铛里点掉这条
    const readItem = store.notifications.find(
      (item) => item.kind === 'notice',
    ) as NotificationItem & { messageId: string };
    vi.mocked(markStaffNoticeRead).mockResolvedValueOnce(undefined);
    await store.markRead(readItem);
    expect(store.unreadCount).toBe(0);

    // 下一单到达：后端计数回到 1，与读之前相同
    vi.mocked(getStaffNoticeUnreadCount).mockResolvedValueOnce(1);
    vi.mocked(getStaffNoticeInbox).mockResolvedValueOnce(
      noticePage('新订单待处理-2'),
    );
    await store.refreshNotifications();

    // 计数“看起来没变”，但确实来了一条新消息，必须响
    expect(playChime).toHaveBeenCalledTimes(1);
    expect(speak).toHaveBeenCalledWith('您有一笔新订单，请及时处理');
  });

  it('still alerts after marking everything read', async () => {
    const store = useMessageStore();

    vi.mocked(getStaffNoticeUnreadCount).mockResolvedValueOnce(3);
    vi.mocked(getStaffNoticeInbox).mockResolvedValueOnce(
      noticePage('新订单待处理'),
    );
    await store.refreshNotifications();
    expect(playChime).not.toHaveBeenCalled();

    vi.mocked(markAllStaffNoticesRead).mockResolvedValueOnce(undefined);
    await store.markAllRead();
    expect(store.unreadCount).toBe(0);

    vi.mocked(getStaffNoticeUnreadCount).mockResolvedValueOnce(3);
    vi.mocked(getStaffNoticeInbox).mockResolvedValueOnce(
      noticePage('新订单待处理-批量'),
    );
    await store.refreshNotifications();

    expect(playChime).toHaveBeenCalledTimes(1);
  });

  it('keeps silent for a refresh that is not a real increase', async () => {
    const store = useMessageStore();

    vi.mocked(getStaffNoticeUnreadCount).mockResolvedValue(2);
    vi.mocked(getStaffNoticeInbox).mockResolvedValue(
      noticePage('新订单待处理'),
    );
    await store.refreshNotifications();
    // 重复推送但未读没变（例如同一事件重投）：不能重复响
    await store.refreshNotifications();

    expect(playChime).not.toHaveBeenCalled();
  });

  it('queues the chime instead of dropping it while audio is still locked', async () => {
    const { isAudioPlayable, queueAudioReplay } =
      await import('#/composables/use-message-alert');
    vi.mocked(isAudioPlayable).mockReturnValue(false);
    const store = useMessageStore();

    // 首轮只定基线（存量未读不提醒），第二轮才是真正的新消息
    vi.mocked(getStaffNoticeUnreadCount).mockResolvedValueOnce(1);
    vi.mocked(getStaffNoticeInbox).mockResolvedValueOnce(
      noticePage('存量通知'),
    );
    await store.refreshNotifications();
    expect(queueAudioReplay).not.toHaveBeenCalled();

    vi.mocked(getStaffNoticeUnreadCount).mockResolvedValueOnce(2);
    vi.mocked(getStaffNoticeInbox).mockResolvedValueOnce(
      noticePage('新订单待处理'),
    );
    await store.refreshNotifications();

    // 自动播放策略拦下提示音时不能彻底静默：排进补播队列，首次手势后补响
    expect(queueAudioReplay).toHaveBeenCalledTimes(1);
    expect(playChime).not.toHaveBeenCalled();
    // 语音不依赖 AudioContext，必须立刻念，不能等解锁
    expect(speak).toHaveBeenCalledWith('您有一笔新订单，请及时处理');
  });
});

// 音频补播队列是模块级状态，须在未 mock 本模块的用例里验证（见 use-message-alert.test.ts）
