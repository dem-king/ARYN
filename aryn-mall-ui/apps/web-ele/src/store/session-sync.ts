import type { SessionInvalidPayload } from './session';

import { useAccessStore } from '@vben/stores';

import { useSessionStore } from './session';

/**
 * 管理端多标签页会话同步（二期契约 §3.1）。
 *
 * <p>通道优先 `BroadcastChannel('aryn-session')`；不支持时退化为
 * `localStorage` 的 `storage` 事件（key 固定 `aryn-session-event`）。通道不可用时
 * **静默降级**，任何异常都不得影响会话失效主流程。</p>
 *
 * <p>消息协议：</p>
 * - `{type:'session-invalid', from, at, payload:{reason,subReason,invalidAt,device,detectedAt}}`
 *   → 其它标签页落状态 + 弹窗（不重复 toast、不 logout）；
 * - `{type:'session-restored', from, at}` → 其它标签页整页重载（token 已持久化）。
 *
 * <p>每条消息带模块级唯一 `tabId`，收到自己发的消息一律忽略（防回环）。</p>
 */
export const SESSION_SYNC_CHANNEL_NAME = 'aryn-session';
export const SESSION_SYNC_STORAGE_KEY = 'aryn-session-event';

export type SessionSyncMessage =
  | {
      at: number;
      from: string;
      payload: SessionInvalidPayload;
      type: 'session-invalid';
    }
  | {
      at: number;
      from: string;
      type: 'session-restored';
    };

let sessionTabId: null | string = null;
let channel: BroadcastChannel | null = null;
let channelHandler: ((event: MessageEvent) => void) | null = null;
let storageHandler: ((event: StorageEvent) => void) | null = null;
let listenerStarted = false;

/** 本标签页的模块级唯一 id（内存生成，刷新即变）。 */
export function getSessionTabId(): string {
  sessionTabId ??= createSessionTabId();
  return sessionTabId;
}

function createSessionTabId(): string {
  try {
    if (
      typeof crypto !== 'undefined' &&
      typeof crypto.randomUUID === 'function'
    ) {
      return crypto.randomUUID();
    }
  } catch {
    // 旧环境没有 crypto.randomUUID：降级到时间戳 + 随机串
  }
  return `tab-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

export function isBroadcastChannelSupported(): boolean {
  return typeof BroadcastChannel !== 'undefined';
}

function postSessionSyncMessage(message: SessionSyncMessage) {
  if (isBroadcastChannelSupported()) {
    try {
      channel ??= new BroadcastChannel(SESSION_SYNC_CHANNEL_NAME);
      channel.postMessage(message);
      return;
    } catch {
      // BroadcastChannel 异常：退化到 storage 事件
    }
  }
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(SESSION_SYNC_STORAGE_KEY, JSON.stringify(message));
    }
  } catch {
    // 静默降级：同步通道不可用不能影响会话失效主流程
  }
}

/** 本标签页首次进入失效态时广播（调用方保证仅在首次迁移时调用）。 */
export function broadcastSessionInvalid(payload: SessionInvalidPayload) {
  postSessionSyncMessage({
    at: Date.now(),
    from: getSessionTabId(),
    payload,
    type: 'session-invalid',
  });
}

/** 重登成功、清空失效态之前广播，让其它标签页整页重载恢复。 */
export function broadcastSessionRestored() {
  postSessionSyncMessage({
    at: Date.now(),
    from: getSessionTabId(),
    type: 'session-restored',
  });
}

function parseSessionSyncMessage(raw: unknown): null | SessionSyncMessage {
  let value = raw;
  if (typeof raw === 'string') {
    try {
      value = JSON.parse(raw);
    } catch {
      return null;
    }
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null;
  }
  const message = value as Record<string, unknown>;
  if (typeof message.from !== 'string' || !message.from) {
    return null;
  }
  const at = typeof message.at === 'number' ? message.at : Date.now();
  if (message.type === 'session-restored') {
    return { at, from: message.from, type: 'session-restored' };
  }
  if (message.type === 'session-invalid') {
    const payload = message.payload;
    if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
      return null;
    }
    return {
      at,
      from: message.from,
      payload: payload as SessionInvalidPayload,
      type: 'session-invalid',
    };
  }
  return null;
}

function reloadCurrentPage() {
  try {
    window.location?.reload();
  } catch {
    // 静默降级：重载失败不影响当前标签页的失效弹窗
  }
}

/**
 * 处理一条同步消息（BroadcastChannel 的 `event.data` 或 storage 的 `newValue`）。
 *
 * @returns 是否真正处理了该消息（测试与断言用；格式不合法/自己发的返回 false）
 */
export function handleSessionSyncMessage(raw: unknown): boolean {
  const message = parseSessionSyncMessage(raw);
  if (!message) {
    return false;
  }
  // 收到自己发的消息必须忽略，避免同步回环
  if (message.from === getSessionTabId()) {
    return false;
  }
  if (message.type === 'session-invalid') {
    const sessionStore = useSessionStore();
    const accessStore = useAccessStore();
    // 落状态 + 弹窗；invalidate 内部同时处理锁屏解锁，且幂等（不重复弹/不重复 toast）
    sessionStore.invalidate(message.payload);
    accessStore.setLoginExpired(true);
    return true;
  }
  // 其它标签页已重新登录：token 已持久化，整页重载是最简单可靠的恢复方式
  reloadCurrentPage();
  return true;
}

/**
 * 开始监听同步消息（幂等）。返回停止函数，便于应用卸载/单测清理。
 */
export function startSessionSync(): () => void {
  if (listenerStarted || typeof window === 'undefined') {
    return stopSessionSync;
  }
  listenerStarted = true;

  if (isBroadcastChannelSupported()) {
    try {
      channel ??= new BroadcastChannel(SESSION_SYNC_CHANNEL_NAME);
      channelHandler = (event: MessageEvent) => {
        handleSessionSyncMessage(event.data);
      };
      channel.addEventListener('message', channelHandler);
    } catch {
      // 不支持/构造失败：仅用 storage 兜底
      channel = null;
      channelHandler = null;
    }
  }

  storageHandler = (event: StorageEvent) => {
    if (event.key !== SESSION_SYNC_STORAGE_KEY) {
      return;
    }
    handleSessionSyncMessage(event.newValue);
  };
  try {
    window.addEventListener('storage', storageHandler);
  } catch {
    // 静默降级
  }

  return stopSessionSync;
}

/** 停止监听（可重复调用；停止后可再次 start）。 */
export function stopSessionSync() {
  if (channel && channelHandler) {
    try {
      channel.removeEventListener('message', channelHandler);
    } catch {
      // 忽略：通道可能已关闭
    }
  }
  try {
    channel?.close();
  } catch {
    // 忽略
  }
  channel = null;
  channelHandler = null;

  if (storageHandler && typeof window !== 'undefined') {
    try {
      window.removeEventListener('storage', storageHandler);
    } catch {
      // 忽略
    }
  }
  storageHandler = null;
  listenerStarted = false;
}
