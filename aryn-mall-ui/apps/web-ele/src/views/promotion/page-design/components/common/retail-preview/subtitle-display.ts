/**
 * 商品子标题显示判定。
 *
 * 与小程序端 `aryn-mall-uniapp/src/components/diy/subtitle-display.ts` 同口径：
 * 存量商品大量把商品名原样填进 sub_title，子标题与名称相同时隐藏，
 * 避免管理端预览和小程序卡片出现两行完全相同的文字。
 */
export function shouldShowSubtitle(item: {
  name?: unknown;
  subTitle?: unknown;
}): boolean {
  const title = String(item.name ?? '').trim();
  const subtitle = String(item.subTitle ?? '').trim();
  return Boolean(subtitle) && subtitle !== title;
}
