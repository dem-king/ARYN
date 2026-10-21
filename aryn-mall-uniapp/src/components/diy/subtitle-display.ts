/**
 * 商品子标题显示判定。
 *
 * 存量商品大量把商品名原样填进 `goods_spu.sub_title`；子标题与名称相同时
 * 直接隐藏，避免卡片出现两行完全相同的文字。判定口径与商详页 GoodsInfo 一致。
 */
export function shouldShowSubtitle(name: unknown, subTitle: unknown): boolean {
  const title = String(name ?? '').trim()
  const subtitle = String(subTitle ?? '').trim()
  return Boolean(subtitle) && subtitle !== title
}
