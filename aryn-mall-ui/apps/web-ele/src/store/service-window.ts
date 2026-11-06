import { ref } from 'vue';

/**
 * 客服工作台「独立窗口」协同（模块级单例）。
 *
 * <p>背景：工作台支持在主窗口之外再开一个同源独立浏览器窗口，两窗共享同一
 * 登录态与 WS 推送。由此产生四类跨窗问题，本模块是唯一的协调入口：</p>
 *
 * 1. **提醒宿主**：两条 WS 都会触发声音/语音/桌面通知，必须只让一个窗口发声。
 *    规则（确定性、无选主竞态）：独立窗口存在时由它做提醒宿主，主窗口静默；
 *    独立窗口关闭后主窗口自动接管。
 * 2. **会话定位**：主窗口点铃铛时，如果独立窗口开着，应把会话交给独立窗口打开，
 *    而不是把主窗口跳到工作台。
 * 3. **状态收敛**：任一窗口推进已读/领取/转交/关闭后通知对端刷新。
 * 4. **生命周期**：独立窗口关闭/重复打开/主窗口登出、锁屏、切租户时的联动。
 *
 * 实现选型：`BroadcastChannel` 为主（与 `session-sync.ts` 同模式），
 * `localStorage` storage 事件兜底；独立窗口存活用 localStorage 租约兜底，
 * 保证通道不可用时仍能判断「独立窗口是否开着」。</p>
 */

export type ServiceDeskMessageType =
  | 'alive'
  | 'bye'
  | 'changed'
  | 'focus'
  | 'hello'
  | 'locate'
  | 'lock'
  | 'logout'
  | 'read'
  | 'reclaim'
  | 'tenant';

export type ServiceDeskRole = 'desk' | 'main';

export interface ServiceDeskMessage {
  at: number;
  conversationId?: string;
  from: string;
  /** 会话变更类型：claimed / closed / transferred ... */
  kind?: string;
  lastReadSeq?: number;
  locked?: boolean;
  role: ServiceDeskRole;
  tenantId?: string;
  type: ServiceDeskMessageType;
}

export interface ServiceDeskLease {
  at: number;
  conversationId?: string;
  role: 'desk';
  tabId: string;
}

export type OpenDeskResult = 'blocked' | 'focused' | 'opened';

/** 通道名/存储键/窗口名统一导出，便于单测与排查。 */
export const SERVICE_DESK_CHANNEL_NAME = 'aryn-service-desk';
export const SERVICE_DESK_STORAGE_KEY = 'aryn-service-desk-event';
export const SERVICE_DESK_LEASE_KEY = 'aryn-service-desk-lease';
export const SERVICE_DESK_WINDOW_NAME = 'aryn-service-desk';

/** 独立窗口每 2s 续租；主窗口 8s 收不到租约即判定已关闭。 */
export const DESK_LEASE_INTERVAL_MS = 2000;
export const DESK_ALIVE_TIMEOUT_MS = 8000;

/**
 * 独立窗口 URL。
 *
 * <p>管理端是 hash 路由（`VITE_ROUTER_HISTORY=hash`），因此固定为
 * `origin + base + '/#/message/service-window'`；不能直接拼 `?standalone=1`
 * 到工作台原路由上——布局由路由 meta 决定，query 去不掉 BasicLayout。</p>
 */
export function buildServiceDeskUrl(options: {
  base?: string;
  conversationId?: string;
  origin?: string;
}): string {
  const origin =
    options.origin ??
    (typeof window === 'undefined' ? '' : window.location.origin);
  const rawBase =
    options.base ??
    (import.meta !== undefined && import.meta.env?.VITE_BASE
      ? String(import.meta.env.VITE_BASE)
      : '/');
  const base = rawBase.endsWith('/') ? rawBase : `${rawBase}/`;
  const query = options.conversationId
    ? `?conversationId=${encodeURIComponent(options.conversationId)}`
    : '';
  return `${origin}${base}#/message/service-window${query}`;
}

