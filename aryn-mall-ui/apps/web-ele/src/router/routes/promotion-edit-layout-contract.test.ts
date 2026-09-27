import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import promotionRoutes from './modules/promotion';

const here = dirname(fileURLToPath(import.meta.url));

/**
 * 营销活动编辑页必须注册在动态路由模块（会被挂到 BasicLayout 下）。
 *
 * 2026-09-25：秒杀/折扣编辑页曾注册在 core.ts 顶层，该位置的路由直接挂在
 * router 上、不经过 BasicLayout，页面整屏渲染并遮住侧边菜单与顶部导航。
 */
describe('promotion edit route layout', () => {
  const coreSource = readFileSync(resolve(here, 'core.ts'), 'utf8');

  const editRoutes = [
    {
      listPath: '/promotion/seckill-activity/index',
      name: 'SeckillActivityEdit',
      path: '/promotion/seckill-activity/edit',
    },
    {
      listPath: '/promotion/discount-activity/index',
      name: 'DiscountActivityEdit',
      path: '/promotion/discount-activity/edit',
    },
  ];

  it('registers promotion edit pages inside the layout route module', () => {
    for (const item of editRoutes) {
      const route = promotionRoutes.find((route) => route.name === item.name);

      expect(route, `${item.name} 缺少路由定义`).toBeDefined();
      expect(route?.path).toBe(item.path);
      expect(route?.meta?.hideInMenu).toBe(true);
      // 编辑页不在菜单里，靠 activePath 保持左侧菜单高亮在对应列表页
      expect(route?.meta?.activePath).toBe(item.listPath);
    }
  });

  it('keeps promotion edit pages out of the layout-less core routes', () => {
    expect(coreSource).not.toContain('seckill-activity/edit');
    expect(coreSource).not.toContain('discount-activity/edit');
  });
});
