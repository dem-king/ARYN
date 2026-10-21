import { describe, expect, it } from 'vitest';

import {
  validateGoodsAttrTags,
  validateGoodsSkus,
} from './goods-spu-form-validation';

describe('goods spu form validation', () => {
  it('rejects an empty sku list', () => {
    expect(validateGoodsSkus('1', [])).toBe('请至少配置一个SKU');
  });

  it('rejects negative price or stock values', () => {
    expect(
      validateGoodsSkus('0', [
        {
          costPrice: 8,
          originalPrice: 12,
          salesPrice: 10,
          stock: -1,
        },
      ]),
    ).toBe('SKU价格和库存不能小于0');
  });

  it('rejects duplicate multi-spec combinations', () => {
    const specsArr = [
      {
        specsId: 'color',
        specsValueId: 'red',
      },
    ];

    expect(
      validateGoodsSkus('1', [
        {
          costPrice: 8,
          originalPrice: 12,
          salesPrice: 10,
          specsArr,
          stock: 1,
        },
        {
          costPrice: 9,
          originalPrice: 13,
          salesPrice: 11,
          specsArr,
          stock: 2,
        },
      ]),
    ).toBe('SKU规格组合不能重复');
  });

  it('rejects an existing sku without its inventory version', () => {
    expect(
      validateGoodsSkus('0', [
        {
          costPrice: 8,
          id: 'sku-1',
          originalPrice: 12,
          salesPrice: 10,
          stock: 1,
        },
      ]),
    ).toBe('商品库存版本缺失，请刷新后重试');
  });

  it('accepts complete unique multi-spec skus', () => {
    expect(
      validateGoodsSkus('1', [
        {
          costPrice: 8,
          originalPrice: 12,
          salesPrice: 10,
          specsArr: [{ specsId: 'color', specsValueId: 'red' }],
          stock: 1,
        },
        {
          costPrice: 9,
          originalPrice: 13,
          salesPrice: 11,
          specsArr: [{ specsId: 'color', specsValueId: 'blue' }],
          stock: 2,
        },
      ]),
    ).toBeNull();
  });
});

describe('goods attr tag validation', () => {
  it('accepts empty or missing attr tags', () => {
    expect(validateGoodsAttrTags([])).toBeNull();
    expect(validateGoodsAttrTags(null)).toBeNull();
    expect(validateGoodsAttrTags(undefined)).toBeNull();
  });

  it('accepts blank placeholder rows and trims values', () => {
    expect(
      validateGoodsAttrTags([
        { label: '  ', value: '  ' },
        { label: ' 系列 ', value: ' 地标 ' },
      ]),
    ).toBeNull();
  });

  it('accepts valid popup and page tags', () => {
    expect(
      validateGoodsAttrTags([
        { label: '系列', tone: 'green', value: '地标' },
        {
          label: '储存条件',
          linkType: 'popup',
          popupContent: '0-4℃ 冷藏保存',
          popupTitle: '储存方式',
          value: '冷藏',
        },
        {
          label: '榜单',
          linkType: 'page',
          linkUrl: '/pages/product/rank/index',
          value: '上榜',
        },
        {
          label: '产地',
          linkType: 'page',
          linkUrl: '/sub-pages/product/origin/index',
          value: '阳澄湖',
        },
      ]),
    ).toBeNull();
  });

  it('rejects more than six attr tags', () => {
    const tags = Array.from({ length: 7 }, (_, index) => ({
      label: `属性名${index}`,
      value: `属性值${index}`,
    }));

    expect(validateGoodsAttrTags(tags)).toBe('商品属性标签最多配置 6 个');
  });

  it('rejects a tag that only fills one of name and value', () => {
    expect(
      validateGoodsAttrTags([
        { label: '系列', value: '地标' },
        { label: '', value: '冷藏' },
      ]),
    ).toBe('第 2 个属性标签的属性名和值必须同时填写');
  });

  it('rejects a popup tag without content', () => {
    expect(
      validateGoodsAttrTags([
        {
          label: '储存条件',
          linkType: 'popup',
          popupContent: '   ',
          value: '冷藏',
        },
      ]),
    ).toBe('第 1 个属性标签的弹窗内容不能为空');
  });

  it('rejects a page tag without an internal page path', () => {
    expect(
      validateGoodsAttrTags([
        {
          label: '榜单',
          linkType: 'page',
          linkUrl: 'https://example.com/rank',
          value: '上榜',
        },
      ]),
    ).toBe('第 1 个属性标签的跳转路径必须以 /pages/ 或 /sub-pages/ 开头');
  });

  it('rejects over-length tag names and values', () => {
    expect(
      validateGoodsAttrTags([{ label: '名'.repeat(13), value: '地标' }]),
    ).toBe('第 1 个属性标签的属性名不能超过 12 个字符');

    expect(
      validateGoodsAttrTags([{ label: '系列', value: '值'.repeat(25) }]),
    ).toBe('第 1 个属性标签的属性值不能超过 24 个字符');
  });

  it('rejects over-length popup and page fields', () => {
    expect(
      validateGoodsAttrTags([
        {
          label: '储存条件',
          linkType: 'popup',
          popupContent: '说明',
          popupTitle: '标'.repeat(31),
          value: '冷藏',
        },
      ]),
    ).toBe('第 1 个属性标签的弹窗标题不能超过 30 个字符');

    expect(
      validateGoodsAttrTags([
        {
          label: '储存条件',
          linkType: 'popup',
          popupContent: '说'.repeat(501),
          value: '冷藏',
        },
      ]),
    ).toBe('第 1 个属性标签的弹窗内容不能超过 500 个字符');

    expect(
      validateGoodsAttrTags([
        {
          label: '榜单',
          linkType: 'page',
          linkUrl: `/pages/${'x'.repeat(500)}`,
          value: '上榜',
        },
      ]),
    ).toBe('第 1 个属性标签的跳转路径不能超过 500 个字符');
  });
});
