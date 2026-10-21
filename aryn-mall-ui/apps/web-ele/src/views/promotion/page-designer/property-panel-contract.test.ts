import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

const panelSource = readFileSync(
  resolve(
    process.cwd(),
    'apps/web-ele/src/views/promotion/page-designer/components/property-panel.vue',
  ),
  'utf8',
);

describe('property panel content/style split', () => {
  it('renders component-specific style settings in the style tab', () => {
    expect(panelSource).toContain('styleSettings');
    expect(panelSource).toContain('styleSettingComponent');
    expect(panelSource).toContain("activeTab === 'style'");
  });

  it('renders the shared common style editor in the style tab', () => {
    expect(panelSource).toContain('CommonStyle');
    expect(panelSource).toContain('commonStyleValue');
    expect(panelSource).toContain('updateCommonStyle');
  });

  it('keeps content and style panels mutually exclusive', () => {
    expect(panelSource).toContain("activeTab === 'content'");
  });

  it('hides the style tab when a component has no style configuration', () => {
    expect(panelSource).toContain('hasStyleTab');
  });

  it('drops the empty advanced tab until component-level advanced settings exist', () => {
    expect(panelSource).not.toContain('label="高级"');
    expect(panelSource).not.toContain('暂无高级配置');
  });
});
