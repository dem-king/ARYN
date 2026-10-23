import type { RouteRecordRaw } from 'vue-router';

/**
 * 营销活动编辑页路由。
 *
 * 必须放在动态路由模块里：动态路由会被挂到 BasicLayout 的 children 下，
 * 而 core.ts 的顶层路由直接注册到 router 实例上、不经过 BasicLayout，
 * 页面会整屏渲染并遮住侧边菜单与顶部导航。
 */
const routes: RouteRecordRaw[] = [
  {
    name: 'SeckillActivityEdit',
    path: '/promotion/seckill-activity/edit',
    component: () => import('#/views/promotion/seckill-activity/edit.vue'),
    meta: {
      activePath: '/promotion/seckill-activity/index',
      hideInMenu: true,
      title: '秒杀活动编辑',
    },
  },
  {
    name: 'DiscountActivityEdit',
    path: '/promotion/discount-activity/edit',
    component: () => import('#/views/promotion/discount-activity/edit.vue'),
    meta: {
      activePath: '/promotion/discount-activity/index',
      hideInMenu: true,
      title: '折扣活动编辑',
    },
  },
];

export default routes;
