import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

/**
 * 首页切换入口回归保护。
 *
 * 背景（2026-09-23）：运营在「微页面」新建并发布页面后，移动端首页仍显示旧内容。
 * 根因是移动端首页固定读取 `page_type='1' AND home_status='1'` 的装修记录
 * （`PageDesignPreviewService#getPublishedHome`），而列表页的「设为首页」入口在
 * 2026-07-16 被当作“永不成立的死按钮”删除（当时条件是
 * `homeStatus === '0' && pageType === '1'`，而 pageType==='1' 本身就代表首页），
 * 删掉后再无任何方式把已发布页面切换成线上首页。
 *
 * 本测试守住两件事：
 * 1. 列表页必须提供「设为首页」入口，且调用专用的 set-home 接口；
 * 2. 入口只对已发布页面开放，避免把草稿页设成首页后 C 端直接报错。
 */
const listPath = resolve(
  process.cwd(),
  'apps/web-ele/src/views/promotion/page-design/index.vue',
);
const listSource = readFileSync(listPath, 'utf8');

describe('page design list homepage switch', () => {
  it('exposes a set-as-home action wired to the dedicated endpoint', () => {
    expect(listSource).toContain('setAsHome');
    expect(listSource).toContain('设为首页');
  });

  it('only offers the action for pages that already have a live version', () => {
    const tooltipStart = listSource.indexOf('<ElTooltip content="设为首页">');
    expect(tooltipStart).toBeGreaterThan(-1);
    const guard = listSource.slice(
      tooltipStart,
      listSource.indexOf('</ElTooltip>', tooltipStart),
    );

    // 守卫必须同时覆盖「已发布」与「当前不是首页」，否则会重复触发或让 C 端读不到快照
    expect(guard).toContain("publishedStatus === '1'");
    expect(guard).toContain("homeStatus !== '1'");
  });
});
