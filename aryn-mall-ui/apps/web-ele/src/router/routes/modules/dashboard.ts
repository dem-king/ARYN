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
    name: '页面装修',
    path: '/pagedesign/form',
    component: () => import('#/views/promotion/page-design/form.vue'),
    meta: {
      icon: 'lucide:area-chart',
      title: '页面装修',
      hideInMenu: true,
      noBasicLayout: true,
      openInNewWindow: true,
    },
  },
];

export default routes;
