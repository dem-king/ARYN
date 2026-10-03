import { describe, expect, it } from 'vitest';

import {
  buildThemePayloadFromPrimary,
  derivePageBackgroundColor,
  deriveSecondaryColor,
  mixTowardWhite,
  THEME_PRESETS,
} from './theme-presets';

/**
 * 主题颜色衍生口径契约。
 *
 * 三端同源：后端 ThemeColorUtils.deriveSecondary（混白 35%）、
 * C 端 mallThemeStore 默认值、管理端画布预览必须一致，
 * 否则「设为默认主题」后运营在画布看到的与实机不同。
 */
describe('theme-presets', () => {
  it('mixTowardWhite 按 0~1 白占比混色', () => {
    expect(mixTowardWhite('#FF2237', 0)).toBe('#FF2237');
    expect(mixTowardWhite('#FF2237', 1)).toBe('#FFFFFF');
    // G: 34 + 221*0.5 = 144.5 → 145 = 0x91；B: 55 + 200*0.5 = 155 = 0x9B
    expect(mixTowardWhite('#FF2237', 0.5)).toBe('#FF919B');
    expect(mixTowardWhite('nope', 0.5)).toBeNull();
  });

  it('辅色 = 主色混白 35%（与后端 ThemeColorUtils.deriveSecondary 同口径）', () => {
    // G: 34 + 221*0.35 = 111.35 → 111 = 0x6F；B: 55 + 200*0.35 = 125 = 0x7D
    expect(deriveSecondaryColor('#FF2237')).toBe('#FF6F7D');
  });

  it('页面底色 = 主色混白 92%', () => {
    // G: 34 + 221*0.92 = 237.32 → 237 = 0xED；B: 55 + 200*0.92 = 239 = 0xEF
    expect(derivePageBackgroundColor('#FF2237')).toBe('#FFEDEF');
    expect(derivePageBackgroundColor('bad')).toBe('#F5F5F5');
  });

  it('预设底色统一由主色衍生，且主色色相覆盖常见品牌区间', () => {
    for (const preset of THEME_PRESETS) {
      expect(preset.pageBackgroundColor).toBe(
        derivePageBackgroundColor(preset.primaryColor),
      );
    }
    expect(THEME_PRESETS.length).toBeGreaterThanOrEqual(8);
  });

  it('由主色生成表单值时导航保持白底黑字', () => {
    const payload = buildThemePayloadFromPrimary('#4D7FFF', '天空蓝');
    expect(payload).toMatchObject({
      navigationColor: '#FFFFFF',
      navigationTextColor: '#222222',
      primaryColor: '#4D7FFF',
      radius: 8,
      themeName: '天空蓝',
    });
  });
});
