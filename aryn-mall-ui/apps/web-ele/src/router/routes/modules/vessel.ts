import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:ship',
      order: 8,
      title: '船舶与客户',
    },
    name: 'Vessel',
    path: '/vessel',
    children: [
      {
        name: 'VesselArchive',
        path: '/vessel/archive',
        component: () => import('#/views/vessel/vessel/index.vue'),
        meta: {
          icon: 'lucide:ship',
          title: '船舶档案',
        },
      },
      {
        name: 'VesselCallCalendar',
        path: '/vessel/call-calendar',
        component: () => import('#/views/vessel/call-calendar/index.vue'),
        meta: {
          icon: 'lucide:calendar',
          title: '靠港日历',
        },
      },
    ],
  },
];

export default routes;
