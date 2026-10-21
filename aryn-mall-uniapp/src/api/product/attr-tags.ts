/**
 * 商品属性标签（商详首屏属性卡）数据契约。
 *
 * 对应后端 `GoodsSpu.attrTags`（`GoodsAttrTag`），随商品详情接口
 * `/product/app/goodsspu/{id}` 一起下发：运营在管理端按商品配置，最多 6 个；
 * 属性值与属性名必填，图标、色调、点击行为可选。
 *
 * 接口字段是**系统边界输入**：旧数据可能缺字段、脏数据可能带任意值，
 * 因此这里只描述「接口原样字段」，归一化/降级统一收口到
 * `sub-pages/product/goods-detail/components/goods-attr-tags.ts`，
 * 组件只消费归一化后的视图模型。
 */

/** 卡片色调；未知值前端按 gray 兜底（与后端白名单一致） */
export const GOODS_ATTR_TAG_TONES = ['green', 'blue', 'gray', 'orange'] as const
export type GoodsAttrTagTone = (typeof GOODS_ATTR_TAG_TONES)[number]

/** 点击行为；未知值降级 none（与后端白名单一致） */
export const GOODS_ATTR_TAG_LINK_TYPES = ['none', 'popup', 'page'] as const
export type GoodsAttrTagLinkType = (typeof GOODS_ATTR_TAG_LINK_TYPES)[number]

/**
 * 预置图标 key 白名单（与后端 `GoodsAttrTagNormalizer.ICONS` 一致）。
 * 图标值还允许 http(s) 图片地址，两种形态由归一化模块区分。
 */
export const GOODS_ATTR_TAG_ICON_KEYS = [
  'location',
  'snowflake',
  'trophy',
  'package',
  'security',
  'time',
  'shopping-bag',
  'star-filled',
] as const
export type GoodsAttrTagIconKey = (typeof GOODS_ATTR_TAG_ICON_KEYS)[number]

/** 单商品最多 6 个：再多既挤占首屏标题层级，一屏内也滑不完 */
export const GOODS_ATTR_TAG_MAX = 6

/** 后端下发的一张属性卡（字段全部可缺失/可为 null） */
export interface GoodsAttrTagPayload {
  key?: string | null
  label?: string | null
  value?: string | null
  /** 预置图标 key 或 http(s) 图片地址 */
  icon?: string | null
  tone?: string | null
  linkType?: string | null
  popupTitle?: string | null
  popupContent?: string | null
  linkUrl?: string | null
}
