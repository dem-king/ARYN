import type { Ref } from 'vue';

import { computed, ref } from 'vue';

/**
 * 客服工作台「本地快捷回复 / 常用语」。
 *
 * 背景：当前后端没有快捷回复接口，本期先用 localStorage 按坐席隔离保存；
 * 后续接后端时只需要替换这里的读写实现，纯函数与页面用法保持不变。
 *
 * 设计要点：
 * - 解析/校验抽成纯函数，坏数据（手工改过 localStorage、历史版本格式）不会污染工作台；
 * - 组合式内部按 localStorage key 共享同一份状态，常用语弹窗增删后，
 *   工作台输入区的快捷回复入口即时可见，不会出现两个实例各存一份的问题；
 * - localStorage 读写异常（隐私模式 / 配额满 / 站点数据被禁用）静默降级为内存态，
 *   本次会话内仍可正常增删使用，不影响发消息。
 */

export interface DeskQuickReply {
  id: string;
  title: string;
  content: string;
}

/** localStorage key 前缀，完整 key 为 `aryn-service-desk-quick-replies:<staffId>`。 */
export const QUICK_REPLY_STORAGE_PREFIX = 'aryn-service-desk-quick-replies';

/** 单个坐席最多保存的常用语条数，避免 localStorage 无限增长。 */
export const MAX_QUICK_REPLIES = 50;

/** 标题上限：列表里一行展示，过长标题没有可读性。 */
export const MAX_QUICK_REPLY_TITLE_LENGTH = 40;

/** 内容上限：正常客服话术远小于 2000 字，超出基本是误粘贴。 */
export const MAX_QUICK_REPLY_CONTENT_LENGTH = 2000;

/** 内置默认常用语：首次使用（本地还没有数据）直接可用，坐席无需先录入。 */
const DEFAULT_QUICK_REPLY_TEMPLATES: ReadonlyArray<DeskQuickReply> = [
  {
    id: 'default-welcome',
    title: '欢迎语',
    content: '您好，欢迎光临悦航购～很高兴为您服务，请问有什么可以帮到您？',
  },
  {
    id: 'default-urge-shipment',
    title: '催发货',
    content:
      '您好，您的订单仓库正在加急处理，我们会尽快安排发货；发货后会第一时间同步物流单号，请您耐心等待～',
  },
  {
    id: 'default-logistics',
    title: '物流查询',
    content:
      '您好，您可以在「我的订单」中打开对应订单查看物流轨迹；如果物流长时间没有更新，麻烦把订单号发给我，我帮您向快递核实。',
  },
  {
    id: 'default-refund',
    title: '退款指引',
    content:
      '您好，如需退款请在「我的订单」中找到对应订单，点击「申请退款」并选择退款原因；审核通过后款项将原路退回，一般 1-3 个工作日到账。',
  },
  {
    id: 'default-farewell',
    title: '结束语',
    content: '感谢您的耐心等待，如还有其他问题随时联系我，祝您购物愉快，再见～',
  },
];

/** 按坐席隔离存储；坐席 ID 缺失时归入 unknown 桶，避免不同人共用一份。 */
export function quickReplyStorageKey(staffId?: string): string {
  return `${QUICK_REPLY_STORAGE_PREFIX}:${staffId?.trim() || 'unknown'}`;
}

function createDefaultQuickReplies(): DeskQuickReply[] {
  return DEFAULT_QUICK_REPLY_TEMPLATES.map((template) => ({ ...template }));
}

/** 把 localStorage 里的一条原始记录归一化；非法项返回 null（由调用方过滤）。 */
function parseQuickReplyItem(item: unknown): DeskQuickReply | null {
  if (!item || typeof item !== 'object' || Array.isArray(item)) return null;
  const record = item as Record<string, unknown>;
  const id = typeof record.id === 'string' ? record.id.trim() : '';
  const title = typeof record.title === 'string' ? record.title.trim() : '';
  const content =
    typeof record.content === 'string' ? record.content.trim() : '';
  if (!id || !title || !content) return null;
  return {
    id,
    title,
    content: content.slice(0, MAX_QUICK_REPLY_CONTENT_LENGTH),
  };
}

/**
 * 解析 localStorage 里的常用语原文。
 *
 * 坏 JSON / 非数组 / 非法项一律返回空或过滤掉：工作台不能因为一条脏数据打不开。
 * 同一 id 只保留第一条；最多保留 {@link MAX_QUICK_REPLIES} 条。
 */
export function parseQuickReplies(raw: null | string): DeskQuickReply[] {
  if (!raw) return [];
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return [];
  }
  if (!Array.isArray(parsed)) return [];
  const replies: DeskQuickReply[] = [];
  const seenIds = new Set<string>();
  for (const item of parsed) {
    if (replies.length >= MAX_QUICK_REPLIES) break;
    const reply = parseQuickReplyItem(item);
    if (!reply || seenIds.has(reply.id)) continue;
    seenIds.add(reply.id);
    replies.push(reply);
  }
  return replies;
}

/**
 * 校验并归一化「新增/编辑」输入：两端 trim、标题与内容都必填、超长返回 null。
 *
 * 返回 null 而不是抛错：调用方（弹窗）需要把失败原因提示给坐席，不应让异常冒泡。
 */
