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
 * 本测试守住三件事：
 * 1. 列表页必须提供「设为首页」入口，且调用专用的 set-home 接口；
 * 2. 入口只对「已发布的非当前首页微页面」开放——缺已发布守卫会让 C 端读不到快照，
 *    缺类型守卫会把分类页/个人中心页等固定槽位页晋升成首页（pageType 被改写，
 *    槽位页面直接消失），缺 homeStatus 守卫会重复触发；
 * 3. 操作统一挂在 RowActionDropdown 的「⋯」菜单里（2026-10-07 列表重构）。
 */
const listPath = resolve(
  process.cwd(),
  'apps/web-ele/src/views/promotion/page-design/index.vue',
);
const actionsPath = resolve(
  process.cwd(),
  'apps/web-ele/src/views/promotion/page-design/list-actions.ts',
);
const listSource = readFileSync(listPath, 'utf8');
const actionsSource = readFileSync(actionsPath, 'utf8');

describe('page design list homepage switch', () => {
  it('exposes a set-as-home action wired to the dedicated endpoint', () => {
    expect(listSource).toContain('setAsHome');
    expect(listSource).toContain('RowActionDropdown');
    expect(actionsSource).toContain("'set-home'");
  });

  it('only offers the action for published micro pages that are not the current home', () => {
    const start = actionsSource.indexOf('export function rowActions');
    expect(start).toBeGreaterThan(-1);
    const block = actionsSource.slice(
      start,
      actionsSource.indexOf('\n}', start),
    );

    expect(block).toContain("'set-home'");
    expect(block).toContain("pageType === '0'");
    expect(block).toContain("publishedStatus === '1'");
    expect(block).toContain("homeStatus !== '1'");
  });
});

/**
 * 生效徽标回归保护。
 *
 * 背景（2026-10-09）：列表里出现「品质甄选 · 商城首页（微页面）」挂着「生效中」，
 * 与真正的「悦航购 · 商城首页（商城首页）当前首页」并存，运营无法理解哪条才是首页。
 * 根因在后端 `markCEndEffective` 按分页里出现过的每种 pageType 查生效页，
 * 微页面（pageType=0）也被判了一次，于是「已发布且发布最新的微页面」被误标。
 *
 * 后端已收敛为只判定 `PageDesignComponentTypes.EFFECTIVE_SLOT_PAGE_TYPES`，
 * 前端侧对称地守住两件事：
 * 1. 徽标渲染走 `showEffectiveBadge`，微页面即使拿到 effective 也不显示；
 * 2. 槽位页型清单两端同源，任一侧新增页型时测试立即报错。
 */
const apiPath = resolve(
  process.cwd(),
  'apps/web-ele/src/api/promotion/page-design.ts',
);
const apiSource = readFileSync(apiPath, 'utf8');

describe('page design effective badge', () => {
  it('labels only the home row with the current-home wording', () => {
    expect(actionsSource).toContain("pageType === '1' ? '当前首页' : '生效中'");
  });

  it('never renders the effective badge on micro pages', () => {
    const start = actionsSource.indexOf('export function showEffectiveBadge');
    expect(start).toBeGreaterThan(-1);
    const block = actionsSource.slice(
      start,
      actionsSource.indexOf('\n}', start),
    );

    expect(block).toContain('isEffectiveSlotPageType(row.pageType)');
    // 列表模板必须走该判定，而不是裸用后端回显的 effective
    expect(listSource).toContain('showEffectiveBadge(row as PageDesignRecord)');
  });

  it('keeps the effective slot page types in sync with the backend', () => {
    expect(apiSource).toContain(
      "export const EFFECTIVE_SLOT_PAGE_TYPES: PageDesignType[] = ['1', '3', '4', '2']",
    );
    expect(apiSource).not.toContain(
      "EFFECTIVE_SLOT_PAGE_TYPES: PageDesignType[] = ['0'",
    );
  });
});
