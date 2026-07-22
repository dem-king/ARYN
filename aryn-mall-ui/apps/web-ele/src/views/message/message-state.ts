import type { ChatMessage } from '#/api/message/types';

export type MessageSendState = 'failed' | 'sending' | 'sent';

export type ViewChatMessage = ChatMessage & {
  sendState?: MessageSendState;
};

export function mergeServerMessage(
  messages: ViewChatMessage[],
  serverMessage: ChatMessage,
): ViewChatMessage[] {
  const index = messages.findIndex(
    (item) => item.clientMessageId === serverMessage.clientMessageId,
  );
  const merged = [...messages];
  const next: ViewChatMessage = { ...serverMessage, sendState: 'sent' };
  if (index === -1) {
    merged.push(next);
  } else {
    merged.splice(index, 1, next);
  }
  return merged.sort((first, second) => first.seqNo - second.seqNo);
}

export function latestSequence(messages: Array<Pick<ChatMessage, 'seqNo'>>) {
  let latest = 0;
  for (const item of messages) latest = Math.max(latest, item.seqNo);
  return latest;
}

export function markMessageFailed(
  messages: ViewChatMessage[],
  clientMessageId: string,
): ViewChatMessage[] {
  return messages.map((item) =>
    item.clientMessageId === clientMessageId
      ? { ...item, sendState: 'failed' }
      : item,
  );
}
