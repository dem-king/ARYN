import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:megaphone',
      order: 10,
      title: '营销中心',
    },
    name: 'Promotion',
    path: '/promotion',
    children: [
      {
        name: 'ShipSupplyActivity',
        path: '/promotion/ship-supply',
        component: () => import('#/views/promotion/ship-supply/index.vue'),
        meta: {
          icon: 'lucide:badge-percent',
          title: '船供活动',
        },
      },
    ],
  },
];

export default routes;