export function normalizeQuickReplyInput(
  title: string,
  content: string,
): null | { content: string; title: string } {
  const normalizedTitle = title.trim();
  const normalizedContent = content.trim();
  if (!normalizedTitle || !normalizedContent) return null;
  if (normalizedTitle.length > MAX_QUICK_REPLY_TITLE_LENGTH) return null;
  if (normalizedContent.length > MAX_QUICK_REPLY_CONTENT_LENGTH) return null;
  return { content: normalizedContent, title: normalizedTitle };
}

let quickReplyIdSeed = 0;

/** 本地唯一 id：时间戳 + 自增序号 + 随机串，避免同一毫秒内连续新增冲突。 */
function createQuickReplyId(): string {
  quickReplyIdSeed += 1;
  return `qr-${Date.now().toString(36)}-${quickReplyIdSeed.toString(
    36,
  )}-${Math.random().toString(36).slice(2, 8)}`;
}

/** 由标题/内容创建一条常用语；校验失败返回 null。 */
export function createQuickReply(
  title: string,
  content: string,
): DeskQuickReply | null {
  const normalized = normalizeQuickReplyInput(title, content);
  if (!normalized) return null;
  return {
    id: createQuickReplyId(),
    title: normalized.title,
    content: normalized.content,
  };
}

function readQuickReplyStorage(storageKey: string): string | undefined {
  try {
    return localStorage.getItem(storageKey) ?? undefined;
  } catch {
    // 隐私模式 / 站点数据被禁用：读不到就回退到内置默认值
    return undefined;
  }
}

function writeQuickReplyStorage(
  storageKey: string,
  replies: DeskQuickReply[],
): void {
  try {
    localStorage.setItem(storageKey, JSON.stringify(replies));
  } catch {
    // 配额满或存储不可用时静默降级：本次会话仍可增删，只是不落盘
  }
}

/** 进程内共享的坐席状态：同一 key 的多个组合式实例读写同一个 ref。 */
const quickReplyStores = new Map<string, Ref<DeskQuickReply[]>>();

function resolveQuickReplyStore(storageKey: string): Ref<DeskQuickReply[]> {
  const cached = quickReplyStores.get(storageKey);
  if (cached) return cached;
  const raw = readQuickReplyStorage(storageKey);
  const store: Ref<DeskQuickReply[]> = ref(
    raw === undefined ? createDefaultQuickReplies() : parseQuickReplies(raw),
  );
  quickReplyStores.set(storageKey, store);
  return store;
}

/** 组合式入参：坐席 ID 既支持静态字符串，也支持随登录信息变化的 ref。 */
type StaffIdSource = Ref<string | undefined> | string;

function readStaffId(source?: StaffIdSource): string {
  const value = typeof source === 'string' ? source : source?.value;
  return value?.trim() || 'unknown';
}

/**
 * 客服工作台本地快捷回复组合式。
 *
 * 同一坐席（同一 localStorage key）的所有实例共享一份响应式状态：常用语弹窗里
 * 增删后，工作台输入区的快捷回复入口无需额外刷新就能看到最新列表。
 * `replies` 为只读派生 ref，写操作统一走 add / update / remove / reset。
 */
export function useDeskQuickReplies(staffId?: StaffIdSource) {
  const storageKey = computed(() => quickReplyStorageKey(readStaffId(staffId)));
  const replies = computed(
    () => resolveQuickReplyStore(storageKey.value).value,
  );

  function currentStore(): Ref<DeskQuickReply[]> {
    return resolveQuickReplyStore(storageKey.value);
  }

  function persist(): void {
    writeQuickReplyStorage(storageKey.value, currentStore().value);
  }

  /** 新增一条；输入非法或已达条数上限时返回 null。 */
  function add(title: string, content: string): DeskQuickReply | null {
    const store = currentStore();
    if (store.value.length >= MAX_QUICK_REPLIES) return null;
    const reply = createQuickReply(title, content);
    if (!reply) return null;
    store.value = [...store.value, reply];
    persist();
    return reply;
  }

  /** 按 id 更新标题/内容；不存在或归一化失败时返回 null。 */
  function update(
    id: string,
    patch: Partial<Pick<DeskQuickReply, 'content' | 'title'>>,
  ): DeskQuickReply | null {
    const store = currentStore();
    const current = store.value.find((reply) => reply.id === id);
    if (!current) return null;
    const normalized = normalizeQuickReplyInput(
      patch.title ?? current.title,
      patch.content ?? current.content,
    );
    if (!normalized) return null;
    const next: DeskQuickReply = {
      id: current.id,
      title: normalized.title,
      content: normalized.content,
    };
    store.value = store.value.map((reply) => (reply.id === id ? next : reply));
    persist();
    return next;
  }

  /** 删除一条；id 不存在时返回 false。 */
  function remove(id: string): boolean {
    const store = currentStore();
    const next = store.value.filter((reply) => reply.id !== id);
    if (next.length === store.value.length) return false;
    store.value = next;
    persist();
    return true;
  }

  /** 恢复内置默认常用语（覆盖当前坐席的本地数据）。 */
  function reset(): void {
    currentStore().value = createDefaultQuickReplies();
    persist();
  }

  return { add, remove, replies, reset, storageKey, update };
}
