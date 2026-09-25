import { describe, expect, it } from 'vitest';

import {
  buildStaffWebSocketUrl,
  isConversationPush,
  parsePushSignal,
} from './message';

describe('staff websocket url', () => {
  it('keeps the api prefix in Boot mode', () => {
    expect(
      buildStaffWebSocketUrl({
        apiUrl: '/api',
        openBoot: true,
        origin: 'http://localhost:5777',
        token: 'token-1',
      }),
    ).toBe('ws://localhost:5777/api/boot/ws/staff?satoken=token-1');
  });

  it('keeps the api prefix and the service domain in Cloud mode', () => {
    expect(
      buildStaffWebSocketUrl({
        apiUrl: '/api',
        openBoot: false,
        origin: 'http://localhost:5777',
      }),
    ).toBe('ws://localhost:5777/api/message/ws/staff');
  });

  it('upgrades to wss and tolerates a trailing slash in the api url', () => {
    expect(
      buildStaffWebSocketUrl({
        apiUrl: '/api/',
        openBoot: true,
        origin: 'https://admin.example.com',
        token: 'token-1',
      }),
    ).toBe('wss://admin.example.com/api/boot/ws/staff?satoken=token-1');
  });

  it('falls back to the page origin when no api url is configured', () => {
    expect(
      buildStaffWebSocketUrl({
        openBoot: false,
        origin: 'http://localhost:5777',
      }),
    ).toBe('ws://localhost:5777/message/ws/staff');
  });
});

describe('push signal parsing', () => {
  it('parses a conversation message event', () => {
    expect(
      parsePushSignal(
        JSON.stringify({
          conversationId: 'conversation-1',
          eventType: 'CONVERSATION_MESSAGE',
          messageId: 'message-1',
          seqNo: 12,
        }),
      ),
    ).toEqual({
      conversationId: 'conversation-1',
      eventType: 'CONVERSATION_MESSAGE',
      messageId: 'message-1',
      seqNo: 12,
    });
  });

  it('ignores heartbeat and malformed payloads', () => {
    expect(parsePushSignal('PONG')).toBeUndefined();
    expect(parsePushSignal('{')).toBeUndefined();
    expect(parsePushSignal('not-json')).toBeUndefined();
    expect(parsePushSignal(JSON.stringify({ seqNo: 1 }))).toBeUndefined();
    expect(parsePushSignal(undefined)).toBeUndefined();
  });

  it('recognises only conversation events carrying a conversation id', () => {
    expect(
      isConversationPush({
        conversationId: 'conversation-1',
        eventType: 'CONVERSATION_MESSAGE',
      }),
    ).toBe(true);
    expect(
      isConversationPush({
        conversationId: 'conversation-1',
        eventType: 'CONVERSATION_QUEUED',
      }),
    ).toBe(true);
    expect(isConversationPush({ eventType: 'NOTICE_RECEIVED' })).toBe(false);
    expect(
      isConversationPush({ conversationId: 'conversation-1', eventType: 'X' }),
    ).toBe(false);
  });
});
