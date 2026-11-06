import type { ConversationInboxQueryParams } from './conversation';
import type {
  AgentRosterItem,
  ChatMessage,
  ChatMessageType,
  Conversation,
  MessageUploadResult,
  QueueStats,
  QueueStatsGroup,
  QueueStatsTotal,
} from './types';

import { beforeEach, describe, expect, expectTypeOf, it, vi } from 'vitest';

import { requestClient } from '#/api/request';

import { getAgentRoster } from './agent';
import {
  getAdminConversations,
  getQueueStats,
  getStaffConversations,
  recallConversationMessage,
  sendConversationMessage,
} from './conversation';
import { uploadConversationImage } from './upload';

/**
 * P2-A 前端 API 契约（docs/50-需求文档/.../P2-A-接口契约.md）。
 *
 * 这里只锁「前端只负责」的部分：请求路径（微服务域 `message`，双模式改写由
 * requestClient 统一处理）、筛选参数原样透传、以及契约字段的 TS 形状。
 */
const { postMock } = vi.hoisted(() => ({ postMock: vi.fn() }));

vi.mock('#/api/request', () => ({
  requestClient: {
    delete: vi.fn(),
    download: vi.fn(),
    get: vi.fn(),
    patch: vi.fn(),
    post: postMock,
    put: vi.fn(),
  },
}));

const inboxFilters: ConversationInboxQueryParams = {
  assignedStaffId: '1958100000000000011',
  cursor: 'cursor-1',
  keyword: '王小雅',
  limit: 50,
  queueCode: 'DEFAULT',
  statuses: 'WAITING,ASSIGNED',
  unreadOnly: true,
};

describe('p2-A 会话列表筛选参数', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('staff 列表透传全部筛选参数到 /message/staff/conversation', async () => {
    await getStaffConversations({ ...inboxFilters });

    expect(requestClient.get).toHaveBeenCalledWith(
      '/message/staff/conversation',
      { params: inboxFilters },
    );
  });

  it('admin 列表透传全部筛选参数到 /message/admin/conversation/page', async () => {
    await getAdminConversations({ ...inboxFilters });

    expect(requestClient.get).toHaveBeenCalledWith(
      '/message/admin/conversation/page',
      { params: inboxFilters },
    );
  });

  it('statuses 是逗号拼接的 string，不做数组序列化', () => {
    expectTypeOf<ConversationInboxQueryParams['statuses']>().toEqualTypeOf<
      string | undefined
    >();
    expectTypeOf<ConversationInboxQueryParams['unreadOnly']>().toEqualTypeOf<
      boolean | undefined
    >();
  });

  it('旧页签 status 仍可用且不强制与 statuses 同时下发', async () => {
    await getStaffConversations({ limit: 20, status: 'OPEN' });

    expect(requestClient.get).toHaveBeenCalledWith(
      '/message/staff/conversation',
      { params: { limit: 20, status: 'OPEN' } },
    );
  });
});

describe('p2-A roster / queue-stats 请求路径', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('getAgentRoster 请求 /message/staff/agent/roster 且无参数', async () => {
    await getAgentRoster();

    expect(requestClient.get).toHaveBeenCalledWith(
      '/message/staff/agent/roster',
    );
  });

  it('getQueueStats 请求 /message/staff/conversation/queue-stats 且无参数', async () => {
    await getQueueStats();

    expect(requestClient.get).toHaveBeenCalledWith(
      '/message/staff/conversation/queue-stats',
    );
  });

  it('契约字段的 TS 形状：Conversation 增 createTime/waitingSince', () => {
    expectTypeOf<Conversation>().toHaveProperty('createTime');
    expectTypeOf<Conversation>().toHaveProperty('waitingSince');
    expectTypeOf<Conversation['createTime']>().toEqualTypeOf<
      string | undefined
    >();
    expectTypeOf<Conversation['waitingSince']>().toEqualTypeOf<
      string | undefined
    >();
  });

  it('契约字段的 TS 形状：AgentRosterItem', () => {
    expectTypeOf<AgentRosterItem>().toHaveProperty('staffId');
    expectTypeOf<AgentRosterItem>().toHaveProperty('staffName');
    expectTypeOf<AgentRosterItem>().toHaveProperty('staffAvatar');
    expectTypeOf<AgentRosterItem>().toHaveProperty('presenceStatus');
    expectTypeOf<AgentRosterItem>().toHaveProperty('enabled');
    expectTypeOf<AgentRosterItem>().toHaveProperty('autoAccept');
    expectTypeOf<AgentRosterItem>().toHaveProperty('currentActiveCount');
    expectTypeOf<AgentRosterItem>().toHaveProperty('maxActiveCount');
    expectTypeOf<AgentRosterItem>().toHaveProperty('available');
    expectTypeOf<AgentRosterItem['enabled']>().toEqualTypeOf<'0' | '1'>();
    expectTypeOf<AgentRosterItem['presenceStatus']>().toEqualTypeOf<
      'BUSY' | 'OFFLINE' | 'ONLINE' | 'PAUSED'
    >();
    expectTypeOf<AgentRosterItem['available']>().toEqualTypeOf<boolean>();
  });

  it('契约字段的 TS 形状：QueueStats 的 total 与 groups', () => {
    expectTypeOf<QueueStats>().toHaveProperty('total');
    expectTypeOf<QueueStats>().toHaveProperty('groups');
    expectTypeOf<QueueStatsTotal>().toHaveProperty('waitingCount');
    expectTypeOf<QueueStatsTotal>().toHaveProperty('oldestWaitingSince');
    expectTypeOf<QueueStatsTotal>().toHaveProperty('onlineAgentCount');
    expectTypeOf<QueueStatsTotal>().toHaveProperty('availableCapacity');
    expectTypeOf<QueueStatsGroup>().toHaveProperty('queueCode');
    expectTypeOf<QueueStatsGroup>().toHaveProperty('waitingCount');
    expectTypeOf<QueueStatsGroup>().toHaveProperty('oldestWaitingSince');
    expectTypeOf<QueueStats['total']>().toEqualTypeOf<QueueStatsTotal>();
    expectTypeOf<QueueStats['groups']>().toEqualTypeOf<QueueStatsGroup[]>();
  });
});

