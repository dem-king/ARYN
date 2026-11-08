import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { clearDeskTheme, isDeskRoutePath, syncDeskTheme } from './desk-theme';

/**
 * 客服工作台深色令牌（`html.desk-theme`）的泄漏回归。
 *
 * <p>症状：几乎所有列表页的表头变成深蓝、分割线变成深蓝、正文变成浅蓝白，
 * 白底卡片上几乎不可读。根因是工作台把 `desk-theme` 挂在 `<html>` 上后，
 * 在「组件被卸载」这条路径上没有摘掉（历史实现只处理了 KeepAlive 的
 * `onDeactivated`），于是任何一个后续页面都继续吃到工作台的深色令牌。</p>
 *
 * <p>现在类是路由的纯函数：停在哪个路由就决定挂不挂，不再依赖组件生命周期。</p>
 */
describe('desk theme global scope', () => {
  const htmlHasClass = () =>
    document.documentElement.classList.contains('desk-theme');

  beforeEach(() => {
    clearDeskTheme();
  });

  afterEach(() => {
    clearDeskTheme();
  });

  it('adds the class on the desk route and removes it on any other route', () => {
    syncDeskTheme('/message/service');
    expect(htmlHasClass()).toBe(true);

    syncDeskTheme('/user/member-level');
    expect(htmlHasClass()).toBe(false);
  });

  it('keeps the class for both desk routes (embedded and standalone window)', () => {
    syncDeskTheme('/message/service');
    expect(htmlHasClass()).toBe(true);

    clearDeskTheme();
    syncDeskTheme('/message/service-window');
    expect(htmlHasClass()).toBe(true);
  });

  it('never leaks the class to list pages, whatever the previous route was', () => {
    const listPages = [
      '/order/order-info',
      '/user/member-level',
      '/vessel/archive',
      '/product/goods-category',
      '/promotion/zone-goods',
    ];

    // 每条路径都从「工作台刚显示过」的状态出发，模拟用户反复进出
    for (const path of listPages) {
      syncDeskTheme('/message/service');
      expect(htmlHasClass()).toBe(true);

      syncDeskTheme(path);
      expect(htmlHasClass()).toBe(false);
    }
  });

  it('drops the class on non-route exits such as logout', () => {
    syncDeskTheme('/message/service');
    expect(htmlHasClass()).toBe(true);

    clearDeskTheme();
    expect(htmlHasClass()).toBe(false);
  });

  it('recognises both desk routes and nothing else', () => {
    expect(isDeskRoutePath('/message/service')).toBe(true);
    expect(isDeskRoutePath('/message/service-window')).toBe(true);

    expect(isDeskRoutePath('/user/member-level')).toBe(false);
    expect(isDeskRoutePath('/vessel/archive')).toBe(false);
    expect(isDeskRoutePath('/order/order-info')).toBe(false);
    // 前缀相同但不是工作台的路径不能被误判
    expect(isDeskRoutePath('/message/service-config')).toBe(false);
  });
});
