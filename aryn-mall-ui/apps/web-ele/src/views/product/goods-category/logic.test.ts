import type { CategoryBadgeStyle } from './logic';

import { describe, expect, it } from 'vitest';

import {
  badgePreviewStyle,
  buildBadgeOptions,
  buildBadgeStyleMap,
  deriveBadgeFontSize,
  deriveBadgePaddingX,
  NO_BADGE_VALUE,
  normalizeBadgeBorderRadius,
  normalizeBadgeHeight,
  normalizeBadgeIconUrl,
  normalizeBadgeKey,
  resolveBadgeStyle,
  toPreviewPx,
} from './logic';

const BADGE_LIST = [
  {
    badgeKey: '1',
    badgeText: '荐',
    bgColor: '#00B578',
    status: '0',
    textColor: '#FFFFFF',
  },
  {
    badgeKey: '2',
    badgeText: '热',
    bgColor: '#FF4D2D',
    status: '1',
    textColor: '#FFFFFF',
  },
];

/**
 * 文字角标默认样式：28rpx 高 → 字号 19rpx、内边距 5rpx、圆角 4rpx。
 * 用默认值 + 覆盖生成期望对象，避免每处断言都抄一遍全部字段。
 */
function textBadgeStyle(
  overrides: Partial<CategoryBadgeStyle> = {},
): CategoryBadgeStyle {
  return {
    bgColor: '#00B578',
    borderRadius: 4,
    color: '#FFFFFF',
    fontSize: 19,
    height: 28,
    iconUrl: '',
    paddingX: 5,
    text: '荐',
    ...overrides,
  };
}

describe('类目角标 key 归一 normalizeBadgeKey', () => {
  it('空值统一成「无角标」', () => {
    expect(normalizeBadgeKey(null)).toBe(NO_BADGE_VALUE);
    expect(normalizeBadgeKey(undefined)).toBe(NO_BADGE_VALUE);
    expect(normalizeBadgeKey('')).toBe(NO_BADGE_VALUE);
    expect(normalizeBadgeKey('   ')).toBe(NO_BADGE_VALUE);
  });

  it('数字与带空格字符串归一成字符串', () => {
    expect(normalizeBadgeKey(1)).toBe('1');
    expect(normalizeBadgeKey(' 1 ')).toBe('1');
    expect(normalizeBadgeKey('3f2abc')).toBe('3f2abc');
  });
});

describe('角标图片地址归一 normalizeBadgeIconUrl', () => {
  it('http(s):// 与 / 开头的站内绝对路径合法，trim 后返回', () => {
    expect(normalizeBadgeIconUrl(' https://cdn.example.com/a.png ')).toBe(
      'https://cdn.example.com/a.png',
    );
    expect(normalizeBadgeIconUrl('http://cdn.example.com/a.png')).toBe(
      'http://cdn.example.com/a.png',
    );
    expect(normalizeBadgeIconUrl('/static/badge.png')).toBe(
      '/static/badge.png',
    );
  });

  it('空值 / 非法协议 / 相对路径 / 超长地址都视为无图', () => {
    expect(normalizeBadgeIconUrl(null)).toBe('');
    expect(normalizeBadgeIconUrl(undefined)).toBe('');
    expect(normalizeBadgeIconUrl('   ')).toBe('');
    expect(normalizeBadgeIconUrl('ftp://cdn.example.com/a.png')).toBe('');
    expect(normalizeBadgeIconUrl('cdn.example.com/a.png')).toBe('');
    // 512 是合法上限（含首个 /），513 字符视为非法
    expect(normalizeBadgeIconUrl(`/${'x'.repeat(511)}`)).toBe(
      `/${'x'.repeat(511)}`,
    );
    expect(normalizeBadgeIconUrl(`/${'x'.repeat(512)}`)).toBe('');
  });
});

describe('高度/圆角归一与字号推导', () => {
  it('非法值回退缺省，越界值 clamp 到合法范围', () => {
    expect(normalizeBadgeHeight(undefined)).toBe(28);
    expect(normalizeBadgeHeight('')).toBe(28);
    expect(normalizeBadgeHeight('abc')).toBe(28);
    expect(normalizeBadgeHeight(16)).toBe(16);
    expect(normalizeBadgeHeight(96)).toBe(96);
    expect(normalizeBadgeHeight(8)).toBe(16);
    expect(normalizeBadgeHeight(200)).toBe(96);
    expect(normalizeBadgeHeight(28.6)).toBe(29);

    expect(normalizeBadgeBorderRadius(undefined)).toBe(4);
    expect(normalizeBadgeBorderRadius(null)).toBe(4);
    expect(normalizeBadgeBorderRadius('abc')).toBe(4);
    expect(normalizeBadgeBorderRadius(0)).toBe(0);
    expect(normalizeBadgeBorderRadius(-6)).toBe(0);
    expect(normalizeBadgeBorderRadius(999)).toBe(48);
  });

  it('字号/内边距由高度推导，与 C 端 clamp 口径一致', () => {
    expect(deriveBadgeFontSize(28)).toBe(19);
    expect(deriveBadgePaddingX(28)).toBe(5);
    expect(deriveBadgeFontSize(16)).toBe(11);
    expect(deriveBadgePaddingX(16)).toBe(3);
    expect(deriveBadgeFontSize(96)).toBe(48);
    expect(deriveBadgePaddingX(96)).toBe(16);
    // 极小/极大高度分别被 clamp 到字号 10-48、内边距 3-16
    expect(deriveBadgeFontSize(2)).toBe(10);
    expect(deriveBadgePaddingX(2)).toBe(3);
    expect(deriveBadgeFontSize(1000)).toBe(48);
    expect(deriveBadgePaddingX(1000)).toBe(16);
  });
});

