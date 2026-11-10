import { describe, expect, it } from 'vitest';

import {
  buildAttrTagFromTemplate,
  filterAttrSuggestions,
  getAttrLabelPreset,
  getAttrValuePresets,
  GOODS_ATTR_TAG_LABEL_PRESETS,
  GOODS_ATTR_TAG_TEMPLATES,
  isAttrValueUsed,
} from './goods-attr-tag-presets';
import { validateGoodsAttrTags } from './goods-spu-form-validation';

/** 与后端 GoodsAttrTagNormalizer 的白名单逐值一致 */
const BACKEND_ICONS = new Set([
  'location',
  'package',
  'security',
  'shopping-bag',
  'snowflake',
  'star-filled',
  'time',
  'trophy',
]);
const BACKEND_TONES = new Set(['blue', 'gray', 'green', 'orange']);
/** 后端长度上限：属性名 12、属性值 24、弹窗标题 30、弹窗内容 500 */
const MAX_LABEL = 12;
const MAX_VALUE = 24;

describe('goods attr tag presets', () => {
  it('keeps every label preset inside the backend whitelist', () => {
    for (const preset of GOODS_ATTR_TAG_LABEL_PRESETS) {
      expect(preset.label.length).toBeLessThanOrEqual(MAX_LABEL);
      expect(BACKEND_ICONS.has(preset.icon)).toBe(true);
      expect(BACKEND_TONES.has(preset.tone)).toBe(true);
      // 没有值的属性名对运营毫无帮助，等于换了个空输入框
      expect(preset.values.length).toBeGreaterThan(0);
      for (const value of preset.values) {
        expect(value.length).toBeLessThanOrEqual(MAX_VALUE);
        expect(value.trim()).toBe(value);
      }
    }
  });

  it('does not repeat a label preset', () => {
    const labels = GOODS_ATTR_TAG_LABEL_PRESETS.map((item) => item.label);
    expect(new Set(labels).size).toBe(labels.length);
  });

  it('produces templates that pass the submit-time validation', () => {
    // 预设的意义是「点一下就能提交」；被校验拦住就失去意义
    const tags = GOODS_ATTR_TAG_TEMPLATES.map((template) =>
      buildAttrTagFromTemplate(template),
    );
    expect(validateGoodsAttrTags(tags)).toBeNull();
    expect(tags.length).toBeLessThanOrEqual(6);
  });

  it('keeps template copy inside the length limits', () => {
    for (const template of GOODS_ATTR_TAG_TEMPLATES) {
      expect(template.label.length).toBeLessThanOrEqual(MAX_LABEL);
      expect(template.value.length).toBeLessThanOrEqual(MAX_VALUE);
      expect(template.popupTitle.length).toBeLessThanOrEqual(30);
      expect(template.popupContent.length).toBeLessThanOrEqual(500);
      expect(BACKEND_ICONS.has(template.icon)).toBe(true);
      expect(BACKEND_TONES.has(template.tone)).toBe(true);
    }
  });

  it('opens the popup only when the template ships popup copy', () => {
    const popupTemplate = GOODS_ATTR_TAG_TEMPLATES.find(
      (item) => item.popupContent.length > 0,
    );
    // 至少有一条带弹窗文案的模板，否则「点一下就有说明」的引导就断了
    expect(popupTemplate).toBeDefined();
    const withPopup = buildAttrTagFromTemplate(
      popupTemplate as (typeof GOODS_ATTR_TAG_TEMPLATES)[number],
    );
    expect(withPopup.linkType).toBe('popup');

    const withoutPopup = buildAttrTagFromTemplate({
      label: '规格',
      value: '整箱装',
      icon: 'package',
      tone: 'gray',
      popupContent: '   ',
      popupTitle: '',
    });
    expect(withoutPopup.linkType).toBe('none');
    expect(withoutPopup.popupContent).toBe('   ');
  });

  it('scopes value presets to the chosen label', () => {
    expect(getAttrValuePresets('储存条件')).toContain('冷藏');
    expect(getAttrValuePresets(' 储存条件 ')).toContain('冷藏');
    // 自建属性名没有候选值，不猜
    expect(getAttrValuePresets('自定义维度')).toEqual([]);
    expect(getAttrValuePresets('')).toEqual([]);
    expect(getAttrValuePresets(null)).toEqual([]);
  });

  it('resolves the default icon and tone for a label', () => {
    expect(getAttrLabelPreset('产地')?.icon).toBe('location');
    expect(getAttrLabelPreset('产地')?.tone).toBe('green');
    expect(getAttrLabelPreset('自定义维度')).toBeUndefined();
    expect(getAttrLabelPreset(undefined)).toBeUndefined();
  });

  it('matches suggestions by substring and dedupes them', () => {
    expect(filterAttrSuggestions(['系列', '储存条件', '规格'], '')).toEqual([
      '系列',
      '储存条件',
      '规格',
    ]);
    // 运营记得住「冷藏」但记不住词条前缀，所以用包含匹配
    expect(filterAttrSuggestions(['冷藏', '常温', '0-4℃冷藏'], '冷藏')).toEqual(
      ['冷藏', '0-4℃冷藏'],
    );
    expect(filterAttrSuggestions(['系列', '系列', ' '], '')).toEqual(['系列']);
    expect(filterAttrSuggestions(['系列'], '不存在的词')).toEqual([]);
  });

  it('flags a value as used only within the same label', () => {
    const rows = [
      { label: '储存条件', value: '冷藏' },
      { label: '系列', value: '地标' },
      { label: '储存条件', value: '常温' },
    ];

    expect(isAttrValueUsed(rows, '储存条件', '冷藏', 2)).toBe(true);
    // 自己这一行不算重复
    expect(isAttrValueUsed(rows, '储存条件', '冷藏', 0)).toBe(false);
    // 不同属性名下的同名值不算重复
    expect(isAttrValueUsed(rows, '系列', '冷藏', 1)).toBe(false);
    expect(isAttrValueUsed(rows, '储存条件', '', 0)).toBe(false);
  });
});
