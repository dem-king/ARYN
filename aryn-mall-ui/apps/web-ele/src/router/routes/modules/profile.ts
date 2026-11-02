import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

/**
 * 登录用户个人信息页。
 *
 * 放在动态路由模块里才能挂到 BasicLayout 的 children 下（同 promotion 模块），
 * 入口在顶部用户下拉菜单，不需要后端菜单权限，因此 hideInMenu。
 */
const routes: RouteRecordRaw[] = [
  {
    name: 'UserProfile',
    path: '/profile',
    component: () => import('#/views/profile/index.vue'),
    meta: {
      hideInMenu: true,
      icon: 'lucide:user',
      title: $t('page.profile.title'),
    },
  },
];

export default routes;