describe('类目角标样式映射 buildBadgeStyleMap', () => {
  it('按 badgeKey 建立 key → 文字/双色/尺寸映射，字色取 textColor', () => {
    expect(buildBadgeStyleMap(BADGE_LIST)).toEqual({
      1: textBadgeStyle(),
      2: textBadgeStyle({ bgColor: '#FF4D2D', text: '热' }),
    });
  });

  it('跳过「无角标」key 与非法颜色，避免列表渲染脏样式', () => {
    const map = buildBadgeStyleMap([
      {
        badgeKey: '0',
        badgeText: '无',
        bgColor: '#00B578',
        textColor: '#FFFFFF',
      },
      { badgeKey: '3', badgeText: '新', bgColor: 'red', textColor: '#FFFFFF' },
      { badgeKey: '4', badgeText: '空', bgColor: '#00B578', textColor: '' },
      // 后端约定只收 #RRGGBB，三位简写同样视为非法
      { badgeKey: '5', badgeText: '好', bgColor: '#1677FF', textColor: '#FFF' },
      {
        badgeKey: '6',
        badgeText: '好',
        bgColor: '#1677FF',
        textColor: '#FFFFFF',
      },
    ]);
    expect(map).toEqual({
      6: textBadgeStyle({ bgColor: '#1677FF', text: '好' }),
    });
  });

  it('仅图片（文字为空）的角标也进映射，颜色非法回退默认绿/白', () => {
    const map = buildBadgeStyleMap([
      {
        badgeKey: '7',
        badgeText: '',
        iconUrl: 'https://cdn.example.com/badge-new.png',
        bgColor: 'red',
        textColor: '#FFF',
        badgeHeight: 96,
        borderRadius: 60,
      },
    ]);
    expect(map).toEqual({
      7: {
        bgColor: '#00B578',
        borderRadius: 48,
        color: '#FFFFFF',
        fontSize: 48,
        height: 96,
        iconUrl: 'https://cdn.example.com/badge-new.png',
        paddingX: 16,
        text: '',
      },
    });
  });

  it('图片角标带文字时同样优先按图片渲染，脏颜色不阻断', () => {
    const map = buildBadgeStyleMap([
      {
        badgeKey: '8',
        badgeText: '新',
        iconUrl: '/static/badge-new.png',
        bgColor: 'blue',
        textColor: 'white',
      },
    ]);
    expect(map).toEqual({
      8: textBadgeStyle({
        bgColor: '#00B578',
        color: '#FFFFFF',
        iconUrl: '/static/badge-new.png',
        text: '新',
      }),
    });
  });

  it('无文字时 iconUrl 非法视为无图，整条不进入映射', () => {
    const map = buildBadgeStyleMap([
      {
        badgeKey: '9',
        badgeText: ' ',
        iconUrl: 'ftp://cdn.example.com/x.png',
        bgColor: '#00B578',
        textColor: '#FFFFFF',
      },
      {
        badgeKey: '10',
        badgeText: '',
        iconUrl: `https://cdn.example.com/${'x'.repeat(600)}`,
        bgColor: '#00B578',
        textColor: '#FFFFFF',
      },
    ]);
    expect(map).toEqual({});
  });

  it('高度/圆角非法值回退缺省并 clamp 到范围', () => {
    const map = buildBadgeStyleMap([
      {
        badgeKey: '11',
        badgeText: '荐',
        bgColor: '#00B578',
        textColor: '#FFFFFF',
        badgeHeight: 'abc',
        borderRadius: -6,
      },
      {
        badgeKey: '12',
        badgeText: '热',
        bgColor: '#FF4D2D',
        textColor: '#FFFFFF',
        badgeHeight: 200,
        borderRadius: 999,
      },
      {
        badgeKey: '13',
        badgeText: '新',
        bgColor: '#1677FF',
        textColor: '#FFFFFF',
        badgeHeight: 16,
        borderRadius: 0,
      },
    ]);
    expect(map['11']).toMatchObject({
      borderRadius: 0,
      fontSize: 19,
      height: 28,
      paddingX: 5,
    });
    expect(map['12']).toMatchObject({
      borderRadius: 48,
      fontSize: 48,
      height: 96,
      paddingX: 16,
    });
    expect(map['13']).toMatchObject({
      borderRadius: 0,
      fontSize: 11,
      height: 16,
      paddingX: 3,
    });
  });

  it('接口空数据时返回空映射', () => {
    expect(buildBadgeStyleMap(null)).toEqual({});
    expect(buildBadgeStyleMap(undefined)).toEqual({});
    expect(buildBadgeStyleMap([])).toEqual({});
  });
});

