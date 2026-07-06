import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    name: 'VisualScreen',
    path: '/visual/screen',
    component: () => import('#/views/visual/screen/index.vue'),
    meta: {
      title: '数据大屏',
      icon: 'lucide:monitor',
      hideInMenu: true,
      noBasicLayout: true,
    },
  },
];
export default routes;
