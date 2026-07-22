import type { ViewChatMessage } from './message-state';

import type { ChatMessage } from '#/api/message/types';

import { describe, expect, it } from 'vitest';

import {
  latestSequence,
  markMessageFailed,
  mergeServerMessage,
} from './message-state';

function message(overrides: Partial<ViewChatMessage>): ViewChatMessage {
  return {
    clientMessageId: 'client-1',
    content: 'hello',
    conversationId: 'conversation-1',
    createTime: '2026-07-21 10:00:00',
    id: 'local:client-1',
    messageType: 'TEXT',
    seqNo: 0,
    senderId: 'staff-1',
    senderType: 'SYS_USER',
    ...overrides,
  };
}

describe('message state helpers', () => {
  it('replaces the optimistic item by clientMessageId', () => {
    const optimistic = message({ sendState: 'sending' });
    const server = message({ id: 'message-9', seqNo: 9 }) as ChatMessage;

    expect(mergeServerMessage([optimistic], server)).toEqual([
      expect.objectContaining({ id: 'message-9', sendState: 'sent', seqNo: 9 }),
    ]);
  });

  it('keeps messages ordered and exposes the recovery cursor', () => {
    const merged = mergeServerMessage(
      [message({ clientMessageId: 'client-2', id: 'message-10', seqNo: 10 })],
      message({ id: 'message-8', seqNo: 8 }) as ChatMessage,
    );

    expect(merged.map((item) => item.seqNo)).toEqual([8, 10]);
    expect(latestSequence(merged)).toBe(10);
  });

  it('marks only the matching optimistic item as failed', () => {
    const result = markMessageFailed(
      [
        message({ clientMessageId: 'client-1', sendState: 'sending' }),
        message({ clientMessageId: 'client-2', sendState: 'sending' }),
      ],
      'client-1',
    );

    expect(result.map((item) => item.sendState)).toEqual(['failed', 'sending']);
  });
});
