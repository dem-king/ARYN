import type { RouteRecordRaw } from 'vue-router';

/**
 * 客服工作台「独立窗口」页。
 *
 * <p>`noBasicLayout: true` 让该路由直接挂到根、不套管理端 BasicLayout，
 * 打开后是一个只有工作台的全屏窗口（先例：`PageDesigner`）。它不注册后端菜单，
 * 入口是工作台顶栏的「弹出独立窗口」按钮；`hideInMenu/hideInTab` 保证任何
 * 菜单与页签都不出现它。</p>
 *
 * <p>`sessionInvalidModal: true` 是新增约定：该页自行挂载会话失效弹窗，
 * 401 时走页内原地重登而不是降级登出（否则会清掉持久化 token，连主窗口一起登出）。</p>
 */
const routes: RouteRecordRaw[] = [
  {
    name: 'MessageServiceWindow',
    path: '/message/service-window',
    component: () => import('#/views/message/service-window.vue'),
    meta: {
      hideInBreadcrumb: true,
      hideInMenu: true,
      hideInTab: true,
      noBasicLayout: true,
      sessionInvalidModal: true,
      title: '客服工作台',
    },
  },
];

export default routes;