describe('p2-A 批次二：引用 / 撤回 / 图片上传', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('发送引用消息时把 quotedMessageId 一并透传给既有发送接口', async () => {
    await sendConversationMessage('conv-1', {
      clientMessageId: 'client-1',
      content: '收到，我马上处理',
      messageType: 'TEXT',
      quotedMessageId: 'msg-9',
    });

    expect(requestClient.post).toHaveBeenCalledWith(
      '/message/staff/conversation/conv-1/messages',
      {
        clientMessageId: 'client-1',
        content: '收到，我马上处理',
        messageType: 'TEXT',
        quotedMessageId: 'msg-9',
      },
    );
  });

  it('撤回接口路径与契约 §16.1 一致', async () => {
    await recallConversationMessage('conv-1', 'msg-9');

    expect(requestClient.post).toHaveBeenCalledWith(
      '/message/staff/conversation/conv-1/messages/msg-9/recall',
    );
  });

  it('图片上传走 multipart FormData，字段名 file，不手写 Content-Type', async () => {
    const file = new File(['fake-image'], 'a.png', { type: 'image/png' });

    await uploadConversationImage(file);

    expect(postMock).toHaveBeenCalledTimes(1);
    const call = postMock.mock.calls[0] as [string, FormData];
    expect(call[0]).toBe('/message/staff/upload/image');
    expect(call[1]).toBeInstanceOf(FormData);
    expect(call[1].get('file')).toBe(file);
  });

  it('批次二字段 TS 形状：ChatMessage 引用/撤回字段', () => {
    expectTypeOf<ChatMessage>().toHaveProperty('quotedMessageId');
    expectTypeOf<ChatMessage>().toHaveProperty('quotedMessageSummary');
    expectTypeOf<ChatMessage>().toHaveProperty('quotedRecalled');
    expectTypeOf<ChatMessage>().toHaveProperty('quotedSenderName');
    expectTypeOf<ChatMessage>().toHaveProperty('recallStatus');
    expectTypeOf<ChatMessage>().toHaveProperty('recallTime');
    expectTypeOf<ChatMessage['quotedMessageType']>().toEqualTypeOf<
      ChatMessageType | undefined
    >();
    expectTypeOf<ChatMessage['quotedRecalled']>().toEqualTypeOf<
      boolean | undefined
    >();
    expectTypeOf<ChatMessage['recallStatus']>().toEqualTypeOf<
      '0' | '1' | undefined
    >();
    expectTypeOf<ChatMessage['recallTime']>().toEqualTypeOf<
      string | undefined
    >();
  });

  it('批次二字段 TS 形状：MessageUploadResult', () => {
    expectTypeOf<MessageUploadResult>().toHaveProperty('fileId');
    expectTypeOf<MessageUploadResult>().toHaveProperty('url');
    expectTypeOf<MessageUploadResult>().toHaveProperty('name');
    expectTypeOf<MessageUploadResult>().toHaveProperty('size');
    expectTypeOf<MessageUploadResult>().toHaveProperty('contentType');
    expectTypeOf<MessageUploadResult['size']>().toEqualTypeOf<number>();
  });
});
