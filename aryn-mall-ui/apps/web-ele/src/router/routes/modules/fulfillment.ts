import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:package-check',
      order: 9,
      title: '履约作业',
    },
    name: 'Fulfillment',
    path: '/fulfillment',
    children: [
      {
        name: 'FulfillmentWave',
        path: '/fulfillment/wave',
        component: () => import('#/views/fulfillment/wave/index.vue'),
        meta: {
          icon: 'lucide:clipboard-list',
          title: '拣货波次',
        },
      },
      {
        name: 'FulfillmentException',
        path: '/fulfillment/exception',
        component: () => import('#/views/fulfillment/exception/index.vue'),
        meta: {
          icon: 'lucide:alert-triangle',
          title: '履约异常',
        },
      },
      {
        name: 'OrderSharedCart',
        path: '/order/shared-cart',
        component: () => import('#/views/order/shared-cart/index.vue'),
        meta: {
          icon: 'lucide:shopping-basket',
          title: '共享购物车',
        },
      },
      {
        name: 'DeliveryPortBoard',
        path: '/delivery/port-board',
        component: () => import('#/views/delivery/port-board/index.vue'),
        meta: {
          icon: 'lucide:anchor',
          title: '港口配送看板',
        },
      },
    ],
  },
];

export default routes;
