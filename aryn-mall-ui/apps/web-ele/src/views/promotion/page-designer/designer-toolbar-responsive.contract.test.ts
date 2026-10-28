import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

/**
 * 装修编辑器顶栏「一行放不下时」的自适应契约。
 *
 * 背景（2026-10-27 用户反馈 + 截图）：浏览器视口约 1326px 时，顶栏一组控件
 * 互相挤压——`ElRadioGroup` 被压成上下两行、「顶部」溢出到工具栏上方、
 * 「滚动后」压到工具栏下方，中间组还叠到了页面名/状态标签上。
 *
 * 根因有两层：
 * 1. 中列用 `justify-self: center` 按内容宽度居中，轨道不够宽时是「自身溢出
 *    轨道」而不是被裁切，`overflow: hidden` 形同虚设，直接盖住左右两组；
 * 2. 中列子项允许收缩，`el-radio-group` 又是 `flex-wrap: wrap`，空间不足时
 *    分段控件被压窄后竖排。
 *
 * 修复口径（本测试钉死，防回归）：
 * - 中列拉伸填满剩余轨道、内容在内部居中，溢出只裁在自己轨道内；
 * - 中列子项禁止收缩、分段控件禁止换行；
 * - 低优先级信息按视口宽度分级收起（滑杆 / 次要工具文字），保证
 *   `.page-designer` 1180px 最小设计宽度下仍是一行。
 */
const toolbarPath = resolve(
  process.cwd(),
  'apps/web-ele/src/views/promotion/page-designer/components/designer-toolbar.vue',
);
const toolbar = readFileSync(toolbarPath, 'utf8');

/** 取最后一个 <style> 块并剥掉注释：说明性注释会引用旧写法，断言只针对真实声明 */
function styleBlock(source: string) {
  const matches = [...source.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)];
  return (matches.at(-1)?.[1] ?? '').replaceAll(/\/\*[\s\S]*?\*\//g, '');
}

function styleRule(styles: string, selector: string) {
  const escaped = selector.replaceAll(/[.*+?^${}()|[\]\\]/g, String.raw`\$&`);
  return new RegExp(`${escaped}\\s*\\{([^}]*)\\}`).exec(styles)?.[1] ?? '';
}

function mediaBlock(styles: string, query: string) {
  const start = styles.indexOf(`@media (${query})`);
  if (start === -1) return '';
  const open = styles.indexOf('{', start);
  let depth = 0;
  for (let i = open; i < styles.length; i++) {
    if (styles[i] === '{') depth++;
    else if (styles[i] === '}') {
      depth--;
      if (depth === 0) return styles.slice(open + 1, i);
    }
  }
  return '';
}

const styles = styleBlock(toolbar);

describe('装修顶栏分组自适应', () => {
  it('中列拉伸填满剩余轨道：溢出裁在自己轨道内，不压左右两组', () => {
    const center = styleRule(styles, '.toolbar-center');
    expect(center).toMatch(/justify-self:\s*stretch/);
    expect(center).toMatch(/justify-content:\s*center/);
    expect(center).toMatch(/overflow:\s*hidden/);
    // 历史 bug：按内容宽度居中会导致中列自身溢出轨道、盖住两侧按钮
    expect(center).not.toMatch(/justify-self:\s*center/);
    // 三列结构保持不变：左组/中列弹性/右组
    expect(styleRule(styles, '.designer-toolbar')).toMatch(
      /grid-template-columns:\s*auto minmax\(0,\s*1fr\) auto/,
    );
  });

  it('中列子项禁止收缩、分段控件禁止换行：任何宽度下都不会竖排', () => {
    const itemRule = styleRule(
      styles,
      '.toolbar-center :deep(.el-space__item)',
    );
    expect(itemRule).toMatch(/flex:\s*none/);
    const radioRule = styleRule(
      styles,
      '.toolbar-center :deep(.el-radio-group)',
    );
    expect(radioRule).toMatch(/flex-wrap:\s*nowrap/);
  });

  it('1450px 以下收起导航预览滑杆，保留「顶部 / 滚动后」切换', () => {
    const compact = mediaBlock(styles, 'max-width: 1449px');
    expect(compact).toContain('.scroll-preview-fine');
    expect(styleRule(compact, '.scroll-preview-fine')).toMatch(
      /display:\s*none/,
    );
  });

  it('1320px 以下只把次要工具收成图标，主操作保留文字', () => {
    const compact = mediaBlock(styles, 'max-width: 1319px');
    // 次要工具（页面结构/模板/本页主题/素材）文字收起，按钮收紧成方形图标
    expect(styleRule(compact, '.toolbar-label--collapsible')).toMatch(
      /display:\s*none/,
    );
    expect(
      styleRule(compact, '.toolbar-actions :deep(.el-button.toolbar-tool)'),
    ).toMatch(/padding-left:\s*8px/);

    // 收起的只有这四个工具；预览 / 保存草稿 / 发布 必须始终带文字
    const collapsibleLabels = [
      ...toolbar.matchAll(/toolbar-label--collapsible">([^<]+)</g),
    ].map((match) => match[1]?.trim());
    expect(collapsibleLabels).toEqual(['页面结构', '模板', '本页主题', '素材']);
    for (const label of ['预览', '保存草稿', '发布']) {
      expect(toolbar).toMatch(
        new RegExp(
          `@click="emit\\('[a-z]+'\\)"\\s*>\\s*${label}\\s*</ElButton>`,
        ),
      );
      expect(toolbar).not.toContain(`toolbar-label--collapsible">${label}`);
    }
  });

  it('文字收起后按钮仍有可读名称（aria-label + Tooltip 兜底）', () => {
    for (const label of ['页面结构', '模板', '本页主题', '素材']) {
      expect(toolbar).toContain(`aria-label="${label}"`);
    }
    // Tooltip 文案不能与按钮文字完全相同，否则宽屏悬停时是重复提示
    expect(toolbar).toContain('套用页面模板，或把当前页面存为模板');
    expect(toolbar).toContain('素材库：查看与管理图片素材');
  });

  it('页面名宽度连续收缩（clamp），不随断点突变', () => {
    expect(styleRule(styles, '.page-name')).toMatch(
      /width:\s*clamp\(180px,\s*15\.5vw,\s*240px\)/,
    );
  });
});
