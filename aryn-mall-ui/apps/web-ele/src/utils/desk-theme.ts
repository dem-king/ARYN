/**
 * 客服工作台深色令牌的全局作用域开关。
 *
 * <p>工作台需要把 `--el-*` 变深，且 teleport 到 `body` 的下拉/弹窗也要跟着变深，
 * 因此只能挂在 `<html>` 上（`html.desk-theme`），见 `desk-tokens.css`。这是对全局
 * DOM 的写操作，必须保证「离开工作台就一定摘掉」，否则会污染管理端其它页面。</p>
 *
 * <p><b>历史故障</b>：早期由工作台组件在 `onActivated` 挂类、`onDeactivated` 摘类。
 * 组件卸载这条路径没有钩子触发（例如主窗口打开独立窗口后，嵌入工作台被 `v-if`
 * 换成占位态），`desk-theme` 就永久留在 `<html>` 上。此后<b>几乎所有列表页</b>
 * 都吃到工作台的深色令牌：表头变成深蓝、分割线变成深蓝、正文变成浅蓝白，
 * 落在白底卡片上几乎不可读。</p>
 *
 * <p><b>当前设计</b>：类是<b>路由的纯函数</b>——由路由守卫在每次导航结束后按目标
 * 路径统一同步，组件不再参与。这样「谁挂的、谁摘」不再依赖任何组件生命周期，
 * 只取决于当前停在哪一页，从根本上消除泄漏。</p>
 */
const DESK_THEME_CLASS = 'desk-theme';

/** 工作台路由：嵌入态 `/message/service` 与独立窗口 `/message/service-window`。 */
export function isDeskRoutePath(path: string): boolean {
  return (
    path === '/message/service' ||
    path.startsWith('/message/service/') ||
    path === '/message/service-window'
  );
}

/**
 * 按目标路径同步深色令牌：工作台路由挂上，其它路由摘掉。
 *
 * <p>由路由守卫的 `afterEach` 调用，是 `desk-theme` 唯一的写入点。</p>
 */
export function syncDeskTheme(path: string): void {
  if (typeof document === 'undefined') return;
  const classList = document.documentElement.classList;
  if (isDeskRoutePath(path)) {
    classList.add(DESK_THEME_CLASS);
  } else {
    classList.remove(DESK_THEME_CLASS);
  }
}

/**
 * 强制摘掉深色令牌。
 *
 * <p>登出、401 跳登录页等不经过正常页面切换的退出路径用它兜底。</p>
 */
export function clearDeskTheme(): void {
  if (typeof document === 'undefined') return;
  document.documentElement.classList.remove(DESK_THEME_CLASS);
}
