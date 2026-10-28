import { readdirSync, readFileSync } from 'node:fs';
import { relative, resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

const appRoot = resolve(process.cwd(), 'apps/web-ele');
const readApp = (path: string) => readFileSync(resolve(appRoot, path), 'utf8');

function collectVueFiles(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = resolve(dir, entry.name);
    if (entry.isDirectory()) {
      collectVueFiles(path, out);
    } else if (entry.name.endsWith('.vue')) {
      out.push(path);
    }
  }
  return out;
}

describe('list page keepAlive contract', () => {
  it('every route-level list page refreshes silently on reactivation', () => {
    const unwired: string[] = [];
    for (const file of collectVueFiles(resolve(appRoot, 'src/views'))) {
      if (!file.endsWith('index.vue')) {
        continue;
      }
      const source = readFileSync(file, 'utf8');
      const isListPage =
        source.includes('PageFilterPanel') ||
        source.includes('components/pagination');
      if (isListPage && !source.includes('useKeepAliveRefresh(')) {
        unwired.push(relative(appRoot, file));
      }
    }
    expect(unwired).toEqual([]);
  });
});

describe('page-filter-panel collapse contract', () => {
  const source = readApp('src/components/page/page-filter-panel.vue');

  it('collapses to the first 3 filters by default', () => {
    expect(source).toContain('COLLAPSED_VISIBLE_COUNT = 3');
    expect(source).toContain('const collapsed = ref(true);');
  });

  it('keeps the collapse count constant in sync with the CSS selector', () => {
    expect(source).toContain('nth-of-type(n + 4)');
  });

  it('never hides the action buttons row', () => {
    expect(source).toContain('.el-form-item:not(.hx-filter-panel__actions)');
  });

  it('pins the filter panel to the top of the page scroll area', () => {
    expect(source).toContain('position: sticky');
    expect(source).toContain('top: 0;');
  });

  it('falls back to an opaque background, then frosts with backdrop blur', () => {
    expect(source).toContain('background: var(--el-fill-color-lighter);');
    expect(source).toContain('@supports (backdrop-filter: blur(1px))');
    expect(source).toContain('color-mix(');
    expect(source).toContain('backdrop-filter: blur(10px) saturate(1.4);');
  });

  it('keeps the action buttons pinned to the top-right in both states', () => {
    expect(source).toContain('position: absolute;');
    expect(source).toContain('right: 16px;');
  });

  it('reserves button space only on the first condition row', () => {
    // 占位符 order 介于前 3 个条件与第 4 个及以后之间：
    // 第一行为右上角按钮让位，展开后其余条件行用满整行宽度；
    // 宽度取按钮组实测值，避免窄屏下按钮压到查询项
    expect(source).toContain('--hx-filter-actions-width');
    expect(source).toContain('order: 1;');
    expect(source).toContain('order: 2;');
  });

  it('toggles collapse with a text link and rotating chevron before search', () => {
    expect(source).toContain("{{ collapsed ? '展开' : '收起' }}");
    expect(source).toContain('hx-filter-panel__toggle-icon');
    expect(source).toContain("'is-expanded': !collapsed");
    const toggleIndex = source.indexOf("{{ collapsed ? '展开' : '收起' }}");
    const searchIndex = source.indexOf(':icon="Search"');
    expect(toggleIndex).toBeGreaterThan(-1);
    expect(searchIndex).toBeGreaterThan(-1);
    expect(toggleIndex).toBeLessThan(searchIndex);
  });
});

describe('pagination sticky contract', () => {
  const source = readApp('src/components/pagination/index.vue');

  it('pins to the bottom-right of the page scroll area', () => {
    expect(source).toContain('position: sticky');
    expect(source).toContain('bottom: 0;');
    expect(source).toContain('justify-content: flex-end;');
  });

  it('stays at the viewport bottom even when the list is short', () => {
    // 卡片纵向 flex 时 margin-top:auto 把分页推到卡片底部，短列表不上浮
    expect(source).toContain('margin-top: auto;');
  });

  it('keeps an opaque card background so rows do not show through', () => {
    expect(source).toContain('hsl(var(--card))');
  });

  it('frosts with backdrop blur when supported', () => {
    expect(source).toContain('hsl(var(--card) / 62%)');
    expect(source).toContain('backdrop-filter: blur(10px) saturate(1.4);');
  });
});

describe('list card growth contract', () => {
  const source = readFileSync(
    resolve(process.cwd(), 'packages/styles/src/ele/index.css'),
    'utf8',
  );

  it('grows the card only for standard list pages', () => {
    expect(source).toContain('&-view:has(> .hx-filter-panel)');
    expect(source).toContain('&-view:has(> .hx-pagination)');
    expect(source).toContain('min-height: 100%');
  });

  it('lays standard list cards out as a flex column', () => {
    // 纵向 flex 是分页 margin-top:auto 生效的前提
    const cardRule = source.slice(
      source.indexOf('&-view:has(> .hx-filter-panel)'),
      source.indexOf('}', source.indexOf('&-view:has(> .hx-pagination)')),
    );
    expect(cardRule).toContain('display: flex');
    expect(cardRule).toContain('flex-direction: column');
  });

  it('pins pagination inside tab-wrapped list pages too', () => {
    // 卡片高度经 tabs 传导到 pane，分页 margin-top:auto 才能钉到底；
    // el-tabs__content 的 overflow:hidden 会吞掉分页 sticky bottom
    expect(source).toContain('&-view:has(.el-tabs .hx-pagination)');
    expect(source).toContain('overflow: visible');
  });
});
