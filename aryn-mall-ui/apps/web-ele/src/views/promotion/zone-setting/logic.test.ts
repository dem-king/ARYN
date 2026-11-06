import { describe, expect, it } from 'vitest';

import {
  buildZoneSettingPayload,
  createEmptyZoneForm,
  describeCostFloorChip,
  describeDiscountChip,
  formatDiscountRate,
  formatSyncTime,
  isZoneEnabled,
  isZoneFormDirty,
  normalizeZoneForm,
  resolveZoneSyncMeta,
  toNumber,
} from './logic';

describe('数值归一 toNumber', () => {
  it('空值统一成 null，避免 0 与「未设置」混淆', () => {
    expect(toNumber(null)).toBeNull();
    expect(toNumber(undefined)).toBeNull();
    expect(toNumber('')).toBeNull();
    expect(toNumber('   ')).toBeNull();
    expect(toNumber(0)).toBe(0);
    expect(toNumber('0.9')).toBe(0.9);
    expect(toNumber('abc')).toBeNull();
  });
});

describe('专区开关归一 isZoneEnabled', () => {
  it('后端未配置时默认开启', () => {
    expect(isZoneEnabled(null)).toBe(true);
    expect(isZoneEnabled(undefined)).toBe(true);
    expect(isZoneEnabled('')).toBe(true);
  });

  it("兼容 '0'/'1'、数字与布尔值", () => {
    expect(isZoneEnabled('0')).toBe(false);
    expect(isZoneEnabled(0)).toBe(false);
    expect(isZoneEnabled(false)).toBe(false);
    expect(isZoneEnabled('false')).toBe(false);
    expect(isZoneEnabled('1')).toBe(true);
    expect(isZoneEnabled(1)).toBe(true);
    expect(isZoneEnabled(true)).toBe(true);
  });
});

describe('表单加载与保存', () => {
  it('normalizeZoneForm 把字符串数字转成数字，空值转 null', () => {
    expect(
      normalizeZoneForm({
        enabled: '0',
        defaultDiscountRate: '0.9000',
        costFloorMultiplier: null,
        sortMode: '',
      }),
    ).toEqual({
      enabled: false,
      defaultDiscountRate: 0.9,
      costFloorMultiplier: null,
      sortMode: 'default',
    });
  });

  it('未配置专区返回默认开启、无折扣、默认排序', () => {
    expect(normalizeZoneForm(null)).toEqual({
      enabled: true,
      defaultDiscountRate: null,
      costFloorMultiplier: null,
      sortMode: 'default',
    });
  });

  it('保存请求体与改造前一致：enabled 用字符串，空值提交 null', () => {
    expect(
      buildZoneSettingPayload({
        enabled: true,
        defaultDiscountRate: 0.9,
        costFloorMultiplier: null,
        sortMode: 'default',
      }),
    ).toEqual({
      enabled: '1',
      defaultDiscountRate: 0.9,
      costFloorMultiplier: null,
      sortMode: 'default',
    });
  });

  it('历史 sortMode 加载后原样回传，不静默改写', () => {
    // 后端仍可能有已停用的 price_asc：页面不展示它，但未改动时也不许改成 default
    const form = normalizeZoneForm({ sortMode: 'price_asc' });
    expect(form.sortMode).toBe('price_asc');
    expect(buildZoneSettingPayload(form).sortMode).toBe('price_asc');
  });

  it('isZoneFormDirty 逐字段比较，null 与 undefined 视为同一状态', () => {
    const baseline = createEmptyZoneForm();
    expect(isZoneFormDirty({ ...baseline }, baseline)).toBe(false);
    expect(isZoneFormDirty({ ...baseline, enabled: false }, baseline)).toBe(
      true,
    );
    expect(
      isZoneFormDirty({ ...baseline, defaultDiscountRate: 0.9 }, baseline),
    ).toBe(true);
    expect(
      isZoneFormDirty({ ...baseline, sortMode: 'discount_desc' }, baseline),
    ).toBe(true);
    expect(
      isZoneFormDirty(
        { ...createEmptyZoneForm(), costFloorMultiplier: undefined as never },
        baseline,
      ),
    ).toBe(false);
  });
});

describe('折扣率文案', () => {
  it('常规折扣率按「折」展示', () => {
    expect(formatDiscountRate(0.9)).toBe('9 折');
    expect(formatDiscountRate('0.85')).toBe('8.5 折');
    expect(formatDiscountRate(0.855)).toBe('8.55 折');
    expect(formatDiscountRate(1)).toBe('10 折');
  });

  it('空值与越界值不展示成折扣数字', () => {
    expect(formatDiscountRate(null)).toBeNull();
    expect(formatDiscountRate(0)).toBeNull();
    expect(formatDiscountRate(1.2)).toBeNull();
  });

  it('合法极小折扣率不被舍成「0 折」', () => {
    expect(formatDiscountRate(0.0001)).toBe('0.001 折');
    expect(describeDiscountChip(0.0001, '未设置').text).toBe('≈ 0.001 折');
  });

  it('chip 未设置时使用专区专属文案', () => {
    expect(describeDiscountChip(0.9, '仅用等级折扣')).toEqual({
      empty: false,
      text: '≈ 9 折',
    });
    expect(describeDiscountChip(null, '仅用等级折扣')).toEqual({
      empty: true,
      text: '仅用等级折扣',
    });
  });
});

describe('成本兜底文案', () => {
  it('倍率换算成上浮/下浮百分比', () => {
    expect(describeCostFloorChip(1.05)).toEqual({
      empty: false,
      text: '成本上浮 5%',
    });
    expect(describeCostFloorChip(2)).toEqual({
      empty: false,
      text: '成本上浮 100%',
    });
    expect(describeCostFloorChip(1)).toEqual({
      empty: false,
      text: '按成本价兜底',
    });
    expect(describeCostFloorChip(0.9)).toEqual({
      empty: false,
      text: '成本下浮 10%',
    });
  });

  it('空值为未启用', () => {
    expect(describeCostFloorChip(null)).toEqual({
      empty: true,
      text: '未启用',
    });
  });
});

describe('同步状态', () => {
  it('同步时间截断到分钟，空值显示占位符', () => {
    expect(formatSyncTime('2026-11-04 20:27:19')).toBe('2026-11-04 20:27');
    expect(formatSyncTime('2026-11-04T20:27:19')).toBe('2026-11-04 20:27');
    expect(formatSyncTime(null)).toBe('—');
  });

  it('成功/失败/未同步三态文案与语气', () => {
    expect(
      resolveZoneSyncMeta({
        lastSyncStatus: 'SUCCESS',
        lastSyncTime: '2026-11-04 20:27:19',
      }),
    ).toMatchObject({
      label: '已同步',
      tone: 'success',
      time: '2026-11-04 20:27',
    });
    expect(
      resolveZoneSyncMeta({
        lastSyncStatus: 'FAILED',
        lastSyncError: '活动不存在',
      }),
    ).toMatchObject({
      label: '同步失败',
      tone: 'danger',
      tip: '活动不存在',
    });
    expect(resolveZoneSyncMeta(null)).toMatchObject({
      label: '未同步',
      tone: 'info',
      time: '—',
    });
  });
});
