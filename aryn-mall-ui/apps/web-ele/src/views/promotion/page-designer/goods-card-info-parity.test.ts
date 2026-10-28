import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

/**
 * 商品经营类装修组件的「卡片信息」一致性契约。
 *
 * 两端是独立实现，但卡片上给用户看的信息必须同源：库存/销量口径、
 * 规格 chip、商品副标题、购买按钮开关与配色、价格小数位。这里锁住
 * 那些曾经在编辑器预览与小程序实机之间漂移过的字段。
 */

const adminComponentsRoot = resolve(
  process.cwd(),
  'apps/web-ele/src/views/promotion/page-design/components',
);
const mobileDiyRoot = resolve(
  process.cwd(),
  '../aryn-mall-uniapp/src/components/diy',
);

function readAdmin(relativePath: string) {
  return readFileSync(resolve(adminComponentsRoot, relativePath), 'utf8');
}

function readMobile(relativePath: string) {
  return readFileSync(resolve(mobileDiyRoot, relativePath), 'utf8');
}

describe('商品卡片信息与小程序一致', () => {
  it('商品排行只透出库存稀缺提示，不再展示销量与库存绝对值', () => {
    const admin = readAdmin('goods-ranking/index.vue');
    const mobile = readMobile('diy-goods-ranking/index.vue');

    for (const source of [admin, mobile]) {
      expect(source).toContain('stockHintText');
      expect(source).not.toContain('销量 {{ item.sales }}');
      expect(source).not.toContain('库存 {{ item.stock }}');
    }
  });

  it('商品瀑布流 / 多Tab 的 chip 只出规格，库存仅保留稀缺提示', () => {
    const stockChipSource = '现货 $' + '{item.stock}';

    // 两端都不得再渲染「现货 N」：绝对库存不外露，只保留售罄/低库存提示
    for (const source of [
      readAdmin('extension/goods-waterfall/index.vue'),
      readMobile('diy-goods-waterfall/index.vue'),
      readAdmin('extension/goods-tabs/index.vue'),
      readMobile('diy-goods-tabs/index.vue'),
    ]) {
      expect(source).toContain('specsInfo');
      expect(source).toContain('stockHintText');
      expect(source).not.toContain(stockChipSource);
    }
    expect(readAdmin('extension/goods-tabs/index.vue')).not.toContain(
      'goods-tabs-sales',
    );
  });

  it('多Tab商品流补齐商品副标题行', () => {
    expect(readAdmin('extension/goods-tabs/index.vue')).toContain(
      'goods-tabs-subtitle',
    );
    expect(readAdmin('extension/goods-tabs/index.vue')).toContain(
      'shouldShowSubtitle(item)',
    );
    expect(readMobile('diy-goods-tabs/index.vue')).toContain(
      'goods-tabs__subtitle',
    );
  });

  it('手选商品的购买按钮跟随开关、配色与字号，图片跟随圆角配置', () => {
    const admin = readAdmin('goods/index.vue');
    const mobile = readMobile('diy-goods/index.vue');

    for (const source of [admin, mobile]) {
      expect(source).toContain('showBuyBtn !== false');
      expect(source).toContain('buyBtnColor');
      expect(source).toContain('buyBtnSize');
    }
    expect(admin).toContain('imageBorderSize');
    expect(mobile).toContain('imageBorderSize');
  });

  it('瀑布流 / 商品推荐的划线价小数位与小程序一致', () => {
    for (const [adminFile, mobileFile] of [
      ['extension/goods-waterfall/index.vue', 'diy-goods-waterfall/index.vue'],
      ['extension/goods-recommend/index.vue', 'diy-goods-recommend/index.vue'],
    ] as const) {
      const admin = readAdmin(adminFile);
      const mobile = readMobile(mobileFile);
      expect(admin).not.toContain('item.originalPrice.toFixed(2)');
      expect(mobile).not.toContain('item.originalPrice.toFixed(2)');
    }
    expect(readAdmin('extension/goods-waterfall/index.vue')).not.toContain(
      'item.price.toFixed(2)',
    );
    expect(readMobile('diy-goods-waterfall/index.vue')).not.toContain(
      'item.price.toFixed(2)',
    );
  });

  it('排行取数与小程序同走商品分页，并归一化规格摘要', () => {
    const adminData = readAdmin('common/retail-preview/retail-data.ts');

    expect(adminData).toContain('specsInfo');
    // 后台销量统计接口只回 id/name/图/销量，预览会缺价、缺库存
    expect(adminData).not.toContain('salesRanking');
    expect(adminData).not.toContain('getProductSalesTop10');
    expect(adminData).toContain('dependencies.goodsPage');
  });
});