describe('类目角标解析 resolveBadgeStyle', () => {
  const map = buildBadgeStyleMap(BADGE_LIST);

  it('无角标 / 未知 key / 空映射都返回 null（列表显示占位横线）', () => {
    expect(resolveBadgeStyle(map, '0')).toBeNull();
    expect(resolveBadgeStyle(map, null)).toBeNull();
    expect(resolveBadgeStyle(map, '')).toBeNull();
    expect(resolveBadgeStyle(map, 'not-exists')).toBeNull();
    expect(resolveBadgeStyle(null, '1')).toBeNull();
  });

  it('命中配置时返回接口下发的样式', () => {
    expect(resolveBadgeStyle(map, '2')).toEqual(
      textBadgeStyle({ bgColor: '#FF4D2D', text: '热' }),
    );
    // 历史数据可能以数字返回
    expect(resolveBadgeStyle(map, 1)?.text).toBe('荐');
  });
});

describe('类目表单角标选项 buildBadgeOptions', () => {
  it('首项固定「无角标」，停用角标追加「（已停用）」标记', () => {
    expect(buildBadgeOptions(BADGE_LIST)).toEqual([
      { label: '无角标', value: '0' },
      { label: '荐', value: '1' },
      { label: '热（已停用）', value: '2' },
    ]);
  });

  it('无文字角标 label 用「图片角标」，停用仍追加「（已停用）」', () => {
    expect(
      buildBadgeOptions([
        {
          badgeKey: '21',
          badgeText: '',
          iconUrl: 'https://cdn.example.com/a.png',
          status: '1',
        },
        {
          badgeKey: '22',
          badgeText: '  ',
          iconUrl: '/static/b.png',
          status: '0',
        },
      ]),
    ).toEqual([
      { label: '无角标', value: '0' },
      { label: '图片角标（已停用）', value: '21' },
      { label: '图片角标', value: '22' },
    ]);
  });

  it('编辑值不在配置里时追加「已失效角标」，保证可显示可保存', () => {
    expect(buildBadgeOptions(BADGE_LIST, '9')).toEqual([
      { label: '无角标', value: '0' },
      { label: '荐', value: '1' },
      { label: '热（已停用）', value: '2' },
      { label: '已失效角标（9）', value: '9' },
    ]);
  });

  it('无角标或已在配置里的 key 不追加失效项', () => {
    expect(buildBadgeOptions(BADGE_LIST, '0')).toHaveLength(3);
    expect(buildBadgeOptions(BADGE_LIST, undefined)).toHaveLength(3);
    expect(buildBadgeOptions(BADGE_LIST, 1)).toHaveLength(3);
  });

  it('配置接口失败（空列表）时仍保留当前角标值', () => {
    expect(buildBadgeOptions([], '1')).toEqual([
      { label: '无角标', value: '0' },
      { label: '已失效角标（1）', value: '1' },
    ]);
  });
});

describe('预览换算辅助 toPreviewPx / badgePreviewStyle', () => {
  it('rpx → px 按 750rpx 设计稿口径折半', () => {
    expect(toPreviewPx(28)).toBe(14);
    expect(toPreviewPx(19)).toBe(9.5);
    expect(toPreviewPx(5)).toBe(2.5);
    expect(toPreviewPx(0)).toBe(0);
  });

  it('文字角标预览：高度/行高/字号/内边距/圆角全部按 rpx/2 缩放', () => {
    expect(badgePreviewStyle(textBadgeStyle())).toEqual({
      backgroundColor: '#00B578',
      borderRadius: '2px',
      color: '#FFFFFF',
      fontSize: '9.5px',
      height: '14px',
      lineHeight: '14px',
      paddingLeft: '2.5px',
      paddingRight: '2.5px',
    });
  });

  it('图片角标预览按正方形渲染（宽=高=badgeHeight/2，圆角=borderRadius/2）', () => {
    expect(
      badgePreviewStyle(
        textBadgeStyle({
          iconUrl: 'https://cdn.example.com/badge.png',
          height: 96,
          borderRadius: 48,
        }),
      ),
    ).toEqual({ borderRadius: '24px', height: '48px', width: '48px' });
  });
});
