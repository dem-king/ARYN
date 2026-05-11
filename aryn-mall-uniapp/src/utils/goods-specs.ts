export interface GoodsSkuSpec {
  specsName: string
  specsValueName: string
}

export interface GoodsSku {
  specsArr?: GoodsSkuSpec[]
}

export interface GoodsSpecItem {
  specsName: string
  list: Array<{
    specsValueName: string
  }>
}

/**
 * 根据 goodsSpu/goodsInfo 的规格结构初始化 specList：
 * - enableSpecs === '0'：强制生成单规格“默认”
 * - 否则：汇总每个 sku.specsArr 生成 specList（按 specsName 分组、去重 specsValueName）
 *
 * 注意：该函数会直接修改入参对象。
 */
export function initGoodsSpecs(goods: any, defaultSingleSkuName = '默认') {
  if (!goods)
    return

  // 单规格
  if (goods.enableSpecs === '0') {
    if (goods.goodsSkus?.length) {
      goods.goodsSkus[0].specsArr = [
        {
          specsValueName: defaultSingleSkuName,
        },
      ]
    }

    goods.specList = [
      {
        specsName: defaultSingleSkuName,
        list: [
          {
            specsValueName: defaultSingleSkuName,
          },
        ],
      },
    ]
    return
  }

  // 多规格
  const specsMap = new Map<string, Set<string>>()
  goods.goodsSkus?.forEach((sku: any) => {
    if (sku.specsArr && Array.isArray(sku.specsArr)) {
      sku.specsArr.forEach((spec: any) => {
        const { specsName, specsValueName } = spec
        if (!specsMap.has(specsName))
          specsMap.set(specsName, new Set())
        specsMap.get(specsName)?.add(specsValueName)
      })
    }
  })

  const specList: GoodsSpecItem[] = []
  specsMap.forEach((values, name) => {
    const list = Array.from(values).map(value => ({ specsValueName: value }))
    specList.push({
      specsName: name,
      list,
    })
  })

  goods.specList = specList
}