/** 解析收到的协同消息：非法结构、自己发的消息一律忽略。 */
export function parseServiceDeskMessage(
  raw: unknown,
  selfTabId: string,
): null | ServiceDeskMessage {
  let value = raw;
  if (typeof value === 'string') {
    try {
      value = JSON.parse(value);
    } catch {
      return null;
    }
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const message = value as Record<string, unknown>;
  if (typeof message.type !== 'string' || typeof message.from !== 'string') {
    return null;
  }
  if (!message.from || message.from === selfTabId) return null;
  const role = message.role === 'desk' ? 'desk' : 'main';
  return {
    at: typeof message.at === 'number' ? message.at : Date.now(),
    conversationId:
      typeof message.conversationId === 'string'
        ? message.conversationId
        : undefined,
    from: message.from,
    kind: typeof message.kind === 'string' ? message.kind : undefined,
    lastReadSeq:
      typeof message.lastReadSeq === 'number' ? message.lastReadSeq : undefined,
    locked: message.locked === true,
    role,
    tenantId:
      typeof message.tenantId === 'string' ? message.tenantId : undefined,
    type: message.type as ServiceDeskMessageType,
  };
}

/** 独立窗口租约是否仍然有效（纯函数，便于单测边界）。 */
export function isServiceDeskLeaseAlive(
  raw: null | string,
  now: number = Date.now(),
): boolean {
  if (!raw) return false;
  let value: unknown;
  try {
    value = JSON.parse(raw);
  } catch {
    return false;
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const lease = value as Record<string, unknown>;
  if (lease.role !== 'desk') return false;
  const at = typeof lease.at === 'number' ? lease.at : 0;
  if (!at) return false;
  return now - at <= DESK_ALIVE_TIMEOUT_MS;
}

/** 读取租约里的待打开会话（独立窗口启动时可用于恢复选中）。 */
export function readServiceDeskLease(): null | ServiceDeskLease {
  if (typeof localStorage === 'undefined') return null;
  try {
    const raw = localStorage.getItem(SERVICE_DESK_LEASE_KEY);
    if (!raw) return null;
    const value = JSON.parse(raw) as ServiceDeskLease;
    if (!value || value.role !== 'desk' || typeof value.at !== 'number') {
      return null;
    }
    return value;
  } catch {
    return null;
  }
}

function createTabId(): string {
  try {
    if (
      typeof crypto !== 'undefined' &&
      typeof crypto.randomUUID === 'function'
    ) {
      return crypto.randomUUID();
    }
  } catch {
    // 旧环境降级到时间戳 + 随机串
  }
  return `desk-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

type Listener = (message: ServiceDeskMessage) => void;

const tabId = createTabId();
const listeners = new Set<Listener>();

/** 独立窗口是否存活（主窗口视角）。 */
const deskAlive = ref(false);
/** 独立窗口当前打开的会话（主窗口横幅/定位提示用）。 */
const deskConversationId = ref<string>();

let role: null | ServiceDeskRole = null;
let channel: BroadcastChannel | null = null;
let channelHandler: ((event: MessageEvent) => void) | null = null;
let storageHandler: ((event: StorageEvent) => void) | null = null;
let livenessTimer: ReturnType<typeof setInterval> | undefined;
let leaseTimer: ReturnType<typeof setInterval> | undefined;
let pagehideHandler: (() => void) | null = null;
let deskWindow: null | Window = null;
/** 本窗口启动的独立窗口是否被用户主动收回/关闭（用于避免误重复检测）。 */
let closingDesk = false;

function isBroadcastChannelSupported() {
  return typeof BroadcastChannel !== 'undefined';
}

/** 发出消息的入参：at/from 由通道补齐，role 缺省取当前窗口角色。 */
type OutgoingServiceDeskMessage = Omit<
  ServiceDeskMessage,
  'at' | 'from' | 'role' | 'type'
> & {
  role?: ServiceDeskRole;
  type: ServiceDeskMessageType;
};

function postToPeers(message: OutgoingServiceDeskMessage) {
  const payload: ServiceDeskMessage = {
    ...message,
    at: Date.now(),
    from: tabId,
    role: message.role ?? role ?? 'main',
  };
  if (isBroadcastChannelSupported()) {
    try {
      channel ??= new BroadcastChannel(SERVICE_DESK_CHANNEL_NAME);
      channel.postMessage(payload);
      return;
    } catch {
      // 通道构造/发送失败：退化到 storage 事件
    }
  }
  try {
    localStorage.setItem(SERVICE_DESK_STORAGE_KEY, JSON.stringify(payload));
  } catch {
    // 静默降级：协同通道不可用不影响本窗口主流程
  }
}

function handleIncoming(raw: unknown) {
  const message = parseServiceDeskMessage(raw, tabId);
  if (!message) return;
  // 只有独立窗口发来的存活信号才更新主窗口视角的 deskAlive；
  // 否则多个主窗口互相 hello 会被误判为「独立窗口开着」。
  if (
    message.role === 'desk' &&
    (message.type === 'alive' || message.type === 'hello')
  ) {
    updateDeskAlive(true, message.conversationId);
  }
  if (message.role === 'desk' && message.type === 'bye') {
    updateDeskAlive(false);
  }
  if (message.type === 'changed' && message.conversationId) {
    deskConversationId.value = message.conversationId;
  }
  listeners.forEach((listener) => {
    try {
      listener(message);
    } catch {
      // 单个订阅方异常不影响其它窗口/其它订阅方
    }
  });
}

function ensureChannel() {
  if (typeof window === 'undefined') return;
  if (isBroadcastChannelSupported()) {
    try {
      channel ??= new BroadcastChannel(SERVICE_DESK_CHANNEL_NAME);
      if (!channelHandler) {
        channelHandler = (event: MessageEvent) => handleIncoming(event.data);
        channel.addEventListener('message', channelHandler);
      }
    } catch {
      channel = null;
      channelHandler = null;
    }
  }
  if (!storageHandler) {
    storageHandler = (event: StorageEvent) => {
      if (event.key === SERVICE_DESK_STORAGE_KEY) {
        handleIncoming(event.newValue);
      }
      if (event.key === SERVICE_DESK_LEASE_KEY) {
        updateDeskAlive(
          isServiceDeskLeaseAlive(event.newValue),
          readServiceDeskLease()?.conversationId,
        );
      }
    };
    try {
      window.addEventListener('storage', storageHandler);
    } catch {
      storageHandler = null;
    }
  }
}

function updateDeskAlive(alive: boolean, conversationId?: string) {
  deskAlive.value = alive;
  if (conversationId) deskConversationId.value = conversationId;
  if (!alive) deskConversationId.value = undefined;
}

/** 主窗口轮询租约：storage 事件不可用（部分隐私模式）时的兜底路径。 */
function startLivenessWatcher() {
  if (livenessTimer) return;
  const check = () => {
    // 本窗口自己打开的独立窗口：句柄是最可靠的存活信号。
    // 独立窗口被最小化/切到后台时浏览器会节流 2s 租约定时器，单靠 localStorage
    // 租约会把「还开着」误判成已关闭，导致主窗口恢复提醒、一条消息响两遍。
    if (deskWindow) {
      if (!deskWindow.closed) {
        updateDeskAlive(true);
        return;
      }
      deskWindow = null;
    }
    if (closingDesk) return;
    const lease = readServiceDeskLease();
    const alive = isServiceDeskLeaseAlive(lease ? JSON.stringify(lease) : null);
    updateDeskAlive(alive, lease?.conversationId);
  };
  check();
  livenessTimer = setInterval(check, 1000);
}

/** 租约足够新才敢用 window.open('', name) 重取句柄——否则会打开一个空白窗口。 */
function canReacquireDeskHandle(): boolean {
  const lease = readServiceDeskLease();
  if (!lease) return false;
  return Date.now() - lease.at <= 4000;
}

function startDeskLease() {
  const write = () => {
    if (typeof localStorage === 'undefined') return;
    const lease: ServiceDeskLease = {
      at: Date.now(),
      conversationId: deskConversationId.value,
      role: 'desk',
      tabId,
    };
    try {
      localStorage.setItem(SERVICE_DESK_LEASE_KEY, JSON.stringify(lease));
    } catch {
      // 静默降级
    }
  };
  write();
  leaseTimer ??= setInterval(write, DESK_LEASE_INTERVAL_MS);
}

function stopDeskLease() {
  if (leaseTimer) {
    clearInterval(leaseTimer);
    leaseTimer = undefined;
  }
  try {
    localStorage.removeItem(SERVICE_DESK_LEASE_KEY);
  } catch {
    // 忽略
  }
}

/**
 * 主窗口启动协同。
 *
 * <p>幂等：BasicLayout 重新挂载（登录后/布局重建）重复调用不会叠加监听与定时器。</p>
 */
export function startMainServiceDeskRole() {
  if (role === 'main') return;
  role = 'main';
  ensureChannel();
  startLivenessWatcher();
  postToPeers({ type: 'hello' });
}

/**
 * 独立窗口启动协同。
 *
 * <p>options.onDuplicate：已存在另一个独立窗口时回调（宿主自行关闭或提示）；
 * options.onPageHide：窗口关闭前回调。返回清理函数。</p>
 */
export function startDeskServiceDeskRole(options?: {
  onDuplicate?: () => void;
  onPageHide?: () => void;
}): () => void {
  role = 'desk';
  ensureChannel();
  startDeskLease();
  postToPeers({ type: 'hello' });

  // 重复窗口检测：已有独立窗口收到 hello 会回 alive；若 500ms 内收到
  // 其它独立窗口的 alive，说明本窗口是重复打开的，宿主应自行关闭。
  let duplicateCheck = false;
  const duplicate = () => {
    if (duplicateCheck || role !== 'desk' || closingDesk) return;
    duplicateCheck = true;
    options?.onDuplicate?.();
  };
  const duplicateTimer = setTimeout(() => {
    duplicateCheck = true;
  }, 500);
  const duplicateListener: Listener = (message) => {
    // 主窗口/其它独立窗口启动时会广播 hello：回 alive 让对端确认本窗口存活。
    if (message.type === 'hello') {
      postToPeers({
        type: 'alive',
        conversationId: deskConversationId.value,
      });
      return;
    }
    // 收到另一个独立窗口的 alive 说明本窗口是重复打开的。
    if (
      message.type === 'alive' &&
      message.role === 'desk' &&
      !duplicateCheck
    ) {
      clearTimeout(duplicateTimer);
      duplicate();
    }
  };
  listeners.add(duplicateListener);

  const beforeUnload = () => {
    closingDesk = true;
    postToPeers({ type: 'bye' });
    stopDeskLease();
    options?.onPageHide?.();
  };
  pagehideHandler = beforeUnload;
  try {
    window.addEventListener('pagehide', beforeUnload);
  } catch {
    pagehideHandler = null;
  }

  const cleanup = () => {
    clearTimeout(duplicateTimer);
    listeners.delete(duplicateListener);
    if (pagehideHandler) {
      try {
        window.removeEventListener('pagehide', pagehideHandler);
      } catch {
        // 忽略
      }
      pagehideHandler = null;
    }
    stopDeskLease();
    role = null;
  };
  return cleanup;
}

/** 订阅协同消息（收到自己发的消息不会回调）。 */
export function subscribeServiceDesk(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** 当前窗口在跨窗协同里的角色；未启动协同时为 null。 */
export function getServiceDeskRole(): null | ServiceDeskRole {
  return role;
}

/** 当前窗口是否应抑制声音/语音/桌面通知。 */
export function isServiceDeskAlertSuppressed(): boolean {
  return role === 'main' && deskAlive.value;
}

/** 主窗口：独立窗口是否存活。 */
export function useServiceDeskState() {
  return {
    deskAlive,
    deskConversationId,
    isServiceDeskAlertSuppressed,
  };
}

/** 独立窗口：登记当前正在处理的会话，并广播给主窗口更新横幅/状态提示。 */
export function setServiceDeskConversation(conversationId?: string) {
  deskConversationId.value = conversationId;
  if (role === 'desk') {
    postToPeers({ type: 'alive', conversationId });
  }
}

/**
 * 主窗口：立即重新判定独立窗口存活。
 *
 * 用于「聚焦失败/收回失败」时不让占位界面干等 8s 租约超时——用户已经感知到
 * 独立窗口不可用，主窗口应立即恢复可用的工作台。
 */
export function syncServiceDeskLiveness() {
  if (typeof window === 'undefined') return;
  if (deskWindow) {
    if (!deskWindow.closed) {
      updateDeskAlive(true);
      return;
    }
    deskWindow = null;
  }
  const lease = readServiceDeskLease();
  updateDeskAlive(
    isServiceDeskLeaseAlive(lease ? JSON.stringify(lease) : null),
    lease?.conversationId,
  );
}

/** 主窗口：当前是否已有本窗口打开的独立窗口。 */
export function hasOpenedServiceDeskWindow() {
  return Boolean(deskWindow && !deskWindow.closed);
}

/**
 * 主窗口：打开/聚焦独立工作台。
 *
 * <p>必须在 click 事件同步调用（不能先 await 再 open），否则被浏览器拦截。</p>
 */
export function openServiceDeskWindow(conversationId?: string): OpenDeskResult {
  if (typeof window === 'undefined') return 'blocked';
  if (hasOpenedServiceDeskWindow()) {
    if (conversationId) {
      postToPeers({ type: 'locate', conversationId });
    }
    deskWindow?.focus();
    return 'focused';
  }
  // 主窗口刷新后句柄丢失：租约足够新时按窗口名重取句柄
  if (deskAlive.value && canReacquireDeskHandle()) {
    const existing = window.open('', SERVICE_DESK_WINDOW_NAME);
    if (existing && !existing.closed) {
      deskWindow = existing;
      existing.focus();
      return 'focused';
    }
  }
  const width = Math.min(
    1440,
    Math.round((window.screen?.availWidth ?? 1440) * 0.8),
  );
  const height = Math.min(
    900,
    Math.round((window.screen?.availHeight ?? 900) - 80),
  );
  const left = Math.max(
    0,
    Math.round((window.screen?.availWidth ?? width) - width - 40),
  );
  const top = 40;
  const features = [
    `width=${width}`,
    `height=${height}`,
    `left=${left}`,
    `top=${top}`,
    'resizable=yes',
    'scrollbars=yes',
    'menubar=no',
    'toolbar=no',
    'location=no',
    'status=no',
  ].join(',');
  const opened = window.open(
    buildServiceDeskUrl({ conversationId }),
    SERVICE_DESK_WINDOW_NAME,
    features,
  );
  if (!opened) return 'blocked';
  deskWindow = opened;
  // 打开即视为存活：不等 2s 租约，避免首条消息在两侧同时响
  updateDeskAlive(true, conversationId);
  try {
    opened.focus();
  } catch {
    // focus 失败不影响窗口已打开
  }
  return 'opened';
}

/** 主窗口：聚焦独立窗口；不存在时返回 false。 */
export function focusServiceDeskWindow(): boolean {
  if (hasOpenedServiceDeskWindow()) {
    deskWindow?.focus();
    return true;
  }
  if (
    deskAlive.value &&
    typeof window !== 'undefined' &&
    canReacquireDeskHandle()
  ) {
    const existing = window.open('', SERVICE_DESK_WINDOW_NAME);
    if (existing && !existing.closed) {
      deskWindow = existing;
      existing.focus();
      return true;
    }
  }
  return false;
}

/** 主窗口：收回独立工作台（关闭独立窗口）。 */
export function reclaimServiceDeskWindow() {
  closingDesk = true;
  postToPeers({ type: 'reclaim' });
  try {
    deskWindow?.close();
  } catch {
    // 独立窗口可能已自行关闭
  }
  deskWindow = null;
  try {
    localStorage.removeItem(SERVICE_DESK_LEASE_KEY);
  } catch {
    // 忽略
  }
  updateDeskAlive(false);
  closingDesk = false;
}

/** 主窗口：把会话定位到独立窗口；独立窗口不可用时返回 false。 */
export function locateConversationInServiceDesk(
  conversationId: string,
): boolean {
  if (!deskAlive.value) return false;
  postToPeers({ type: 'locate', conversationId });
  focusServiceDeskWindow();
  return true;
}

/** 主窗口：登出/锁屏/切换租户联动。 */
export function notifyServiceDeskLogout() {
  postToPeers({ type: 'logout' });
}
export function notifyServiceDeskLock(locked: boolean) {
  postToPeers({ type: 'lock', locked });
}
export function notifyServiceDeskTenant(tenantId: string) {
  postToPeers({ type: 'tenant', tenantId });
}

/** 双向：已读/会话变更广播，供对端刷新。 */
export function broadcastServiceDeskRead(
  conversationId: string,
  lastReadSeq: number,
) {
  postToPeers({ type: 'read', conversationId, lastReadSeq });
}
export function broadcastServiceDeskChanged(
  conversationId: string,
  kind: string,
) {
  postToPeers({ type: 'changed', conversationId, kind });
}
