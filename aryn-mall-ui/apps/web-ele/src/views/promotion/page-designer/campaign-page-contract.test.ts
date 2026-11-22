import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

import { selectCampaignPreviewGoods } from '../page-design/components/common/retail-preview/campaign-preview';
import { createGoodsGroupDefaults } from '../page-design/components/goods-group/types';
import { getComponentDefinition } from './registry/component-registry';
import { migratePageContent } from './schema/migrate';

const fixture = JSON.parse(
  readFileSync(
    resolve(
      process.cwd(),
      '../aryn-mall-java/aryn-promotion/aryn-promotion-biz/src/test/resources/campaign-page-v3.json',
    ),
    'utf8',
  ),
);

describe('活动包后端生成的装修文档', () => {
  it('通过现有迁移器和每个组件的真实校验器', () => {
    const document = migratePageContent(fixture);
    expect(document.schemaVersion).toBe(3);
    const components = document.sections.flatMap(
      (section) => section.components,
    );
    expect(components.map((component) => component.type)).toEqual([
      'title-text',
      'notice',
      'goods-group',
    ]);
    for (const component of components) {
      const definition = getComponentDefinition(component.type);
      expect(definition).toBeDefined();
      expect(definition?.validate(component.props)).toEqual([]);
    }
  });

  it('固定样本只展示当前货盘中已报价商品，不能回落其他主体或普通价格', () => {
    const props = createGoodsGroupDefaults();
    props.dataSource = { mode: 'manual', targetIds: ['approved', 'unquoted'] };
    const approved = {
      id: 'approved',
      imageUrl: '',
      name: '样本商品',
      price: 90,
      originalPrice: 100,
      sales: 0,
      stock: Number.NaN,
    };
    const foreign = { ...approved, id: 'foreign', price: 1 };
    expect(
      selectCampaignPreviewGoods(
        props,
        new Map([
          ['approved', approved],
          ['foreign', foreign],
        ]),
      ),
    ).toEqual([approved]);
    expect(selectCampaignPreviewGoods(props, new Map())).toEqual([]);
  });
});
