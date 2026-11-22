/** 商品档案弹层的展示数据：只承载商品参数卡没有的深层信息。 */
export interface GoodsProfileNutrition {
  name?: string | null
  amount?: string | null
  unit?: string | null
  nrv?: string | null
}

export interface GoodsProfile {
  ingredients?: string | null
  nutrition?: GoodsProfileNutrition[] | null
  allergens?: string | null
  standard?: string | null
  certifications?: string[] | null
}

/** 商详「商品详情」区的结构化段落：标题 + 纯文本 + 可选配图。 */
export interface GoodsDetailSection {
  title?: string | null
  content?: string | null
  imageUrl?: string | null
}

function text(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

/**
 * 归一化后端 goods_profile。
 *
 * - 空对象/空字段返回 null：端上据此隐藏「商品档案」入口，不渲染空弹层；
 * - nutrition 只保留有项目名的行，数值和单位按运营录入的原文展示，不做二次换算；
 * - certifications 去空、去重，保留录入顺序。
 */
export function normalizeGoodsProfile(raw: unknown): GoodsProfile | null {
  if (!isRecord(raw))
    return null

  const ingredients = text(raw.ingredients)
  const allergens = text(raw.allergens)
  const standard = text(raw.standard)
  const nutrition = Array.isArray(raw.nutrition)
    ? raw.nutrition
        .filter(isRecord)
        .map(item => ({
          name: text(item.name),
          amount: text(item.amount),
          unit: text(item.unit),
          nrv: text(item.nrv),
        }))
        .filter(item => Boolean(item.name))
    : []
  const certifications = Array.isArray(raw.certifications)
    ? [...new Set(raw.certifications.map(text).filter(Boolean))]
    : []

  if (!ingredients && !allergens && !standard && nutrition.length === 0 && certifications.length === 0)
    return null

  return {
    ingredients,
    nutrition,
    allergens,
    standard,
    certifications,
  }
}

/** 只判断是否有内容，供参数卡决定是否显示「商品档案」入口。 */
export function hasGoodsProfile(profile: GoodsProfile | null | undefined): boolean {
  return normalizeGoodsProfile(profile) !== null
}

/**
 * 归一化 detail_sections：按数组顺序保留有标题或有正文的段落。
 * 图片缺失不影响文字段落；标题和正文都为空时整段丢弃。
 */
export function normalizeDetailSections(raw: unknown): GoodsDetailSection[] {
  if (!Array.isArray(raw))
    return []
  return raw
    .filter(isRecord)
    .map(section => ({
      title: text(section.title),
      content: text(section.content),
      imageUrl: text(section.imageUrl),
    }))
    .filter(section => Boolean(section.title || section.content || section.imageUrl))
}

export function hasGoodsDetailSections(sections: GoodsDetailSection[] | null | undefined): boolean {
  return Array.isArray(sections) && sections.length > 0
}
