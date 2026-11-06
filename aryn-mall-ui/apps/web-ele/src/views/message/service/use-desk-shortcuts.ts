import { onBeforeUnmount, onMounted } from 'vue';

/**
 * 客服工作台键盘快捷键。
 *
 * 约束：
 * - 不占用管理端布局已有的 Ctrl/⌘+K 全局搜索，也不占用浏览器保留键；
 * - 输入框/文本框/contenteditable 内只响应 Escape（避免抢输入）；
 * - 同一帧只触发一个动作，处理函数抛错不影响页面。
 */

export type DeskShortcutAction =
  | 'close-layer'
  | 'focus-search'
  | 'next-conversation'
  | 'next-unread'
  | 'open-help'
  | 'open-window'
  | 'prev-conversation';

export interface DeskShortcutHandlers {
  closeTopLayer: () => boolean;
  focusSearch: () => void;
  nextConversation: () => void;
  nextUnread: () => void;
  openHelp: () => void;
  openWindow?: () => void;
  previousConversation: () => void;
}

export interface DeskShortcutEvent {
  altKey?: boolean;
  ctrlKey?: boolean;
  isComposing?: boolean;
  key: string;
  metaKey?: boolean;
  shiftKey?: boolean;
}

export const DESK_SHORTCUTS: Array<{ description: string; keys: string }> = [
  { description: '搜索会话', keys: '/' },
  { description: '搜索会话（备用）', keys: 'Alt K' },
  { description: '上一个会话', keys: 'Alt ↑' },
  { description: '下一个会话', keys: 'Alt ↓' },
  { description: '下一个未读会话', keys: 'Alt N' },
  { description: '弹出独立窗口', keys: 'Alt Shift P' },
  { description: '快捷键帮助', keys: '?' },
  { description: '关闭弹层 / 清空搜索', keys: 'Esc' },
];

/** 当前焦点是否在可输入元素上（输入态只放行 Escape）。 */
function isTypingTarget(target: unknown): boolean {
  if (!target || typeof target !== 'object') return false;
  const element = target as {
    isContentEditable?: boolean;
    tagName?: string;
    type?: string;
  };
  if (element.isContentEditable) return true;
  const tagName = element.tagName?.toUpperCase();
  if (tagName === 'TEXTAREA' || tagName === 'SELECT') return true;
  if (tagName === 'INPUT') {
    // 按钮类 input 不算输入态（Enter/Escape 等仍按页面逻辑处理）
    const type = element.type?.toLowerCase();
    return type !== 'button' && type !== 'checkbox' && type !== 'submit';
  }
  return false;
}

/**
 * 纯函数匹配：给定键盘事件与是否输入态，返回要执行的快捷动作。
 *
 * 抽成纯函数的原因：快捷键矩阵容易在重构时被改坏，但真实 DOM 事件在单测里
 * 构造成本高；这里只保留判定，挂载/卸载与动作分发由 {@link useDeskShortcuts} 负责。
 */
export function matchDeskShortcut(
  event: DeskShortcutEvent,
  typing: boolean,
): DeskShortcutAction | null {
  const key = event.key;
  if (!key) return null;
  // 中文输入法组合中的键用于选词，不能当快捷键
  if (event.isComposing) return null;

  if (key === 'Escape') return 'close-layer';

  // Ctrl/⌘ 组合交给浏览器或全局快捷键，不拦截
  if (event.ctrlKey || event.metaKey) return null;

  if (event.altKey) {
    if (event.shiftKey) {
      if (key.toLowerCase() === 'p') return 'open-window';
      return null;
    }
    if (key === 'ArrowUp') return 'prev-conversation';
    if (key === 'ArrowDown') return 'next-conversation';
    if (key.toLowerCase() === 'k') return 'focus-search';
    if (key.toLowerCase() === 'n') return 'next-unread';
    return null;
  }

  if (typing) return null;
  if (key === '/') return 'focus-search';
  if (key === '?') return 'open-help';
  return null;
}

export interface UseDeskShortcutsOptions {
  /** 返回 false 时忽略键盘事件（例如弹窗打开、页面不可见）。 */
  enabled?: (() => boolean) | boolean;
  target?: Window;
}

/**
 * 在工作台挂载期间监听快捷键。
 *
 * 命中后 preventDefault：Escape 仅在真正关闭了某一层时才阻止默认行为
 * （否则浏览器/Element Plus 自己的关闭逻辑需要收到该事件）。
 */
export function useDeskShortcuts(
  handlers: DeskShortcutHandlers,
  options: UseDeskShortcutsOptions = {},
) {
  const target: undefined | Window =
    options.target ?? (typeof window === 'undefined' ? undefined : window);

  function isEnabled() {
    if (typeof options.enabled === 'function') return options.enabled();
    return options.enabled ?? true;
  }

  function onKeydown(rawEvent: Event) {
    if (!isEnabled()) return;
    const event = rawEvent as KeyboardEvent;
    const typing = isTypingTarget(event.target);
    const action = matchDeskShortcut(event, typing);
    if (!action) return;

    if (action === 'close-layer') {
      if (handlers.closeTopLayer()) event.preventDefault();
      return;
    }

    const run: Record<
      Exclude<DeskShortcutAction, 'close-layer'>,
      () => void
    > = {
      'focus-search': () => {
        handlers.focusSearch();
      },
      'next-conversation': () => {
        handlers.nextConversation();
      },
      'next-unread': () => {
        handlers.nextUnread();
      },
      'open-help': () => {
        handlers.openHelp();
      },
      'open-window': () => {
        handlers.openWindow?.();
      },
      'prev-conversation': () => {
        handlers.previousConversation();
      },
    };

    event.preventDefault();
    try {
      run[action]();
    } catch {
      // 快捷键处理失败不影响主流程
    }
  }

  onMounted(() => {
    target?.addEventListener('keydown', onKeydown);
  });
  onBeforeUnmount(() => {
    target?.removeEventListener('keydown', onKeydown);
  });
}
