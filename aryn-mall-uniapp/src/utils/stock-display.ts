/**
 * 商品库存的展示口径（商详 / 足迹收藏 / 装修卡片统一走这里）。
 *
 * 绝对库存与累计销量不外露：库存是运营敏感数据，销量在私有域小用户群里
 * 只会暴露「没什么人买」；对齐小象超市 / 叮咚买菜等自营即时零售的做法，
 * 只在库存真正紧张时给出稀缺提示（阈值与 diy-goods-tabs 卡片 chip、
 * 限时折扣会场的 ≤10 件口径一致）。
 *
 * 交互层的库存校验（加购封顶 / 缺货置灰 / 超量 toast）不经过这里，
 * 仍按真实库存执行。
 */
import { translate } from '@/locales'

export const STOCK_LOW_THRESHOLD = 10

/**
 * 库存提示文案：0 →「已售罄」；低库存（≤ 阈值）→「仅剩 N 件」；
 * 充足或未知（null / undefined / 空串 / 负数，后端 -1 表示未下发）→ 空串，
 * 由调用方整块隐藏，不渲染空节点。入参放宽到 string 是防后端数字字段
 * 以字符串形态下发。
 */
export function stockHintText(stock: number | string | null | undefined): string {
  if (stock === null || stock === undefined || stock === '')
    return ''
  const value = Number(stock)
  if (!Number.isFinite(value) || value < 0)
    return ''
  if (value <= 0)
    return translate('labels.soldOut')
  if (value <= STOCK_LOW_THRESHOLD)
    return translate('labels.onlyLeft', { count: value })
  return ''
}
