import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:layout-dashboard',
      order: -1,
      title: $t('page.dashboard.title'),
    },
    name: 'Dashboard',
    path: '/dashboard',
    children: [
      {
        name: 'Analytics',
        path: '/analytics',
        component: () => import('#/views/dashboard/analytics/index.vue'),
        meta: {
          affixTab: true,
          icon: 'lucide:area-chart',
          title: $t('page.dashboard.analytics'),
        },
      },
      {
        name: '商品分析',
        path: '/product-analytics',
        component: () => import('#/views/dashboard/product/index.vue'),
        meta: {
          affixTab: true,
          icon: 'lucide:area-chart',
          title: '商品分析',
        },
      },
      {
        name: '用户分析',
        path: '/user-analytics',
        component: () => import('#/views/dashboard/user/index.vue'),
        meta: {
          icon: 'lucide:users',
          title: '用户分析',
        },
      },
      {
        name: '发布商品',
        path: '/spu/form',
        component: () => import('#/views/product/goods-spu/form.vue'),
        meta: {
          icon: 'lucide:area-chart',
          title: '发布商品',
          hideInMenu: true,
        },
      },
      {
        name: '订单详情',
        path: '/order/detail',
        component: () => import('#/views/order/order-info/info/index.vue'),
        meta: {
          icon: 'lucide:area-chart',
          title: '订单详情',
          hideInMenu: true,
        },
      },
      {
        name: '代码生成配置',
        path: '/code/gen',
        component: () => import('#/views/gen/gen-table/generate.vue'),
        meta: {
          icon: 'lucide:area-chart',
          title: '代码生成配置',
          hideInMenu: true,
        },
      },
    ],
  },
  {
    name: 'PageDesigner',
    path: '/page-designer/:id?',
    component: () => import('#/views/promotion/page-designer/index.vue'),
    meta: {
      icon: 'lucide:panel-top-open',
      title: '商城装修',
      hideInMenu: true,
      noBasicLayout: true,
      openInNewWindow: true,
    },
  },
  {
    name: 'LegacyPageDesigner',
    path: '/pagedesign/form',
    redirect: (to) => ({ path: '/page-designer', query: to.query }),
    meta: { hideInMenu: true, title: '旧版页面装修入口' },
  },
  {
    name: 'LegacyHomeDesigner',
    path: '/home-decoration/form',
    redirect: '/page-designer',
    meta: { hideInMenu: true, title: '旧版首页装修入口' },
  },
];

export default routes;
